package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import io.github.maidstorageextension.data.WarehouseCourierData;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.registry.ExtensionItems;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.List;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public final class ExtensionMaidApi implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new CourierTask());
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get(),
                (IMaidBauble) ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override
            public List<MemoryModuleType<?>> getExtraMemoryTypes() {
                return List.of(
                        ExtensionMemoryModules.PERIODIC_SCAN.get(),
                        ExtensionMemoryModules.MISC_SORT.get(),
                        ExtensionMemoryModules.TASK_BELL_CALL.get()
                );
            }
        });
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        ExtensionConfigData.KEY = register.register(new ExtensionConfigData());
        MaintenanceStatusData.KEY = register.register(new MaintenanceStatusData());
        CourierData.KEY = register.register(new CourierData());
        WarehouseCourierData.KEY = register.register(new WarehouseCourierData());
        WarehouseStationData.KEY = register.register(new WarehouseStationData());
    }
}
