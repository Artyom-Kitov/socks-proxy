package ru.nsu.fit.akitov.socks;

import lombok.Builder;
import lombok.Data;
import ru.nsu.fit.akitov.socks.msg.connection.ConnectionRequest;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

@Data
@Builder
public class ChannelAttachment {
    private ChannelState state;
    private ByteBuffer inputBuffer;
    private ByteBuffer outputBuffer;
    private SelectionKey destination;
    private ConnectionRequest request;
    private InetAddress destinationAddress;
}
