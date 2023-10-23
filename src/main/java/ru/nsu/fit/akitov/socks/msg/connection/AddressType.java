package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

@Getter
@AllArgsConstructor
public enum AddressType {

    IPv4(SocksConfiguration.IPv4_ADDRESS),
    IPv6(SocksConfiguration.IPv6_ADDRESS),
    DOMAIN(SocksConfiguration.DOMAIN_NAME);

    public static AddressType of(byte representation) {
        for (AddressType type : AddressType.values()) {
            if (type.representation == representation) {
                return type;
            }
        }
        throw new IllegalArgumentException("address type is not supported");
    }

    private final byte representation;

}
