package ru.nsu.fit.akitov.socks.msg.auth;

import lombok.Builder;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.AuthMethod;
import ru.nsu.fit.akitov.socks.msg.MessageBuildException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
public class AuthRequest {

    private List<AuthMethod> suggestedMethods;

    public static AuthRequest buildFromByteBuffer(ByteBuffer buffer) throws MessageBuildException {
        int version = buffer.get(0);
        if (version != SocksConfiguration.VERSION) {
            throw new MessageBuildException("SOCKS" + version + " not supported");
        }
        int amountSuggestedMethods = buffer.get(1);
        List<AuthMethod> suggestedMethods = new ArrayList<>();
        for (int i = 0; i < amountSuggestedMethods; i++) {
            suggestedMethods.add(AuthMethod.of(buffer.get(2 + i)));
        }
        return AuthRequest.builder()
                .suggestedMethods(suggestedMethods)
                .build();
    }

}
