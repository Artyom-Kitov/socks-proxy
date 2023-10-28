package ru.nsu.fit.akitov.socks;

import lombok.extern.log4j.Log4j2;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethod;
import ru.nsu.fit.akitov.socks.msg.auth.AuthMethodChoice;
import ru.nsu.fit.akitov.socks.msg.auth.AuthRequest;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionRequest;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
            log.info(Arrays.toString(attachment.getInputBuffer().array()) + " nowhere to write :(");
            closeKey(key);
            return;
        }

        attachment.getDestination().interestOps(attachment.getDestination().interestOps() | SelectionKey.OP_WRITE);
        key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
        attachment.getInputBuffer().flip();

        log.info(channel.getRemoteAddress() + " is sending data to " + ((SocketChannel) attachment.getDestination().channel()).getRemoteAddress());
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
        } catch (MessageBuildException e) {
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
        ConnectionRequest request;
        try {
            request = ConnectionRequest.buildFromByteBuffer(attachment.getInputBuffer());
        } catch (MessageBuildException e) {
            log.error("connection request error: " + e.getMessage());
            closeKey(key);
            return;
        }

        InetSocketAddress connectionAddress = request.getSocketAddress();
        SocketChannel destination = createConnectionChannel(connectionAddress, ((SocketChannel) key.channel()).getRemoteAddress());
        SelectionKey destKey = destination.register(key.selector(), SelectionKey.OP_CONNECT);
        key.interestOps(0);
        attachment.setDestination(destKey);
        destKey.attach(ChannelAttachment.builder()
                .state(ChannelState.PROXYING)
                .destination(key)
                .build());
        ((ChannelAttachment) destKey.attachment()).setRequest(request);
        attachment.setState(ChannelState.PROXYING);
        attachment.getInputBuffer().clear();
    }

    private SocketChannel createConnectionChannel(InetSocketAddress address, SocketAddress client) throws IOException {
        SocketChannel destination = null;
        try {
            destination = SocketChannel.open();
            destination.configureBlocking(false);
            log.info(client + " connecting to " + address.getHostName());
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

        InetSocketAddress address = destAttachment.getRequest().getSocketAddress();
        if (!destChannel.finishConnect()) {
            log.error("couldn't connect to " + address);
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
