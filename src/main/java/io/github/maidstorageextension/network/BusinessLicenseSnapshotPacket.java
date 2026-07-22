package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.BusinessLicenseClientData;
import io.github.maidstorageextension.license.BusinessLicenseSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BusinessLicenseSnapshotPacket(BusinessLicenseSnapshot.Snapshot snapshot) {
    public static void encode(BusinessLicenseSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeNbt(BusinessLicenseSnapshot.toTag(packet.snapshot));
    }

    public static BusinessLicenseSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new BusinessLicenseSnapshotPacket(BusinessLicenseSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(BusinessLicenseSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> BusinessLicenseClientData.accept(packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
