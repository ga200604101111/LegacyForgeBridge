package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Non-executing inventory of source-owned legacy Entity behavior surfaces. */
public final class LegacyEntityBehaviorSurfaceAnalyzer {
    public enum CallbackKind {
        ENTITY_INIT,
        TICK,
        ENTITY_TICK,
        READ_NBT,
        WRITE_NBT,
        HURT,
        INTERACT,
        CAN_ATTACK_WITH_ITEM,
        CAN_COLLIDE,
        CAN_PUSH,
        COLLIDE_PLAYER,
        APPLY_COLLISION,
        SET_DEAD,
        MOUNTED_Y_OFFSET,
        Y_OFFSET,
        COLLISION_BOX,
        BOUNDING_BOX,
        CAN_TRIGGER_WALKING,
        RENDER_DISTANCE,
        GET_PARTS,
        WRITE_SPAWN_DATA,
        READ_SPAWN_DATA,
        SHOULD_RIDER_SIT,
        COLLISION_BORDER_SIZE
    }

    public record Callback(CallbackKind kind, String owner, String method, String descriptor) { }
    public record SourceMethod(String owner, String method, String descriptor, int access,
                               CallbackKind callbackKind, boolean trivialNoOp) { }
    public record EntitySurface(String registryName, String sourceClass, String externalBaseClass,
                                List<String> sourceLineage, List<Callback> callbacks,
                                List<SourceMethod> sourceMethods) {
        public EntitySurface {
            sourceLineage = List.copyOf(sourceLineage);
            callbacks = List.copyOf(callbacks);
            sourceMethods = List.copyOf(sourceMethods);
        }
    }
    public record Analysis(List<EntitySurface> entities, List<String> diagnostics) {
        public Analysis {
            entities = List.copyOf(entities);
            diagnostics = List.copyOf(diagnostics);
        }
        public int callbackCount() { return entities.stream().mapToInt(value -> value.callbacks().size()).sum(); }
        public int sourceMethodCount() { return entities.stream().mapToInt(value -> value.sourceMethods().size()).sum(); }
    }

    private record Spec(CallbackKind kind, Set<String> names, String descriptor) {
        boolean matches(MethodNode method) {
            return (method.access & Opcodes.ACC_STATIC) == 0
                    && names.contains(method.name)
                    && descriptor.equals(method.desc);
        }
    }

