package io.github.maidstorageextension.license;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.BusinessLicenseBlockEntity;
import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationValidator;
import io.github.maidstorageextension.logistics.MaidDisplayName;
import io.github.maidstorageextension.network.BusinessLicenseActionPacket;
import io.github.maidstorageextension.network.BusinessLicenseSnapshotPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner-authorized configuration and short-lived world targeting modes for a license. */
public final class BusinessLicenseService {
    public enum TargetMode {
        CONTAINER,
        LANDING,
        WORKER
    }

    private record Pending(UUID license, ResourceLocation dimension, TargetMode mode, long expiresAt) {
    }

    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
    private static final long TARGET_TIMEOUT_TICKS = 20L * 60L;

    private BusinessLicenseService() {
    }

    public static void handle(ServerPlayer player, BusinessLicenseActionPacket packet) {
        if (!(player.containerMenu instanceof BusinessLicenseMenu menu)
                || !menu.licenseId().equals(packet.license())) return;
        if (!menu.remote()) {
            BusinessLicenseBlockEntity block = player.serverLevel().getBlockEntity(menu.position())
                    instanceof BusinessLicenseBlockEntity value ? value : null;
            if (block == null || !block.isOwner(player)
                    || !block.licenseId().equals(packet.license())) return;
        } else if (!canConfigureFromTerminal(player, menu.terminalId(), packet.license())) {
            return;
        }
        BusinessLicenseData data = BusinessLicenseData.get(player.getServer());
        switch (packet.action()) {
            case REFRESH -> { }
            case SET_MODE -> data.setMode(packet.license(), player.getUUID(), packet.mode());
            case RENAME -> data.rename(packet.license(), player.getUUID(), packet.value());
            case TOGGLE_HELD_FILTER -> {
                ItemStack held = player.getMainHandItem();
                ResourceLocation item = held.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(held.getItem());
                if (item != null) data.toggleFilter(packet.license(), player.getUUID(), item);
            }
            case TOGGLE_FILTER -> {
                ResourceLocation item = ResourceLocation.tryParse(packet.value());
                if (item != null && ForgeRegistries.ITEMS.containsKey(item)) {
                    data.toggleFilter(packet.license(), player.getUUID(), item);
                }
            }
            case CLEAR_FILTER -> data.clearFilter(packet.license(), player.getUUID());
            case ARM_CONTAINER -> arm(player, packet.license(), TargetMode.CONTAINER);
            case ARM_LANDING -> arm(player, packet.license(), TargetMode.LANDING);
            case ARM_WORKER -> arm(player, packet.license(), TargetMode.WORKER);
        }
        send(player, packet.license());
    }

    public static boolean canConfigureFromTerminal(ServerPlayer player, UUID terminalId,
                                                   UUID licenseId) {
        TerminalAccountService.Session session = TerminalAccountService.authenticate(player, terminalId);
        BusinessLicenseData.Snapshot license = licenseId == null ? null
                : BusinessLicenseData.get(player.getServer()).get(licenseId);
        return session != null && license != null
                && session.data().ownsLicense(session.account(), licenseId)
                && player.getUUID().equals(license.owner());
    }

