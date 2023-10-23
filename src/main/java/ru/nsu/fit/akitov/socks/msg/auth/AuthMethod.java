package ru.nsu.fit.akitov.socks.msg.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

@Getter
@AllArgsConstructor
public enum AuthMethod {

    NO_AUTH(SocksConfiguration.NO_AUTH),
    NO_ACCEPTABLE_METHOD(SocksConfiguration.AUTH_NO_ACCEPTABLE);

    public static AuthMethod of(byte representation) {
        for (AuthMethod method : AuthMethod.values()) {
            if (method.representation == representation) {
                return method;
            }
        }
        throw new IllegalArgumentException("authentication method is not supported");
    }

    private final byte representation;
}
