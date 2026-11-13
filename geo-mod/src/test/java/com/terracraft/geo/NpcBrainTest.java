package com.terracraft.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NpcBrainTest {
    private static JsonObject reply() {
        JsonObject response = new JsonObject();
        response.addProperty("content", "{\"speech\":\"Bonjour de Léa.\",\"quest_id\":\"contrat:commerce\"}");
        return response;
    }

    @Test void ollamaAndMistralUseValidatedStructuredReplies() throws Exception {
        for (String provider : new String[]{"ollama", "mistral"}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicReference<JsonObject> received = new AtomicReference<>();
            AtomicReference<String> auth = new AtomicReference<>();
            server.createContext("/chat", exchange -> {
                received.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                JsonObject envelope = new JsonObject();
                if (provider.equals("ollama")) {
                    envelope.add("message", reply());
                } else {
                    JsonObject choice = new JsonObject();
                    choice.add("message", reply());
                    var choices = new com.google.gson.JsonArray();
                    choices.add(choice);
                    envelope.add("choices", choices);
                }
                byte[] bytes = envelope.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            try (NpcBrain brain = new NpcBrain(new NpcBrain.Config(provider,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"),
                    "modele-test", "cle-factice", Duration.ofSeconds(2)))) {
                assertEquals("contrat:commerce", brain.ask(new JsonObject(), Set.of("contrat:commerce")).get(4, TimeUnit.SECONDS).questId());
                assertFalse(received.get().get("stream").getAsBoolean());
                assertEquals(provider.equals("mistral") ? "Bearer cle-factice" : null, auth.get());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test void failureOrSlowBodyDoesNotWaitIndefinitely() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                Thread.sleep(1000);
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (NpcBrain brain = new NpcBrain(new NpcBrain.Config("ollama",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"),
                "test", "", Duration.ofMillis(100)))) {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> brain.ask(new JsonObject(), Set.of()).get(2, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsCredentialsOverRemotePlainHttp() {
        assertThrows(IllegalArgumentException.class, () -> new NpcBrain.Config("mistral",
                URI.create("http://example.com/chat"), "test", "cle-factice", Duration.ofSeconds(2)));
    }
}
