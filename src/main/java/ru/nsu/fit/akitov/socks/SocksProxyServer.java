package ru.nsu.fit.akitov.socks;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import ru.nsu.fit.akitov.socks.dns.DomainNameStorage;
import ru.nsu.fit.akitov.socks.dns.ResolveQueues;
import ru.nsu.fit.akitov.socks.msg.exception.AddressNotSupportedException;
import ru.nsu.fit.akitov.socks.msg.exception.CommandNotSupportedException;
import ru.nsu.fit.akitov.socks.msg.exception.SocksException;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethod;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethodChoice;
import ru.nsu.fit.akitov.socks.msg.auth.AuthRequest;
import ru.nsu.fit.akitov.socks.msg.connection.AddressType;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionRequest;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.Duration;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocksProxyServer implements Runnable {

    static final int BUFFER_SIZE = 4096;
    static final InetSocketAddress DNS_SERVER_ADDRESS = ResolverConfig.getCurrentConfig().server();

    final int port;
    Selector selector;
    DatagramChannel dnsResolver;
    final ResolveQueues resolveQueues = new ResolveQueues();
    final DomainNameStorage domainNameStorage = new DomainNameStorage();

    @Override
    public void run() {
        try (ServerSocketChannel serverSocket = createServerSocket()) {
            dnsResolver = createResolver();
            selector = Selector.open();
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            dnsResolver.register(selector, SelectionKey.OP_READ);
            log.info("Started at port " + port);

            while (selector.select() >= 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                handleKeys(keys);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private ServerSocketChannel createServerSocket() throws IOException {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        return serverSocket;
    }

    private DatagramChannel createResolver() throws IOException {
        DatagramChannel resolver = DatagramChannel.open();
        resolver.socket().bind(new InetSocketAddress(0));
        resolver.configureBlocking(false);
        return resolver;
    }

    private void handleKeys(Set<SelectionKey> keys) {
        Iterator<SelectionKey> iterator = keys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (!key.isValid()) {
                continue;
            }
            try {
                if (key.isAcceptable()) {
                    registerChannel(key);
                } else if (key.isReadable()) {
                    readChannel(key);
                } else if (key.isConnectable()) {
                    connectChannel(key);
                } else if (key.isWritable()) {
                    writeChannel(key);
                } else {
                    throw new IllegalStateException("unknown key state");
                }
            } catch (IOException e) {
                try {
                    closeKey(key);
                } catch (IOException ex) {
                    log.error(ex.getMessage());
                }
            }
        }
    }

    private void registerChannel(SelectionKey key) throws IOException {
        SelectableChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }

    private void readChannel(SelectionKey key) throws IOException {
        if (key.channel().equals(dnsResolver)) {
            handleDnsResponse();
            return;
        }
        SocketChannel channel = (SocketChannel) key.channel();
        if (key.attachment() == null) {
            key.attach(ChannelAttachment.builder()
                    .state(ChannelState.AUTHORIZING)
                    .inputBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                    .build());
        }

        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        int bytesRead = channel.read(attachment.getInputBuffer());
        if (bytesRead <= 0) {
            closeKey(key);
            return;
        }
        if (attachment.getState() == ChannelState.AUTHORIZING || attachment.getState() == ChannelState.CONNECTING) {
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        if (attachment.getDestination() == null) {
            closeKey(key);
            return;
        }

        attachment.getDestination().interestOps(attachment.getDestination().interestOps() | SelectionKey.OP_WRITE);
        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
        attachment.getInputBuffer().flip();

        log.info(channel.getRemoteAddress() + " is sending data to " + ((SocketChannel) attachment.getDestination().channel()).getRemoteAddress());
    }

    private void handleDnsResponse() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        dnsResolver.receive(buffer);
        buffer.flip();
        Message response = new Message(buffer);
        if (response.getHeader().getRcode() != Rcode.NOERROR) {
            log.error("some domain name not resolved");
            return;
        }
        List<Record> records = response.getSection(Section.ANSWER);
        ARecord resolved = (ARecord) records.get(records.size() - 1);
        String domainName = records.get(0).getName().toString();
        domainName = domainName.substring(0, domainName.length() - 1);
        InetAddress resolvedAddress = resolved.getAddress();
        domainNameStorage.putDomainNameAddress(domainName, resolvedAddress);
        Set<SelectionKey> waiting = resolveQueues.remove(domainName);
        if (waiting == null) {
            return;
        }
        for (SelectionKey key : waiting) {
            ChannelAttachment attachment = (ChannelAttachment) key.attachment();
            attachment.setDestinationAddress(resolvedAddress);
            startConnection(key);
        }
    }

    private void closeKey(SelectionKey key) throws IOException {
        log.info("closing " + ((SocketChannel) key.channel()).getRemoteAddress());
        key.interestOps(0);
        key.channel().close();
        key.cancel();
        if (key.attachment() != null) {
            SelectionKey dest = ((ChannelAttachment) key.attachment()).getDestination();
            if (dest != null) {
                ((ChannelAttachment) dest.attachment()).setDestination(null);
                if (!dest.isWritable()) {
                    ((ChannelAttachment) dest.attachment()).getOutputBuffer().flip();
                }
                dest.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void writeChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        switch (attachment.getState()) {
            case AUTHORIZING -> authorize(key);
            case CONNECTING -> handleConnectionRequest(key);
            case PROXYING -> {
                int bytesWritten = channel.write(attachment.getOutputBuffer());
                if (bytesWritten == -1) {
                    closeKey(key);
                } else if (attachment.getOutputBuffer().remaining() == 0) {
                    if (attachment.getDestination() == null) {
                        closeKey(key);
                    } else {
                        attachment.getOutputBuffer().clear();
                        attachment.getDestination().interestOps(attachment.getDestination().interestOps() | SelectionKey.OP_READ);
                        key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                    }
                }
            }
        }
    }

    private void authorize(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        log.info(channel.getRemoteAddress() + " is being authorized");
        AuthRequest request;
        try {
            request = AuthRequest.buildFromByteBuffer(attachment.getInputBuffer());
        } catch (SocksException e) {
            log.error("auth request error: " + e.getMessage());
            closeKey(key);
            return;
        }
        AuthMethodChoice methodChoice = AuthMethodChoice.choose(request.suggestedMethods());
        channel.write(methodChoice.toByteBuffer());
        if (methodChoice.getChosen() == AuthMethod.NO_ACCEPTABLE_METHOD) {
            log.error("couldn't authorize " + channel.getRemoteAddress());
            closeKey(key);
            return;
        }
        log.info(channel.getRemoteAddress() + " successfully authorized");
        key.interestOps(SelectionKey.OP_READ);
        attachment.setState(ChannelState.CONNECTING);
        attachment.getInputBuffer().clear();
    }

    private void handleConnectionRequest(SelectionKey key) throws IOException {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionRequest request;
        try {
            request = ConnectionRequest.buildFromByteBuffer(attachment.getInputBuffer());
        } catch (CommandNotSupportedException e) {
            log.error(e.getMessage());
            channel.write(ConnectionResponse.builder().responseCode(SocksConfiguration.STATUS_COMMAND_NOT_SUPPORTED)
                    .build().toByteBuffer());
            closeKey(key);
            return;
        } catch (AddressNotSupportedException e) {
            log.error(e.getMessage());
            channel.write(ConnectionResponse.builder().responseCode(SocksConfiguration.STATUS_ADDRESS_NOT_SUPPORTED)
                    .build().toByteBuffer());
            closeKey(key);
            return;
        } catch (SocksException e) {
            log.error(e.getMessage());
            channel.write(ConnectionResponse.builder().responseCode(SocksConfiguration.STATUS_GENERAL_FAILURE)
                    .build().toByteBuffer());
            closeKey(key);
            return;
        }
        attachment.setRequest(request);
        attachment.getInputBuffer().clear();
        if (request.addressType() != AddressType.DOMAIN) {
            attachment.setDestinationAddress(InetAddress.getByName(request.getHostName()));
            startConnection(key);
        } else {
            Optional<InetAddress> address = domainNameStorage.getDomainNameAddress(request.getHostName());
            if (address.isEmpty()) {
                startResolving(key, request.getHostName());
            } else {
                attachment.setDestinationAddress(address.get());
                startConnection(key);
            }
        }
    }

    private void startConnection(SelectionKey key) throws IOException {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        InetSocketAddress connectionAddress = new InetSocketAddress(attachment.getDestinationAddress(),
                attachment.getRequest().port());
        SocketChannel destination = createConnectionChannel(connectionAddress,
                ((SocketChannel) key.channel()).getRemoteAddress());

        SelectionKey destKey = destination.register(key.selector(), SelectionKey.OP_CONNECT);
        key.interestOps(0);
        attachment.setDestination(destKey);
        destKey.attach(ChannelAttachment.builder()
                .state(ChannelState.PROXYING)
                .destination(key)
                .build());
        ((ChannelAttachment) destKey.attachment()).setRequest(attachment.getRequest());
        attachment.setState(ChannelState.PROXYING);
    }

    private void startResolving(SelectionKey key, String domainName) throws IOException {
        log.info("resolving " + domainName);
        key.interestOps(0);
        InetSocketAddress dnsServer = ResolverConfig.getCurrentConfig().servers().get(0);
        Resolver resolver = new SimpleResolver(dnsServer);
        resolver.setTCP(false);

        Message query = Message.newQuery(Record.newRecord(Name.fromString(domainName + "."), Type.A, DClass.IN));
        dnsResolver.send(ByteBuffer.wrap(query.toWire()), DNS_SERVER_ADDRESS);
        resolveQueues.put(domainName, key);
    }

    private SocketChannel createConnectionChannel(InetSocketAddress address,
                                                  SocketAddress clientAddress) throws IOException {
        SocketChannel destination = null;
        try {
            destination = SocketChannel.open();
            destination.configureBlocking(false);
            log.info(clientAddress + " connecting to " + address.getHostName());
            destination.connect(address);
        } catch (IOException e) {
            if (destination != null) {
                destination.close();
            }
            throw e;
        }
        return destination;
    }

    private void connectChannel(SelectionKey key) throws IOException {
        SocketChannel destChannel = (SocketChannel) key.channel();
        ChannelAttachment destAttachment = (ChannelAttachment) key.attachment();
        SocketChannel clientChannel = (SocketChannel) destAttachment.getDestination().channel();
        ChannelAttachment clientAttachment = (ChannelAttachment) destAttachment.getDestination().attachment();

        String address = destAttachment.getRequest().getHostName();
        if (!destChannel.finishConnect()) {
            log.error("couldn't connect to " + address);
            destChannel.write(ConnectionResponse.builder()
                    .responseCode(SocksConfiguration.STATUS_CONNECTION_REFUSED)
                    .request(destAttachment.getRequest())
                    .build().toByteBuffer());
            closeKey(key);
            return;
        }

        log.info(clientChannel.getRemoteAddress() + " connected to " + address);
        destAttachment.setInputBuffer(ByteBuffer.allocate(BUFFER_SIZE));
        destAttachment.setOutputBuffer(clientAttachment.getInputBuffer());
        clientAttachment.setOutputBuffer(destAttachment.getInputBuffer());

        ConnectionResponse response = ConnectionResponse.builder()
                        .request(destAttachment.getRequest()).responseCode(SocksConfiguration.STATUS_GRANTED)
                        .build();

        destAttachment.getInputBuffer().put(response.toByteBuffer().array()).flip();

        clientAttachment.setState(ChannelState.PROXYING);
        destAttachment.setState(ChannelState.PROXYING);
        destAttachment.getDestination().interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps(0);
    }

}
