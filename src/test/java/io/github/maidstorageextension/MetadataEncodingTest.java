package io.github.maidstorageextension;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataEncodingTest {
    @Test
    void expandedModMetadataKeepsUtf8Chinese() throws IOException {
        Path metadataPath = Path.of("build", "resources", "main", "META-INF", "mods.toml");
        String metadata = Files.readString(metadataPath, StandardCharsets.UTF_8);

        assertTrue(metadata.contains("displayName=\"女仆仓管扩展\""),
                "Expanded mods.toml lost the Chinese display name");
        assertTrue(metadata.contains("authors=\"hzbj\""),
                "Expanded mods.toml lost the maintainer attribution");
        assertTrue(metadata.contains("issueTrackerURL=\"https://github.com/hzbj/maid-storage-manager-extension/issues\""),
                "Expanded mods.toml lost the extension issue tracker");
        assertTrue(metadata.contains("license=\"MIT (code); CC BY-NC-SA 4.0 (assets)\""),
                "Expanded mods.toml lost the mixed license declaration");
        assertFalse(metadata.contains("å¥³ä»"),
                "Expanded mods.toml contains UTF-8 text decoded as Latin-1");
    }
}
