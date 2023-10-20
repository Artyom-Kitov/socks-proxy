package ru.nsu.fit.akitov.socks.msg;

import java.nio.ByteBuffer;

public interface ServerResponse {
    ByteBuffer toByteBuffer();
}
