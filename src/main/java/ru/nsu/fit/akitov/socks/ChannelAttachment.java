package ru.nsu.fit.akitov.socks;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionRequest;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChannelAttachment {
    ChannelState state;
    ByteBuffer inputBuffer;
    ByteBuffer outputBuffer;
    SelectionKey destination;
    ConnectionRequest request;
    InetAddress destinationAddress;
}
