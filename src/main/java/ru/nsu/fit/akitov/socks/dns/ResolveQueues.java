package ru.nsu.fit.akitov.socks.dns;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
public class ResolveQueues {

    final Map<String, Set<SelectionKey>> nameResolveRequiring = new HashMap<>();

    public void put(String name, SelectionKey key) {
        Set<SelectionKey> requiring = nameResolveRequiring.get(name);
        if (requiring == null) {
            requiring = new HashSet<>();
        }
        requiring.add(key);
        nameResolveRequiring.put(name, requiring);
    }

    public Set<SelectionKey> remove(String name) {
        return nameResolveRequiring.remove(name);
    }

}
