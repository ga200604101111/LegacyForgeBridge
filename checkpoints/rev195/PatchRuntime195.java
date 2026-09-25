import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pinned, narrowly scoped local rebuild. No Minecraft/API test classes are delivered. */
public final class PatchRuntime195 implements Opcodes {
    static final String BASE = "5cc1cdd4ef617150e87af5e607c0b56241f067853ef63a527efcaf2302e42376";
    static final String ROOT = "dev/yinghuang/legacyforgebridge/";
    static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n;
    }
    static MethodNode typed(ClassNode n, String name) {
        var found = n.methods.stream().filter(m -> m.name.equals(name) && (m.access & ACC_BRIDGE) == 0).toList();
        if (found.size() != 1) throw new IllegalStateException("Ambiguous method: " + name);
        return found.getFirst();
    }
    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]), donorRoot = Path.of(args[1]), output = Path.of(args[2]);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(base)));
        if (!BASE.equals(hash)) throw new IllegalStateException("Input is not the exact delivered rev194: " + hash);
        String name = ROOT + "render/ConvertedLegacyGridPotRenderer.class";
        try (var jar = new JarFile(base.toFile())) {
            ClassNode host = read(jar.getInputStream(jar.getJarEntry(name)).readAllBytes());
            ClassNode donor = read(Files.readAllBytes(donorRoot.resolve(name)));
            MethodNode old = typed(host, "extractRenderState"), fresh = typed(donor, "extractRenderState");
            if (!old.desc.equals(fresh.desc) || old.access != fresh.access) throw new IllegalStateException("Public ABI changed");
            int wrong = 0, correct = 0, superCalls = 0;
            for (var i : old.instructions) if (i instanceof MethodInsnNode m && m.name.equals("method_28498")) {
                if (!m.owner.equals("net/minecraft/class_2680") || !m.desc.equals("(Lnet/minecraft/class_2746;)Z"))
                    throw new IllegalStateException("Unexpected input property call");
                wrong++;
            }
            for (var i : fresh.instructions) if (i instanceof MethodInsnNode m) {
                if (m.name.equals("method_28498")) {
                    if (!m.owner.equals("net/minecraft/class_2680") || !m.desc.equals("(Lnet/minecraft/class_2769;)Z"))
                        throw new IllegalStateException("Incorrect compiled Property ABI: " + m.desc);
                    correct++;
                }
                if (m.owner.equals("net/minecraft/class_827") && m.name.equals("extractRenderState")) {
                    m.name = "method_74331"; superCalls++;
                }
            }
            if (wrong != 1 || correct != 1 || superCalls != 1) throw new IllegalStateException("Unexpected method shape");
            old.instructions = fresh.instructions; old.tryCatchBlocks = fresh.tryCatchBlocks;
            old.localVariables = fresh.localVariables;
            old.visibleLocalVariableAnnotations = fresh.visibleLocalVariableAnnotations;
            old.invisibleLocalVariableAnnotations = fresh.invisibleLocalVariableAnnotations;
            old.maxStack = fresh.maxStack; old.maxLocals = fresh.maxLocals;
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); host.accept(writer);
            Path file = output.resolve(name); Files.createDirectories(file.getParent()); Files.write(file, writer.toByteArray());
        }
        String clockName = ROOT + "render/LegacySuspendedModelRenderer.class";
        try (var jar = new JarFile(base.toFile())) {
            ClassNode host = read(jar.getInputStream(jar.getJarEntry(clockName)).readAllBytes());
            ClassNode donor = read(Files.readAllBytes(donorRoot.resolve(clockName)));
            MethodNode old = typed(host, "extract"), fresh = typed(donor, "extract");
            if (!old.desc.equals(fresh.desc) || old.access != fresh.access) throw new IllegalStateException("Clock public ABI changed");
            int previous = 0, current = 0;
            for (var i : old.instructions) if (i instanceof MethodInsnNode m && m.name.equals("method_8510")) {
                if (!m.owner.equals("net/minecraft/class_638") || !m.desc.equals("()J")) throw new IllegalStateException("Unexpected old clock call");
                previous++;
            }
            for (var i : fresh.instructions) if (i instanceof MethodInsnNode m && m.name.equals("method_75260")) {
                if (!m.owner.equals("net/minecraft/class_638") || !m.desc.equals("()J")) throw new IllegalStateException("Unexpected rebuilt clock call");
                current++;
            }
            if (previous != 1 || current != 1) throw new IllegalStateException("Unexpected clock call shape");
            old.instructions = fresh.instructions; old.tryCatchBlocks = fresh.tryCatchBlocks;
            old.localVariables = fresh.localVariables;
            old.visibleLocalVariableAnnotations = fresh.visibleLocalVariableAnnotations;
            old.invisibleLocalVariableAnnotations = fresh.invisibleLocalVariableAnnotations;
            old.maxStack = fresh.maxStack; old.maxLocals = fresh.maxLocals;
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); host.accept(writer);
            Path file = output.resolve(clockName); Files.createDirectories(file.getParent()); Files.write(file, writer.toByteArray());
        }
        Path info = output.resolve(ROOT + "BuildInfo.class"); Files.createDirectories(info.getParent());
        Files.copy(donorRoot.resolve(ROOT + "BuildInfo.class"), info, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("PASS: rebuilt typed extraction body has Property class_2769; clock call rebuilt to method_75260; original erased bridges and unrelated bodies retained.");
    }
}
