package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.client.BusinessLicenseClientData;
import io.github.maidstorageextension.license.BusinessLicenseSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BusinessLicenseSnapshotPacket(BusinessLicenseSnapshot.Snapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<BusinessLicenseSnapshotPacket> TYPE =
            new Type<>(MaidStorageManagerExtension.id("business_license_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BusinessLicenseSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), BusinessLicenseSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(BusinessLicenseSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(BusinessLicenseSnapshot.toTag(packet.snapshot));
    }

    public static BusinessLicenseSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BusinessLicenseSnapshotPacket(BusinessLicenseSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(BusinessLicenseSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BusinessLicenseClientData.accept(packet.snapshot));
    }
}
