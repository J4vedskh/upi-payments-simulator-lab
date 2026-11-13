package com.javed.upi.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class OpenApiContractTest {
  private static final String PAYMENT_SERVICE = "http://localhost:8081";
  private static final String LEDGER_SERVICE = "http://localhost:8082";

  @Test
  void documentsLedgerEventIntakeAndProfileAwarePaymentAuthentication() throws IOException {
    Map<String, Object> document = loadContract();
    Map<String, Object> paths = map(document.get("paths"));

    Map<String, Object> intake = operation(paths, "/internal/events/payments", "post");
    assertThat(intake.get("operationId")).isEqualTo("recordPaymentEvent");
    assertThat(String.valueOf(intake.get("description")))
        .contains("idempotent by paymentId", "first successful", "replays return it unchanged");
    assertThat(serverUrls(intake)).containsExactly(LEDGER_SERVICE);
    assertThat(list(intake.get("security"))).isEmpty();
    assertThat(schemaReference(intake, "requestBody"))
        .isEqualTo("#/components/schemas/PaymentEvent");
    assertThat(responseSchemaReference(intake, "200"))
        .isEqualTo("#/components/schemas/LedgerEntry");
    assertThat(String.valueOf(map(map(intake.get("responses")).get("200")).get("description")))
        .contains("Successfully recorded ledger entry", "replays");
    assertThat(responseSchemaReference(intake, "400"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    assertInvalidEventExample(intake);
    assertUnsupportedMediaTypeExample(intake, "/internal/events/payments");

    Map<String, Object> schemas = map(map(document.get("components")).get("schemas"));
    assertSchema(
        schemas,
        "PaymentRequest",
        List.of("payerVpa", "payeeVpa", "amount", "currency", "channel"));
    assertThat(map(schemas.get("PaymentRequest")))
        .containsEntry("additionalProperties", false);
    assertNonBlankStringProperties(
        schemas,
        "PaymentRequest",
        List.of("payerVpa", "payeeVpa", "currency", "channel"));
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
    assertThat(map(schemas.get("PaymentEvent")))
        .containsEntry("additionalProperties", false);
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
    assertSchema(
        schemas,
        "ErrorResponse",
        List.of("timestamp", "status", "error", "message", "path", "validationErrors"));
    assertThat(map(schemas.get("ErrorResponse")))
        .containsEntry("additionalProperties", false);

    Map<String, Object> submitPayment = operation(paths, "/api/payments", "post");
    assertThat(serverUrls(submitPayment)).containsExactly(PAYMENT_SERVICE);
    assertThat(String.valueOf(submitPayment.get("description")))
        .contains("default profile", "secure profile", "payment.write", "Idempotency-Key");
    assertIdempotencyKeyParameter(document, submitPayment);
    List<Object> security = list(submitPayment.get("security"));
    assertThat(security).hasSize(2);
    assertThat(map(security.get(0))).isEmpty();
    assertThat(list(map(security.get(1)).get("bearerAuth"))).isEmpty();
    assertSecurePaymentErrorExample(
        submitPayment,
        "401",
        "UNAUTHORIZED",
        "Bearer authentication is required.");
    assertSecurePaymentErrorExample(
        submitPayment,
        "403",
        "FORBIDDEN",
        "Access is denied.");
    assertPaymentBadRequestExamples(submitPayment);
    assertIdempotencyConflictExample(submitPayment);
    assertPublicationFailureExample(submitPayment);
    assertUnsupportedMediaTypeExample(submitPayment, "/api/payments");

    Map<String, Object> securitySchemes = map(map(document.get("components"))
        .get("securitySchemes"));
    assertThat(map(securitySchemes.get("bearerAuth")))
        .containsEntry("type", "http")
        .containsEntry("scheme", "bearer")
        .containsEntry("bearerFormat", "JWT");

    Map<String, Object> paymentLookup = operation(paths, "/api/payments/{paymentId}", "get");
    assertThat(serverUrls(paymentLookup)).containsExactly(PAYMENT_SERVICE);
    assertNotFoundExample(paymentLookup, "Payment not found.", "/api/payments/pay_missing");
    assertThat(serverUrls(operation(paths, "/api/ledger", "get")))
        .containsExactly(LEDGER_SERVICE);
    Map<String, Object> ledgerLookup = operation(paths, "/api/ledger/{paymentId}", "get");
    assertThat(serverUrls(ledgerLookup)).containsExactly(LEDGER_SERVICE);
    assertNotFoundExample(ledgerLookup, "Ledger entry not found.", "/api/ledger/pay_missing");
  }

  private static Map<String, Object> loadContract() throws IOException {
    Path contract = contractPath();
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    try (InputStream input = Files.newInputStream(contract)) {
      return map(new Yaml(new SafeConstructor(options)).load(input));
    }
  }

  private static void assertInvalidEventExample(Map<String, Object> intake) {
    Map<String, Object> badRequest = map(map(intake.get("responses")).get("400"));
    Map<String, Object> content = map(badRequest.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-01T10:00:00Z")
        .containsEntry("status", 400)
        .containsEntry("error", "VALIDATION_FAILED")
        .containsEntry("message", "Request body is malformed or missing a required field.")
        .containsEntry("path", "/internal/events/payments");
    assertThat(map(example.get("validationErrors"))).isEmpty();
  }

  private static void assertUnsupportedMediaTypeExample(
      Map<String, Object> operation, String expectedPath) {
    assertThat(responseSchemaReference(operation, "415"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(operation.get("responses")).get("415"));
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-02T05:00:00Z")
        .containsEntry("status", 415)
        .containsEntry("error", "UNSUPPORTED_MEDIA_TYPE")
        .containsEntry("message", "Content-Type is not supported for this endpoint.")
        .containsEntry("path", expectedPath);
    assertThat(map(example.get("validationErrors"))).isEmpty();
  }

  private static void assertPaymentBadRequestExamples(Map<String, Object> submitPayment) {
    assertThat(responseSchemaReference(submitPayment, "400"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(submitPayment.get("responses")).get("400"));
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> examples = map(map(content.get("application/json")).get("examples"));
    assertThat(examples)
        .containsOnlyKeys("fieldValidation", "malformedBody", "invalidIdempotencyKey");

    Map<String, Object> fieldValidation = map(map(examples.get("fieldValidation")).get("value"));
    assertThat(fieldValidation)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-03T05:00:00Z")
        .containsEntry("status", 400)
        .containsEntry("error", "VALIDATION_FAILED")
        .containsEntry("message", "Request validation failed.")
        .containsEntry("path", "/api/payments");
    assertThat(map(fieldValidation.get("validationErrors")))
        .containsOnlyKeys("payerVpa")
        .containsEntry("payerVpa", "must not be blank");

    Map<String, Object> malformedBody = map(map(examples.get("malformedBody")).get("value"));
    assertThat(malformedBody)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-03T05:00:00Z")
        .containsEntry("status", 400)
        .containsEntry("error", "VALIDATION_FAILED")
        .containsEntry("message", "Request body is malformed.")
        .containsEntry("path", "/api/payments");
    assertThat(map(malformedBody.get("validationErrors"))).isEmpty();

    Map<String, Object> invalidKey = map(map(examples.get("invalidIdempotencyKey")).get("value"));
    assertThat(invalidKey)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-11T05:00:00Z")
        .containsEntry("status", 400)
        .containsEntry("error", "VALIDATION_FAILED")
        .containsEntry(
            "message",
            "Idempotency-Key must contain 1 to 128 visible ASCII characters.")
        .containsEntry("path", "/api/payments");
    assertThat(map(invalidKey.get("validationErrors"))).isEmpty();
  }

  private static void assertIdempotencyKeyParameter(
      Map<String, Object> document,
      Map<String, Object> submitPayment) {
    List<Object> parameters = list(submitPayment.get("parameters"));
    assertThat(parameters).singleElement().satisfies(parameter -> assertThat(map(parameter))
        .containsEntry("$ref", "#/components/parameters/IdempotencyKey"));

    Map<String, Object> components = map(document.get("components"));
    Map<String, Object> parameter = map(map(components.get("parameters")).get("IdempotencyKey"));
    assertThat(parameter)
        .containsEntry("name", "Idempotency-Key")
        .containsEntry("in", "header")
        .containsEntry("required", false);
    assertThat(String.valueOf(parameter.get("description")))
        .contains("bounded", "retention window", "does not extend", "new submission");
    assertThat(map(parameter.get("schema")))
        .containsEntry("type", "string")
        .containsEntry("minLength", 1)
        .containsEntry("maxLength", 128)
        .containsEntry("pattern", "^[!-~]+$");
  }

  private static void assertIdempotencyConflictExample(Map<String, Object> submitPayment) {
    assertThat(responseSchemaReference(submitPayment, "409"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(submitPayment.get("responses")).get("409"));
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-11T05:00:00Z")
        .containsEntry("status", 409)
        .containsEntry("error", "IDEMPOTENCY_CONFLICT")
        .containsEntry(
            "message",
            "Idempotency-Key was already used for a different payment request.")
        .containsEntry("path", "/api/payments");
    assertThat(map(example.get("validationErrors"))).isEmpty();
  }

  private static void assertPublicationFailureExample(Map<String, Object> submitPayment) {
    assertThat(responseSchemaReference(submitPayment, "503"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(submitPayment.get("responses")).get("503"));
    assertThat(String.valueOf(response.get("description")))
        .contains("Synchronous", "local payment", "asynchronous broker");
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-13T05:00:00Z")
        .containsEntry("status", 503)
        .containsEntry("error", "EVENT_PUBLICATION_FAILED")
        .containsEntry("message", "Payment event publication is temporarily unavailable.")
        .containsEntry("path", "/api/payments");
    assertThat(map(example.get("validationErrors"))).isEmpty();
  }

  private static void assertSecurePaymentErrorExample(
      Map<String, Object> operation,
      String statusCode,
      String error,
      String message) {
    assertThat(responseSchemaReference(operation, statusCode))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(operation.get("responses")).get(statusCode));
    Map<String, Object> challenge = map(map(response.get("headers")).get("WWW-Authenticate"));
    assertThat(map(challenge.get("schema"))).containsEntry("type", "string");
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-04T05:00:00Z")
        .containsEntry("status", Integer.parseInt(statusCode))
        .containsEntry("error", error)
        .containsEntry("message", message)
        .containsEntry("path", "/api/payments");
    assertThat(map(example.get("validationErrors"))).isEmpty();
  }

  private static void assertNotFoundExample(
      Map<String, Object> operation,
      String message,
      String path) {
    assertThat(responseSchemaReference(operation, "404"))
        .isEqualTo("#/components/schemas/ErrorResponse");
    Map<String, Object> response = map(map(operation.get("responses")).get("404"));
    Map<String, Object> content = map(response.get("content"));
    assertThat(content).containsOnlyKeys("application/json");
    Map<String, Object> example = map(map(content.get("application/json")).get("example"));
    assertThat(example)
        .containsOnlyKeys("timestamp", "status", "error", "message", "path", "validationErrors")
        .containsEntry("timestamp", "2026-11-07T13:00:00Z")
        .containsEntry("status", 404)
        .containsEntry("error", "NOT_FOUND")
        .containsEntry("message", message)
        .containsEntry("path", path);
    assertThat(map(example.get("validationErrors"))).isEmpty();
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

  private static void assertNonBlankStringProperties(
      Map<String, Object> schemas,
      String schemaName,
      List<String> propertyNames) {
    Map<String, Object> properties = map(map(schemas.get(schemaName)).get("properties"));
    for (String propertyName : propertyNames) {
      assertThat(map(properties.get(propertyName)))
          .containsEntry("type", "string")
          .containsEntry("minLength", 1)
          .containsEntry("pattern", ".*\\S.*");
    }
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
