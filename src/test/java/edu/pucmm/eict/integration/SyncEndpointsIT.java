package edu.pucmm.eict.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pucmm.eict.AppFactory;
import edu.pucmm.eict.auth.JwtService;
import edu.pucmm.eict.models.Usuario;
import edu.pucmm.eict.services.FormularioService;
import edu.pucmm.eict.services.UserService;
import io.javalin.Javalin;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncEndpointsIT {
    private static Javalin app;
    private static int port;
    private static String adminToken;
    private static String operadorToken;
    private static HttpClient httpClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void setup() {
        JwtService jwtService = new JwtService();
        UserService userService = new UserService();
        FormularioService formularioService = new FormularioService();
        app = AppFactory.create(formularioService, userService, jwtService).start(0);
        port = app.port();

        adminToken = jwtService.generateToken(new Usuario("1", "Admin", "admin@encuestas.local", "ADMIN"));
        operadorToken = jwtService.generateToken(new Usuario("2", "Operador", "digitador@encuestas.local", "OPERADOR"));
        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void teardown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void shouldSyncViaRestEndpoint() throws Exception {
        String payload = "[{\"id\":\"rest-1\",\"nombre\":\"Ana\",\"sector\":\"Centro\",\"nivelEscolar\":\"GRADO\",\"usuarioRegistro\":\"digitador@encuestas.local\",\"latitud\":19.45,\"longitud\":-70.69,\"sincronizado\":false}]";

        HttpRequest syncRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl("/api/formularios/sync")))
                .header("Authorization", "Bearer " + operadorToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> syncResponse = httpClient.send(syncRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode syncJson = OBJECT_MAPPER.readTree(syncResponse.body());

        assertEquals(200, syncResponse.statusCode());
        assertEquals(1, syncJson.get("sincronizados").asInt());
    }

    @Test
    void shouldRejectDeleteForOperadorRole() throws Exception {
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl("/api/formularios/rest-1")))
                .header("Authorization", "Bearer " + operadorToken)
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, deleteResponse.statusCode());
    }

    @Test
    void shouldSyncViaWebSocketEndpoint() throws Exception {
        CompletableFuture<String> messageFuture = new CompletableFuture<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageFuture.complete(data.toString());
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                webSocket.request(1);
                return null;
            }
        };

        WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(baseWsUrl("/sync?token=" + adminToken)), listener)
                .join();

        String payload = "[{\"id\":\"ws-1\",\"nombre\":\"Luis\",\"sector\":\"Norte\",\"nivelEscolar\":\"MEDIO\",\"usuarioRegistro\":\"admin@encuestas.local\",\"latitud\":19.40,\"longitud\":-70.60,\"sincronizado\":false}]";
        ws.sendText(payload, true).join();

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(messageFuture::isDone);
        assertTrue(messageFuture.join().contains("\"sincronizados\":1"));

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();

        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl("/api/formularios")))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode array = OBJECT_MAPPER.readTree(listResponse.body());

        assertEquals(200, listResponse.statusCode());
        assertTrue(array.isArray());
        boolean containsWsRecord = false;
        for (JsonNode item : array) {
            if ("ws-1".equals(item.path("id").asText())) {
                containsWsRecord = true;
                break;
            }
        }
        assertTrue(containsWsRecord);
    }

    private static String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private static String baseWsUrl(String path) {
        return "ws://localhost:" + port + path;
    }
}


