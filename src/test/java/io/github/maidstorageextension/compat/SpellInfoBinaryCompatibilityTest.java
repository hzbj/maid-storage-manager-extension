package io.github.maidstorageextension.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpellInfoBinaryCompatibilityTest {
    @Test
    void remoteLookupUsesFieldsSharedBy184And19InsteadOfRemovedGetters() throws Exception {
        String info = "com/github/yimeng261/maidspell/item/bauble/enderPocket/"
                + "EnderPocketService$EnderPocketMaidInfo";
        Set<String> fields = new HashSet<>();
        try (InputStream input = getClass().getResourceAsStream("EnderPocketCompat$SpellAccess.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (owner.equals(info) && opcode == Opcodes.GETFIELD) fields.add(name);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            assertFalse(owner.equals(info)
                                    && Set.of("getMaidEntityId", "getLevelKey").contains(name),
                                    "Spell 1.9.x removed " + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(Set.of("maidEntityId", "levelKey"), fields);
        Class<?> type = Class.forName(info.replace('/', '.'), false, getClass().getClassLoader());
        assertEquals(int.class, type.getField("maidEntityId").getType());
        assertEquals("net.minecraft.resources.ResourceKey", type.getField("levelKey").getType().getName());
    }
}
