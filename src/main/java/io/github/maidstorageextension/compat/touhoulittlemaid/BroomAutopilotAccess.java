package io.github.maidstorageextension.compat.touhoulittlemaid;

/**
 * Exposes the synchronized control-authority flag added to Touhou Little Maid brooms.
 */
public interface BroomAutopilotAccess {
    boolean maidStorageExtension$isAutopilot();

    void maidStorageExtension$setAutopilot(boolean autopilot);
}
