package io.github.maidstorageextension.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredRuntimeDependencyTest {
    @Test
    void optionalSpellAddonIsAvailableToDefaultDevelopmentRuns() throws IOException {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);

        assertTrue(build.matches("(?s).*runtimeOnly\\s+fg\\.deobf\\("
                        + "'maven\\.modrinth:QHB4kBBS:[^']+'\\).*"),
                "The optional spell add-on should be present in the default development runtime");
    }
}
