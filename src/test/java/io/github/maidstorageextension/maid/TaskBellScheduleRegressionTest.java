package io.github.maidstorageextension.maid;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskBellScheduleRegressionTest {
    @Test
    void activeTaskBellCallCancelsTheBaseSchedulerBeforeItCanClearTheWalkTarget() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/mixin/ScheduleBehaviorMixin.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("@Inject(method = \"start\", at = @At(\"HEAD\"), cancellable = true)"),
                "The task-bell takeover must be able to cancel the base scheduler at HEAD");
        assertTrue(source.contains("ci.cancel();"),
                "The task-bell takeover must cancel the base scheduler while a call is active");
    }

    @Test
    void activeMiscSortCancelsTheBaseSchedulerBeforeItCanSelectOrdinaryPlacement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/mixin/ScheduleBehaviorMixin.java"),
                StandardCharsets.UTF_8);
        int headHandler = source.indexOf("private void maidStorageExtension$tick");
        int nextInjection = source.indexOf("@Inject", headHandler + 1);
        String headBody = source.substring(headHandler, nextInjection);

        assertTrue(headBody.contains("ExtensionWorkControl.shouldHoldExclusiveAction(maid)"),
                "The HEAD scheduler gate must recognize protected miscellaneous cargo");
        assertTrue(headBody.contains("ci.cancel();"),
                "The base scheduler must not switch protected cargo to ordinary PLACE mode");
    }
}
