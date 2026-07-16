package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persisted courier link and transaction journal. Item stacks retain their complete NBT. */
public final class CourierData implements TaskDataKey<CourierData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("courier");
    public static TaskDataKey<Data> KEY;
    public static final int DEFAULT_BROOM_FLIGHT_DISTANCE = 32;
    public static final int MIN_BROOM_FLIGHT_DISTANCE = 32;
    public static final int MAX_BROOM_FLIGHT_DISTANCE = 64;
    public static final int MAX_WAREHOUSES = 6;

    public enum Phase {
        UNBOUND,
        WAITING_APPROVAL,
        IDLE,
        TRAVEL_TO_WAREHOUSE_REQUEST,
        REQUEST_HANDOFF,
        REQUEST_RUNNING,
        REQUEST_WAITING_SPACE,
        TRAVEL_TO_OWNER,
        TRAVEL_TO_DELIVERY_CHEST,
        DELIVERY_CHEST_WAITING_SPACE,
        WAITING_WITH_CARGO_AT_DELIVERY_CHEST,
        OWNER_HANDOFF,
        OWNER_WAITING_SPACE,
        RETURNING_TO_ORIGIN,
        RETURNING_AFTER_LANDING_FAILURE,
        WAITING_OWNER_PICKUP,
        WAITING_AT_STATION_AFTER_RECALL,
        WAITING_AT_DELIVERY_CHEST,
        WAITING_FOR_SAFE_LANDING,
        TRAVEL_TO_WAREHOUSE_DEPOSIT,
        DEPOSIT_HANDOFF,
        DEPOSIT_RUNNING,
        DEPOSIT_RETURNING,
        DEPOSIT_WAITING_SPACE,
        LINK_UNAVAILABLE
    }

    public enum TransportMode {
        NONE,
        WALK,
        ENDER_POCKET,
        BROOM,
        BROOM_ENDER_POCKET;

        public boolean usesBroom() {
            return this == BROOM || this == BROOM_ENDER_POCKET;
        }

        public boolean usesEnderPocket() {
            return this == ENDER_POCKET || this == BROOM_ENDER_POCKET;
        }
    }

    public static final class ManifestEntry {
        private final ItemStack prototype;
        private final int amount;
        private final int baseline;

        public ManifestEntry(ItemStack prototype, int amount, int baseline) {
            this.prototype = prototype.copyWithCount(1);
            this.amount = Math.max(0, amount);
            this.baseline = Math.max(0, baseline);
        }

        public ItemStack prototype() { return prototype; }
        public int amount() { return amount; }
        public int baseline() { return baseline; }
    }

    /** One authorized warehouse, its mailbox authority, and the separate open-air flight pad. */
    public record WarehouseBinding(UUID warehouse, BlockPos warehousePos,
                                   ResourceLocation warehouseDimension, BlockPos mailboxPos,
                                   ResourceLocation mailboxDimension, BlockPos stationPos,
                                   ResourceLocation stationDimension, String warehouseName) {
        public WarehouseBinding {
            warehousePos = warehousePos == null ? null : warehousePos.immutable();
            mailboxPos = mailboxPos == null ? null : mailboxPos.immutable();
            stationPos = stationPos == null ? null : stationPos.immutable();
            warehouseName = warehouseName == null ? "" : warehouseName;
        }

        public boolean hasStation() {
            return mailboxPos != null && mailboxDimension != null
                    && stationPos != null && stationDimension != null;
        }
    }

    public static final class Data {
        private UUID warehouse;
        private UUID pendingWarehouse;
        private WarehouseBinding pendingBinding;
        private BlockPos warehousePos;
        private ResourceLocation warehouseDimension;
        private final List<WarehouseBinding> warehouses = new ArrayList<>();
        private BlockPos originPos;
        private ResourceLocation originDimension;
        private boolean originOwner;
        private BlockPos deliveryPos;
        private ResourceLocation deliveryDimension;
        private BlockPos ownerTargetPos;
        private ResourceLocation ownerTargetDimension;
        private Phase phase = Phase.UNBOUND;
        private boolean depositRequested;
        private boolean spaceWarningSent;
        private boolean accessoryWarningSent;
        private boolean requestFinished;
        private boolean targetWarningSent;
        private TransportMode transportMode = TransportMode.NONE;
        private int broomFlightDistance = DEFAULT_BROOM_FLIGHT_DISTANCE;
        private boolean stayHomeAfterDelivery;
        private boolean followOverrideActive;
        private boolean homeModeBeforeCourier;
        private UUID courierBroom;
        private Phase flightLeg;
        private int flightCruiseY;
        private BlockPos flightTakeoffPos;
        private BlockPos flightLandingPos;
        private boolean flightLanded;
        private boolean flightWarningSent;
        private int flightSearchMinRadius;
        private int flightSearchMaxRadius;
        private int flightRejectedLandings;
        private int flightLandingFailures;
        private ItemStack pendingList = ItemStack.EMPTY;
        private final List<ItemStack> pendingCargo = new ArrayList<>();
        private final List<ManifestEntry> requestManifest = new ArrayList<>();
        private final List<ManifestEntry> depositManifest = new ArrayList<>();
        private long lastDepositProgressGameTime;
        private int lastDepositRemaining = -1;
        private long handoffStartedGameTime = -1L;
        private Phase groundApproachPhase;
        private BlockPos groundApproachPos;
        private long groundApproachProgressGameTime = -1L;
        private int groundTeleportFailures;

        public UUID warehouse() { return warehouse; }
        public UUID pendingWarehouse() { return pendingWarehouse; }
        public WarehouseBinding pendingBinding() { return pendingBinding; }
        public BlockPos warehousePos() { return warehousePos; }
        public ResourceLocation warehouseDimension() { return warehouseDimension; }
        public BlockPos stationPos() {
            WarehouseBinding binding = activeBinding();
            return binding == null ? null : binding.stationPos();
        }
        public ResourceLocation stationDimension() {
            WarehouseBinding binding = activeBinding();
            return binding == null ? null : binding.stationDimension();
        }
        public String warehouseName() {
            WarehouseBinding binding = activeBinding();
            return binding == null ? "" : binding.warehouseName();
        }
        public List<WarehouseBinding> warehouses() { return List.copyOf(warehouses); }
        public BlockPos originPos() { return originPos; }
        public ResourceLocation originDimension() { return originDimension; }
        public boolean originOwner() { return originOwner; }
        public BlockPos deliveryPos() { return deliveryPos; }
        public ResourceLocation deliveryDimension() { return deliveryDimension; }
        public BlockPos ownerTargetPos() { return ownerTargetPos; }
        public ResourceLocation ownerTargetDimension() { return ownerTargetDimension; }
        public Phase phase() { return phase; }
        public boolean depositRequested() { return depositRequested; }
        public boolean spaceWarningSent() { return spaceWarningSent; }
        public boolean accessoryWarningSent() { return accessoryWarningSent; }
        public boolean requestFinished() { return requestFinished; }
        public boolean targetWarningSent() { return targetWarningSent; }
        public TransportMode transportMode() { return transportMode; }
        public int broomFlightDistance() { return broomFlightDistance; }
        public boolean stayHomeAfterDelivery() { return stayHomeAfterDelivery; }
        public boolean followOverrideActive() { return followOverrideActive; }
        public boolean homeModeBeforeCourier() { return homeModeBeforeCourier; }
        public UUID courierBroom() { return courierBroom; }
        public Phase flightLeg() { return flightLeg; }
        public int flightCruiseY() { return flightCruiseY; }
        public BlockPos flightTakeoffPos() { return flightTakeoffPos; }
        public BlockPos flightLandingPos() { return flightLandingPos; }
        public boolean flightLanded() { return flightLanded; }
        public boolean flightWarningSent() { return flightWarningSent; }
        public int flightSearchMinRadius() { return flightSearchMinRadius; }
        public int flightSearchMaxRadius() { return flightSearchMaxRadius; }
        public int flightRejectedLandings() { return flightRejectedLandings; }
        public int flightLandingFailures() { return flightLandingFailures; }
        public ItemStack pendingList() { return pendingList; }
        public List<ItemStack> pendingCargo() { return pendingCargo; }
        public List<ManifestEntry> requestManifest() { return requestManifest; }
        public List<ManifestEntry> depositManifest() { return depositManifest; }
        public long lastDepositProgressGameTime() { return lastDepositProgressGameTime; }
        public int lastDepositRemaining() { return lastDepositRemaining; }
        public long handoffStartedGameTime() { return handoffStartedGameTime; }
        public Phase groundApproachPhase() { return groundApproachPhase; }
        public BlockPos groundApproachPos() { return groundApproachPos; }
        public long groundApproachProgressGameTime() { return groundApproachProgressGameTime; }
        public int groundTeleportFailures() { return groundTeleportFailures; }

        public void bind(UUID value, BlockPos pos, ResourceLocation dimension) {
            warehouses.clear();
            pendingBinding = null;
            pendingWarehouse = null;
            if (value == null) {
                warehouse = null;
                warehousePos = null;
                warehouseDimension = null;
            } else {
                WarehouseBinding binding = new WarehouseBinding(
                        value, pos, dimension, null, null, null, null, "");
                warehouses.add(binding);
                setActive(binding);
            }
            phase = warehouses.isEmpty() ? Phase.UNBOUND : Phase.IDLE;
            clearRoute();
        }

        public void requestApproval(UUID value) {
            requestApproval(value == null ? null : new WarehouseBinding(
                    value, null, null, null, null, null, null, ""));
        }

        public void requestApproval(WarehouseBinding value) {
            clearGroundApproach();
            pendingBinding = value;
            pendingWarehouse = value == null ? null : value.warehouse();
            phase = value == null
                    ? warehouses.isEmpty() ? Phase.UNBOUND : Phase.IDLE
                    : Phase.WAITING_APPROVAL;
        }

        public boolean addWarehouse(WarehouseBinding binding) {
            if (binding == null || binding.warehouse() == null) return false;
            int existing = indexOf(binding.warehouse());
            if (existing >= 0) {
                warehouses.set(existing, binding);
                if (binding.warehouse().equals(warehouse)) setActive(binding);
            } else {
                if (warehouses.size() >= MAX_WAREHOUSES) return false;
                warehouses.add(binding);
                if (warehouse == null) setActive(binding);
            }
            pendingBinding = null;
            pendingWarehouse = null;
            if (phase == Phase.UNBOUND || phase == Phase.WAITING_APPROVAL) phase = Phase.IDLE;
            return true;
        }

        public boolean selectWarehouse(UUID value) {
            int index = indexOf(value);
            if (index < 0) return false;
            WarehouseBinding binding = warehouses.remove(index);
            warehouses.add(0, binding);
            setActive(binding);
            pendingBinding = null;
            pendingWarehouse = null;
            phase = Phase.IDLE;
            clearRoute();
            return true;
        }

        public boolean removeWarehouse(UUID value) {
            int index = indexOf(value);
            if (index < 0) return false;
            warehouses.remove(index);
            if (value != null && value.equals(warehouse)) {
                if (warehouses.isEmpty()) {
                    warehouse = null;
                    warehousePos = null;
                    warehouseDimension = null;
                    phase = Phase.UNBOUND;
                } else {
                    setActive(warehouses.get(0));
                    phase = Phase.IDLE;
                }
            }
            clearRoute();
            return true;
        }

        public WarehouseBinding binding(UUID value) {
            int index = indexOf(value);
            return index < 0 ? null : warehouses.get(index);
        }

        private WarehouseBinding activeBinding() {
            return binding(warehouse);
        }

        private int indexOf(UUID value) {
            if (value == null) return -1;
            for (int i = 0; i < warehouses.size(); i++) {
                if (value.equals(warehouses.get(i).warehouse())) return i;
            }
            return -1;
        }

        private void setActive(WarehouseBinding binding) {
            warehouse = binding == null ? null : binding.warehouse();
            warehousePos = binding == null ? null : binding.warehousePos();
            warehouseDimension = binding == null ? null : binding.warehouseDimension();
        }

        public void phase(Phase value) {
            Phase next = value == null ? Phase.UNBOUND : value;
            if (phase != next) clearGroundApproach();
            phase = next;
        }
        public void depositRequested(boolean value) { depositRequested = value; }
        public void spaceWarningSent(boolean value) { spaceWarningSent = value; }
        public void accessoryWarningSent(boolean value) { accessoryWarningSent = value; }
        public void requestFinished(boolean value) { requestFinished = value; }
        public void targetWarningSent(boolean value) { targetWarningSent = value; }
        public void transportMode(TransportMode value) {
            transportMode = value == null ? TransportMode.NONE : value;
        }
        public void broomFlightDistance(int value) {
            broomFlightDistance = clampBroomFlightDistance(value);
        }
        public void stayHomeAfterDelivery(boolean value) {
            stayHomeAfterDelivery = value;
        }
        public void deliveryTarget(BlockPos pos, ResourceLocation dimension) {
            deliveryPos = pos == null ? null : pos.immutable();
            deliveryDimension = pos == null ? null : dimension;
        }
        public void ownerTarget(BlockPos pos, ResourceLocation dimension) {
            ownerTargetPos = pos == null ? null : pos.immutable();
            ownerTargetDimension = pos == null ? null : dimension;
            targetWarningSent = false;
            clearGroundApproach();
        }
        public void beginFollowOverride(boolean homeMode) {
            if (followOverrideActive) return;
            homeModeBeforeCourier = homeMode;
            followOverrideActive = true;
        }
        public void clearFollowOverride() {
            followOverrideActive = false;
            homeModeBeforeCourier = false;
        }
        public void flight(UUID broom, Phase leg, int cruiseY) {
            courierBroom = broom;
            flightLeg = leg;
            flightCruiseY = cruiseY;
            flightLanded = false;
        }
        public void retargetFlight(Phase leg) {
            flightLeg = leg;
            flightTakeoffPos = null;
            flightLandingPos = null;
            flightLanded = false;
            flightWarningSent = false;
            flightSearchMinRadius = 0;
            flightSearchMaxRadius = 0;
            flightRejectedLandings = 0;
            flightLandingFailures = 0;
        }
        public void prepareFlight(Phase leg, BlockPos takeoffPos) {
            if (flightLeg != leg) {
                flightLandingPos = null;
                flightSearchMinRadius = 0;
                flightSearchMaxRadius = 0;
                flightRejectedLandings = 0;
            }
            courierBroom = null;
            flightLeg = leg;
            flightCruiseY = 0;
            flightTakeoffPos = takeoffPos == null ? null : takeoffPos.immutable();
            flightLanded = false;
            flightWarningSent = false;
            flightLandingFailures = 0;
        }
        public void flightLandingPos(BlockPos value) {
            flightLandingPos = value == null ? null : value.immutable();
        }
        public void finishFlight() {
            courierBroom = null;
            flightCruiseY = 0;
            flightLanded = true;
        }
        public void flightWarningSent(boolean value) { flightWarningSent = value; }
        public void flightSearchWindow(int minRadius, int maxRadius) {
            flightSearchMinRadius = Math.max(0, minRadius);
            flightSearchMaxRadius = Math.max(flightSearchMinRadius, maxRadius);
        }
        public void flightRejectedLandings(int value) {
            flightRejectedLandings = Math.max(0, value);
        }
        public void flightLandingFailures(int value) {
            flightLandingFailures = Math.max(0, value);
        }
        public void rejectFlightLanding() {
            flightLandingPos = null;
            flightTakeoffPos = null;
            flightLanded = false;
            flightWarningSent = false;
            flightLandingFailures = 0;
            flightRejectedLandings++;
        }
        public void clearFlight() {
            courierBroom = null;
            flightLeg = null;
            flightCruiseY = 0;
            flightTakeoffPos = null;
            flightLandingPos = null;
            flightLanded = false;
            flightWarningSent = false;
            flightSearchMinRadius = 0;
            flightSearchMaxRadius = 0;
            flightRejectedLandings = 0;
            flightLandingFailures = 0;
        }
        public void pendingList(ItemStack value) { pendingList = value == null ? ItemStack.EMPTY : value.copy(); }
        public void lastDepositProgressGameTime(long value) { lastDepositProgressGameTime = Math.max(0L, value); }
        public void lastDepositRemaining(int value) { lastDepositRemaining = value; }
        public void handoffStartedGameTime(long value) { handoffStartedGameTime = value; }
        public void groundApproach(Phase phase, BlockPos position, long progressGameTime) {
            groundApproachPhase = phase;
            groundApproachPos = position == null ? null : position.immutable();
            groundApproachProgressGameTime = progressGameTime;
        }
        public void groundTeleportFailures(int value) {
            groundTeleportFailures = Math.max(0, value);
        }
        public void clearGroundApproach() {
            groundApproachPhase = null;
            groundApproachPos = null;
            groundApproachProgressGameTime = -1L;
            groundTeleportFailures = 0;
        }

        public void rememberWarehouse(BlockPos pos, ResourceLocation dimension) {
            warehousePos = pos;
            warehouseDimension = dimension;
            int index = indexOf(warehouse);
            if (index >= 0) {
                WarehouseBinding current = warehouses.get(index);
                warehouses.set(index, new WarehouseBinding(current.warehouse(), pos, dimension,
                        current.mailboxPos(), current.mailboxDimension(),
                        current.stationPos(), current.stationDimension(), current.warehouseName()));
            }
        }

        public void beginRoute(BlockPos pos, ResourceLocation dimension, boolean ownerAnchor) {
            clearGroundApproach();
            originPos = pos;
            originDimension = dimension;
            originOwner = ownerAnchor;
            targetWarningSent = false;
            handoffStartedGameTime = -1L;
        }

        public void clearRoute() {
            originPos = null;
            originDimension = null;
            originOwner = false;
            ownerTargetPos = null;
            ownerTargetDimension = null;
            targetWarningSent = false;
            handoffStartedGameTime = -1L;
            transportMode = TransportMode.NONE;
            clearGroundApproach();
            clearFlight();
        }

        public void clearRequest() {
            requestManifest.clear();
            requestFinished = false;
            spaceWarningSent = false;
        }

        public void clearDeposit() {
            depositManifest.clear();
            depositRequested = false;
            spaceWarningSent = false;
            lastDepositProgressGameTime = 0L;
            lastDepositRemaining = -1;
        }
    }

    @Override
    public ResourceLocation getKey() { return LOCATION; }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        if (data.warehouse != null) tag.putUUID("warehouse", data.warehouse);
        if (data.pendingWarehouse != null) tag.putUUID("pendingWarehouse", data.pendingWarehouse);
        tag.put("warehouses", writeWarehouseBindings(data.warehouses));
        if (data.pendingBinding != null) {
            tag.put("pendingBinding", writeWarehouseBinding(data.pendingBinding));
        }
        if (data.warehousePos != null) tag.putLong("warehousePos", data.warehousePos.asLong());
        if (data.warehouseDimension != null) tag.putString("warehouseDimension", data.warehouseDimension.toString());
        if (data.originPos != null) tag.putLong("originPos", data.originPos.asLong());
        if (data.originDimension != null) tag.putString("originDimension", data.originDimension.toString());
        tag.putBoolean("originOwner", data.originOwner);
        if (data.deliveryPos != null) tag.putLong("deliveryPos", data.deliveryPos.asLong());
        if (data.deliveryDimension != null) tag.putString("deliveryDimension", data.deliveryDimension.toString());
        if (data.ownerTargetPos != null) tag.putLong("ownerTargetPos", data.ownerTargetPos.asLong());
        if (data.ownerTargetDimension != null) {
            tag.putString("ownerTargetDimension", data.ownerTargetDimension.toString());
        }
        tag.putString("phase", data.phase.name());
        tag.putBoolean("depositRequested", data.depositRequested);
        tag.putBoolean("spaceWarningSent", data.spaceWarningSent);
        tag.putBoolean("accessoryWarningSent", data.accessoryWarningSent);
        tag.putBoolean("requestFinished", data.requestFinished);
        tag.putBoolean("targetWarningSent", data.targetWarningSent);
        tag.putString("transportMode", data.transportMode.name());
        tag.putInt("broomFlightDistance", data.broomFlightDistance);
        tag.putBoolean("stayHomeAfterDelivery", data.stayHomeAfterDelivery);
        tag.putBoolean("followOverrideActive", data.followOverrideActive);
        tag.putBoolean("homeModeBeforeCourier", data.homeModeBeforeCourier);
        if (data.courierBroom != null) tag.putUUID("courierBroom", data.courierBroom);
        if (data.flightLeg != null) tag.putString("flightLeg", data.flightLeg.name());
        tag.putInt("flightCruiseY", data.flightCruiseY);
        if (data.flightTakeoffPos != null) tag.putLong("flightTakeoffPos", data.flightTakeoffPos.asLong());
        if (data.flightLandingPos != null) tag.putLong("flightLandingPos", data.flightLandingPos.asLong());
        tag.putBoolean("flightLanded", data.flightLanded);
        tag.putBoolean("flightWarningSent", data.flightWarningSent);
        tag.putInt("flightSearchMinRadius", data.flightSearchMinRadius);
        tag.putInt("flightSearchMaxRadius", data.flightSearchMaxRadius);
        tag.putInt("flightRejectedLandings", data.flightRejectedLandings);
        tag.putInt("flightLandingFailures", data.flightLandingFailures);
        if (!data.pendingList.isEmpty()) tag.put("pendingList", data.pendingList.save(new CompoundTag()));
        tag.put("pendingCargo", writeStacks(data.pendingCargo));
        tag.put("requestManifest", writeManifest(data.requestManifest));
        tag.put("depositManifest", writeManifest(data.depositManifest));
        tag.putLong("lastDepositProgressGameTime", data.lastDepositProgressGameTime);
        tag.putInt("lastDepositRemaining", data.lastDepositRemaining);
        tag.putLong("handoffStartedGameTime", data.handoffStartedGameTime);
        if (data.groundApproachPhase != null) {
            tag.putString("groundApproachPhase", data.groundApproachPhase.name());
        }
        if (data.groundApproachPos != null) {
            tag.putLong("groundApproachPos", data.groundApproachPos.asLong());
        }
        tag.putLong("groundApproachProgressGameTime", data.groundApproachProgressGameTime);
        tag.putInt("groundTeleportFailures", data.groundTeleportFailures);
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        if (tag.hasUUID("warehouse")) data.warehouse = tag.getUUID("warehouse");
        if (tag.hasUUID("pendingWarehouse")) data.pendingWarehouse = tag.getUUID("pendingWarehouse");
        if (tag.contains("warehousePos", Tag.TAG_LONG)) data.warehousePos = BlockPos.of(tag.getLong("warehousePos"));
        if (tag.contains("warehouseDimension", Tag.TAG_STRING)) {
            data.warehouseDimension = ResourceLocation.tryParse(tag.getString("warehouseDimension"));
        }
        ListTag bindings = tag.getList("warehouses", Tag.TAG_COMPOUND);
        for (int i = 0; i < bindings.size() && data.warehouses.size() < MAX_WAREHOUSES; i++) {
            WarehouseBinding binding = readWarehouseBinding(bindings.getCompound(i));
            if (binding != null && data.indexOf(binding.warehouse()) < 0) {
                data.warehouses.add(binding);
            }
        }
        if (data.warehouses.isEmpty() && data.warehouse != null) {
            data.warehouses.add(new WarehouseBinding(data.warehouse, data.warehousePos,
                    data.warehouseDimension, null, null, null, null, ""));
        }
        if (!data.warehouses.isEmpty()) {
            int active = data.indexOf(data.warehouse);
            if (active > 0) data.warehouses.add(0, data.warehouses.remove(active));
            data.setActive(data.warehouses.get(0));
        }
        if (tag.contains("pendingBinding", Tag.TAG_COMPOUND)) {
            data.pendingBinding = readWarehouseBinding(tag.getCompound("pendingBinding"));
            data.pendingWarehouse = data.pendingBinding == null
                    ? data.pendingWarehouse : data.pendingBinding.warehouse();
        }
        if (tag.contains("originPos", Tag.TAG_LONG)) data.originPos = BlockPos.of(tag.getLong("originPos"));
        if (tag.contains("originDimension", Tag.TAG_STRING)) {
            data.originDimension = ResourceLocation.tryParse(tag.getString("originDimension"));
        }
        data.originOwner = tag.getBoolean("originOwner");
        if (tag.contains("deliveryPos", Tag.TAG_LONG)) {
            data.deliveryPos = BlockPos.of(tag.getLong("deliveryPos"));
        }
        if (tag.contains("deliveryDimension", Tag.TAG_STRING)) {
            data.deliveryDimension = ResourceLocation.tryParse(tag.getString("deliveryDimension"));
        }
        if (tag.contains("ownerTargetPos", Tag.TAG_LONG)) {
            data.ownerTargetPos = BlockPos.of(tag.getLong("ownerTargetPos"));
        }
        if (tag.contains("ownerTargetDimension", Tag.TAG_STRING)) {
            data.ownerTargetDimension = ResourceLocation.tryParse(tag.getString("ownerTargetDimension"));
        }
        try {
            data.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ignored) {
            data.phase = data.warehouse == null ? Phase.UNBOUND : Phase.IDLE;
        }
        data.depositRequested = tag.getBoolean("depositRequested");
        data.spaceWarningSent = tag.getBoolean("spaceWarningSent");
        data.accessoryWarningSent = tag.getBoolean("accessoryWarningSent");
        data.requestFinished = tag.getBoolean("requestFinished");
        data.targetWarningSent = tag.getBoolean("targetWarningSent");
        try {
            data.transportMode = TransportMode.valueOf(tag.getString("transportMode"));
        } catch (IllegalArgumentException ignored) {
            data.transportMode = TransportMode.NONE;
        }
        data.broomFlightDistance = tag.contains("broomFlightDistance")
                ? clampBroomFlightDistance(tag.getInt("broomFlightDistance"))
                : DEFAULT_BROOM_FLIGHT_DISTANCE;
        data.stayHomeAfterDelivery = tag.getBoolean("stayHomeAfterDelivery");
        data.followOverrideActive = tag.getBoolean("followOverrideActive");
        data.homeModeBeforeCourier = tag.getBoolean("homeModeBeforeCourier");
        if (tag.hasUUID("courierBroom")) data.courierBroom = tag.getUUID("courierBroom");
        try {
            data.flightLeg = Phase.valueOf(tag.getString("flightLeg"));
        } catch (IllegalArgumentException ignored) {
            data.flightLeg = null;
        }
        data.flightCruiseY = tag.getInt("flightCruiseY");
        if (tag.contains("flightTakeoffPos", Tag.TAG_LONG)) {
            data.flightTakeoffPos = BlockPos.of(tag.getLong("flightTakeoffPos"));
        }
        if (tag.contains("flightLandingPos", Tag.TAG_LONG)) {
            data.flightLandingPos = BlockPos.of(tag.getLong("flightLandingPos"));
        }
        data.flightLanded = tag.getBoolean("flightLanded");
        data.flightWarningSent = tag.getBoolean("flightWarningSent");
        data.flightSearchMinRadius = Math.max(0, tag.getInt("flightSearchMinRadius"));
        data.flightSearchMaxRadius = Math.max(data.flightSearchMinRadius,
                tag.getInt("flightSearchMaxRadius"));
        data.flightRejectedLandings = Math.max(0, tag.getInt("flightRejectedLandings"));
        data.flightLandingFailures = Math.max(0, tag.getInt("flightLandingFailures"));
        if (tag.contains("pendingList", Tag.TAG_COMPOUND)) {
            data.pendingList = ItemStack.of(tag.getCompound("pendingList"));
        }
        data.pendingCargo.addAll(readStacks(tag.getList("pendingCargo", Tag.TAG_COMPOUND)));
        data.requestManifest.addAll(readManifest(tag.getList("requestManifest", Tag.TAG_COMPOUND)));
        data.depositManifest.addAll(readManifest(tag.getList("depositManifest", Tag.TAG_COMPOUND)));
        data.lastDepositProgressGameTime = Math.max(0L, tag.getLong("lastDepositProgressGameTime"));
        data.lastDepositRemaining = tag.contains("lastDepositRemaining")
                ? tag.getInt("lastDepositRemaining") : -1;
        data.handoffStartedGameTime = tag.contains("handoffStartedGameTime", Tag.TAG_LONG)
                ? tag.getLong("handoffStartedGameTime") : -1L;
        try {
            data.groundApproachPhase = Phase.valueOf(tag.getString("groundApproachPhase"));
        } catch (IllegalArgumentException ignored) {
            data.groundApproachPhase = null;
        }
        if (tag.contains("groundApproachPos", Tag.TAG_LONG)) {
            data.groundApproachPos = BlockPos.of(tag.getLong("groundApproachPos"));
        }
        data.groundApproachProgressGameTime = tag.contains(
                "groundApproachProgressGameTime", Tag.TAG_LONG)
                ? tag.getLong("groundApproachProgressGameTime") : -1L;
        data.groundTeleportFailures = Math.max(0, tag.getInt("groundTeleportFailures"));
        return data;
    }

    private static ListTag writeManifest(List<ManifestEntry> entries) {
        ListTag list = new ListTag();
        for (ManifestEntry entry : entries) {
            CompoundTag tag = entry.prototype.save(new CompoundTag());
            tag.putInt("CourierAmount", entry.amount);
            tag.putInt("CourierBaseline", entry.baseline);
            list.add(tag);
        }
        return list;
    }

    private static ListTag writeWarehouseBindings(List<WarehouseBinding> bindings) {
        ListTag list = new ListTag();
        for (WarehouseBinding binding : bindings) list.add(writeWarehouseBinding(binding));
        return list;
    }

    private static CompoundTag writeWarehouseBinding(WarehouseBinding binding) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("warehouse", binding.warehouse());
        if (binding.warehousePos() != null) tag.putLong("warehousePos", binding.warehousePos().asLong());
        if (binding.warehouseDimension() != null) {
            tag.putString("warehouseDimension", binding.warehouseDimension().toString());
        }
        if (binding.mailboxPos() != null) tag.putLong("mailboxPos", binding.mailboxPos().asLong());
        if (binding.mailboxDimension() != null) {
            tag.putString("mailboxDimension", binding.mailboxDimension().toString());
        }
        if (binding.stationPos() != null) tag.putLong("stationPos", binding.stationPos().asLong());
        if (binding.stationDimension() != null) {
            tag.putString("stationDimension", binding.stationDimension().toString());
        }
        if (!binding.warehouseName().isEmpty()) tag.putString("warehouseName", binding.warehouseName());
        return tag;
    }

    private static WarehouseBinding readWarehouseBinding(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("warehouse")) return null;
        BlockPos warehousePos = tag.contains("warehousePos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("warehousePos")) : null;
        ResourceLocation warehouseDimension = tag.contains("warehouseDimension", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("warehouseDimension")) : null;
        BlockPos mailboxPos = tag.contains("mailboxPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("mailboxPos")) : null;
        ResourceLocation mailboxDimension = tag.contains("mailboxDimension", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("mailboxDimension")) : null;
        BlockPos stationPos = tag.contains("stationPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("stationPos")) : null;
        ResourceLocation stationDimension = tag.contains("stationDimension", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("stationDimension")) : null;
        return new WarehouseBinding(tag.getUUID("warehouse"), warehousePos, warehouseDimension,
                mailboxPos, mailboxDimension, stationPos, stationDimension,
                tag.getString("warehouseName"));
    }

    private static ListTag writeStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    private static List<ItemStack> readStacks(ListTag list) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    private static List<ManifestEntry> readManifest(ListTag list) {
        List<ManifestEntry> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack stack = ItemStack.of(tag);
            if (!stack.isEmpty()) {
                result.add(new ManifestEntry(stack, tag.getInt("CourierAmount"),
                        tag.getInt("CourierBaseline")));
            }
        }
        return result;
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, new Data());
    }

    public static int clampBroomFlightDistance(int value) {
        return Math.max(MIN_BROOM_FLIGHT_DISTANCE, Math.min(MAX_BROOM_FLIGHT_DISTANCE, value));
    }
}
