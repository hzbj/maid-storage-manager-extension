package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-safe account roster. Password hashes and device grants never enter this payload. */
public final class TerminalAccountSnapshot {
    public record Maid(UUID id, String name, boolean online, boolean courierTask, boolean driverTask,
                       boolean hasBroom, boolean busy, String phase, String transportMode,
                       ResourceLocation dimension, BlockPos position) {
        public Maid {
            name = name == null ? "" : name;
            phase = phase == null ? "offline" : phase;
            transportMode = transportMode == null ? "none" : transportMode;
            position = position == null ? null : position.immutable();
        }

        public boolean hasPosition() { return dimension != null && position != null; }
    }

    public record Mailbox(ResourceLocation dimension, BlockPos position, UUID warehouse,
                          String warehouseName, boolean valid,
                          boolean warehouseOnline, boolean warehouseOnDuty,
                          List<WarehouseManager> managers) {
        public Mailbox {
            warehouseName = warehouseName == null ? "" : warehouseName;
            position = position == null ? null : position.immutable();
            managers = managers == null ? List.of() : List.copyOf(managers);
        }
    }

    public record WarehouseManager(UUID id, String name, String status, String detail) {
        public WarehouseManager {
            name = name == null ? "" : name;
            status = status == null || status.isBlank() ? "offline" : status;
            detail = detail == null ? "" : detail;
        }
    }

    public record Snapshot(boolean authenticated, boolean passwordChangeRequired,
                           String username, UUID account,
                           UUID selectedCourier, UUID selectedDriver, MailboxKey selectedMailbox,
                           List<Maid> maids, List<Mailbox> mailboxes, String error) {
        public Snapshot {
            username = username == null ? "" : username;
            maids = maids == null ? List.of() : List.copyOf(maids);
            mailboxes = mailboxes == null ? List.of() : List.copyOf(mailboxes);
            error = error == null ? "" : error;
        }

        public static Snapshot loggedOut(String error) {
            return new Snapshot(false, false, "", null, null, null, null,
                    List.of(), List.of(), error);
        }
    }

    private TerminalAccountSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("authenticated", snapshot.authenticated());
        tag.putBoolean("passwordChangeRequired", snapshot.passwordChangeRequired());
        tag.putString("username", snapshot.username());
        if (snapshot.account() != null) tag.putUUID("account", snapshot.account());
        if (snapshot.selectedCourier() != null) tag.putUUID("selectedCourier", snapshot.selectedCourier());
        if (snapshot.selectedDriver() != null) tag.putUUID("selectedDriver", snapshot.selectedDriver());
        if (snapshot.selectedMailbox() != null) {
            tag.put("selectedMailbox", snapshot.selectedMailbox().toTag());
        }
        tag.putString("error", snapshot.error());
        ListTag maids = new ListTag();
        for (Maid maid : snapshot.maids()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", maid.id());
            entry.putString("name", maid.name());
            entry.putBoolean("online", maid.online());
            entry.putBoolean("courierTask", maid.courierTask());
            entry.putBoolean("driverTask", maid.driverTask());
            entry.putBoolean("hasBroom", maid.hasBroom());
            entry.putBoolean("busy", maid.busy());
            entry.putString("phase", maid.phase());
            entry.putString("transportMode", maid.transportMode());
            if (maid.hasPosition()) {
                entry.putString("dimension", maid.dimension().toString());
                entry.putLong("position", maid.position().asLong());
            }
            maids.add(entry);
        }
        tag.put("maids", maids);
        ListTag mailboxes = new ListTag();
        for (Mailbox mailbox : snapshot.mailboxes()) {
            if (mailbox.dimension() == null || mailbox.position() == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", mailbox.dimension().toString());
            entry.putLong("position", mailbox.position().asLong());
            if (mailbox.warehouse() != null) entry.putUUID("warehouse", mailbox.warehouse());
            entry.putString("warehouseName", mailbox.warehouseName());
            entry.putBoolean("valid", mailbox.valid());
            entry.putBoolean("warehouseOnline", mailbox.warehouseOnline());
            entry.putBoolean("warehouseOnDuty", mailbox.warehouseOnDuty());
            ListTag managers = new ListTag();
            for (WarehouseManager manager : mailbox.managers()) {
                if (manager.id() == null) continue;
                CompoundTag managerTag = new CompoundTag();
                managerTag.putUUID("id", manager.id());
                managerTag.putString("name", manager.name());
                managerTag.putString("status", manager.status());
                managerTag.putString("detail", manager.detail());
                managers.add(managerTag);
            }
            entry.put("managers", managers);
            mailboxes.add(entry);
        }
        tag.put("mailboxes", mailboxes);
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag) {
        if (tag == null) return Snapshot.loggedOut("");
        List<Maid> maids = new ArrayList<>();
        ListTag maidTags = tag.getList("maids", Tag.TAG_COMPOUND);
        for (int i = 0; i < maidTags.size(); i++) {
            CompoundTag entry = maidTags.getCompound(i);
            if (!entry.hasUUID("id")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            BlockPos position = entry.contains("position", Tag.TAG_LONG)
                    ? BlockPos.of(entry.getLong("position")) : null;
            maids.add(new Maid(entry.getUUID("id"), entry.getString("name"),
                    entry.getBoolean("online"), entry.getBoolean("courierTask"),
                    entry.getBoolean("driverTask"),
                    entry.getBoolean("hasBroom"), entry.getBoolean("busy"),
                    entry.getString("phase"), entry.getString("transportMode"),
                    dimension, position));
        }
        List<Mailbox> mailboxes = new ArrayList<>();
        ListTag mailboxTags = tag.getList("mailboxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < mailboxTags.size(); i++) {
            CompoundTag entry = mailboxTags.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            BlockPos position = entry.contains("position", Tag.TAG_LONG)
                    ? BlockPos.of(entry.getLong("position")) : null;
            if (dimension == null || position == null) continue;
            List<WarehouseManager> managers = new ArrayList<>();
            ListTag managerTags = entry.getList("managers", Tag.TAG_COMPOUND);
            for (int j = 0; j < managerTags.size(); j++) {
                CompoundTag manager = managerTags.getCompound(j);
                if (!manager.hasUUID("id")) continue;
                managers.add(new WarehouseManager(manager.getUUID("id"),
                        manager.getString("name"), manager.getString("status"),
                        manager.getString("detail")));
            }
            mailboxes.add(new Mailbox(dimension, position,
                    entry.hasUUID("warehouse") ? entry.getUUID("warehouse") : null,
                    entry.getString("warehouseName"), entry.getBoolean("valid"),
                    entry.getBoolean("warehouseOnline"),
                    entry.getBoolean("warehouseOnDuty"), managers));
        }
        return new Snapshot(tag.getBoolean("authenticated"),
                tag.getBoolean("passwordChangeRequired"), tag.getString("username"),
                tag.hasUUID("account") ? tag.getUUID("account") : null,
                tag.hasUUID("selectedCourier") ? tag.getUUID("selectedCourier") : null,
                tag.hasUUID("selectedDriver") ? tag.getUUID("selectedDriver") : null,
                tag.contains("selectedMailbox", Tag.TAG_COMPOUND)
                        ? MailboxKey.fromTag(tag.getCompound("selectedMailbox")) : null,
                maids, mailboxes, tag.getString("error"));
    }
}
