package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.Builder;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.exception.AddressNotSupportedException;
import ru.nsu.fit.akitov.socks.msg.exception.CommandNotSupportedException;
import ru.nsu.fit.akitov.socks.msg.exception.SocksException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Builder
public record ConnectionRequest(ProxyCommand command, AddressType addressType, byte[] rawAddress, int port) {

    public static ConnectionRequest buildFromByteBuffer(ByteBuffer buffer) throws SocksException {
        buffer.position(0);
        int version = buffer.get();
        if (version != SocksConfiguration.VERSION) {
            throw new SocksException("SOCKS" + version + " is not supported");
        }

        ProxyCommand command;
        try {
            command = ProxyCommand.of(buffer.get());
        } catch (IllegalArgumentException e) {
            throw new CommandNotSupportedException(e.getMessage());
        }

        if (buffer.get() != 0) {
            throw new SocksException("reserved byte is not zero");
        }

        AddressType type;
        try {
            type = AddressType.of(buffer.get());
        } catch (IllegalArgumentException e) {
            throw new AddressNotSupportedException(e.getMessage());
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

    public String getHostName() throws UnknownHostException {
        switch (addressType) {
            case IPv4, IPv6 -> {
                return InetAddress.getByAddress(rawAddress).getHostAddress();
            }
            case DOMAIN -> {
                return new String(Arrays.copyOfRange(rawAddress, 1, rawAddress.length));
            }
            default -> throw new IllegalStateException("unknown address type");
        }
    }

}
