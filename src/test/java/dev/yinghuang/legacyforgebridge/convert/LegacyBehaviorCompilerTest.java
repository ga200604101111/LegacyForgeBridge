package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.behavior.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorCompilerTest {
    @TempDir Path temp;
    @Test void sourceTooltipsUseReleaseAndEquipmentRunAcrossUnrelatedNamespaces() throws Exception {
        for(String mod:List.of("alchemy","astronomy")) {
            Path source=BehaviorFixture.create(temp.resolve(mod),mod,false);
            var compiled=compile(source,mod);
            assertEquals(2,compiled.items().size());assertEquals(2,compiled.events().size());assertTrue(compiled.diagnostics().isEmpty(),compiled.diagnostics().toString());
            load(compiled,mod);
            try {
                var blade=LegacyBehaviorRegistry.item(mod+":blade");var cloak=LegacyBehaviorRegistry.item(mod+":cloak");
                var tag=new LegacyBehaviorApi.Tag(Map.of("bonus",37));var stack=new LegacyBehaviorApi.Stack(blade.item(),tag);
                var player=new LegacyBehaviorApi.Player();var world=new LegacyBehaviorApi.World();player.field_70170_p=world;
                LegacyBehaviorApi.begin(mod,(key,args)->key);
                try {
                    List<String> lines=new ArrayList<>();blade.item().func_77624_a(stack,player,lines,false);
                    assertEquals(List.of("source tooltip","bonus=37"),lines);
                    assertEquals(LegacyBehaviorApi.UseAction.block,blade.item().func_77661_b(stack));
                    blade.item().func_77659_a(stack,world,player);assertSame(stack,player.requestedUse);assertEquals(72000,player.requestedDuration);
                    blade.item().func_77615_a(stack,world,player,71999);assertEquals(1,tag.getInteger("released"));
                    tag.setString("mode","charge");assertEquals(LegacyBehaviorApi.UseAction.bow,blade.item().func_77661_b(stack));
                    tag.setString("mode","disabled");player.requestedUse=null;blade.item().func_77659_a(stack,world,player);assertNull(player.requestedUse);
                    player.field_70181_x=.42D;player.equipment[3]=new LegacyBehaviorApi.Stack(cloak.item(),null);
                    var event=new LegacyBehaviorApi.Event();event.entityLiving=player;
                    for(var e:LegacyBehaviorRegistry.events("jump"))if(e.mod().equals(mod))e.program().run(event);
                    assertEquals(.62D,player.field_70181_x,1E-9);
                    for(var e:LegacyBehaviorRegistry.events("fall"))if(e.mod().equals(mod))e.program().run(event);assertTrue(event.isCanceled());
                    player.equipment[3]=null;event=new LegacyBehaviorApi.Event();event.entityLiving=player;
                    for(var e:LegacyBehaviorRegistry.events("fall"))if(e.mod().equals(mod))e.program().run(event);
                    assertFalse(event.isCanceled());
                } finally {LegacyBehaviorApi.end();}
            } finally {LegacyBehaviorRegistry.removeMod(mod);}
        }
    }
    @Test void unsupportedTooltipDoesNotDropProvenUseAndEquipmentCallbacks()throws Exception {
        var compiled=compile(BehaviorFixture.create(temp.resolve("unsupported"),"alchemy",true),"alchemy");
        assertTrue(compiled.diagnostics().stream().anyMatch(d->d.contains("java/lang/System")));
        var blade=compiled.items().stream().filter(i->i.id().endsWith(":blade")).findFirst().orElseThrow();
        assertFalse(blade.hooks().contains("tooltip"));assertTrue(blade.hooks().contains("use"));assertEquals(2,compiled.events().size());
    }
    @Test void newCombatAndMessageAdaptersCompileAcrossIndependentSourceNamespaces() throws Exception {
        for(String mod:List.of("alchemy","astronomy")) {
            var compiled=compile(BehaviorFixture.createCommands(temp.resolve("commands-"+mod),mod),mod);
            assertTrue(compiled.diagnostics().isEmpty(),compiled.diagnostics().toString());load(compiled,mod);
            try {
                var blade=LegacyBehaviorRegistry.item(mod+":blade");assertTrue(blade.hooks().contains("hit"));
                LegacyBehaviorApi.begin(mod,(key,args)->key);
                try {
                    var world=new LegacyBehaviorApi.World();var player=new LegacyBehaviorApi.Player();player.field_70170_p=world;
                    var victim=new LegacyBehaviorApi.Living();victim.field_70170_p=world;
                    var stack=new LegacyBehaviorApi.Stack(blade.item(),new LegacyBehaviorApi.Tag());
                    blade.item().func_77644_a(stack,victim,player);
                    assertEquals(8.5D,victim.field_70159_w,1E-9);assertEquals(.25D,victim.field_70181_x,1E-9);
                    assertEquals(4,((LegacyBehaviorApi.Durability)world.commands.getFirst()).amount());
                    blade.item().func_77615_a(stack,world,player,71999);
                    assertEquals(1,stack.tag.getInteger("released"));assertEquals("independent message",player.messages.getFirst().text());
                } finally {LegacyBehaviorApi.end();}
            } finally {LegacyBehaviorRegistry.removeMod(mod);}
        }
    }
    @Test void serializedHookOrderDoesNotDependOnJvmImmutableSetRandomization() throws Exception {
        var compiled=compile(BehaviorFixture.create(temp.resolve("order"),"alchemy",false),"alchemy");
        for(var item:compiled.items())assertEquals(new ArrayList<>(new TreeSet<>(item.hooks())),new ArrayList<>(item.hooks()));
    }
    private LegacyBehaviorCompiler.Result compile(Path source,String mod)throws Exception {
        return new LegacyBehaviorCompiler().compile(source,mod,"test/"+mod+"/Bootstrap",Map.of("blade",mod+":blade","cloak",mod+":cloak"));
    }
    private void load(LegacyBehaviorCompiler.Result result,String mod)throws Exception{
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
                byte[] bytes=result.classes().get(name.replace('.','/')+".class");if(bytes==null)throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        Class.forName("test."+mod+".Bootstrap",true,loader).getMethod("initialize").invoke(null);
    }
}
