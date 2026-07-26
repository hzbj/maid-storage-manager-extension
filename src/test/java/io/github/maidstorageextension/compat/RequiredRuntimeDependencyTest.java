package io.github.maidstorageextension.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredRuntimeDependencyTest {
    @Test
    void spellAddonRequiredByModMetadataIsAvailableToDevelopmentRuns() throws IOException {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);

        assertTrue(build.matches("(?s).*localRuntime\\s+"
                        + "'maven\\.modrinth:QHB4kBBS:3tvArxwR'.*"),
                "The pinned spell add-on must be present in the NeoForge development runtime");
    }
}
