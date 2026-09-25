package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.EquipmentFixture;
import dev.yinghuang.legacyforgebridge.convert.Hashing;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEquipmentRenderPassTest {
    @TempDir Path temp;

    @Test void sourceProgramReplacesAutofitAndIsCalledByTheGeneratedClient() throws Exception {
        for (String namespace : List.of("alchemy", "astronomy")) {
            ConversionContext context = prepare(namespace, true);
            new LegacyEquipmentRenderPass().apply(context);
            JsonObject item = item(context);
            assertFalse(item.has("equipmentRender"), "Do not register both fallback and source renderers");
            assertTrue(item.has("sourceEquipmentProgram"));
            assertTrue(Files.isRegularFile(context.stagingDir().resolve(LegacyEquipmentRenderPass.MARKER)));
            String client = GeneratedModEntrypointPass.generatedClientClass(context.metadata()).replace('.', '/');
            assertTrue(Files.isRegularFile(context.stagingDir().resolve(client + "Equipment.class")));
            assertTrue(Files.isRegularFile(context.stagingDir().resolve(item.get("sourceEquipmentProgram").getAsString().replace('.', '/') + ".class")));
            new GeneratedSemanticCodePass().apply(context);
            List<String> calls = new ArrayList<>();
            new ClassReader(Files.readAllBytes(context.stagingDir().resolve(client + ".class")))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                    calls.add(owner + "." + name);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(calls.contains(client + "Equipment.initialize"));
            assertTrue(calls.stream().noneMatch(call -> call.endsWith("GeneratedEquipmentSupport.register")));
        }
    }

    @Test void mixedCaseSourceEquipmentResourcesAreCanonicalizedBeforeCodegen() throws Exception {
        ConversionContext context=prepare("alchemy",true);
        Path assets=context.stagingDir().resolve("assets/alchemy");
        Files.move(assets.resolve("models"),assets.resolve("Models"));
        Files.move(assets.resolve("textures"),assets.resolve("Textures"));

        new LegacyEquipmentRenderPass().apply(context);

        assertTrue(item(context).has("sourceEquipmentProgram"));
        assertTrue(Files.isRegularFile(assets.resolve("models/relic.obj")));
        assertTrue(Files.isRegularFile(assets.resolve("textures/relic.png")));
        try(var children=Files.list(assets)){
            var names=children.map(p->p.getFileName().toString()).toList();
            assertTrue(names.contains("models"));
            assertTrue(names.contains("textures"));
            assertFalse(names.contains("Models"));
            assertFalse(names.contains("Textures"));
        }
    }

    @Test void missingSourceTextureKeepsExistingFallbackAndDiagnosesTheFailure() throws Exception {
        ConversionContext context = prepare("alchemy", false);
        new LegacyEquipmentRenderPass().apply(context);
        assertTrue(item(context).has("equipmentRender"));
        assertFalse(item(context).has("sourceEquipmentProgram"));
        assertFalse(Files.exists(context.stagingDir().resolve(LegacyEquipmentRenderPass.MARKER)));
        assertTrue(context.diagnostics().snapshot().stream().anyMatch(d -> d.ruleId().equals("LFB-EQUIPMENT-0004")));
    }

    private ConversionContext prepare(String ns, boolean texturePresent) throws Exception {
        Path work = Files.createTempDirectory(temp, ns);
        Path source = EquipmentFixture.create(work.resolve("fixture"), ns, false);
        Path staging = work.resolve("staging");
        Path manifest = staging.resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{\"items\":[{\"id\":\"" + ns + ":relic\",\"kind\":\"wing\",\"equipmentRender\":{\"anchor\":\"body\",\"autoCenter\":true,\"fit\":1.75}}]}");
        Path model = staging.resolve("assets/" + ns + "/models/relic.obj");
        Files.createDirectories(model.getParent()); Files.writeString(model, "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
        if (texturePresent) {
            Path texture = staging.resolve("assets/" + ns + "/textures/relic.png");
            Files.createDirectories(texture.getParent());
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0x7FFFFFFF); ImageIO.write(image, "png", texture.toFile());
        }
        return new ConversionContext(source, staging, work.resolve(ns + "-lfb.jar"), Hashing.sha256(source),
                Files.size(source), LegacyModMetadata.read(source), new LegacyJarAnalyzer().analyze(source),
                new DiagnosticCollector(), "independent-equipment-fixture");
    }

    private JsonObject item(ConversionContext context) throws Exception {
        return JsonParser.parseString(Files.readString(context.stagingDir().resolve("legacyforgebridge/converted-content.json")))
                .getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
    }
}
