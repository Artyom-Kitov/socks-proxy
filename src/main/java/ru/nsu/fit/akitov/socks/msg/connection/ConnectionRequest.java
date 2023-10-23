package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;

import java.nio.ByteBuffer;

@Builder
public record ConnectionRequest(ProxyCommand command, AddressType addressType, byte[] rawAddress, int port) {

    public static ConnectionRequest buildFromByteBuffer(ByteBuffer buffer) throws MessageBuildException {
        int version = buffer.get(0);
        if (version != SocksConfiguration.VERSION) {
            throw new MessageBuildException("SOCKS" + version + "is not supported");
        }
        if (buffer.get(2) != 0) {
            throw new MessageBuildException("reserved byte is not zero");
        }

        ProxyCommand command;
        try {
            command = ProxyCommand.of(buffer.get(1));
        } catch (IllegalArgumentException e) {
            throw new MessageBuildException(e);
        }

        AddressType type;
        try {
            type = AddressType.of(buffer.get(3));
        } catch (IllegalArgumentException e) {
            throw new MessageBuildException(e);
        }

        byte[] rawAddress = getRawAddress(buffer, type);
        int portIndex = type == AddressType.DOMAIN ? 4 + rawAddress.length : 3 + rawAddress.length;
        byte[] portBytes = new byte[2];
        buffer.slice(portIndex, 2).get(portBytes);
        int port = portBytes[1] << 8 | portBytes[0];
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
                buffer.slice(4, 4).get(result);
            }
            case IPv6 -> {
                result = new byte[16];
                buffer.slice(4, 16).get(result);
            }
            case DOMAIN -> {
                int length = buffer.get(4);
                result = new byte[length];
                buffer.slice(5, length).get(result);
            }
            default -> throw new IllegalStateException("unexpected address type: " + type);
        }
        return result;
    }

}
