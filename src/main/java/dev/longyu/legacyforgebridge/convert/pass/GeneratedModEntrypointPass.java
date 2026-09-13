package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Emits a tiny modern Fabric entrypoint into every converted candidate.
 *
 * <p>The converted JAR is therefore the owner of its own lifecycle. It delegates generic
 * compatibility services to LegacyForgeBridge, but item/content and client presentation startup is
 * initiated by the converted mod's own generated class rather than by LFB scanning every resource
 * container globally.</p>
 */
public final class GeneratedModEntrypointPass implements ConversionPass {
    public static final String MARKER_PATH = "legacyforgebridge/generated-entrypoint.marker";

    @Override
    public String id() {
        return "generated-modern-fabric-entrypoint";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        String binaryName = entrypointClass(context.metadata());
        String internalName = binaryName.replace('.', '/');
        Path output = context.stagingDir().resolve(internalName + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, generate(internalName, context.metadata().fabricId()));

        Path marker = context.stagingDir().resolve(MARKER_PATH);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, binaryName + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info(
                "LFB-CONVERT-ENTRYPOINT-0001",
                SupportLevel.ADAPTED,
                "Generated modern Fabric main/client entrypoint " + binaryName
                        + " so the converted mod owns its lifecycle and delegates only compatibility services to LegacyForgeBridge."
        );
    }

    public static String entrypointClass(LegacyModMetadata metadata) {
        String safe = metadata.fabricId().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_");
        if (safe.isBlank()) {
            safe = "legacy_mod";
        }
        return "dev.longyu.legacyforgebridge.generated." + safe + ".ConvertedModEntrypoint";
    }

    private static byte[] generate(String internalName, String modId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V21,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                internalName,
                null,
                "java/lang/Object",
                new String[]{"net/fabricmc/api/ModInitializer", "net/fabricmc/api/ClientModInitializer"}
        );

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor main = writer.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize", "()V", null, null);
        main.visitCode();
        main.visitLdcInsn(modId);
        main.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/longyu/legacyforgebridge/convert/runtime/ConvertedContentRuntime",
                "initializeMod",
                "(Ljava/lang/String;)V",
                false
        );
        main.visitInsn(Opcodes.RETURN);
        main.visitMaxs(1, 1);
        main.visitEnd();

        MethodVisitor client = writer.visitMethod(Opcodes.ACC_PUBLIC, "onInitializeClient", "()V", null, null);
        client.visitCode();
        client.visitLdcInsn(modId);
        client.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/longyu/legacyforgebridge/render/ConvertedEquipmentRenderRuntime",
                "initializeMod",
                "(Ljava/lang/String;)V",
                false
        );
        client.visitInsn(Opcodes.RETURN);
        client.visitMaxs(1, 1);
        client.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
