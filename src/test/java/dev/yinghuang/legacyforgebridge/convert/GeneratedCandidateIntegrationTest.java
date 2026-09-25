package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.pass.GeneratedModEntrypointPass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCandidateIntegrationTest {
    @TempDir Path tempDir;

    @Test
    void convertedCandidateOwnsLifecycleContentAndClientBytecode() throws Exception {
        Path source=tempDir.resolve("StandaloneLegacy.jar");
        String metadata="[{\"modid\":\"standalonelegacy\",\"name\":\"Standalone Legacy\",\"version\":\"1.0\",\"mcversion\":\"1.7.10\",\"dependencies\":[]}]";
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){add(out,"mcmod.info",metadata);add(out,"assets/standalonelegacy/textures/item/example.png","x");}
        ConversionResult result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        assertEquals(ConversionStatus.CONVERTED,result.status());
        Path candidate=result.candidateJar().orElseThrow();
        assertEquals("StandaloneLegacy-lfb.jar",candidate.getFileName().toString());
        assertTrue(new ManagedCandidateInstaller(tempDir.resolve("mods"),tempDir.resolve("cache"),false).isLoaderSafeCandidate(candidate));

        try(JarFile jar=new JarFile(candidate.toFile())){
            String base="dev/yinghuang/legacyforgebridge/generated/standalonelegacy/";
            assertNotNull(jar.getJarEntry(base+"ConvertedModEntrypoint.class"));
            assertNotNull(jar.getJarEntry(base+"GeneratedContent.class"));
            assertNotNull(jar.getJarEntry(base+"GeneratedClient.class"));
            assertNotNull(jar.getJarEntry(GeneratedModEntrypointPass.MARKER_PATH));

            Set<String> owners=new LinkedHashSet<>();
            new ClassReader(jar.getInputStream(jar.getJarEntry(base+"ConvertedModEntrypoint.class"))).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String methodName,String desc,boolean itf){owners.add(owner);}};}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(owners.contains(base+"GeneratedContent"));
            assertTrue(owners.contains(base+"GeneratedClient"));
            assertFalse(owners.contains("dev/yinghuang/legacyforgebridge/convert/runtime/ConvertedContentRuntime"));
            assertFalse(owners.contains("dev/yinghuang/legacyforgebridge/render/ConvertedEquipmentRenderRuntime"));

            JsonObject fabric;
            try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(jar.getJarEntry("fabric.mod.json")),StandardCharsets.UTF_8)){fabric=JsonParser.parseReader(reader).getAsJsonObject();}
            String binary="dev.yinghuang.legacyforgebridge.generated.standalonelegacy.ConvertedModEntrypoint";
            assertEquals(binary,fabric.getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
            assertEquals(binary,fabric.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString());
        }
    }

    private static void add(JarOutputStream out,String name,String value)throws Exception{out.putNextEntry(new JarEntry(name));out.write(value.getBytes(StandardCharsets.UTF_8));out.closeEntry();}
}