    private static final List<Spec> SPECS = List.of(
            spec(CallbackKind.ENTITY_INIT, "()V", "entityInit", "func_70088_a"),
            spec(CallbackKind.TICK, "()V", "onUpdate", "func_70071_h_"),
            spec(CallbackKind.ENTITY_TICK, "()V", "onEntityUpdate", "func_70030_z"),
            spec(CallbackKind.READ_NBT, "(Lnet/minecraft/nbt/NBTTagCompound;)V", "readEntityFromNBT", "func_70037_a"),
            spec(CallbackKind.WRITE_NBT, "(Lnet/minecraft/nbt/NBTTagCompound;)V", "writeEntityToNBT", "func_70014_b"),
            spec(CallbackKind.HURT, "(Lnet/minecraft/util/DamageSource;F)Z", "attackEntityFrom", "func_70097_a"),
            spec(CallbackKind.INTERACT, "(Lnet/minecraft/entity/player/EntityPlayer;)Z", "interact", "interactFirst", "func_70085_c"),
            spec(CallbackKind.CAN_ATTACK_WITH_ITEM, "()Z", "canAttackWithItem", "func_70075_an"),
            spec(CallbackKind.CAN_COLLIDE, "()Z", "canBeCollidedWith", "func_70067_L"),
            spec(CallbackKind.CAN_PUSH, "()Z", "canBePushed", "func_70104_M"),
            spec(CallbackKind.COLLIDE_PLAYER, "(Lnet/minecraft/entity/player/EntityPlayer;)V", "onCollideWithPlayer", "func_70100_b_"),
            spec(CallbackKind.APPLY_COLLISION, "(Lnet/minecraft/entity/Entity;)V", "applyEntityCollision", "func_70108_f"),
            spec(CallbackKind.SET_DEAD, "()V", "setDead", "func_70106_y"),
            spec(CallbackKind.MOUNTED_Y_OFFSET, "()D", "getMountedYOffset", "func_70042_X"),
            spec(CallbackKind.Y_OFFSET, "()D", "getYOffset", "func_70033_W"),
            spec(CallbackKind.COLLISION_BOX,
                    "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/AxisAlignedBB;", "getCollisionBox", "func_70114_g"),
            spec(CallbackKind.BOUNDING_BOX,
                    "()Lnet/minecraft/util/AxisAlignedBB;", "getBoundingBox", "getCollisionBoundingBox", "func_70046_E"),
            spec(CallbackKind.CAN_TRIGGER_WALKING, "()Z", "canTriggerWalking", "func_70041_e_"),
            spec(CallbackKind.RENDER_DISTANCE, "(D)Z", "isInRangeToRenderDist", "func_70112_a"),
            spec(CallbackKind.GET_PARTS, "()[Lnet/minecraft/entity/Entity;", "getParts", "func_70021_al"),
            spec(CallbackKind.WRITE_SPAWN_DATA, "(Lio/netty/buffer/ByteBuf;)V", "writeSpawnData"),
            spec(CallbackKind.READ_SPAWN_DATA, "(Lio/netty/buffer/ByteBuf;)V", "readSpawnData"),
            spec(CallbackKind.SHOULD_RIDER_SIT, "()Z", "shouldRiderSit"),
            spec(CallbackKind.COLLISION_BORDER_SIZE, "()F", "getCollisionBorderSize", "func_70111_Y")
    );

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = loadClasses(jarPath);
        LegacyEntityDataWatcherAnalyzer.Analysis definitions = new LegacyEntityDataWatcherAnalyzer().analyze(jarPath);
        List<String> diagnostics = new ArrayList<>(definitions.diagnostics());
        List<EntitySurface> output = new ArrayList<>();

        for (LegacyEntityDataWatcherAnalyzer.Rule definition : definitions.rules()) {
            EnumMap<CallbackKind,Callback> effective = new EnumMap<>(CallbackKind.class);
            List<SourceMethod> sourceMethods = new ArrayList<>();
            List<String> lineage = new ArrayList<>();
            String owner = definition.sourceClass();
            Set<String> visited = new LinkedHashSet<>();
            while (owner != null && visited.add(owner)) {
                ClassNode node = classes.get(owner);
                if (node == null) break;
                lineage.add(node.name);
                for (MethodNode method : node.methods) {
                    if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
                    if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
                    CallbackKind kind = callbackKind(method);
                    sourceMethods.add(new SourceMethod(node.name, method.name, method.desc, method.access, kind, trivialNoOp(method)));
                    if (kind != null && !effective.containsKey(kind))
                        effective.put(kind, new Callback(kind, node.name, method.name, method.desc));
                }
                owner = node.superName;
            }

            List<Callback> callbacks = new ArrayList<>();
            for (CallbackKind kind : CallbackKind.values()) {
                Callback callback = effective.get(kind);
                if (callback != null) callbacks.add(callback);
            }
            output.add(new EntitySurface(definition.registryName(), definition.sourceClass(), owner,
                    lineage, callbacks, sourceMethods));
        }

        return new Analysis(output, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean trivialNoOp(MethodNode method) {
        int executable = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode < 0) continue;
            executable++;
            if (opcode != Opcodes.RETURN) return false;
        }
        return executable == 1;
    }

    private static CallbackKind callbackKind(MethodNode method) {
        for (Spec spec : SPECS) if (spec.matches(method)) return spec.kind();
        return null;
    }

    private static Spec spec(CallbackKind kind, String descriptor, String... names) {
        return new Spec(kind, Set.of(names), descriptor);
    }

    private static Map<String,ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // The entity definition analyzer owns malformed-class diagnostics. An unreadable
                    // class is never invented as behavior evidence here.
                }
            }
        }
        return classes;
    }
}
