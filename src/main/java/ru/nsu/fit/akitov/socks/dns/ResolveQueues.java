package ru.nsu.fit.akitov.socks.dns;

import lombok.NoArgsConstructor;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor
public class ResolveQueues {

    private final Map<String, Set<SelectionKey>> nameResolveRequiring = new HashMap<>();

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
