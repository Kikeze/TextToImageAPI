package com.example.textimage.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.org.springframework=INFO", "debug=false"})
class TextImageApiTest {
    @LocalServerPort
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void explicitOptionsReturnAValidPngAndMetadata() throws Exception {
        var response = post("{\"text\":\"Hola, mundo\",\"font\":\"sansserif\",\"color\":\"#2457c5\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("mimeType").asString()).isEqualTo("image/png");
        assertThat(body.get("font").asString()).isEqualTo("SansSerif");
        assertThat(body.get("color").asString()).isEqualTo("#2457C5");
        var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(body.get("imageBase64").asString())));
        assertThat(image.getWidth()).isEqualTo(body.get("width").asInt()).isLessThanOrEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(body.get("height").asInt());
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"text\":\"Hola\"}", "{\"text\":\"Hola\",\"font\":null,\"color\":null}"})
    void omittedAndNullOptionsUseRandomDefaults(String json) throws Exception {
        var response = post(json);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("font").asString()).isNotBlank();
        assertThat(body.get("color").asString()).matches("#[0-9A-F]{6}");
        assertThat(ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(body.get("imageBase64").asString())))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"   \"}",
            "{\"text\":\"Hola\",\"font\":\"\"}", "{\"text\":\"Hola\",\"font\":\"   \"}",
            "{\"text\":\"Hola\",\"font\":\"font-not-installed-123\"}",
            "{\"text\":\"Hola\",\"color\":\"\"}", "{\"text\":\"Hola\",\"color\":\"red\"}",
            "{\"text\":\"Hola\",\"color\":\"#12345678\"}", "{broken-json", ""})
    void invalidInputReturnsProblemDetails(String json) throws Exception {
        var response = post(json);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(mapper.readTree(response.body()).get("status").asInt()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("stackTrace");
    }

    @Test
    void rejectsTextAndImageSizeLimitsWithoutTruncating() throws Exception {
        assertThat(post(mapper.writeValueAsString(Map.of("text", "a".repeat(2000), "font", "SansSerif"))).statusCode()).isEqualTo(200);
        assertThat(post(mapper.writeValueAsString(Map.of("text", "a".repeat(2001)))).statusCode()).isEqualTo(400);
        assertThat(post(mapper.writeValueAsString(Map.of("text", "W".repeat(35) + "\n".repeat(1965), "font", "SansSerif"))).statusCode()).isEqualTo(400);
        assertThat(post(mapper.writeValueAsString(Map.of("text", new String(Character.toChars(0x10FFFF))))).statusCode()).isEqualTo(400);
    }

    @Test
    void rejectsUnsupportedMediaTypeAndHttpMethod() throws Exception {
        var uri = URI.create("http://127.0.0.1:" + port + "/api/v1/text-images");
        var response = client.send(HttpRequest.newBuilder(uri).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("Hola")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(415);
        var get = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(405);
    }

    private HttpResponse<String> post(String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/text-images"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
