package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierCompletedRequestListLoopRegressionTest {
    private static final Path COURIER_SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/maid/courier/CourierService.java");
    private static final Path NETWORK_SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/logistics/NetworkWarehouseService.java");

    @Test
    void completedListRemainsEditableInTerminalButCannotStartAnotherCourierJob()
            throws Exception {
        String courier = read(COURIER_SERVICE);
        String beginRequest = between(courier, "private static boolean beginRequest(",
                "private static void beginDeposit(");
        String tick = between(courier, "public static void tick(ServerLevel level, EntityMaid courier)",
                "public static void tickBroomFlight(");
        String orphanRecovery = between(courier,
                "private static void recoverOrphanedRequestLists(",
                "private static boolean hasDispatchableRequest(");
        String standbyWakeup = between(courier, "private static boolean hasDispatchableRequest(",
                "private static void dispatchRequest(");
        String dispatchRequest = between(courier, "private static void dispatchRequest(",
                "private static boolean isActiveCourierList(");
        String automaticFinder = between(courier,
                "private static EditableRequestList findDispatchableRequestList(",
                "public static EditableRequestList findEditableRequestList(");
        String terminal = read(NETWORK_SERVICE);

        assertTrue(beginRequest.contains("findDispatchableRequestList(courier)"));
        assertTrue(tick.contains("recoverOrphanedRequestLists(courier)"));
        assertTrue(orphanRecovery.contains("restoreRequestTag(list, meta)"));
        assertTrue(orphanRecovery.contains("RequestListItem.TAG_IGNORE_TASK, true"));
        assertTrue(standbyWakeup.contains("findDispatchableRequestList(courier)"));
        assertTrue(dispatchRequest.contains("findDispatchableRequestList(courier)"));
        assertTrue(automaticFinder.contains("RequestListItem.isIgnored(stack)"));
        assertTrue(terminal.contains("CourierService.findEditableRequestList(courier)"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "Missing start marker: " + start);
        assertTrue(endIndex > startIndex, "Missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }
}
