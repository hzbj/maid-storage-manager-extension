package io.github.maidstorageextension.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteMaidIndexDataTest {
    @Test
    void savedLocationsSurviveServerRestart() {
        UUID maid = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("maid", maid);
        entry.putString("dimension", "minecraft:the_nether");
        entry.putLong("position", new BlockPos(320, 70, -96).asLong());
        entry.putString("name", "Courier");
        entry.putString("task", "maid_storage_manager_extension:courier");
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(entry);
        root.put("maids", list);

        RemoteMaidIndexData data = RemoteMaidIndexData.load(root);
        RemoteMaidIndexData.Entry decoded = data.get(maid);
        assertEquals(new ResourceLocation("minecraft", "the_nether"), decoded.dimension());
        assertEquals(new BlockPos(320, 70, -96), decoded.position());
        assertEquals("Courier", decoded.name());
    }
}
