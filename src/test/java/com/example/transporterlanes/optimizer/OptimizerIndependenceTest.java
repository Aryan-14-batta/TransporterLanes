package com.example.transporterlanes.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The optimizer must stay plain Java: its production sources may not mention Spring, Jakarta, JPA or Hibernate
 * packages anywhere (imports, fully qualified annotations or types, static imports, comments).
 */
class OptimizerIndependenceTest {

    private static final Path SOURCES = Path.of("src/main/java/com/example/transporterlanes/optimizer");
    private static final List<String> FORBIDDEN = List.of(
            "org.springframework", "jakarta.", "javax.persistence", "org.hibernate");

    @Test
    void productionSourcesImportNoFrameworkClasses() throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(SOURCES)) {
            files = listing.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(files).as("optimizer sources found").hasSizeGreaterThan(5);

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                assertThat(FORBIDDEN).as("%s line %d: %s", file.getFileName(), i + 1, line)
                        .noneMatch(line::contains);
            }
        }
    }

    @Test
    void detectorFlagsFullyQualifiedUsageNotJustImports() {
        // Guards the guard: a fully qualified annotation with no import line must still be caught.
        String line = "@org.springframework.stereotype.Component public final class Sneaky {}";
        assertThat(FORBIDDEN).anyMatch(line::contains);
    }
}
