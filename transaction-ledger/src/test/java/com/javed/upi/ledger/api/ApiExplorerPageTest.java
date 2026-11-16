package com.javed.upi.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiExplorerPageTest {

  @Test
  void publishesAPinnedReadOnlyExplorerForTheVersionedContract() throws IOException {
    String explorer = Files.readString(projectFile("docs/api-explorer.md"));
    String mkdocs = Files.readString(projectFile("mkdocs.yml"));
    Path renderer = projectFile("docs/javascripts/rapidoc-12.1.0.min.js");

    assertThat(explorer)
        .contains(
            "<rapi-doc",
            "spec-url=\"../api/openapi.yaml\"",
            "render-style=\"read\"",
            "allow-try=\"false\"",
            "show-header=\"false\"",
            "[Download the OpenAPI YAML](api/openapi.yaml)");
    assertThat(mkdocs)
        .contains(
            "API Explorer: api-explorer.md",
            "path: javascripts/rapidoc-12.1.0.min.js",
            "type: module")
        .doesNotContain("rapidoc@latest", "unpkg.com/rapidoc", "cdn.jsdelivr.net");
    assertThat(sha256(renderer))
        .isEqualTo("dd03b2810660666704cf8eaa46dedde416bb64654f2bd8d0e269a043d7fa00ac");
  }

  private static String sha256(Path file) throws IOException {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("SHA-256 is required by the Java runtime.", exception);
    }
  }

  private static Path projectFile(String relativePath) {
    Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    List<Path> candidates = List.of(
        workingDirectory.resolve(relativePath),
        workingDirectory.resolve("../" + relativePath).normalize());
    return candidates.stream()
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "Unable to locate " + relativePath + " from " + workingDirectory));
  }
}
