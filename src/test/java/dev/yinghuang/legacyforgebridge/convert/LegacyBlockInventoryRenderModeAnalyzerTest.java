package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockInventoryRenderModeAnalyzerTest {
    private static final String OWNER = "foreign/render/RenderIds";
    private static final String HANDLER = "cpw/mods/fml/client/registry/ISimpleBlockRenderingHandler";

    @TempDir Path temp;

    @Test
    void customForgeHandlersAndVanillaRenderTypesBoundFlatInventoryScope() throws Exception {
        Path source = sourceJar();
        var analysis = new LegacyBlockInventoryRenderModeAnalyzer().analyze(source);
        Map<String,LegacyBlockInventoryRenderModeAnalyzer.Mode> modes = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockInventoryRenderModeAnalyzer.Rule::registryName,
                        LegacyBlockInventoryRenderModeAnalyzer.Rule::mode, (a,b)->a));

        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.FLAT_2D, modes.get("flat"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.THREE_D, modes.get("three"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.FLAT_2D, modes.get("pane"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.THREE_D, modes.get("fence"));
        assertFalse(modes.containsKey("unknown"), "unknown custom/extended render IDs must fail closed");

        Path staging = temp.resolve("staging");
        for (String name : List.of("flat","three","pane","fence","unknown")) {
            texture(staging, "blocks/" + name + ".png");
            texture(staging, "items/" + name + ".png");
        }
        new GenericContentPass().apply(context(source, staging));

        assertEquals("minecraft:item/generated", parent(staging, "flat"));
        assertEquals("minecraft:item/generated", parent(staging, "pane"));
        assertEquals("fixture:block/three", parent(staging, "three"));
        assertEquals("fixture:block/fence", parent(staging, "fence"));
        assertEquals("fixture:block/unknown", parent(staging, "unknown"));
    }

    @Test
    void platformTableMatchesKnown1710FlatVs3dBoundary() {
        assertTrue(LegacyBlockRenderType1710.renderItemIn3d(0).orElseThrow());
        assertTrue(LegacyBlockRenderType1710.renderItemIn3d(11).orElseThrow());
        assertFalse(LegacyBlockRenderType1710.renderItemIn3d(18).orElseThrow());
        assertFalse(LegacyBlockRenderType1710.renderItemIn3d(40).orElseThrow());
        assertTrue(LegacyBlockRenderType1710.renderItemIn3d(99).isEmpty());
    }

    private Path sourceJar() throws Exception {
        Path jar = temp.resolve("inventory-render.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/render/Handler2D.class", handler("foreign/render/Handler2D", "java/lang/Object", false, true));
            put(out, "foreign/render/Handler3D.class", handler("foreign/render/Handler3D", "foreign/render/Handler2D", true, false));
            put(out, OWNER + ".class", renderIds());
            put(out, "foreign/block/Flat.class", block("foreign/block/Flat", "flatUID", null));
            put(out, "foreign/block/Three.class", block("foreign/block/Three", "threeUID", null));
            put(out, "foreign/block/Pane.class", block("foreign/block/Pane", null, 18));
            put(out, "foreign/block/Fence.class", block("foreign/block/Fence", null, 11));
            put(out, "foreign/block/Unknown.class", block("foreign/block/Unknown", null, 99));
            put(out, "foreign/Bootstrap.class", bootstrap());
        }
        return jar;
    }

    private static byte[] handler(String name, String parent, boolean threeD, boolean directInterface) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, parent,
                directInterface ? new String[]{HANDLER} : null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0,0); ctor.visitEnd();
        MethodVisitor mode = w.visitMethod(Opcodes.ACC_PUBLIC, "shouldRender3DInInventory", "(I)Z", null, null);
        mode.visitCode(); mode.visitInsn(threeD ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        mode.visitInsn(Opcodes.IRETURN); mode.visitMaxs(0,0); mode.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] renderIds() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, OWNER, null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL,"flatUID","I",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL,"threeUID","I",null,null).visitEnd();
        helper(w, "flat", "foreign/render/Handler2D");
        helper(w, "three", "foreign/render/Handler3D");
        MethodVisitor cl = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        cl.visitCode();
        cl.visitMethodInsn(Opcodes.INVOKESTATIC,OWNER,"flat","()I",false);
        cl.visitFieldInsn(Opcodes.PUTSTATIC,OWNER,"flatUID","I");
        cl.visitMethodInsn(Opcodes.INVOKESTATIC,OWNER,"three","()I",false);
        cl.visitFieldInsn(Opcodes.PUTSTATIC,OWNER,"threeUID","I");
        cl.visitInsn(Opcodes.RETURN); cl.visitMaxs(0,0); cl.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static void helper(ClassWriter w, String name, String handler) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,name,"()I",null,null);
        m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry",
                "getNextAvailableRenderId","()I",false);
        m.visitVarInsn(Opcodes.ISTORE,0);
        m.visitVarInsn(Opcodes.ILOAD,0);
        m.visitTypeInsn(Opcodes.NEW,handler); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,handler,"<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/RenderingRegistry",
                "registerBlockHandler","(ILcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V",false);
        m.visitVarInsn(Opcodes.ILOAD,0); m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0,1); m.visitEnd();
    }

    private static byte[] block(String name, String renderField, Integer renderType) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0,0); ctor.visitEnd();
        MethodVisitor render = w.visitMethod(Opcodes.ACC_PUBLIC,"func_149645_b","()I",null,null);
        render.visitCode();
        if (renderField != null) render.visitFieldInsn(Opcodes.GETSTATIC,OWNER,renderField,"I");
        else render.visitIntInsn(Opcodes.BIPUSH,renderType);
        render.visitInsn(Opcodes.IRETURN); render.visitMaxs(0,0); render.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor cl = w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        cl.visitCode();
        registration(cl,"foreign/block/Flat","flat");
        registration(cl,"foreign/block/Three","three");
        registration(cl,"foreign/block/Pane","pane");
        registration(cl,"foreign/block/Fence","fence");
        registration(cl,"foreign/block/Unknown","unknown");
        cl.visitInsn(Opcodes.RETURN); cl.visitMaxs(0,0); cl.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static void registration(MethodVisitor m, String type, String name) {
        m.visitTypeInsn(Opcodes.NEW,type); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);
        m.visitLdcInsn(name);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        var metadata = new LegacyModMetadata(source.getFileName().toString(), "test",
                List.of(new LegacyModMetadata.ModEntry("fixture","Fixture","1.0","1.7.10",List.of())));
        var analysis = new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,
                0,0,0,0, Set.of(),Set.of(),Set.of());
        return new ConversionContext(source,staging,temp.resolve("candidate.jar"),Hashing.sha256(source),
                Files.size(source),metadata,analysis,new DiagnosticCollector(),"test");
    }

    private static void texture(Path staging, String relative) throws Exception {
        Path path = staging.resolve("assets/fixture/textures/" + relative);
        Files.createDirectories(path.getParent()); Files.write(path,new byte[]{0});
    }

    private static String parent(Path staging, String name) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve("assets/fixture/models/item/" + name + ".json"))).getAsJsonObject();
        return root.get("parent").getAsString();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
