package io.github.maidstorageextension.maid.behavior;

import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import studio.fantasyit.maid_storage_manager.util.ViewedInventoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscSortCommitOrderTest {
    @Test
    void destinationTransferCommitsCargoJournalBeforeRefreshingSharedInventoryCache()
            throws IOException {
        assertJournalCommittedBeforeCacheSync("tickDeposit");
    }

    @Test
    void sourceReturnCommitsCargoJournalBeforeRefreshingSharedInventoryCache()
            throws IOException {
        assertJournalCommittedBeforeCacheSync("tickReturn");
    }

    private static void assertJournalCommittedBeforeCacheSync(String methodName) throws IOException {
        Map<String, Integer> calls = new HashMap<>();
        int[] instruction = {0};
        String classPath = "/" + Type.getInternalName(MiscSortBehavior.class) + ".class";
        try (InputStream stream = MiscSortBehavior.class.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled miscellaneous-sort behavior is missing");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            instruction[0]++;
                            if (owner.equals(Type.getInternalName(MiscSortMemory.CargoLine.class))
                                    && name.equals("setInFlightCount")) {
                                calls.putIfAbsent("journal", instruction[0]);
                            }
                            if (owner.equals(Type.getInternalName(MiscSortMemory.class))
                                    && name.equals("replaceCargoLines")) {
                                calls.putIfAbsent("commit", instruction[0]);
                            }
                            if (owner.equals(Type.getInternalName(ViewedInventoryUtil.class))
                                    && name.equals("ambitiousAddItemAndSync")) {
                                calls.putIfAbsent("cache", instruction[0]);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(calls.containsKey("journal"), "Cargo journal update is missing");
        assertTrue(calls.containsKey("commit"), "Cargo journal commit is missing");
        assertTrue(calls.containsKey("cache"), "Shared inventory cache refresh is missing");
        assertTrue(calls.get("journal") < calls.get("cache")
                        && calls.get("commit") < calls.get("cache"),
                "The cargo journal must be committed before cache synchronization can interrupt "
                        + "the transfer and leave an empty maid stuck at an open chest");
    }
}
