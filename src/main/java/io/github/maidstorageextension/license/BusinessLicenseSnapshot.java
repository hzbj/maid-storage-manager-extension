package io.github.maidstorageextension.license;

import io.github.maidstorageextension.data.BusinessLicenseData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BusinessLicenseSnapshot {
    public record Worker(UUID id, String name, boolean online, boolean onDuty) {
        public Worker {
            name = name == null ? "" : name;
        }
    }

    public record Snapshot(UUID id, String name, BusinessLicenseData.RuleMode mode,
                           List<ResourceLocation> filterItems, ResourceLocation profession,
                           List<Worker> workers, int containers, BlockPos landing,
                           long revision, String blocker) {
        public Snapshot {
            name = name == null ? "" : name;
            mode = mode == null ? BusinessLicenseData.RuleMode.WHITELIST : mode;
            filterItems = filterItems == null ? List.of() : List.copyOf(filterItems);
            workers = workers == null ? List.of() : List.copyOf(workers);
            landing = landing == null ? null : landing.immutable();
            blocker = blocker == null ? "" : blocker;
        }
    }

    private BusinessLicenseSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        if (snapshot.id() != null) tag.putUUID("id", snapshot.id());
        tag.putString("name", snapshot.name());
        tag.putString("mode", snapshot.mode().name());
        ListTag filters = new ListTag();
        for (ResourceLocation item : snapshot.filterItems()) {
            CompoundTag value = new CompoundTag();
            value.putString("item", item.toString());
            filters.add(value);
        }
        tag.put("filters", filters);
        if (snapshot.profession() != null) tag.putString("profession", snapshot.profession().toString());
        ListTag workers = new ListTag();
        for (Worker worker : snapshot.workers()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", worker.id());
            value.putString("name", worker.name());
            value.putBoolean("online", worker.online());
            value.putBoolean("onDuty", worker.onDuty());
            workers.add(value);
        }
        tag.put("workers", workers);
        tag.putInt("containers", snapshot.containers());
        if (snapshot.landing() != null) tag.putLong("landing", snapshot.landing().asLong());
        tag.putLong("revision", snapshot.revision());
        tag.putString("blocker", snapshot.blocker());
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag) {
        UUID id = tag != null && tag.hasUUID("id") ? tag.getUUID("id") : null;
        if (tag == null) return new Snapshot(null, "", BusinessLicenseData.RuleMode.WHITELIST,
                List.of(), null, List.of(), 0, null, 0L, "");
        BusinessLicenseData.RuleMode mode;
        try {
            mode = BusinessLicenseData.RuleMode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException ignored) {
            mode = BusinessLicenseData.RuleMode.WHITELIST;
        }
        List<ResourceLocation> filters = new ArrayList<>();
        ListTag filterTags = tag.getList("filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < filterTags.size() && i < BusinessLicenseData.MAX_FILTER_ITEMS; i++) {
            ResourceLocation item = ResourceLocation.tryParse(filterTags.getCompound(i).getString("item"));
            if (item != null) filters.add(item);
        }
        List<Worker> workers = new ArrayList<>();
        ListTag workerTags = tag.getList("workers", Tag.TAG_COMPOUND);
        for (int i = 0; i < workerTags.size() && i < BusinessLicenseData.MAX_WORKERS; i++) {
            CompoundTag value = workerTags.getCompound(i);
            if (value.hasUUID("id")) workers.add(new Worker(value.getUUID("id"),
                    value.getString("name"), value.getBoolean("online"),
                    value.getBoolean("onDuty")));
        }
        return new Snapshot(id, tag.getString("name"), mode, filters,
                ResourceLocation.tryParse(tag.getString("profession")), workers,
                Math.max(0, tag.getInt("containers")),
                tag.contains("landing", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("landing")) : null,
                Math.max(0L, tag.getLong("revision")), tag.getString("blocker"));
    }
}
