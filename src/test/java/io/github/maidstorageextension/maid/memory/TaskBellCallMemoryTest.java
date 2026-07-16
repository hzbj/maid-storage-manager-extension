package io.github.maidstorageextension.maid.memory;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskBellCallMemoryTest {
    @Test
    void travellingCallTransitionsToWaitingWithoutLosingItsSafeStand() {
        UUID caller = UUID.randomUUID();
        TaskBellCallMemory call = new TaskBellCallMemory(
                new BlockPos(10, 64, 10),
                new BlockPos(11, 64, 10),
                caller,
                200);

        assertEquals(TaskBellCallMemory.Phase.TRAVELLING, call.phase());
        assertFalse(call.hasArrived());
        call.markArrived(240);
        assertEquals(TaskBellCallMemory.Phase.WAITING, call.phase());
        assertTrue(call.hasArrived());
        assertEquals(240, call.arrivedAt());
        assertEquals(new BlockPos(11, 64, 10), call.standPos());
        assertEquals(caller, call.callerUuid());
    }
}
