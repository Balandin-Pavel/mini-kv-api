package ru.balandin.minikv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class MiniKvTest {
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock();
        KeyValueStore store = new KeyValueStore(clock);
        store.set("a", "hello");
        check(store.append("a", " world").equals("hello world"));
        check(store.expire("a", Duration.ofSeconds(5)));
        clock.advance(Duration.ofSeconds(4));
        check(store.get("a").isPresent());
        clock.advance(Duration.ofSeconds(1));
        check(store.size() == 0);
        check(store.get("a").isEmpty());
        store.push("queue", "first");
        store.push("queue", "second");
        check(((KeyValueStore.Sequence) store.get("queue").orElseThrow()).items().size() == 2);
        store.addMember("tags", "java");
        store.addMember("tags", "java");
        check(((KeyValueStore.Members) store.get("tags").orElseThrow()).items().size() == 1);
        try {
            store.append("queue", "wrong");
            throw new AssertionError("type mismatch accepted");
        } catch (IllegalStateException expected) { }

        var server = new MiniKvServer(new KeyValueStore(Clock.systemUTC())).start(0);
        try {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/keys/demo";
            check(send(client, base, "PUT", "hello").statusCode() == 200);
            check(send(client, base, "GET", "").body().contains("\"value\":\"hello\""));
            check(send(client, base + "/append", "POST", " world").statusCode() == 200);
            check(send(client, base, "GET", "").body().contains("hello world"));
            check(send(client, base + "/list", "POST", "wrong").statusCode() == 409);
            check(send(client, base, "DELETE", "").statusCode() == 200);
            check(send(client, base, "GET", "").statusCode() == 404);
        } finally {
            server.stop(0);
        }
        System.out.println("MiniKV tests passed");
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .method(method, method.equals("GET")
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
