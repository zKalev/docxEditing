package com.example.docs;

import com.example.docs.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentApiTest {

    @TempDir
    static Path storage;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage-dir", storage::toString);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JwtService jwtService;

    @Autowired
    JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void listsSeededSampleAndBuildsSignedConfig() throws Exception {
        JsonNode list = getJson("/api/documents");
        assertThat(list.findValuesAsString("id")).contains("sample.docx");

        JsonNode response = getJson("/api/documents/sample.docx/config");
        JsonNode config = response.get("config");
        assertThat(response.get("documentServerUrl").asString()).isNotBlank();
        assertThat(config.at("/document/url").asString()).endsWith("/api/documents/sample.docx/file");
        assertThat(config.at("/editorConfig/callbackUrl").asString()).endsWith("/api/documents/track?id=sample.docx");

        Map<String, Object> claims = jwtService.verify(config.get("token").asString());
        assertThat(((Map<?, ?>) claims.get("document")).get("key")).isEqualTo(config.at("/document/key").asString());
    }

    @Test
    void forceSaveCallbackOverwritesFileAndChangesKey() throws Exception {
        Files.write(storage.resolve("target.docx"), new byte[]{1, 2, 3});
        Files.write(storage.resolve("edited.docx"), new byte[]{9, 9, 9, 9});
        String keyBefore = getJson("/api/documents/target.docx/config").at("/config/document/key").asString();

        // Pretend Document Server finished editing: the "edited" file is served by this app's own file endpoint.
        String editedUrl = "http://localhost:" + port + "/api/documents/edited.docx/file";
        String token = jwtService.sign(Map.of("status", 6, "key", keyBefore, "url", editedUrl));
        String body = jsonMapper.writeValueAsString(Map.of("status", 6, "key", keyBefore, "url", editedUrl, "token", token));

        assertThat(post("/api/documents/track?id=target.docx", body)).isEqualTo("{\"error\":0}");
        assertThat(Files.readAllBytes(storage.resolve("target.docx"))).containsExactly(9, 9, 9, 9);
        assertThat(getJson("/api/documents/target.docx/config").at("/config/document/key").asString())
                .isNotEqualTo(keyBefore);
    }

    @Test
    void rejectsUnsignedCallbackAndPathTraversal() throws Exception {
        assertThat(post("/api/documents/track?id=sample.docx", "{\"status\":2,\"url\":\"http://evil/x\"}"))
                .isEqualTo("{\"error\":1}");
        HttpResponse<String> traversal = http.send(
                HttpRequest.newBuilder(URI.create(url("/api/documents/..%2Fpom.xml/file"))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(traversal.statusCode()).isIn(400, 404);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url(path))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readTree(response.body());
    }

    private String post(String path, String json) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
