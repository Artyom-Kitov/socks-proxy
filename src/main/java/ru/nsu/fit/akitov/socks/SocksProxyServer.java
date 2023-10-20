package ru.nsu.fit.akitov.socks;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

@Log4j2
public class SocksProxyServer implements Runnable {



    private static final int BUFFER_SIZE = 1024;

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

            while (selector.select() > -1) {
                Set<SelectionKey> keys = selector.selectedKeys();
                poll(keys);
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

    private void poll(Set<SelectionKey> keys) {
        Iterator<SelectionKey> iterator = keys.iterator();
        while (iterator.hasNext()) {
            try {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    registerChannel(key);
                } else if (key.isReadable()) {
                    readChannel(key);
                } else if (key.isConnectable()) {

                } else if (key.isWritable()) {

                } else {
                    throw new IllegalStateException("unknown key state");
                }
            } catch (IOException e) {
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
            key.attach(ChannelAttachment.builder()
                    .authorized(false)
                    .inputBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                    .outputBuffer(ByteBuffer.allocate(BUFFER_SIZE))
                    .build());
        }

        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        int bytesRead = channel.read(attachment.getInputBuffer());
        if (bytesRead <= 0 || attachment.getInputBuffer().get(0) != SocksConfiguration.VERSION) {
            channel.close();
        } else if (!attachment.isAuthorized()) {
            sendAuthorizationResponse(key);
        }
    }

    private void sendAuthorizationResponse(SelectionKey key) {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        byte[] buffer = attachment.getInputBuffer().array();
        for (int i = 0; i < buffer[1]; i++) {
            if (buffer[2 + i] == SocksConfiguration.NO_AUTH) {
                //
            }
        }
    }

    private void writeChannel(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        log.info(channel.getRemoteAddress() + " wants to write");
    }

}
