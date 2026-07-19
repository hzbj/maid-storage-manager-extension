package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Passenger-service journal. Courier cargo, request lists and warehouse hand-offs never enter it. */
public final class DriverData implements TaskDataKey<DriverData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("driver");
    public static TaskDataKey<Data> KEY;

    public enum Phase {
        IDLE,
        LOCATING_PICKUP,
        TO_PICKUP,
        WAITING_RIDER,
        TO_DESTINATION,
        FOLLOWING_OWNER,
        RETURNING_TO_WAREHOUSE,
        WAREHOUSE_STANDBY,
        PLAYER_CONTROLLED,
        EMERGENCY_LANDING
    }

    public static final class Data {
        private Phase phase = Phase.IDLE;
        private UUID rider;
        private ResourceLocation dimension;
        private BlockPos requestedPickup;
        private BlockPos pickup;
        private BlockPos destinationAnchor;
        private BlockPos destination;
        private BlockPos takeoff;
        private MailboxKey returnMailbox;
        /*
         * The existing broom autopilot is shared as a movement primitive only. This isolated
         * journal is never the maid's CourierData and therefore cannot expose courier cargo.
         */
        private CourierData.Data flight = new CourierData.Data();

        public Phase phase() { return phase; }
        public UUID rider() { return rider; }
        public ResourceLocation dimension() { return dimension; }
        public BlockPos requestedPickup() { return requestedPickup; }
        public BlockPos pickup() { return pickup; }
        public BlockPos destinationAnchor() { return destinationAnchor; }
        public BlockPos destination() { return destination; }
        public BlockPos takeoff() { return takeoff; }
        public MailboxKey returnMailbox() { return returnMailbox; }
        public CourierData.Data flight() { return flight; }

        public boolean activeTrip() {
            return switch (phase) {
                case LOCATING_PICKUP, TO_PICKUP, WAITING_RIDER, TO_DESTINATION,
                        RETURNING_TO_WAREHOUSE, PLAYER_CONTROLLED, EMERGENCY_LANDING -> true;
                default -> false;
            };
        }

        public void begin(UUID rider, ResourceLocation dimension, BlockPos requestedPickup,
                          BlockPos actualPickup, BlockPos destination, BlockPos takeoff,
                          int broomDistance) {
            this.rider = rider;
            this.dimension = dimension;
            this.requestedPickup = immutable(requestedPickup);
            this.pickup = immutable(actualPickup);
            this.destinationAnchor = immutable(destination);
            this.destination = null;
            this.takeoff = immutable(takeoff);
            this.returnMailbox = null;
            this.flight = new CourierData.Data();
            this.flight.transportMode(CourierData.TransportMode.BROOM);
            this.flight.broomFlightDistance(broomDistance);
            setPhase(Phase.TO_PICKUP);
        }

        public void setPhase(Phase value) {
            phase = value == null ? Phase.IDLE : value;
            flight.phase(switch (phase) {
                case LOCATING_PICKUP, TO_PICKUP, RETURNING_TO_WAREHOUSE ->
                        CourierData.Phase.TRANSPORT_TO_PICKUP;
                case WAITING_RIDER -> CourierData.Phase.TRANSPORT_WAITING_RIDER;
                case TO_DESTINATION -> CourierData.Phase.TRANSPORT_TO_DESTINATION;
                case PLAYER_CONTROLLED -> CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED;
                case EMERGENCY_LANDING -> CourierData.Phase.TRANSPORT_EMERGENCY_LANDING;
                default -> CourierData.Phase.IDLE;
            });
        }

        public void pickup(BlockPos value) { pickup = immutable(value); }
        public void destination(BlockPos value) { destination = immutable(value); }

        public void beginReturn(MailboxKey mailbox, BlockPos landing) {
            rider = null;
            returnMailbox = mailbox;
            requestedPickup = immutable(landing);
            pickup = immutable(landing);
            destinationAnchor = immutable(landing);
            destination = null;
            takeoff = null;
            flight.retargetFlight(CourierData.Phase.TRANSPORT_TO_PICKUP);
            setPhase(Phase.RETURNING_TO_WAREHOUSE);
        }

        public void finishTrip(boolean followOwner) {
            rider = null;
            requestedPickup = null;
            pickup = null;
            destinationAnchor = null;
            destination = null;
            takeoff = null;
            returnMailbox = null;
            flight = new CourierData.Data();
            phase = followOwner ? Phase.FOLLOWING_OWNER : Phase.IDLE;
        }

        public void standby() {
            finishTrip(false);
            phase = Phase.WAREHOUSE_STANDBY;
        }

        private static BlockPos immutable(BlockPos value) {
            return value == null ? null : value.immutable();
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
        if (data.rider != null) tag.putUUID("rider", data.rider);
        if (data.dimension != null) tag.putString("dimension", data.dimension.toString());
        putPos(tag, "requestedPickup", data.requestedPickup);
        putPos(tag, "pickup", data.pickup);
        putPos(tag, "destinationAnchor", data.destinationAnchor);
        putPos(tag, "destination", data.destination);
        putPos(tag, "takeoff", data.takeoff);
        if (data.returnMailbox != null) tag.put("returnMailbox", data.returnMailbox.toTag());
        tag.put("flight", new CourierData().writeSaveData(data.flight));
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        try {
            data.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ignored) {
            data.phase = Phase.IDLE;
        }
        data.rider = tag.hasUUID("rider") ? tag.getUUID("rider") : null;
        data.dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        data.requestedPickup = readPos(tag, "requestedPickup");
        data.pickup = readPos(tag, "pickup");
        data.destinationAnchor = readPos(tag, "destinationAnchor");
        data.destination = readPos(tag, "destination");
        data.takeoff = readPos(tag, "takeoff");
        data.returnMailbox = tag.contains("returnMailbox", Tag.TAG_COMPOUND)
                ? MailboxKey.fromTag(tag.getCompound("returnMailbox")) : null;
        data.flight = tag.contains("flight", Tag.TAG_COMPOUND)
                ? new CourierData().readSaveData(tag.getCompound("flight"))
                : new CourierData.Data();
        data.setPhase(data.phase);
        return data;
    }

    public static Data get(EntityMaid maid) {
        Data data = maid.getOrCreateData(KEY, new Data());
        CourierData.Data legacy = CourierData.get(maid);
        if (data.phase == Phase.IDLE && isLegacyTransport(legacy.phase())) {
            data.rider = legacy.transportRider();
            data.dimension = legacy.transportDimension();
            data.requestedPickup = legacy.transportPickupAnchor();
            data.pickup = legacy.transportPickup();
            data.destinationAnchor = legacy.transportDestinationAnchor();
            data.destination = legacy.transportDestination();
            data.flight = new CourierData().readSaveData(
                    new CourierData().writeSaveData(legacy));
            data.setPhase(fromLegacy(legacy.phase()));
            legacy.clearPassengerTransport();
            maid.setAndSyncData(CourierData.KEY, legacy);
            maid.setAndSyncData(KEY, data);
        }
        return data;
    }

    /** Lets the shared broom primitive persist into the driver journal instead of CourierData. */
    public static boolean syncFlight(EntityMaid maid, CourierData.Data flight) {
        if (KEY == null) return false;
        Data data = maid.getOrCreateData(KEY, new Data());
        if (data.flight != flight) return false;
        maid.setAndSyncData(KEY, data);
        return true;
    }

    private static boolean isLegacyTransport(CourierData.Phase phase) {
        return switch (phase) {
            case TRANSPORT_TO_PICKUP, TRANSPORT_WAITING_RIDER, TRANSPORT_TO_DESTINATION,
                    TRANSPORT_PLAYER_CONTROLLED, TRANSPORT_EMERGENCY_LANDING -> true;
            default -> false;
        };
    }

    private static Phase fromLegacy(CourierData.Phase phase) {
        return switch (phase) {
            case TRANSPORT_TO_PICKUP -> Phase.TO_PICKUP;
            case TRANSPORT_WAITING_RIDER -> Phase.WAITING_RIDER;
            case TRANSPORT_TO_DESTINATION -> Phase.TO_DESTINATION;
            case TRANSPORT_PLAYER_CONTROLLED -> Phase.PLAYER_CONTROLLED;
            case TRANSPORT_EMERGENCY_LANDING -> Phase.EMERGENCY_LANDING;
            default -> Phase.IDLE;
        };
    }

    private static void putPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) tag.putLong(key, pos.asLong());
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
