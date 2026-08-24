package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierRequestListPersistenceContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/maid/courier/CourierService.java");

    @Test
    void selectionPriorityAndOriginPersistenceCoverBothHandsAndLowestBackpackSlot()
            throws Exception {
        String source = read();
        String finder = between(source, "public static EditableRequestList findEditableRequestList(",
                "public static void updateEditableRequestList(");

        assertInOrder(finder, "getMainHandItem()", "getOffhandItem()", "for (int slot = 0;");
        assertTrue(finder.contains("CourierData.RequestListOrigin.MAIN_HAND"));
        assertTrue(finder.contains("CourierData.RequestListOrigin.OFF_HAND"));
        assertTrue(finder.contains("CourierData.RequestListOrigin.BACKPACK"));
        assertTrue(source.contains(
                "data.requestListOrigin(selection.origin(), selection.backpackSlot())"));
    }

    @Test
    void completionRestoresOriginalSlotThenBackpackAndKeepsPendingRecordWhenFull()
            throws Exception {
        String source = read();
        String returning = between(source, "private static boolean returnList(",
                "/** Called from the request-return mixin");

        assertTrue(returning.contains("restoreOriginalRequestListSlot(courier, data, list)"));
        assertTrue(returning.contains(
                "CourierInventory.insert(courier.getAvailableBackpackInv(), list)"));
        assertTrue(returning.contains("if (restored) data.clearRequestListOrigin()"));
        assertTrue(returning.contains("data.pendingList()"));
        assertTrue(returning.contains("data.pendingList(ItemStack.EMPTY)"));
        assertTrue(source.contains("if (!returnList(courier, data, list, !success)) data.pendingList(list)"));
    }

    private static String read() throws Exception {
        return Files.readString(SERVICE, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static void assertInOrder(String source, String first, String second, String third) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        int thirdIndex = source.indexOf(third);
        assertTrue(firstIndex >= 0 && firstIndex < secondIndex && secondIndex < thirdIndex);
    }
}
