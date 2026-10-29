package com.javed.upi.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTest {
  private static final String PAYMENT_SERVICE = "http://localhost:8081";
  private static final String LEDGER_SERVICE = "http://localhost:8082";

  @Test
  void documentsLedgerEventIntakeAndProfileAwarePaymentAuthentication() throws IOException {
    Map<String, Object> document = loadContract();
    Map<String, Object> paths = map(document.get("paths"));

    Map<String, Object> intake = operation(paths, "/internal/events/payments", "post");
    assertThat(intake.get("operationId")).isEqualTo("recordPaymentEvent");
    assertThat(serverUrls(intake)).containsExactly(LEDGER_SERVICE);
    assertThat(list(intake.get("security"))).isEmpty();
    assertThat(schemaReference(intake, "requestBody"))
        .isEqualTo("#/components/schemas/PaymentEvent");
    assertThat(responseSchemaReference(intake, "200"))
        .isEqualTo("#/components/schemas/LedgerEntry");
    assertThat(map(intake.get("responses"))).containsKey("400");

    Map<String, Object> schemas = map(map(document.get("components")).get("schemas"));
    assertSchema(
        schemas,
        "PaymentRequest",
        List.of("payerVpa", "payeeVpa", "amount", "currency", "channel"));
    List<String> paymentResponseFields = List.of(
        "paymentId",
        "payerVpa",
        "payeeVpa",
        "amount",
        "currency",
        "status",
        "decisionCode",
        "decisionMessage",
        "decidedAt");
    assertSchema(schemas, "PaymentResponse", paymentResponseFields);
    assertThat(map(map(schemas.get("PaymentResponse")).get("properties")))
        .doesNotContainKey("channel");
    assertArbitraryPrecisionAmount(schemas, "PaymentRequest");
    assertArbitraryPrecisionAmount(schemas, "PaymentResponse");
    assertStatusEnum(schemas, "PaymentResponse");
    assertDateTime(schemas, "PaymentResponse", "decidedAt");
    assertSchema(
        schemas,
        "PaymentEvent",
        paymentResponseFields);
    assertArbitraryPrecisionAmount(schemas, "PaymentEvent");
    assertStatusEnum(schemas, "PaymentEvent");
    assertDateTime(schemas, "PaymentEvent", "decidedAt");
    assertSchema(
        schemas,
        "LedgerEntry",
        List.of(
            "paymentId",
            "payerVpa",
            "payeeVpa",
            "amount",
            "currency",
            "status",
            "decisionCode",
            "recordedAt"));
    assertArbitraryPrecisionAmount(schemas, "LedgerEntry");
    assertStatusEnum(schemas, "LedgerEntry");
    assertDateTime(schemas, "LedgerEntry", "recordedAt");

    Map<String, Object> submitPayment = operation(paths, "/api/payments", "post");
    assertThat(serverUrls(submitPayment)).containsExactly(PAYMENT_SERVICE);
    assertThat(String.valueOf(submitPayment.get("description")))
        .contains("default profile", "secure profile", "payment.write");
    List<Object> security = list(submitPayment.get("security"));
    assertThat(security).hasSize(2);
    assertThat(map(security.get(0))).isEmpty();
    assertThat(list(map(security.get(1)).get("bearerAuth"))).isEmpty();
    assertThat(map(submitPayment.get("responses"))).containsKeys("401", "403");

    Map<String, Object> securitySchemes = map(map(document.get("components"))
        .get("securitySchemes"));
    assertThat(map(securitySchemes.get("bearerAuth")))
        .containsEntry("type", "http")
        .containsEntry("scheme", "bearer")
        .containsEntry("bearerFormat", "JWT");

    assertThat(serverUrls(operation(paths, "/api/payments/{paymentId}", "get")))
        .containsExactly(PAYMENT_SERVICE);
    assertThat(serverUrls(operation(paths, "/api/ledger", "get")))
        .containsExactly(LEDGER_SERVICE);
    assertThat(serverUrls(operation(paths, "/api/ledger/{paymentId}", "get")))
        .containsExactly(LEDGER_SERVICE);
  }

  private static Map<String, Object> loadContract() throws IOException {
    Path contract = contractPath();
    try (InputStream input = Files.newInputStream(contract)) {
      return map(new Yaml().load(input));
    }
  }

  private static Path contractPath() {
    Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    List<Path> candidates = List.of(
        workingDirectory.resolve("docs/api/openapi.yaml"),
        workingDirectory.resolve("../docs/api/openapi.yaml").normalize());
    return candidates.stream()
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "Unable to locate docs/api/openapi.yaml from " + workingDirectory));
  }

  private static Map<String, Object> operation(
      Map<String, Object> paths, String path, String method) {
    return map(map(paths.get(path)).get(method));
  }

  private static List<String> serverUrls(Map<String, Object> operation) {
    return list(operation.get("servers")).stream()
        .map(OpenApiContractTest::map)
        .map(server -> String.valueOf(server.get("url")))
        .toList();
  }

  private static String schemaReference(Map<String, Object> operation, String bodyKey) {
    Map<String, Object> body = map(operation.get(bodyKey));
    Map<String, Object> content = map(body.get("content"));
    Map<String, Object> mediaType = map(content.get("application/json"));
    return String.valueOf(map(mediaType.get("schema")).get("$ref"));
  }

  private static String responseSchemaReference(
      Map<String, Object> operation, String statusCode) {
    Map<String, Object> response = map(map(operation.get("responses")).get(statusCode));
    Map<String, Object> content = map(response.get("content"));
    Map<String, Object> mediaType = map(content.get("application/json"));
    return String.valueOf(map(mediaType.get("schema")).get("$ref"));
  }

  private static void assertSchema(
      Map<String, Object> schemas, String name, List<String> requiredFields) {
    Map<String, Object> schema = map(schemas.get(name));
    assertThat(schema).containsEntry("type", "object");
    assertThat(list(schema.get("required"))).containsExactlyElementsOf(requiredFields);
    assertThat(map(schema.get("properties"))).containsOnlyKeys(requiredFields.toArray(String[]::new));
  }

  private static void assertArbitraryPrecisionAmount(
      Map<String, Object> schemas, String schemaName) {
    Map<String, Object> properties = map(map(schemas.get(schemaName)).get("properties"));
    assertThat(map(properties.get("amount")))
        .containsEntry("type", "number")
        .doesNotContainKey("format");
  }

  private static void assertStatusEnum(Map<String, Object> schemas, String schemaName) {
    Map<String, Object> properties = map(map(schemas.get(schemaName)).get("properties"));
    Map<String, Object> status = map(properties.get("status"));
    assertThat(status).containsEntry("type", "string");
    assertThat(list(status.get("enum")))
        .containsExactly("ACCEPTED", "REVIEW", "REJECTED");
  }

  private static void assertDateTime(
      Map<String, Object> schemas, String schemaName, String propertyName) {
    Map<String, Object> properties = map(map(schemas.get(schemaName)).get("properties"));
    assertThat(map(properties.get(propertyName)))
        .containsEntry("type", "string")
        .containsEntry("format", "date-time");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Object>) value;
  }
}
