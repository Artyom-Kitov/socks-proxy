package ru.nsu.fit.akitov.socks;

import lombok.extern.log4j.Log4j2;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethod;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethodChoice;
import ru.nsu.fit.akitov.socks.msg.auth.AuthRequest;
import ru.nsu.fit.akitov.socks.msg.connection.AddressType;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionRequest;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

@Log4j2
public class SocksProxyServer implements Runnable {

    private static final int BUFFER_SIZE = 4096;

    private final int port;
    private Selector selector;

    public SocksProxyServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocketChannel serverSocket = createServerSocket()) {
            selector = Selector.open();
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            log.info("Started at port " + port);

            while (selector.select() > 0) {
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
                log.error(e.getMessage());
            }
        }
    }

    private void registerChannel(SelectionKey key) throws IOException {
        SelectableChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }

    private void readChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if (key.attachment() == null) {
            key.attach(ChannelAttachment.builder().authorized(false).proxying(false)
                    .inputBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                    .build());
        }

        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        int bytesRead = channel.read(attachment.getInputBuffer());
        if (bytesRead <= 0) {
            closeKey(key);
            return;
        }
        if (!attachment.isAuthorized() || !attachment.isProxying()) {
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }

        log.info("reading from " + channel.getRemoteAddress());
        attachment.getDestination().interestOps(attachment.getDestination().interestOps() | SelectionKey.OP_WRITE);
        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
        attachment.getInputBuffer().flip();
    }

    private void closeKey(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey destKey = ((ChannelAttachment) key.attachment()).getDestination();
        if (destKey != null) {
            ((ChannelAttachment) destKey.attachment()).setDestination(null);
            if ((destKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((ChannelAttachment) destKey.attachment()).getOutputBuffer().flip();
            }
            destKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void writeChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        if (!attachment.isAuthorized()) {
            log.info(channel.getRemoteAddress() + " is being authorized");
            if (!authorize(key)) {
                log.error("couldn't authorize " + channel.getRemoteAddress());
                closeKey(key);
                return;
            }
            key.interestOps(SelectionKey.OP_READ);
            return;
        }
        if (!attachment.isProxying()) {
            if (!handleConnectionRequest(key)) {
                closeKey(key);
            }
            return;
        }

        if (channel.write(attachment.getOutputBuffer()) == -1) {
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

    private boolean authorize(SelectionKey key) throws IOException {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        AuthRequest request;
        try {
            request = AuthRequest.buildFromByteBuffer(attachment.getInputBuffer());
        } catch (MessageBuildException e) {
            log.error(e.getMessage());
            return false;
        }

        SocketChannel channel = (SocketChannel) key.channel();
        AuthMethodChoice choice = AuthMethodChoice.choose(request.suggestedMethods());
        channel.write(AuthMethodChoice.choose(request.suggestedMethods()).toByteBuffer());
        if (choice.getChosen() == AuthMethod.NO_ACCEPTABLE_METHOD) {
            return false;
        }
        attachment.setAuthorized(true);
        attachment.getInputBuffer().clear();
        return true;
    }

    private boolean handleConnectionRequest(SelectionKey key) throws IOException {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        ConnectionRequest request;
        try {
            request = ConnectionRequest.buildFromByteBuffer(attachment.getInputBuffer());
        } catch (MessageBuildException e) {
            log.error(e.getMessage());
            return false;
        }

        InetAddress connectionAddress = request.addressType() == AddressType.DOMAIN ?
                InetAddress.getByName(new String(request.rawAddress())) :
                InetAddress.getByAddress(request.rawAddress());

        SocketChannel destination = getConnectionChannel(connectionAddress, request.port());
        SelectionKey destKey = destination.register(key.selector(), SelectionKey.OP_CONNECT);
        key.interestOps(0);
        attachment.setDestination(destKey);
        destKey.attach(ChannelAttachment.builder().authorized(true).proxying(true).destination(key)
                .inputBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                .build());

        attachment.setProxying(true);
        attachment.getInputBuffer().clear();
        return true;
    }

    private SocketChannel getConnectionChannel(InetAddress address, int port) throws IOException {
        SocketChannel destination = null;
        try {
            destination = SocketChannel.open();
            destination.configureBlocking(false);
            log.info("connecting to " + address.getCanonicalHostName());
            destination.connect(new InetSocketAddress(address, port));
        } catch (IOException e) {
            if (destination != null) {
                destination.close();
            }
            throw e;
        }
        return destination;
    }

    private void connectChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        SelectionKey clientKey = ((ChannelAttachment) key.attachment()).getDestination();
        SocketChannel clientChannel = (SocketChannel) clientKey.channel();
        if (!channel.finishConnect()) {
            clientChannel.write(ConnectionResponse.builder()
                    .responseCode(SocksConfiguration.STATUS_HOST_UNREACHABLE)
                    .build().toByteBuffer());
            closeKey(key);
            return;
        }

        clientChannel.write(ConnectionResponse.builder()
                .responseCode(SocksConfiguration.STATUS_GRANTED)
                .build().toByteBuffer());

        log.info("connected to " + channel.getRemoteAddress());

        ChannelAttachment destAttachment = (ChannelAttachment) clientKey.attachment();
        attachment.setOutputBuffer(destAttachment.getInputBuffer());
        destAttachment.setOutputBuffer(attachment.getInputBuffer());
        clientKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        key.interestOps(0);
    }

}
