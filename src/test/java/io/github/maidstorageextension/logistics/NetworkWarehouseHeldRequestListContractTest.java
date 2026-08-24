package io.github.maidstorageextension.logistics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkWarehouseHeldRequestListContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/logistics/NetworkWarehouseService.java");

    @Test
    void withdrawalOnlyUpdatesAnExistingCourierListFromHandsOrBackpack() throws Exception {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String submit = between(source, "private static void submitRequest(",
                "private static void confirmDeposit(");

        assertTrue(submit.contains("CourierService.findEditableRequestList(courier)"));
        assertTrue(submit.contains("ItemStack request = requestSelection.stack()"));
        assertTrue(submit.contains("NetworkWarehouseRequestFactory.update("));
        assertTrue(submit.contains("if (requestSelection == null)"));
        assertTrue(submit.contains("CourierService.updateEditableRequestList("));
        assertFalse(submit.contains("new ItemStack("));
        assertFalse(submit.contains("ItemHandlerHelper"));
        assertFalse(submit.contains("InvWrapper"));
        assertFalse(submit.contains("insertItem"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }
}
