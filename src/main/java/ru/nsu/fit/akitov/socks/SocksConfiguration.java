package ru.nsu.fit.akitov.socks;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SocksConfiguration {
    public final byte VERSION = 0x05;
    public final byte NO_AUTH = 0x00;
    public final byte AUTH_NO_ACCEPTABLE = (byte) 0xFF;
    public final byte ADDRESS_IPv4 = 0x01;
    public final byte ADDRESS_DOMAIN = 0x03;
    public final byte ADDRESS_IPv6 = 0x04;
    public final byte CMD_TCP_CONNECT = 0x01;
    public final byte STATUS_GRANTED = 0x00;
    public final byte STATUS_GENERAL_FAILURE = 0x01;
    public final byte STATUS_CONNECTION_REFUSED = 0x05;
    public final byte STATUS_COMMAND_NOT_SUPPORTED = 0x07;
    public final byte STATUS_ADDRESS_NOT_SUPPORTED = 0x08;
}
