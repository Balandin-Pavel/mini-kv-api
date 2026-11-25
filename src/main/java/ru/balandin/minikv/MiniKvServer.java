package ru.balandin.minikv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class MiniKvServer {
    private final KeyValueStore store;

    public MiniKvServer(KeyValueStore store) { this.store = store; }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/keys", this::handle);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/keys") && method.equals("GET")) {
                respond(exchange, 200, "{\"size\":" + store.size() + "}");
                return;
            }
            String prefix = "/keys/";
            if (!path.startsWith(prefix)) {
                respond(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            String rest = path.substring(prefix.length());
            String[] parts = rest.split("/", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String action = parts.length == 2 ? parts[1] : "";
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (method.equals("PUT") && action.isEmpty()) {
                store.set(key, body);
                respond(exchange, 200, "{\"stored\":true}");
            } else if (method.equals("GET") && action.isEmpty()) {
                var value = store.get(key);
                respond(exchange, value.isPresent() ? 200 : 404,
                        value.map(MiniKvServer::toJson).orElse("{\"error\":\"missing key\"}"));
            } else if (method.equals("DELETE") && action.isEmpty()) {
                boolean deleted = store.delete(key);
                respond(exchange, deleted ? 200 : 404, "{\"deleted\":" + deleted + "}");
            } else if (method.equals("POST") && action.equals("append")) {
                respond(exchange, 200, toJson(new KeyValueStore.Text(store.append(key, body))));
            } else if (method.equals("POST") && action.equals("list")) {
                respond(exchange, 200, toJson(new KeyValueStore.Sequence(store.push(key, body))));
            } else if (method.equals("POST") && action.equals("members")) {
                respond(exchange, 200, toJson(new KeyValueStore.Members(store.addMember(key, body))));
            } else if (method.equals("POST") && action.equals("expire")) {
                long seconds = Long.parseLong(body.trim());
                boolean updated = store.expire(key, Duration.ofSeconds(seconds));
                respond(exchange, updated ? 200 : 404, "{\"updated\":" + updated + "}");
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        } catch (IllegalStateException e) {
            respond(exchange, 409, "{\"error\":\"wrong value type\"}");
        } catch (IllegalArgumentException | NullPointerException e) {
            respond(exchange, 400, "{\"error\":\"invalid request\"}");
        }
    }

    private static String toJson(KeyValueStore.Value value) {
        if (value instanceof KeyValueStore.Text text) {
            return "{\"type\":\"string\",\"value\":\"" + escape(text.content()) + "\"}";
        }
        if (value instanceof KeyValueStore.Sequence sequence) {
            return "{\"type\":\"list\",\"value\":" + array(sequence.items().stream().toList()) + "}";
        }
        KeyValueStore.Members members = (KeyValueStore.Members) value;
        return "{\"type\":\"set\",\"value\":" +
                array(members.items().stream().sorted(Comparator.naturalOrder()).toList()) + "}";
    }

    private static String array(java.util.List<String> items) {
        return items.stream().map(item -> "\"" + escape(item) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8082 : Integer.parseInt(args[0]);
        new MiniKvServer(new KeyValueStore(Clock.systemUTC())).start(port);
        System.out.println("MiniKV API: http://127.0.0.1:" + port);
    }
}
