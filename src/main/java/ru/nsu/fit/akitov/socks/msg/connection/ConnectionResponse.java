package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.Builder;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

import java.nio.ByteBuffer;

@Builder
public record ConnectionResponse(byte responseCode) {

    public ByteBuffer toByteBuffer() {
        ByteBuffer result = ByteBuffer.allocate(3);
        result.put(SocksConfiguration.VERSION);
        result.put(responseCode);
        return result;
    }

}
