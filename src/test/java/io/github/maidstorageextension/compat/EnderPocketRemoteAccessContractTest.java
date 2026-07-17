package io.github.maidstorageextension.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderPocketRemoteAccessContractTest {
    private static final Path MAIN = Path.of(
            "src/main/java/io/github/maidstorageextension");

    @Test
    void courierMenuUsesTheOfficialRemoteMaidSessionInsteadOfClientTracking() throws Exception {
        String source = read("maid/courier/CourierConfigMenu.java");

        assertTrue(source.contains("EnderPocketCompat.resolveRemoteMaid("
                + "playerInventory.player, maidId)"));
        assertTrue(source.contains(
                "EnderPocketCompat.syncRemoteProxyBeforeMenu(playerInventory.player, maidId)"));
        assertFalse(source.contains("playerInventory.player.level().getEntity(maidId)"));
    }

    @Test
    void everyRemoteConfigurationPacketResolvesTheActiveEnderPocketSession() throws Exception {
        for (String file : List.of(
                "network/CourierCommandPacket.java",
                "network/ExtensionMaidConfigPacket.java",
                "network/StationApprovalPacket.java")) {
            String source = read(file);
            assertTrue(source.contains(
                            "EnderPocketCompat.resolveRemoteMaid(sender, packet.maidId)"),
                    file + " bypasses the active Ender Pocket remote session");
            assertTrue(source.contains("EnderPocketCompat.syncRemoteProxy(sender,"),
                    file + " leaves the remote proxy stale after a successful update");
            assertFalse(source.contains("sender.level().getEntity(packet.maidId)"),
                    file + " still requires the maid to be tracked in the player's dimension");
        }
    }

    @Test
    void courierButtonsSendTheStableMenuEntityIdEvenIfTheProxyIsTemporarilyAbsent()
            throws Exception {
        String source = read("client/CourierConfigScreen.java");

        assertTrue(source.contains(
                "CourierCommandPacket.courier(menu.maidId(), command)"));
        assertTrue(source.contains(
                "CourierCommandPacket.broomFlightDistance(\n"
                        + "                menu.maidId(), broomFlightDistance)"));
        assertTrue(source.contains(
                "CourierCommandPacket.postDeliveryHomeMode(\n"
                        + "                menu.maidId(), stayHomeAfterDelivery)"));
        assertTrue(source.contains(
                "CourierCommandPacket.selectWarehouse(menu.maidId(), warehouse)"));
    }

    @Test
    void worldQueriesForRemoteBindAndUnbindUseTheCouriersDimension() throws Exception {
        String source = read("maid/courier/CourierService.java");
        String bind = between(source, "public static void requestNearestWarehouse(",
                "public static void setBroomFlightDistance(");
        assertTrue(bind.contains(
                "courier.level() instanceof ServerLevel courierLevel"));
        assertTrue(bind.contains(
                "CourierWarehouseStationService.findNearest(\n"
                        + "                courierLevel, courier.blockPosition())"));
        assertFalse(bind.contains("owner.serverLevel()"));

        String unbind = between(source, "public static void unbind(",
                "public static void selectWarehouse(");
        assertTrue(unbind.contains("findMaid(courierLevel, warehouseId)"));
        assertFalse(unbind.contains("owner.serverLevel().getEntity"));
    }

    @Test
    void spellRemoteSessionApiIsOnTheCompileClasspath() throws Exception {
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);

        assertTrue(build.matches("(?s).*compileOnly\\s+fg\\.deobf\\("
                        + "'maven\\.modrinth:QHB4kBBS:bIBBeovu'\\).*"),
                "The official Ender Pocket session resolver must be compiled against directly");

        Class<?> service = loadWithoutInitialization(
                "com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService");
        service.getDeclaredMethod("resolveRemoteMaid", ServerPlayer.class, int.class);
        service.getDeclaredMethod("syncRemoteProxyBeforeMenu", ServerPlayer.class,
                loadWithoutInitialization(
                        "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid"));
        Class<?> cache = loadWithoutInitialization(
                "com.github.yimeng261.maidspell.item.bauble.enderPocket."
                        + "EnderPocketMaidProxyCache");
        cache.getDeclaredMethod("find", Level.class, int.class);
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static Class<?> loadWithoutInitialization(String name) throws ClassNotFoundException {
        return Class.forName(name, false,
                EnderPocketRemoteAccessContractTest.class.getClassLoader());
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }
}
