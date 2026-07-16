package io.github.maidstorageextension.maid.behavior;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscSortTargetContextRegressionTest {
    @Test
    void depositPassKeepsTheCurrentContextWhileCargoStillTargetsTheSameChest()
            throws IOException {
        boolean[] usesRemainingCargoDecision = {false};
        boolean[] invokesPhysicalDeliveryReconciliation = {false};
        boolean[] reconcilesPhysicalDeliveryEvidence = {false};
        String classPath = "/" + Type.getInternalName(MiscSortBehavior.class) + ".class";
        try (InputStream stream = MiscSortBehavior.class.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled miscellaneous-sort behavior is missing");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("tickDeposit")
                            && !name.equals("reconcileDestinationCargo")) return null;
                    String visitedMethod = name;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            if (owner.equals(Type.getInternalName(MiscCargoAccounting.class))
                                    && name.equals("destinationOperationComplete")) {
                                usesRemainingCargoDecision[0] = true;
                            }
                            if (visitedMethod.equals("tickDeposit")
                                    && owner.equals(Type.getInternalName(MiscSortBehavior.class))
                                    && name.equals("reconcileDestinationCargo")) {
                                invokesPhysicalDeliveryReconciliation[0] = true;
                            }
                            if (owner.equals(Type.getInternalName(MiscCargoAccounting.class))
                                    && name.equals("reconcileDestinationJournal")) {
                                reconcilesPhysicalDeliveryEvidence[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(usesRemainingCargoDecision[0],
                "A deposit pass must not finish and rapidly reopen the same physical chest "
                        + "while an in-flight line still points at that chest");
        assertTrue(invokesPhysicalDeliveryReconciliation[0],
                "A deposit pass must invoke physical delivery reconciliation before completion");
        assertTrue(reconcilesPhysicalDeliveryEvidence[0],
                "The open destination must reconcile a stale journal only from matching "
                        + "maid-loss and target-gain evidence");
    }
}
