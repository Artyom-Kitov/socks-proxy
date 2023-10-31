package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.Builder;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

import java.nio.ByteBuffer;

@Builder
public record ConnectionResponse(byte responseCode, ConnectionRequest request) {

    public ByteBuffer toByteBuffer() {
        if (request == null) {
            return ByteBuffer.wrap(new byte[]{SocksConfiguration.VERSION, responseCode, 0});
        }
        ByteBuffer result = ByteBuffer.allocate(6 + request.rawAddress().length);
        result.put(SocksConfiguration.VERSION);
        result.put(responseCode);
        result.put((byte) 0);
        result.put(request.addressType().getRepresentation());
        result.put(request.rawAddress());
        result.put((byte) ((request.port() & 0xff00) >> 8));
        result.put((byte) (request.port() & 0x00ff));
        return result;
    }

}
