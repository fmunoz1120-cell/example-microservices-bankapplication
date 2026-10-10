package com.backend.client;

import static org.assertj.core.api.Assertions.*;

import com.backend.client.repository.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Tag("integration")
class ClientIntegrationTest {
  @LocalServerPort int port;
  @Autowired ClientRepository clients;
  @Autowired OutboxRepository outbox;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  HttpResponse<String> send(String method, String path, String body) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void httpCrudKeepsPasswordSecretAndWritesOutbox() throws Exception {
    String dni = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    String body =
        "{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"completeName\":\"Ana Pérez\",\"dniTypeId\":101,\"dni\":\""
            + dni
            + "\",\"gender\":\"F\",\"age\":30,\"address\":\"Quito\",\"phone\":\"099\",\"password\":\"password123\",\"isActive\":true}";
    var created = send("POST", "/api/clients", body);
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.body()).doesNotContain("password");
    Long id = json.readTree(created.body()).get("id").asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM clients c JOIN persons p ON c.id = p.id WHERE c.id = ? AND"
                    + " p.dni = ?",
                Long.class,
                id,
                dni))
        .isEqualTo(1L);
    assertThat(clients.findById(id).orElseThrow().createdAt).isNotNull();
    assertThat(clients.findById(id).orElseThrow().updatedAt).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM clients WHERE id = ? AND created_at IS NOT NULL AND"
                    + " updated_at IS NOT NULL",
                Long.class,
                id))
        .isEqualTo(1L);
    jdbc.update(
        "UPDATE persons SET updated_at = TIMESTAMPTZ '2000-01-01 00:00:00+00' WHERE id = ?", id);
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at > TIMESTAMPTZ '2000-01-01 00:00:00+00' FROM persons WHERE id ="
                    + " ?",
                Boolean.class,
                id))
        .isTrue();
    long initialVersion =
        jdbc.queryForObject("SELECT version FROM persons WHERE id = ?", Long.class, id);
    assertThat(
            new BCryptPasswordEncoder()
                .matches("password123", clients.findById(id).orElseThrow().password))
        .isTrue();
    assertThat(outbox.findAll())
        .anyMatch(e -> e.clientId.equals(id) && !e.payload.contains("password"));
    assertThat(send("POST", "/api/clients", body).statusCode()).isEqualTo(409);
    assertThat(send("GET", "/api/clients/" + id, null).statusCode()).isEqualTo(200);
    assertThat(
            send("PATCH", "/api/clients/" + id, "{\"completeName\":\"Eva Pérez\"}")
                .body())
        .contains("Eva Pérez");
    assertThat(
            jdbc.queryForObject("SELECT complete_name FROM persons WHERE id = ?", String.class, id))
        .isEqualTo("Eva Pérez");
    assertThat(jdbc.queryForObject("SELECT version FROM persons WHERE id = ?", Long.class, id))
        .isGreaterThan(initialVersion);
    assertThat(send("PUT", "/api/clients/" + id, body).statusCode()).isEqualTo(200);
    assertThat(send("PATCH", "/api/clients/" + id, "{\"id\":5}").statusCode()).isEqualTo(400);
    assertThat(send("DELETE", "/api/clients/" + id, null).statusCode()).isEqualTo(204);
    assertThat(clients.findById(id).orElseThrow().isActive).isFalse();
    assertThat(clients.findById(id).orElseThrow().deletedAt).isNotNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM persons WHERE id = ?", Long.class, id))
        .isEqualTo(1L);
  }

  @Test
  void sharedPrimaryKeyRejectsClientWithoutPerson() {
    long absentId = -1L;
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM persons WHERE id = ?", Long.class, absentId))
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO clients (id, password, is_active) VALUES (?, ?, TRUE)",
                    absentId,
                    "test-hash"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
