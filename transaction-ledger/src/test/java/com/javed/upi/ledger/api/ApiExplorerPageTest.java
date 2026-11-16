package com.javed.upi.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiExplorerPageTest {

  @Test
  void publishesAPinnedReadOnlyExplorerForTheVersionedContract() throws IOException {
    String explorer = Files.readString(projectFile("docs/api-explorer.md"));
    String mkdocs = Files.readString(projectFile("mkdocs.yml"));

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
            "path: https://unpkg.com/rapidoc@9.3.9/dist/rapidoc-min.js",
            "type: module")
        .doesNotContain("rapidoc@latest", "unpkg.com/rapidoc/dist/rapidoc-min.js");
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
