package io.github.maidstorageextension.maid.memory;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public class TaskBellCallMemory {
    public enum Phase {
        TRAVELLING,
        WAITING
    }

    private final BlockPos bellPos;
    private BlockPos standPos;
    private final UUID callerUuid;
    private final int travelStartedAt;
    private Phase phase = Phase.TRAVELLING;
    private int arrivedAt = -1;

    public TaskBellCallMemory(BlockPos bellPos, BlockPos standPos, UUID callerUuid, int travelStartedAt) {
        this.bellPos = bellPos;
        this.standPos = standPos;
        this.callerUuid = callerUuid;
        this.travelStartedAt = travelStartedAt;
    }

    public BlockPos bellPos() {
        return bellPos;
    }

    public BlockPos standPos() {
        return standPos;
    }

    public void standPos(BlockPos value) {
        standPos = value;
    }

    public UUID callerUuid() {
        return callerUuid;
    }

    public int travelStartedAt() {
        return travelStartedAt;
    }

    public Phase phase() {
        return phase;
    }

    public int arrivedAt() {
        return arrivedAt;
    }

    public boolean hasArrived() {
        return phase == Phase.WAITING;
    }

    public void markArrived(int tick) {
        if (!hasArrived()) {
            arrivedAt = tick;
            phase = Phase.WAITING;
        }
    }
}
