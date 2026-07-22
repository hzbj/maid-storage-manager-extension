package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent route transaction journal, deliberately separate from ordinary CourierData. */
public final class MaidLogisticsCourierData implements TaskDataKey<MaidLogisticsCourierData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("maid_logistics");
    public static TaskDataKey<Data> KEY;

    public enum Phase {
        IDLE,
        TO_LICENSE_SOURCE,
        TO_LICENSE_DESTINATION,
        WAREHOUSE_RUNNING,
        RESTORING_REQUEST_LIST,
        SAFE_RETURN,
        BLOCKED
    }

    public static final class CargoEntry {
        private final ItemStack prototype;
        private final int amount;
        private final int baseline;

        public CargoEntry(ItemStack prototype, int amount, int baseline) {
            this.prototype = prototype == null ? ItemStack.EMPTY : prototype.copyWithCount(1);
            this.amount = Math.max(0, amount);
            this.baseline = Math.max(0, baseline);
        }

        public ItemStack prototype() { return prototype; }
        public int amount() { return amount; }
        public int baseline() { return baseline; }
    }

    public static final class Data {
        private Phase phase = Phase.IDLE;
        private UUID account;
        private UUID route;
        private UUID requestToken;
        private ItemStack originalRequest = ItemStack.EMPTY;
        private CompoundTag originalCourier = new CompoundTag();
        private UUID temporaryWarehouse;
        private boolean temporaryAuthorizationAdded;
        private boolean warehouseStarted;
        private long phaseStarted;
        private final List<CargoEntry> cargo = new ArrayList<>();
        private CourierData.Data flight = new CourierData.Data();

        public Phase phase() { return phase; }
        public UUID account() { return account; }
        public UUID route() { return route; }
        public UUID requestToken() { return requestToken; }
        public ItemStack originalRequest() { return originalRequest; }
        public CompoundTag originalCourier() { return originalCourier; }
        public UUID temporaryWarehouse() { return temporaryWarehouse; }
        public boolean temporaryAuthorizationAdded() { return temporaryAuthorizationAdded; }
        public boolean warehouseStarted() { return warehouseStarted; }
        public long phaseStarted() { return phaseStarted; }
        public List<CargoEntry> cargo() { return cargo; }
        public CourierData.Data flight() { return flight; }

        public boolean active() { return phase != Phase.IDLE; }

        public void begin(UUID account, UUID route, UUID token, ItemStack request,
                          CompoundTag courier, long gameTime) {
            this.account = account;
            this.route = route;
            this.requestToken = token;
            this.originalRequest = request.copy();
            this.originalCourier = courier == null ? new CompoundTag() : courier.copy();
            this.temporaryWarehouse = null;
            this.temporaryAuthorizationAdded = false;
            this.warehouseStarted = false;
            this.phaseStarted = gameTime;
            this.cargo.clear();
            this.flight = new CourierData.Data();
            this.flight.transportMode(CourierData.TransportMode.BROOM);
            this.phase = Phase.TO_LICENSE_SOURCE;
        }

        public void phase(Phase value, long gameTime) {
            phase = value == null ? Phase.BLOCKED : value;
            phaseStarted = gameTime;
        }

        public void temporaryWarehouse(UUID value, boolean authorizationAdded) {
            temporaryWarehouse = value;
            temporaryAuthorizationAdded = authorizationAdded;
        }

        public void warehouseStarted(boolean value) { warehouseStarted = value; }

        public void clear() {
            phase = Phase.IDLE;
            account = null;
            route = null;
            requestToken = null;
            originalRequest = ItemStack.EMPTY;
            originalCourier = new CompoundTag();
            temporaryWarehouse = null;
            temporaryAuthorizationAdded = false;
            warehouseStarted = false;
            phaseStarted = 0L;
            cargo.clear();
            flight = new CourierData.Data();
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", data.phase.name());
        if (data.account != null) tag.putUUID("account", data.account);
        if (data.route != null) tag.putUUID("route", data.route);
        if (data.requestToken != null) tag.putUUID("requestToken", data.requestToken);
        if (!data.originalRequest.isEmpty()) {
            tag.put("originalRequest", data.originalRequest.save(new CompoundTag()));
        }
        tag.put("originalCourier", data.originalCourier.copy());
        if (data.temporaryWarehouse != null) tag.putUUID("temporaryWarehouse", data.temporaryWarehouse);
        tag.putBoolean("temporaryAuthorizationAdded", data.temporaryAuthorizationAdded);
        tag.putBoolean("warehouseStarted", data.warehouseStarted);
        tag.putLong("phaseStarted", data.phaseStarted);
        ListTag cargo = new ListTag();
        for (CargoEntry entry : data.cargo) {
            if (entry.prototype.isEmpty() || entry.amount <= 0) continue;
            CompoundTag value = entry.prototype.save(new CompoundTag());
            value.putInt("LogisticsAmount", entry.amount);
            value.putInt("LogisticsBaseline", entry.baseline);
            cargo.add(value);
        }
        tag.put("cargo", cargo);
        tag.put("flight", new CourierData().writeSaveData(data.flight));
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        try {
            data.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ignored) {
            data.phase = Phase.BLOCKED;
        }
        data.account = tag.hasUUID("account") ? tag.getUUID("account") : null;
        data.route = tag.hasUUID("route") ? tag.getUUID("route") : null;
        data.requestToken = tag.hasUUID("requestToken") ? tag.getUUID("requestToken") : null;
        data.originalRequest = tag.contains("originalRequest", Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound("originalRequest")) : ItemStack.EMPTY;
        data.originalCourier = tag.getCompound("originalCourier").copy();
        data.temporaryWarehouse = tag.hasUUID("temporaryWarehouse")
                ? tag.getUUID("temporaryWarehouse") : null;
        data.temporaryAuthorizationAdded = tag.getBoolean("temporaryAuthorizationAdded");
        data.warehouseStarted = tag.getBoolean("warehouseStarted");
        data.phaseStarted = tag.getLong("phaseStarted");
        ListTag cargo = tag.getList("cargo", Tag.TAG_COMPOUND);
        for (int i = 0; i < cargo.size(); i++) {
            CompoundTag value = cargo.getCompound(i);
            ItemStack prototype = ItemStack.of(value);
            if (!prototype.isEmpty() && value.getInt("LogisticsAmount") > 0) {
                data.cargo.add(new CargoEntry(prototype, value.getInt("LogisticsAmount"),
                        value.getInt("LogisticsBaseline")));
            }
        }
        data.flight = tag.contains("flight", Tag.TAG_COMPOUND)
                ? new CourierData().readSaveData(tag.getCompound("flight"))
                : new CourierData.Data();
        return data;
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, new Data());
    }

    /** Persists shared broom primitive changes in this journal instead of ordinary CourierData. */
    public static boolean syncFlight(EntityMaid maid, CourierData.Data flight) {
        if (KEY == null) return false;
        Data data = maid.getOrCreateData(KEY, new Data());
        if (data.flight != flight) return false;
        maid.setAndSyncData(KEY, data);
        return true;
    }
}