    /** Opens the same editor remotely after the terminal account revalidates node ownership. */
    public static boolean openFromTerminal(ServerPlayer player, UUID terminalId,
                                           io.github.maidstorageextension.logistics.MaidLogisticsData.NodeRef node) {
        if (node == null
                || node.kind() != io.github.maidstorageextension.logistics.MaidLogisticsData.NodeKind.LICENSE
                || !canConfigureFromTerminal(player, terminalId, node.license())) return false;
        BusinessLicenseData.Snapshot license = BusinessLicenseData.get(player.getServer())
                .get(node.license());
        if (license == null || !license.dimension().equals(node.dimension())
                || !license.position().equals(node.position())) return false;
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new BusinessLicenseMenu(
                        containerId, inventory, license.position(), license.id(), terminalId),
                Component.translatable("gui.maid_storage_manager_extension.business_license.title")),
                buffer -> {
                    buffer.writeBlockPos(license.position());
                    buffer.writeUUID(license.id());
                    buffer.writeBoolean(true);
                    buffer.writeUUID(terminalId);
                });
        return true;
    }

    private static void arm(ServerPlayer player, UUID license, TargetMode mode) {
        PENDING.put(player.getUUID(), new Pending(license, player.level().dimension().location(),
                mode, player.serverLevel().getGameTime() + TARGET_TIMEOUT_TICKS));
        player.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.business_license.mode." +
                        mode.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public static boolean targetBlock(ServerPlayer player, ServerLevel level, BlockPos clicked,
                                      Direction face) {
        Pending pending = pending(player, level);
        if (pending == null || pending.mode == TargetMode.WORKER) return false;
        BusinessLicenseData data = BusinessLicenseData.get(level.getServer());
        BusinessLicenseData.Snapshot license = data.get(pending.license);
        if (license == null || !player.getUUID().equals(license.owner())) {
            PENDING.remove(player.getUUID());
            return true;
        }
        boolean success;
        if (pending.mode == TargetMode.CONTAINER) {
            boolean handler = level.getBlockEntity(clicked) != null
                    && level.getBlockEntity(clicked).getCapability(
                    ForgeCapabilities.ITEM_HANDLER, face).isPresent();
            success = handler && data.toggleContainer(pending.license, player.getUUID(),
                    new BusinessLicenseData.ContainerRef(clicked, face));
        } else {
            BlockPos landing = clicked.relative(face);
            success = face == Direction.UP
                    && BusinessLicenseData.withinHorizontal(license.position(), landing,
                    BusinessLicenseData.RANGE)
                    && CourierWarehouseStationValidator.hasValidPad(level, landing)
                    && data.setLanding(pending.license, player.getUUID(), landing);
        }
        PENDING.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable(success
                ? "message.maid_storage_manager_extension.business_license.target_saved"
                : "message.maid_storage_manager_extension.business_license.target_invalid"));
        return true;
    }

    public static boolean targetMaid(ServerPlayer player, Entity target) {
        if (!(target instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) return false;
        Pending pending = pending(player, level);
        if (pending == null || pending.mode != TargetMode.WORKER) return false;
        BusinessLicenseData.BindWorkerResult result = BusinessLicenseData.get(level.getServer())
                .toggleWorker(pending.license, player.getUUID(), maid.getUUID(), maid.getOwnerUUID(),
                        maid.getTask().getUid());
        PENDING.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.business_license.worker."
                        + result.name().toLowerCase(java.util.Locale.ROOT)));
        return true;
    }

    private static Pending pending(ServerPlayer player, ServerLevel level) {
        Pending value = PENDING.get(player.getUUID());
        if (value == null) return null;
        if (!value.dimension.equals(level.dimension().location())
                || value.expiresAt < level.getGameTime()) {
            PENDING.remove(player.getUUID());
            return null;
        }
        return value;
    }

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAt < time);
    }

    /** Sneak-use a placed license with a logged-in terminal to register the node. */
    public static boolean registerToTerminal(ServerPlayer player, UUID terminalId,
                                             ServerLevel level, BlockPos position) {
        if (!(level.getBlockEntity(position) instanceof BusinessLicenseBlockEntity block)) return false;
        TerminalAccountService.Session session = TerminalAccountService.authenticate(player, terminalId);
        if (session == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.terminal.login_required"));
            return true;
        }
        BusinessLicenseData.Snapshot license = BusinessLicenseData.get(level.getServer())
                .get(block.licenseId());
        if (license == null || !player.getUUID().equals(license.owner())) {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.business_license.not_owner"));
            return true;
        }
        var result = session.data().registerLicense(session.account(),
                new io.github.maidstorageextension.terminal.TerminalAccountData.BusinessLicense(
                        license.id(), license.dimension(), license.position(), license.name()));
        player.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.business_license.register."
                        + result.name().toLowerCase(java.util.Locale.ROOT)));
        TerminalAccountService.update(player, terminalId);
        return true;
    }

    public static void send(ServerPlayer player, UUID licenseId) {
        BusinessLicenseData.Snapshot value = BusinessLicenseData.get(player.getServer()).get(licenseId);
        if (value == null || !player.getUUID().equals(value.owner())) return;
        List<BusinessLicenseSnapshot.Worker> workers = new ArrayList<>();
        for (UUID id : value.workers()) {
            EntityMaid maid = TerminalAccountService.findMaid(player.getServer(), id);
            workers.add(new BusinessLicenseSnapshot.Worker(id,
                    maid == null ? id.toString().substring(0, 8) : MaidDisplayName.encode(maid),
                    maid != null, maid != null && value.profession() != null
                    && value.profession().equals(maid.getTask().getUid())));
        }
        BusinessLicenseSnapshot.Snapshot snapshot = new BusinessLicenseSnapshot.Snapshot(
                value.id(), value.name(), value.mode(), value.filterItems(), value.profession(),
                workers, value.containers().size(), value.landingPos(), value.revision(), value.blocker());
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BusinessLicenseSnapshotPacket(snapshot));
    }
}
