package dev.longyu.legacyforgebridge.convert;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Compiles unrelated legacy mod fixtures, excluding the stub game API from the source JAR. */
public final class BehaviorFixture {
    private BehaviorFixture() { }
    public static Path create(Path root,String namespace,boolean unsupported) throws Exception {
        Path source=root.resolve("sources"),classes=root.resolve("classes");Files.createDirectories(classes);
        Map<String,String> files=new LinkedHashMap<>();
        files.put("net.minecraft.item.Item", "public class Item {public Item func_77655_b(String n){return this;}}");
        files.put("net.minecraft.item.ItemSword", """
            public class ItemSword extends Item {
              public ItemSword(Object material){}
              public ItemStack onItemRightClick(ItemStack s,net.minecraft.world.World w,net.minecraft.entity.player.EntityPlayer p){p.func_71008_a(s,72000);return s;}
              public EnumAction getItemUseAction(ItemStack s){return EnumAction.block;}
              public int getMaxItemUseDuration(ItemStack s){return 72000;}
            }
            """);
        files.put("net.minecraft.item.ItemArmor","public class ItemArmor extends Item {public ItemArmor(Object material,int render,int slot){}}");
        files.put("net.minecraft.item.EnumAction","public enum EnumAction {none,eat,drink,block,bow}");
        files.put("net.minecraft.item.ItemStack","public class ItemStack {public net.minecraft.nbt.NBTTagCompound func_77978_p(){return null;}public Item func_77973_b(){return null;}}");
        files.put("net.minecraft.nbt.NBTTagCompound","public class NBTTagCompound {public int func_74762_e(String k){return 0;}public String func_74779_i(String k){return null;}public void func_74768_a(String k,int n){}}");
        files.put("net.minecraft.world.World","public class World {public boolean field_72995_K;}");
        files.put("net.minecraft.entity.Entity","public class Entity {public net.minecraft.world.World field_70170_p;public double field_70181_x;}");
        files.put("net.minecraft.entity.EntityLivingBase","public class EntityLivingBase extends Entity {public net.minecraft.item.ItemStack func_71124_b(int slot){return null;}}");
        files.put("net.minecraft.entity.player.EntityPlayer","public class EntityPlayer extends net.minecraft.entity.EntityLivingBase {public void func_71008_a(net.minecraft.item.ItemStack s,int t){}}");
        files.put("net.minecraftforge.event.entity.living.LivingEvent","public class LivingEvent {public net.minecraft.entity.EntityLivingBase entityLiving;public static class LivingJumpEvent extends LivingEvent {}}");
        files.put("net.minecraftforge.event.entity.living.LivingFallEvent","public class LivingFallEvent {public net.minecraft.entity.EntityLivingBase entityLiving;public void setCanceled(boolean b){}}");
        files.put("cpw.mods.fml.common.eventhandler.SubscribeEvent","@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface SubscribeEvent {}");
        files.put("cpw.mods.fml.common.eventhandler.EventBus","public class EventBus {public void register(Object o){}}");
        files.put("net.minecraftforge.common.MinecraftForge","public class MinecraftForge {public static cpw.mods.fml/common/eventhandler/EventBus EVENT_BUS;}".replace('/','.'));
        files.put(namespace+".Mod","public class Mod {public static net.minecraft.item.Item BLADE=new Blade(\"blade\");public static net.minecraft.item.Item CLOAK=new Cloak(\"cloak\");public void init(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());}}");
        files.put(namespace+".Blade", """
            import net.minecraft.item.*;
            import net.minecraft.nbt.NBTTagCompound;
            import net.minecraft.entity.player.EntityPlayer;
            import net.minecraft.world.World;
            public class Blade extends ItemSword {
              public Blade(String n){super(null);func_77655_b(n);}
              public void addInformation(ItemStack s,EntityPlayer p,java.util.List<String> lines,boolean advanced){
                NBTTagCompound tag=s.func_77978_p();lines.add("source tooltip");
                if(tag!=null)lines.add("bonus="+tag.func_74762_e("bonus"));
                EXTRA
              }
              public ItemStack onItemRightClick(ItemStack s,World w,EntityPlayer p){
                if(s.func_77978_p()!=null && s.func_77978_p().func_74779_i("mode").equals("disabled"))return s;
                p.func_71008_a(s,getMaxItemUseDuration(s));return s;
              }
              public EnumAction getItemUseAction(ItemStack s){return s.func_77978_p()!=null && s.func_77978_p().func_74779_i("mode").equals("charge")?EnumAction.bow:EnumAction.block;}
              public int getMaxItemUseDuration(ItemStack s){return 72000;}
              public void onPlayerStoppedUsing(ItemStack s,World w,EntityPlayer p,int remaining){if(s.func_77978_p()!=null)s.func_77978_p().func_74768_a("released",1);}
            }
            """.replace("EXTRA",unsupported?"System.getenv(\"NOT_ALLOWED\");":""));
        files.put(namespace+".Cloak","public class Cloak extends net.minecraft.item.ItemArmor {public Cloak(String n){super(null,0,1);func_77655_b(n);}}");
        files.put(namespace+".Events","""
            import cpw.mods.fml.common.eventhandler.SubscribeEvent;
            import net.minecraftforge.event.entity.living.*;
            public class Events {
              @SubscribeEvent public void jump(LivingEvent.LivingJumpEvent e){
                if(e.entityLiving.func_71124_b(3)!=null && e.entityLiving.func_71124_b(3).func_77973_b() instanceof Cloak)e.entityLiving.field_70181_x+=.2D;
              }
              @SubscribeEvent public void fall(LivingFallEvent e){
                if(e.entityLiving.func_71124_b(3)!=null && e.entityLiving.func_71124_b(3).func_77973_b() instanceof Cloak)e.setCanceled(true);
              }
            }
            """);
        // Legacy Item material descriptors are explicit, matching normal reobfuscated Forge JARs.
        files.put("net.minecraft.item.Item", "public class Item {public enum ToolMaterial {DIAMOND} public Item func_77655_b(String n){return this;}}");
        files.replace("net.minecraft.item.ItemSword",files.get("net.minecraft.item.ItemSword").replace("Object material","Item.ToolMaterial material"));
        files.replace("net.minecraft.item.ItemArmor","public class ItemArmor extends Item {public enum ArmorMaterial {DIAMOND} public ItemArmor(ArmorMaterial material,int render,int slot){}}");
        List<String> args=new ArrayList<>(List.of("--release","8","-encoding","UTF-8","-d",classes.toString()));
        for(var entry:files.entrySet()){
            String name=entry.getKey();Path p=source.resolve(name.replace('.','/')+".java");Files.createDirectories(p.getParent());
            Files.writeString(p,"package "+name.substring(0,name.lastIndexOf('.'))+";\n"+entry.getValue(),StandardCharsets.UTF_8);args.add(p.toString());
        }
        int status=ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new));
        if(status!=0)throw new IllegalStateException("Fixture compilation failed: "+status);
        Path jar=root.resolve(namespace+".jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar));var walk=Files.walk(classes.resolve(namespace))){
            for(Path p:walk.filter(Files::isRegularFile).sorted().toList()){
                out.putNextEntry(new JarEntry(classes.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();
            }
        }
        return jar;
    }
}
