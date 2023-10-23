package ru.nsu.fit.akitov.socks.msg.auth;

import lombok.Builder;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Builder
public record AuthRequest(List<AuthMethod> suggestedMethods) {

    public static AuthRequest buildFromByteBuffer(ByteBuffer buffer) throws MessageBuildException {
        int version = buffer.get(0);
        if (version != SocksConfiguration.VERSION) {
            throw new MessageBuildException("SOCKS" + version + " is not supported");
        }
        int amountSuggestedMethods = buffer.get(1);
        List<AuthMethod> suggestedMethods = new ArrayList<>();
        for (int i = 0; i < amountSuggestedMethods; i++) {
            try {
                suggestedMethods.add(AuthMethod.of(buffer.get(2 + i)));
            } catch (IllegalArgumentException ignore) {
            }
        }
        return AuthRequest.builder()
                .suggestedMethods(suggestedMethods)
                .build();
    }

}
