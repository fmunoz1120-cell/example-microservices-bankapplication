package com.backend.account;

import static org.assertj.core.api.Assertions.*;

import com.backend.account.model.ClientProjection;
import com.backend.account.repository.ClientProjectionRepository;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Tag("integration")
class AccountHttpIntegrationTest {
  @LocalServerPort int port;
  @Autowired ClientProjectionRepository clients;
  @Autowired ObjectMapper json;

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
  void apiValidatesMoneyDatesAndReports() throws Exception {
    var c = new ClientProjection();
    c.id = Math.abs(UUID.randomUUID().getMostSignificantBits());
    c.name = "HTTP client";
    c.active = true;
    clients.saveAndFlush(c);
    String body =
        "{\"number\":\""
            + UUID.randomUUID().toString().substring(0, 20)
            + "\",\"accountTypeId\":201,\"clientId\":"
            + c.id
            + ",\"initialAmount\":100,\"isActive\":true,\"workAreaId\":1}";
    var created = send("POST", "/api/accounts", body);
    assertThat(created.statusCode()).isEqualTo(201);
    long id = json.readTree(created.body()).get("id").asLong();
    assertThat(send("POST", "/api/accounts", body).statusCode()).isEqualTo(409);
    assertThat(send("PUT", "/api/accounts/" + id, body).statusCode()).isEqualTo(200);
    assertThat(send("GET", "/api/accounts/" + id, null).body()).contains("100");
    assertThat(send("PATCH", "/api/accounts/" + id, "{\"accountTypeId\":202}").statusCode())
        .isEqualTo(200);
    var failed =
        send(
            "POST",
            "/api/transactions",
            "{\"accountId\":"
                + id
                + ",\"transactionTypeId\":302,\"amount\":-101,\"workAreaId\":1}");
    assertThat(failed.statusCode()).isEqualTo(409);
    assertThat(json.readTree(failed.body()).get("detail").asString())
        .isEqualTo("Saldo no disponible");
    assertThat(
            send(
                    "POST",
                    "/api/transactions",
                    "{\"accountId\":"
                        + id
                        + ",\"transactionTypeId\":301,\"amount\":1,\"workAreaId\":1,\"date\":\"2000-01-01\"}")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            send(
                    "GET",
                    "/api/transactions/clients/"
                        + c.id
                        + "/report?dateTransactionStart=2026-01-02&dateTransactionEnd=2026-01-01",
                    null)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            send(
                    "GET",
                    "/api/transactions/clients/"
                        + c.id
                        + "/report?dateTransactionStart=2026-01-01&dateTransactionEnd=2026-12-31",
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(send("DELETE", "/api/accounts/" + id, null).statusCode()).isEqualTo(204);
  }
}
