package ru.balandin.minikv;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Typed, in-memory key-value storage inspired by the C++ Valkey-like lab.
 * Every operation is synchronized so an HTTP handler observes whole updates.
 */
public final class KeyValueStore {
    public sealed interface Value permits Text, Sequence, Members {}
    public record Text(String content) implements Value {}
    public record Sequence(List<String> items) implements Value {
        public Sequence { items = List.copyOf(items); }
    }
    public record Members(Set<String> items) implements Value {
        public Members { items = Set.copyOf(items); }
    }
    private record Entry(Value value, Instant expiresAt) {}

    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;

    public KeyValueStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized void set(String key, String text) {
        requireKey(key);
        entries.put(key, new Entry(new Text(Objects.requireNonNull(text)), null));
    }

    public synchronized String append(String key, String suffix) {
        Value current = get(key).orElse(new Text(""));
        if (!(current instanceof Text text)) throw new IllegalStateException("wrong value type");
        String result = text.content() + Objects.requireNonNull(suffix);
        entries.put(key, new Entry(new Text(result), expiry(key)));
        return result;
    }

    public synchronized List<String> push(String key, String item) {
        Value current = get(key).orElse(new Sequence(List.of()));
        if (!(current instanceof Sequence sequence)) throw new IllegalStateException("wrong value type");
        List<String> result = new ArrayList<>(sequence.items());
        result.add(Objects.requireNonNull(item));
        entries.put(key, new Entry(new Sequence(result), expiry(key)));
        return List.copyOf(result);
    }

    public synchronized Set<String> addMember(String key, String item) {
        Value current = get(key).orElse(new Members(Set.of()));
        if (!(current instanceof Members members)) throw new IllegalStateException("wrong value type");
        Set<String> result = new LinkedHashSet<>(members.items());
        result.add(Objects.requireNonNull(item));
        entries.put(key, new Entry(new Members(result), expiry(key)));
        return Set.copyOf(result);
    }

    public synchronized Optional<Value> get(String key) {
        requireKey(key);
        Entry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiresAt() != null && !clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized boolean expire(String key, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("positive ttl required");
        if (get(key).isEmpty()) return false;
        Entry current = entries.get(key);
        entries.put(key, new Entry(current.value(), clock.instant().plus(ttl)));
        return true;
    }

    public synchronized boolean delete(String key) {
        get(key);
        return entries.remove(key) != null;
    }

    public synchronized int size() {
        for (String key : List.copyOf(entries.keySet())) {
            get(key);
        }
        return entries.size();
    }

    private Instant expiry(String key) {
        Entry entry = entries.get(key);
        return entry == null ? null : entry.expiresAt();
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
    }
}
