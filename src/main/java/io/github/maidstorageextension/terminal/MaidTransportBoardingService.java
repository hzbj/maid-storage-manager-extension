package io.github.maidstorageextension.terminal;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Bridges both the broom and its overlapping maid passenger to one authorized boarding path. */
public final class MaidTransportBoardingService {
    public enum Result {
        NOT_TRANSPORT,
        REJECTED,
        BOARDED
    }

    private MaidTransportBoardingService() {
    }

    public static Result tryBoard(ServerPlayer player, Entity target) {
        if (player == null || target == null || !(player.level() instanceof ServerLevel level)) {
            return Result.NOT_TRANSPORT;
        }
        EntityMaid driver;
        EntityBroom broom;
        if (target instanceof EntityMaid maid) {
            driver = maid;
            CourierData.Data data = CourierData.get(driver);
            if (data.phase() != CourierData.Phase.TRANSPORT_WAITING_RIDER) {
                return Result.NOT_TRANSPORT;
            }
            broom = CourierBroomFlightService.transportBroom(level, driver, data);
        } else if (target instanceof EntityBroom targetBroom
                && targetBroom.getPersistentData().hasUUID(
                CourierBroomFlightService.TAG_TRANSPORT_RIDER)) {
            broom = targetBroom;
            driver = broom.getPassengers().stream()
                    .filter(EntityMaid.class::isInstance)
                    .map(EntityMaid.class::cast)
                    .findFirst().orElse(null);
        } else {
            return Result.NOT_TRANSPORT;
        }
        if (driver == null || broom == null || !driver.isAlive() || !broom.isAlive()) {
            return Result.REJECTED;
        }
        CourierData.Data data = CourierData.get(driver);
        UUID intended = data.transportRider();
        if (data.phase() != CourierData.Phase.TRANSPORT_WAITING_RIDER
                || intended == null || !intended.equals(player.getUUID())
                || !broom.getPersistentData().hasUUID(
                CourierBroomFlightService.TAG_TRANSPORT_RIDER)
                || !intended.equals(broom.getPersistentData().getUUID(
                CourierBroomFlightService.TAG_TRANSPORT_RIDER))) {
            return Result.REJECTED;
        }
        if (player.isPassenger()) {
            return player.getVehicle() == broom ? Result.BOARDED : Result.REJECTED;
        }
        if (!MaidTransportBoardingPolicy.withinRange(player.distanceToSqr(broom))
                || broom.getPassengers().size() != 1
                || broom.getFirstPassenger() != driver) {
            return Result.REJECTED;
        }
        return player.startRiding(broom, true) ? Result.BOARDED : Result.REJECTED;
    }
}
