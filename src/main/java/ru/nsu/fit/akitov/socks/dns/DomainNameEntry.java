package ru.nsu.fit.akitov.socks.dns;

import lombok.Builder;

import java.net.InetAddress;
import java.time.Instant;

@Builder
public record DomainNameEntry(InetAddress address, Instant expiresAt) {
}
