package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.EquipmentFixture;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer;
import dev.yinghuang.legacyforgebridge.render.LegacyEquipmentProgram;
import dev.yinghuang.legacyforgebridge.render.LegacyRenderMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SourceEquipmentCodegenTest {
    @TempDir Path temp;
    @Test void executesGeneratedAgeAndPostureProgramsWithoutMinecraftOrManifestInterpretation() throws Exception {
        Path source=EquipmentFixture.create(temp.resolve("fixture"),"alchemy",false);
        var binding=new LegacyItemRenderAnalyzer().analyzeEquipment(source).bindings().getFirst();
        Path texture=temp.resolve("assets/alchemy/textures/relic.png"); Files.createDirectories(texture.getParent());
        BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB); image.setRGB(0,0,0x7FFFFFFF);
        ImageIO.write(image,"png",texture.toFile());
        String owner="dev/yinghuang/legacyforgebridge/generated/test/EquipmentPose";
        byte[] bytes=LegacyEquipmentRenderPass.compileProgram(owner,binding,temp);
        class Loader extends ClassLoader { Class<?> loadProgram() { return defineClass(owner.replace('/','.'),bytes,0,bytes.length); } }
        LegacyEquipmentProgram program=(LegacyEquipmentProgram)new Loader().loadProgram().getConstructor().newInstance();
        for (boolean crouching : new boolean[]{false,true}) for(float age:new float[]{0,10,100}) {
            Capture sink=new Capture();
            program.render(sink,new float[]{0,0,age,0,0,0.0625F},crouching);
            assertEquals(0,sink.depth); assertEquals(2,sink.draws); assertTrue(sink.translucent);
            float expected=LegacyRenderMath.cos(age*0.2F)*7+(crouching?30:5);
            assertEquals(expected,sink.angles.get(0),0.00001F); assertEquals(-expected,sink.angles.get(1),0.00001F);
            assertEquals(List.of("push","scale","translate","rotate","draw","pop","push","translate","rotate","draw","pop"),sink.events);
        }
    }
    private static final class Capture implements LegacyEquipmentProgram.Sink {
        int depth,draws; boolean translucent; List<Float> angles=new ArrayList<>(); List<String> events=new ArrayList<>();
        public void push(){depth++;events.add("push");} public void pop(){depth--;events.add("pop");}
        public void scale(float x,float y,float z){events.add("scale");}
        public void translate(float x,float y,float z){events.add("translate");}
        public void rotate(float a,float x,float y,float z){angles.add(a);events.add("rotate");}
        public void draw(String m,String t,boolean lighting,boolean cull,boolean alpha){draws++;translucent=alpha;events.add("draw");}
    }
}
