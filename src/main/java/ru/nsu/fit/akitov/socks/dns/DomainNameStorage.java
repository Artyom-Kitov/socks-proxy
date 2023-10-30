package ru.nsu.fit.akitov.socks.dns;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
public class DomainNameStorage {

    static final Duration RECORD_TTL = Duration.ofHours(1);

    public final Map<String, DomainNameEntry> hosts = new HashMap<>();

    public void putDomainNameAddress(String name, InetAddress address) {
        DomainNameEntry domainNameEntry = DomainNameEntry.builder()
                .address(address)
                .expiresAt(Instant.now().plus(RECORD_TTL))
                .build();
        hosts.put(name, domainNameEntry);
    }

    public Optional<InetAddress> getDomainNameAddress(String name) {
        DomainNameEntry domainNameEntry = hosts.get(name);
        if (domainNameEntry == null) {
            return Optional.empty();
        }
        if (domainNameEntry.expiresAt().isBefore(Instant.now())) {
            hosts.remove(name);
            return Optional.empty();
        }
        return Optional.of(domainNameEntry.address());
    }

}
