package io.github.maidstorageextension.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierRequestTargetFeedbackRegressionTest {
    @Test
    void unchangedRequestTargetDoesNotRepeatTheRecordedMessage() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/event/InteractionEvents.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("if (!CourierRequestTarget.write("),
                "The request-list message must be gated by an actual target change");
    }
}
