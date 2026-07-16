package io.github.maidstorageextension.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import io.github.maidstorageextension.network.ReachabilityDebugPacket;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ReachabilityDebugClientData {
    private static List<ReachabilityDebugPacket.Entry> entries = List.of();
    private static long expiresAt;

    private ReachabilityDebugClientData() {
    }

    public static void show(List<ReachabilityDebugPacket.Entry> newEntries, int durationTicks) {
        entries = List.copyOf(newEntries);
        Minecraft minecraft = Minecraft.getInstance();
        expiresAt = minecraft.level == null ? durationTicks : minecraft.level.getGameTime() + durationTicks;
    }

    public static List<ReachabilityDebugPacket.Entry> getVisibleEntries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getGameTime() >= expiresAt) {
            entries = List.of();
        }
        return entries;
    }
}
