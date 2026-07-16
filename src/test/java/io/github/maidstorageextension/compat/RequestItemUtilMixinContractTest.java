package io.github.maidstorageextension.compat;

import io.github.maidstorageextension.mixin.RequestItemUtilMixin;
import io.github.maidstorageextension.mixin.RequestRetBehaviorMixin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestItemUtilMixinContractTest {
    @Test
    void malformedRequestUuidIsRepairedBeforeEitherCompletionPathReadsIt() throws IOException {
        List<String> calls = handlerCalls();
        int repair = calls.indexOf(Type.getInternalName(RequestListSafety.class) + ".ensureJobUuid");
        int courierFinish = calls.indexOf(
                "io/github/maidstorageextension/maid/courier/CourierService.finishRequest");

        assertTrue(repair >= 0, "The request-stop mixin must repair malformed job UUIDs");
        assertTrue(courierFinish > repair,
                "UUID repair must run before courier and upstream completion read the request UUID");
    }

    @Test
    void blockedWarehouseReturnUsesSafeMaidTeleportBeforeVirtualItemFallback() throws IOException {
        List<String> calls = methodCalls(RequestRetBehaviorMixin.class,
                "maidStorageExtension$persistCourierOverflow");
        int recover = calls.indexOf(
                "io/github/maidstorageextension/maid/courier/CourierService.tryRecoverRequestHandoff");
        int virtualFallback = calls.indexOf(
                "studio/fantasyit/maid_storage_manager/util/InvUtil.throwItemVirtual");

        assertTrue(recover >= 0,
                "A wall-stalled warehouse return must try Touhou Little Maid's safe teleport");
        assertTrue(virtualFallback > recover,
                "Safe face-to-face recovery must run before the upstream virtual-item fallback");
    }

    private static List<String> handlerCalls() throws IOException {
        return methodCalls(RequestItemUtilMixin.class,
                "maidStorageExtension$returnCourierRequestWithoutDropping");
    }

    private static List<String> methodCalls(Class<?> type, String methodName) throws IOException {
        List<String> calls = new ArrayList<>();
        String classPath = "/" + Type.getInternalName(type) + ".class";
        try (InputStream stream = type.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled request-item mixin is missing");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }
}
