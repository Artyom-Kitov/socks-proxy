package ru.nsu.fit.akitov.socks;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SocksConfiguration {

    public final byte VERSION = 0x05;
    public final byte NO_AUTH = 0x00;
    public final byte AUTH_NO_ACCEPTABLE = (byte) 0xFF;
    public final byte IPv4_ADDRESS = 0x01;
    public final byte DOMAIN_NAME = 0x03;
    public final byte IPv6_ADDRESS = 0x04;
    public final byte TCP_CONNECT = 0x01;
    public final byte STATUS_GRANTED = 0x00;
    public final byte STATUS_GENERAL_FAILURE = 0x01;
    public final byte STATUS_CONNECTION_NOT_ALLOWED = 0x02;
    public final byte STATUS_NET_UNREACHABLE = 0x03;
    public final byte STATUS_HOST_UNREACHABLE = 0x04;
    public final byte STATUS_REFUSED = 0x05;
    public final byte STATUS_TTL_EXPIRED = 0x06;
    public final byte STATUS_COMMAND_NOT_SUPPORTED = 0x07;
    public final byte STATUS_ADDRESS_NOT_SUPPORTED = 0x08;

}
