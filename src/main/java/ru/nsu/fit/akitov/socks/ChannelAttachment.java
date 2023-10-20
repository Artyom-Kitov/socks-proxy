package ru.nsu.fit.akitov.socks;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChannelAttachment {
    boolean authorized;
    ByteBuffer inputBuffer;
    ByteBuffer outputBuffer;
    SelectionKey destination;
}
