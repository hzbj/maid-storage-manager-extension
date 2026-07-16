package io.github.maidstorageextension.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import io.github.maidstorageextension.maid.behavior.MiscSortBehavior;
import io.github.maidstorageextension.maid.behavior.view.RefreshInventoryListBehavior;
import io.github.maidstorageextension.mixin.CustomEmptyModelMixin;
import io.github.maidstorageextension.mixin.PlaceMoveBehaviorMixin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import studio.fantasyit.maid_storage_manager.items.render.CustomEmptyModel;
import studio.fantasyit.maid_storage_manager.craft.work.ProgressData;
import studio.fantasyit.maid_storage_manager.items.ProgressPad;
import studio.fantasyit.maid_storage_manager.maid.behavior.ScheduleBehavior;
import studio.fantasyit.maid_storage_manager.maid.behavior.place.PlaceBehavior;
import studio.fantasyit.maid_storage_manager.maid.behavior.place.PlaceMoveBehavior;
import studio.fantasyit.maid_storage_manager.maid.behavior.view.ViewBehavior;
import studio.fantasyit.maid_storage_manager.maid.behavior.view.ViewMoveBehavior;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;
import studio.fantasyit.maid_storage_manager.maid.memory.ViewedInventoryMemory;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import studio.fantasyit.maid_storage_manager.storage.base.ISlotBasedStorage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilitySeamTest {
    @Test
    void official1156MixinSeamsArePresent() {
        assertDoesNotThrow(() -> ScheduleBehavior.class.getDeclaredMethod(
                "start", ServerLevel.class, EntityMaid.class, long.class));
        assertDoesNotThrow(() -> StorageManageTask.class.getDeclaredMethod(
                "createBrainTasks", EntityMaid.class));
        assertDoesNotThrow(() -> PlaceMoveBehavior.class.getDeclaredMethod(
                "priorityTarget", ServerLevel.class, EntityMaid.class));
        assertDoesNotThrow(() -> PlaceMoveBehavior.class.getDeclaredField("chestPos"));
        assertDoesNotThrow(() -> PlaceMoveBehavior.class.getDeclaredField("maidAvailableItems"));
        assertDoesNotThrow(() -> ViewedInventoryMemory.class.getDeclaredMethod("positionFlatten"));
        assertDoesNotThrow(() -> ViewBehavior.class.getDeclaredMethod(
                "tick", ServerLevel.class, EntityMaid.class, long.class));
        assertDoesNotThrow(() -> ViewBehavior.class.getDeclaredMethod(
                "stop", ServerLevel.class, EntityMaid.class, long.class));
        assertDoesNotThrow(() -> ViewBehavior.class.getDeclaredField("context"));
        assertDoesNotThrow(() -> ViewBehavior.class.getDeclaredField("target"));
        assertDoesNotThrow(() -> ViewMoveBehavior.class.getDeclaredMethod(
                "start", ServerLevel.class, EntityMaid.class, long.class));
        assertDoesNotThrow(() -> PlaceBehavior.class.getDeclaredMethod(
                "exceedSlotLimit", ISlotBasedStorage.class, ItemStack.class, EntityMaid.class));
        assertDoesNotThrow(() -> PlaceBehavior.class.getDeclaredField("target"));
        assertDoesNotThrow(() -> StorageManagerConfigData.class.getDeclaredMethod(
                "readSaveData", CompoundTag.class));
        assertDoesNotThrow(() -> CustomEmptyModel.class.getDeclaredMethod("getParticleIcon"));
        assertDoesNotThrow(() -> ProgressData.class.getDeclaredMethod(
                "fromMaidAuto", EntityMaid.class, ServerLevel.class,
                ProgressPad.Viewing.class, ProgressPad.Merge.class, int.class));
    }

    @Test
    void miscFallbackRedirectCoversProduction1156FlattenSeam() throws IOException {
        String expectedTarget = "Lstudio/fantasyit/maid_storage_manager/maid/memory/ViewedInventoryMemory;"
                + "positionFlatten()Ljava/util/Map;";
        assertEquals(1, productionPriorityFlattenInvocationCount(expectedTarget),
                "Official 1.15.6 priorityTarget must have exactly one flatten invocation");

        RedirectMetadata redirect = miscPriorityRedirectMetadata();
        assertNotNull(redirect, "Misc priority candidate redirect is missing");
        assertTrue(redirect.methods.contains("priorityTarget"),
                "Misc priority redirect does not select priorityTarget");
        assertEquals(expectedTarget, redirect.target,
                "Misc priority redirect does not cover the production flatten invocation");
        assertEquals(1, redirect.require, "Misc priority redirect must fail closed when its seam changes");
    }

    @Test
    void cleanupAndListRefreshDispatchThroughTheMaidsActiveNavigation() throws IOException {
        String navigationOwner = "net/minecraft/world/entity/ai/navigation/PathNavigation";
        String moveDescriptor = "(DDDD)Z";
        assertEquals(1, invocationCount(
                MiscSortBehavior.class, "setTravelTarget", navigationOwner, moveDescriptor));
        assertEquals(1, invocationCount(
                RefreshInventoryListBehavior.class, "setTravelTarget", navigationOwner, moveDescriptor));
    }

    @Test
    void tlmClothConfigExtensionSeamIsPresent() {
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getRoot"));
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getEntryBuilder"));
        assertDoesNotThrow(() -> AbstractMaidContainerGui.class.getDeclaredMethod("getMaid"));
    }

    @Test
    void particleMixinSelectorCoversProduction1156Method() throws IOException {
        Set<String> productionMethods = productionParticleMethods();
        Set<String> injectionSelectors = particleInjectionSelectors();
        Set<String> normalizedSelectors = new HashSet<>();
        injectionSelectors.forEach(selector -> normalizedSelectors.add(methodName(selector)));

        assertFalse(productionMethods.isEmpty(), "Production CustomEmptyModel particle method is missing");
        assertFalse(normalizedSelectors.isEmpty(), "Particle mixin has no @Inject method selector");
        assertFalse(disjoint(productionMethods, normalizedSelectors),
                () -> "Particle mixin selectors " + injectionSelectors
                        + " do not cover production methods " + productionMethods);
    }

    private static Set<String> productionParticleMethods() throws IOException {
        Path jarPath = productionMaidStorageManagerJar();
        String classPath = "studio/fantasyit/maid_storage_manager/items/render/CustomEmptyModel.class";
        Set<String> names = new HashSet<>();
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry(classPath);
            assertNotNull(entry, "Official 1.15.6 CustomEmptyModel class is missing");
            try (InputStream stream = jar.getInputStream(entry)) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (descriptor.equals("()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;")) {
                            names.add(name);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return names;
    }

    private static Set<String> particleInjectionSelectors() throws IOException {
        Set<String> selectors = new HashSet<>();
        String classPath = "/" + Type.getInternalName(CustomEmptyModelMixin.class) + ".class";
        try (InputStream stream = CustomEmptyModelMixin.class.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled particle mixin class is missing");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if (!annotationDescriptor.equals(Type.getDescriptor(Inject.class))) {
                                return null;
                            }
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitArray(String name) {
                                    if (!"method".equals(name)) {
                                        return null;
                                    }
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            selectors.add(String.valueOf(value));
                                        }
                                    };
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return selectors;
    }

    private static int productionPriorityFlattenInvocationCount(String expectedTarget) throws IOException {
        Path jarPath = productionMaidStorageManagerJar();
        String classPath = "studio/fantasyit/maid_storage_manager/maid/behavior/place/PlaceMoveBehavior.class";
        int[] count = {0};
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry(classPath);
            assertNotNull(entry, "Official 1.15.6 PlaceMoveBehavior class is missing");
            try (InputStream stream = jar.getInputStream(entry)) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (!name.equals("priorityTarget")) {
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name,
                                                        String descriptor, boolean isInterface) {
                                String target = "L" + owner + ";" + name + descriptor;
                                if (target.equals(expectedTarget)) {
                                    count[0]++;
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return count[0];
    }

    private static Path productionMaidStorageManagerJar() throws IOException {
        assertNotNull(CustomEmptyModel.class.getProtectionDomain().getCodeSource(),
                "Cannot locate the resolved Maid Storage Manager dependency");
        try {
            return Path.of(CustomEmptyModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid Maid Storage Manager dependency path", exception);
        }
    }

    private static RedirectMetadata miscPriorityRedirectMetadata() throws IOException {
        RedirectMetadata[] result = {null};
        String classPath = "/" + Type.getInternalName(PlaceMoveBehaviorMixin.class) + ".class";
        try (InputStream stream = PlaceMoveBehaviorMixin.class.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled place move mixin class is missing");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("maidStorageExtension$excludeMiscFromPriorityCandidates")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if (!annotationDescriptor.equals(Type.getDescriptor(Redirect.class))) {
                                return null;
                            }
                            RedirectMetadata metadata = new RedirectMetadata();
                            result[0] = metadata;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    if ("require".equals(name)) {
                                        metadata.require = (Integer) value;
                                    }
                                }

                                @Override
                                public AnnotationVisitor visitArray(String name) {
                                    if (!"method".equals(name)) {
                                        return null;
                                    }
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            metadata.methods.add(String.valueOf(value));
                                        }
                                    };
                                }

                                @Override
                                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                    if (!"at".equals(name)) {
                                        return null;
                                    }
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            if ("target".equals(name)) {
                                                metadata.target = String.valueOf(value);
                                            }
                                        }
                                    };
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return result[0];
    }

    private static int invocationCount(Class<?> declaringClass, String methodName,
                                       String invocationOwner, String invocationDescriptor)
            throws IOException {
        int[] count = {0};
        String classPath = "/" + Type.getInternalName(declaringClass) + ".class";
        try (InputStream stream = declaringClass.getResourceAsStream(classPath)) {
            assertNotNull(stream, "Compiled class is missing: " + declaringClass.getName());
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            if (owner.equals(invocationOwner)
                                    && descriptor.equals(invocationDescriptor)) {
                                count[0]++;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return count[0];
    }

    private static final class RedirectMetadata {
        private final Set<String> methods = new HashSet<>();
        private String target;
        private int require = -1;
    }

    private static String methodName(String selector) {
        int descriptorStart = selector.indexOf('(');
        return descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
    }

    private static boolean disjoint(Set<String> first, Set<String> second) {
        return first.stream().noneMatch(second::contains);
    }
}
