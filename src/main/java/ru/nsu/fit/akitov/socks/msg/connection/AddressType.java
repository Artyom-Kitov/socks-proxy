package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

@Getter
@AllArgsConstructor
public enum AddressType {

    IPv4(SocksConfiguration.ADDRESS_IPv4),
    IPv6(SocksConfiguration.ADDRESS_IPv6),
    DOMAIN(SocksConfiguration.ADDRESS_DOMAIN);

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
