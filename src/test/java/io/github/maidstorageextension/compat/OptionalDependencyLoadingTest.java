package io.github.maidstorageextension.compat;

import org.junit.jupiter.api.Test;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalDependencyLoadingTest {
    @Test
    void entryPointsLinkWhenEitherOrBothOptionalApisAreMissing() throws Exception {
        for (Set<String> missing : List.of(
                Set.of("com.github.yimeng261.maidspell."),
                Set.of("me.shedaniel.clothconfig2."),
                Set.of("com.github.yimeng261.maidspell.", "me.shedaniel.clothconfig2."))) {
            ClassLoader loader = new WithoutOptionalApis(missing);
            for (String entryPoint : List.of(
                    "compat.EnderPocketCompat",
                    "client.ClientCompatibilityEvents",
                    "client.ConfigScreenBridge",
                    "mixin.StorageManagerConfigScreenMixin")) {
                Class<?> type = Class.forName(
                        "io.github.maidstorageextension." + entryPoint, false, loader);
                // Reflection also forces JVM verification and resolves every method signature.
                assertDoesNotThrow(type::getDeclaredMethods, missing + ": " + entryPoint);
            }
        }
    }

    @Test
    void clothListenerIsNeverAutomaticallyScannedWithoutItsApi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/client/ClientClothConfigEvents.java"));
        assertFalse(source.contains("@Mod.EventBusSubscriber"));
        assertFalse(source.contains("@EventBusSubscriber"));
    }

    @Test
    void packagedMetadataMakesBothIntegrationsOptional() throws Exception {
        String metadata = Files.readString(Path.of("build/resources/main/META-INF/neoforge.mods.toml"));
        for (String mod : List.of("touhou_little_maid_spell", "cloth_config")) {
            String dependency = metadata.substring(metadata.indexOf("modId=\"" + mod + "\""))
                    .split("\\[\\[", 2)[0];
            assertTrue(dependency.contains("type=\"optional\""), mod);
        }
    }

    @Test
    void packagedSpellRangeAccepts19ReleasesIncludingLoaderSuffixes() throws Exception {
        String metadata = Files.readString(Path.of("build/resources/main/META-INF/neoforge.mods.toml"));
        String dependency = metadata.substring(metadata.indexOf("modId=\"touhou_little_maid_spell\""))
                .split("\\[\\[", 2)[0];
        String rangeText = dependency.split("versionRange=\"", 2)[1].split("\"", 2)[0];
        VersionRange range = VersionRange.createFromVersionSpec(rangeText);
        for (String version : List.of("1.8.4", "1.9.0", "1.9.1", "1.9.1-neoforge")) {
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)),
                    () -> version + " must be accepted by " + rangeText);
        }
        assertFalse(range.containsVersion(new DefaultArtifactVersion("1.8.3")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("1.10")));
    }

    private static final class WithoutOptionalApis extends ClassLoader {
        private static final String PROJECT = "io.github.maidstorageextension.";
        private final Set<String> missing;

        private WithoutOptionalApis(Set<String> missing) {
            super(OptionalDependencyLoadingTest.class.getClassLoader());
            this.missing = missing;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (missing.stream().anyMatch(name::startsWith)) {
                throw new ClassNotFoundException("Optional dependency deliberately absent: " + name);
            }
            if (!name.startsWith(PROJECT)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream input = getParent().getResourceAsStream(resource)) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
