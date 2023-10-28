package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.Builder;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Builder
public record ConnectionRequest(ProxyCommand command, AddressType addressType, byte[] rawAddress, int port) {

    public static ConnectionRequest buildFromByteBuffer(ByteBuffer buffer) throws MessageBuildException {
        buffer.position(0);
        int version = buffer.get();
        if (version != SocksConfiguration.VERSION) {
            throw new MessageBuildException("SOCKS" + version + " is not supported");
        }

        ProxyCommand command;
        try {
            command = ProxyCommand.of(buffer.get());
        } catch (IllegalArgumentException e) {
            throw new MessageBuildException(e);
        }

        if (buffer.get() != 0) {
            throw new MessageBuildException("reserved byte is not zero");
        }

        AddressType type;
        try {
            type = AddressType.of(buffer.get());
        } catch (IllegalArgumentException e) {
            throw new MessageBuildException(e);
        }
        byte[] rawAddress = getRawAddress(buffer, type);
        int port = ((0xFF & buffer.get()) << 8) | (0xFF & buffer.get());
        return ConnectionRequest.builder()
                .command(command)
                .addressType(type)
                .rawAddress(rawAddress)
                .port(port)
                .build();
    }

    private static byte[] getRawAddress(ByteBuffer buffer, AddressType type) {
        byte[] result;
        switch (type) {
            case IPv4 -> {
                result = new byte[4];
                buffer.get(result);
            }
            case IPv6 -> {
                result = new byte[16];
                buffer.get(result);
            }
            case DOMAIN -> {
                int length = buffer.get(buffer.position());
                result = new byte[length + 1];
                buffer.get(result);
            }
            default -> throw new IllegalStateException("unexpected address type: " + type);
        }
        return result;
    }

    public InetSocketAddress getSocketAddress() throws UnknownHostException {
        InetAddress address;
        switch (addressType()) {
            case IPv4, IPv6 -> address = InetAddress.getByAddress(rawAddress);
            case DOMAIN -> address = InetAddress.getByName(new String(Arrays.copyOfRange(rawAddress, 1, rawAddress.length)));
            default -> throw new IllegalStateException();
        }
        return new InetSocketAddress(address, port);
    }

}
