package dev.longyu.legacyforgebridge.convert;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Independent source corpus compiled against minimal stubs; stub classes are NOT in the JAR. */
public final class EquipmentFixture {
    private EquipmentFixture() { }
    public static Path create(Path work, String namespace, boolean dynamic) throws Exception {
        Path sources = work.resolve("source"), classes = work.resolve("classes");
        Files.createDirectories(classes);
        Map<String,String> stubs = Map.of(
            "net/minecraft/entity/Entity", "public boolean isSneaking(){return false;} public boolean unknown(){return false;}",
            "net/minecraft/entity/EntityLivingBase", "",
            "net/minecraft/item/ItemStack", "",
            "net/minecraft/item/ItemArmor", "public ItemArmor(Object material,int render,int slot){} public ItemArmor setUnlocalizedName(String name){return this;}",
            "net/minecraft/client/model/ModelBiped", "public void render(net.minecraft.entity.Entity e,float a,float b,float c,float d,float f,float g){}",
            "net/minecraft/util/ResourceLocation", "public ResourceLocation(String name){}",
            "net/minecraft/util/MathHelper", "public static float cos(float n){return (float)Math.cos(n);}",
            "net/minecraft/client/renderer/texture/TextureManager", "public void bindTexture(net.minecraft.util.ResourceLocation resource){}",
            "net/minecraft/client/Minecraft", "public net.minecraft.client.renderer.texture.TextureManager renderEngine; public static Minecraft getMinecraft(){return null;}",
            "org/lwjgl/opengl/GL11", "public static void glPushMatrix(){} public static void glPopMatrix(){} public static void glDisable(int flag){} public static void glScalef(float a,float b,float c){} public static void glTranslatef(float a,float b,float c){} public static void glRotatef(float a,float b,float c,float d){}"
        );
        List<String> arguments = new ArrayList<>(List.of("--release","8","-d",classes.toString()));
        for (var entry : stubs.entrySet()) {
            String name = entry.getKey(); int split = name.lastIndexOf('/');
            write(sources, name, "package " + name.substring(0,split).replace('/','.') + "; public class " + name.substring(split+1) + " {" + entry.getValue() + "}", arguments);
        }
        write(sources, "net/minecraftforge/client/model/IModelCustom", "package net.minecraftforge.client.model; public interface IModelCustom {void renderAll();}", arguments);
        write(sources, "net/minecraftforge/client/model/AdvancedModelLoader", "package net.minecraftforge.client.model; public class AdvancedModelLoader {public static IModelCustom loadModel(net.minecraft.util.ResourceLocation id){return null;}}", arguments);
        String code = """
                package NS;
                import net.minecraft.item.*;
                import net.minecraft.entity.*;
                import net.minecraft.client.model.*;
                import net.minecraft.util.*;
                import net.minecraft.client.Minecraft;
                import net.minecraftforge.client.model.*;
                import org.lwjgl.opengl.GL11;
                public class Content {
                    public static final Armor HELD = new Armor("relic");
                    public static class Armor extends ItemArmor {
                        private String name;
                        public Armor(String name){super(null,0,1);this.name=name;setUnlocalizedName(name);}
                        public ModelBiped getArmorModel(EntityLivingBase e,ItemStack stack,int slot){return new Model(name);}
                    }
                    public static class Model extends ModelBiped {
                        private static final IModelCustom MESH=AdvancedModelLoader.loadModel(new ResourceLocation("NS:models/relic.obj"));
                        private ResourceLocation texture;
                        private float animate;
                        public Model(String name){texture=new ResourceLocation(new StringBuilder().append("NS:textures/").append(name).append(".png").toString());}
                        public void render(Entity entity,float limb,float amplitude,float age,float yaw,float pitch,float scale){
                            if(DYNAMIC) animate=MathHelper.cos(age*0.2F)*7F+30F;
                            else animate=MathHelper.cos(age*0.2F)*7F+5F;
                            Minecraft.getMinecraft().renderEngine.bindTexture(texture);
                            GL11.glDisable(2896); GL11.glDisable(2884);
                            GL11.glPushMatrix();
                            GL11.glScalef(2,2,2); GL11.glTranslatef(0,1.25F,0);
                            GL11.glRotatef(animate,0,1,0); MESH.renderAll();
                            GL11.glPopMatrix();
                            GL11.glPushMatrix();
                            GL11.glTranslatef(0,0.5F,0); GL11.glRotatef(-animate,0,1,0); MESH.renderAll();
                            GL11.glPopMatrix();
                        }
                    }
                }
                """.replace("NS",namespace).replace("DYNAMIC",dynamic?"entity.unknown()":"entity.isSneaking()");
        write(sources, namespace + "/Content", code, arguments);
        int exit = ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new));
        if (exit != 0) throw new IllegalStateException("Fixture javac failed " + exit);
        Path jar = work.resolve(namespace + ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar)); var walk = Files.walk(classes.resolve(namespace))) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\','/')));
                output.write(Files.readAllBytes(path)); output.closeEntry();
            }
        }
        return jar;
    }
    private static void write(Path root, String name, String content, List<String> arguments) throws Exception {
        Path path = root.resolve(name + ".java"); Files.createDirectories(path.getParent());
        Files.writeString(path,content); arguments.add(path.toString());
    }
}
