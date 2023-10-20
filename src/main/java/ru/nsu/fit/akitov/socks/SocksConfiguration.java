package ru.nsu.fit.akitov.socks;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SocksConfiguration {

    public byte VERSION = 0x05;
    public byte NO_AUTH = 0x00;
    public byte AUTH_NO_ACCEPTABLE = (byte) 0xFF;
    public byte IPv4_ADDRESS = 0x01;
    public byte DOMAIN_NAME = 0x03;
    public byte IPv6_ADDRESS = 0x04;
    public byte TCP_CONNECT = 0x01;
    public byte STATUS_GRANTED = 0x00;
    public byte STATUS_GENERAL_FAILURE = 0x01;
    public byte STATUS_CONNECTION_NOT_ALLOWED = 0x02;
    public byte STATUS_NET_UNREACHABLE = 0x03;
    public byte STATUS_HOST_UNREACHABLE = 0x04;
    public byte STATUS_REFUSED = 0x05;
    public byte STATUS_TTL_EXPIRED = 0x06;
    public byte STATUS_COMMAND_NOT_SUPPORTED = 0x07;
    public byte STATUS_ADDRESS_NOT_SUPPORTED = 0x08;

}
