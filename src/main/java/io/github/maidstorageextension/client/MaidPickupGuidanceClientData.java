package io.github.maidstorageextension.client;

import io.github.maidstorageextension.network.MaidPickupGuidancePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class MaidPickupGuidanceClientData {
    public record Guidance(UUID driver, ResourceLocation dimension, BlockPos position) {
    }

    private static Guidance current;

    private MaidPickupGuidanceClientData() {
    }

    public static void accept(MaidPickupGuidancePacket packet) {
        current = packet != null && packet.active()
                && packet.driver() != null && packet.dimension() != null
                && packet.position() != null
                ? new Guidance(packet.driver(), packet.dimension(), packet.position()) : null;
    }

    public static Guidance current() {
        return current;
    }
}
