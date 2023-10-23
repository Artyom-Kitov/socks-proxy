package ru.nsu.fit.akitov.socks.msg.auth;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

import java.nio.ByteBuffer;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
@Getter
public class AuthMethodChoice {

    private AuthMethod chosen;

    public static AuthMethodChoice choose(List<AuthMethod> authMethods) {
        AuthMethod method;
        if (authMethods.contains(AuthMethod.NO_AUTH)) {
            method = AuthMethod.NO_AUTH;
        } else {
            method = AuthMethod.NO_ACCEPTABLE_METHOD;
        }
        return AuthMethodChoice.builder()
                .chosen(method)
                .build();
    }

    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(new byte[] { SocksConfiguration.VERSION, chosen.getRepresentation() });
    }

}
