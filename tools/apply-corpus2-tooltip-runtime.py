#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRegistry.java"
COMPILER=ROOT/"src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
RUNTIME=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRuntime.java"
CLIENT=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorClient.java"
COMPILER_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorTooltipEventCompilerTest.java"
RUNTIME_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorTooltipEventRuntimeTest.java"
CLIENT_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorClientTooltipComponentsTest.java"

def replace_once(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

reg=REG.read_text(encoding="utf-8")
if "String targetItemId" not in reg:
    reg=replace_once(reg,
'''    public record EventDefinition(String mod, String kind, LegacyBehaviorApi.EventProgram program) { }
''',
'''    public record EventDefinition(String mod, String kind, String targetItemId, LegacyBehaviorApi.EventProgram program) {
        public EventDefinition(String mod, String kind, LegacyBehaviorApi.EventProgram program) { this(mod,kind,null,program); }
    }
''','target-aware event definition')
    reg=replace_once(reg,
'''    public static void registerEvent(String mod, String kind, LegacyBehaviorApi.EventProgram program) {
        Pending pending = pending();
        if (!pending.mod().equals(mod)) throw new IllegalArgumentException("Behavior event owner mismatch");
        pending.events().add(new EventDefinition(mod, kind, Objects.requireNonNull(program)));
    }
''',
'''    public static void registerEvent(String mod, String kind, LegacyBehaviorApi.EventProgram program) {
        registerEvent(mod,kind,null,program);
    }
    public static void registerEvent(String mod, String kind, String targetItemId, LegacyBehaviorApi.EventProgram program) {
        Pending pending = pending();
        if (!pending.mod().equals(mod)) throw new IllegalArgumentException("Behavior event owner mismatch");
        if (targetItemId != null) {
            if (!targetItemId.startsWith(pending.mod() + ':')) throw new IllegalArgumentException("Behavior event target owner mismatch");
            if (!pending.items().containsKey(targetItemId)) throw new IllegalArgumentException("Behavior event target not registered yet: " + targetItemId);
        }
        pending.events().add(new EventDefinition(mod, kind, targetItemId, Objects.requireNonNull(program)));
    }
''','target-aware event registration')
    REG.write_text(reg,encoding="utf-8")

compiler=COMPILER.read_text(encoding="utf-8")
if 'registerEvent","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;' not in compiler:
    old='''            m.visitLdcInsn(mod);m.visitLdcInsn(e.kind);m.visitTypeInsn(Opcodes.NEW,name+"Event"+i);m.visitInsn(Opcodes.DUP);
            if(e.targetItemId()!=null){
                m.visitLdcInsn(e.targetItemId());m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"bootstrapItem","(Ljava/lang/String;)L"+REG+"$ItemDefinition;",false);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,REG+"$ItemDefinition","item","()L"+API+"$Item;",false);m.visitTypeInsn(Opcodes.CHECKCAST,receiver);
            }else m.visitVarInsn(Opcodes.ALOAD,locals.get(e.owner));
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,name+"Event"+i,"<init>","(L"+receiver+";)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerEvent","(Ljava/lang/String;Ljava/lang/String;L"+API+"$EventProgram;)V",false);
'''
    new='''            m.visitLdcInsn(mod);m.visitLdcInsn(e.kind);if(e.targetItemId()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(e.targetItemId());m.visitTypeInsn(Opcodes.NEW,name+"Event"+i);m.visitInsn(Opcodes.DUP);
            if(e.targetItemId()!=null){
                m.visitLdcInsn(e.targetItemId());m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"bootstrapItem","(Ljava/lang/String;)L"+REG+"$ItemDefinition;",false);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,REG+"$ItemDefinition","item","()L"+API+"$Item;",false);m.visitTypeInsn(Opcodes.CHECKCAST,receiver);
            }else m.visitVarInsn(Opcodes.ALOAD,locals.get(e.owner));
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,name+"Event"+i,"<init>","(L"+receiver+";)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerEvent","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;L"+API+"$EventProgram;)V",false);
'''
    compiler=replace_once(compiler,old,new,'compiler target-aware event bootstrap')
    COMPILER.write_text(compiler,encoding="utf-8")

runtime=RUNTIME.read_text(encoding="utf-8")
if "tooltipPrograms(" not in runtime:
    runtime=replace_once(runtime,
'''    public static void tooltip(ItemStack stack,Player player,boolean advanced,List<String> lines) {
        var d=definition(stack);if(d==null||!d.hooks().contains("tooltip"))return;
        Snapshot snapshot=new Snapshot(player==null?null:player.level());
        invoke(d.mod(),"tooltip/"+BuiltInRegistries.ITEM.getKey(stack.getItem()),()->{
            d.item().func_77624_a(snapshot.stack(stack),player==null?new LegacyBehaviorApi.Player():(LegacyBehaviorApi.Player)snapshot.living(player),lines,advanced);
            return true;
        },false);
    }
''',
'''    public static void tooltip(ItemStack stack,Player player,boolean advanced,List<String> lines) {
        var d=definition(stack);if(d==null||!d.hooks().contains("tooltip"))return;
        Snapshot snapshot=new Snapshot(player==null?null:player.level());
        invoke(d.mod(),"tooltip/"+BuiltInRegistries.ITEM.getKey(stack.getItem()),()->{
            d.item().func_77624_a(snapshot.stack(stack),player==null?new LegacyBehaviorApi.Player():(LegacyBehaviorApi.Player)snapshot.living(player),lines,advanced);
            return true;
        },false);
    }
    /** Client-presentation Forge ItemTooltipEvent bridge. The snapshot is intentionally never committed. */
    public static boolean tooltipEvent(ItemStack stack,List<String> lines) {
        if(stack==null||stack.isEmpty()||lines==null)return false;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Snapshot snapshot=new Snapshot(null);
        LegacyBehaviorApi.Stack source=snapshot.stack(stack);
        return source!=null&&tooltipPrograms(id,source,lines);
    }
    static boolean tooltipPrograms(String itemId,LegacyBehaviorApi.Stack sourceStack,List<String> lines) {
        if(itemId==null||sourceStack==null||lines==null||!validTooltip(lines))return false;
        boolean matched=false;
        BoundedTooltipList bounded=new BoundedTooltipList(lines);
        for(var program:LegacyBehaviorRegistry.events("tooltipEvent")) {
            if(program.targetItemId()!=null&&!program.targetItemId().equals(itemId))continue;
            matched=true;
            var event=new LegacyBehaviorApi.Event();event.itemStack=sourceStack;event.toolTip=bounded;
            boolean completed=invoke(program.mod(),"event/tooltip/"+itemId,()->{program.program().run(event);return true;},false);
            if(!completed||event.itemStack!=sourceStack||event.toolTip!=bounded||!validTooltip(bounded))return false;
        }
        return matched;
    }
    private static boolean validTooltip(List<String> lines) {
        if(lines.size()>256)return false;
        for(String line:lines)if(line==null||line.length()>4096)return false;
        return true;
    }
    private static final class BoundedTooltipList extends AbstractList<String> {
        private final List<String> delegate;
        BoundedTooltipList(List<String> delegate){this.delegate=Objects.requireNonNull(delegate);}
        @Override public String get(int index){return delegate.get(index);}
        @Override public int size(){return delegate.size();}
        @Override public String set(int index,String value){check(value);return delegate.set(index,value);}
        @Override public void add(int index,String value){check(value);if(delegate.size()>=256)throw new IllegalStateException("Source tooltip line budget exceeded");delegate.add(index,value);}
        @Override public String remove(int index){return delegate.remove(index);}
        private static void check(String value){if(value==null||value.length()>4096)throw new IllegalArgumentException("Invalid source tooltip line");}
    }
''','tooltip event runtime bridge')
    RUNTIME.write_text(runtime,encoding="utf-8")

client=CLIENT.read_text(encoding="utf-8")
if "class TooltipComponents" not in client:
    client=client.replace('import java.util.ArrayList;\n','import java.util.AbstractList;\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Objects;\n')
    client=replace_once(client,
'''            LegacyBehaviorRuntime.tooltip(stack,Minecraft.getInstance().player,flag.isAdvanced(),extra);
            for(String line:extra.stream().limit(128).toList())if(line!=null)lines.add(formatted(line.length()>4096?line.substring(0,4096):line));
        });
''',
'''            LegacyBehaviorRuntime.tooltip(stack,Minecraft.getInstance().player,flag.isAdvanced(),extra);
            for(String line:extra.stream().limit(128).toList())if(line!=null)lines.add(formatted(line.length()>4096?line.substring(0,4096):line));
            TooltipComponents working=new TooltipComponents(lines);
            if(LegacyBehaviorRuntime.tooltipEvent(stack,working))working.commitTo(lines);
        });
''','client tooltip event dispatch')
    client=replace_once(client,
'''    static Component formatted(String value) { return LegacyText.formatted(value); }
}
''',
'''    static Component formatted(String value) { return LegacyText.formatted(value); }
    static final class TooltipComponents extends AbstractList<String> {
        private final List<Component> values;
        TooltipComponents(List<Component> source){values=new ArrayList<>(Objects.requireNonNull(source));}
        @Override public String get(int index){return values.get(index).getString();}
        @Override public int size(){return values.size();}
        @Override public String set(int index,String value){
            Objects.requireNonNull(value);Component old=values.get(index);String before=old.getString();Component replacement;
            if(value.startsWith(before)){
                MutableComponent copy=old.copy();String suffix=value.substring(before.length());
                if(!suffix.isEmpty())copy.append(formatted(suffix));replacement=copy;
            }else replacement=formatted(value);
            values.set(index,replacement);return before;
        }
        @Override public void add(int index,String value){Objects.requireNonNull(value);if(values.size()>=256)throw new IllegalStateException("Source tooltip line budget exceeded");values.add(index,formatted(value));}
        @Override public String remove(int index){return values.remove(index).getString();}
        void commitTo(List<Component> target){target.clear();target.addAll(values);}
    }
}
''','component-preserving tooltip buffer')
    CLIENT.write_text(client,encoding="utf-8")

compiler_test=COMPILER_TEST.read_text(encoding="utf-8")
if "program.targetItemId()" not in compiler_test:
    compiler_test=replace_once(compiler_test,
'''            var program=LegacyBehaviorRegistry.events("tooltipEvent").stream().filter(e->e.mod().equals("tooltip_fixture")).findFirst().orElseThrow();
            var root=new LegacyBehaviorApi.Tag();root.setInteger("toolLevel",3);
''',
'''            var program=LegacyBehaviorRegistry.events("tooltipEvent").stream().filter(e->e.mod().equals("tooltip_fixture")).findFirst().orElseThrow();
            assertEquals("tooltip_fixture:unsafe",program.targetItemId());
            var root=new LegacyBehaviorApi.Tag();root.setInteger("toolLevel",3);
''','compiler test target registry identity')
    COMPILER_TEST.write_text(compiler_test,encoding="utf-8")

if not RUNTIME_TEST.exists():
    RUNTIME_TEST.write_text(r'''package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorTooltipEventRuntimeTest {
    @Test void targetFilteringUsesExactPresentationItemAndNeverCommitsTemporaryNbt() {
        String mod="minecraft";
        var stickItem=new LegacyBehaviorApi.Item();var stoneItem=new LegacyBehaviorApi.Item();var wrongRuns=new AtomicInteger();
        try {
            assertTrue(LegacyBehaviorRegistry.begin(mod));
            LegacyBehaviorRegistry.registerPresentationItem("minecraft:stick",stickItem);
            LegacyBehaviorRegistry.registerPresentationItem("minecraft:stone",stoneItem);
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stick",event->{
                assertSame(stickItem,event.itemStack.item);
                if(event.itemStack.field_77990_d==null)event.itemStack.setTagCompound(new LegacyBehaviorApi.Tag());
                event.itemStack.field_77990_d.setInteger("toolLevel",9);
                event.toolTip.set(0,event.toolTip.get(0)+" Level:9");
            });
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stone",event->wrongRuns.incrementAndGet());
            LegacyBehaviorRegistry.finish();

            ItemStack nativeStack=new ItemStack(Items.STICK);var lines=new ArrayList<>(List.of("Stick","Stats"));
            assertNull(nativeStack.get(DataComponents.CUSTOM_DATA));
            assertTrue(LegacyBehaviorRuntime.tooltipEvent(nativeStack,lines));
            assertEquals(List.of("Stick Level:9","Stats"),lines);
            assertEquals(0,wrongRuns.get());
            assertNull(nativeStack.get(DataComponents.CUSTOM_DATA),"presentation tooltip callback committed temporary source NBT");
        } finally {LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }

    @Test void invalidTooltipMutationFailsClosed() {
        String mod="minecraft";
        try {
            assertTrue(LegacyBehaviorRegistry.begin(mod));LegacyBehaviorRegistry.registerPresentationItem("minecraft:stick",new LegacyBehaviorApi.Item());
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stick",event->event.toolTip.add("x".repeat(4097)));
            LegacyBehaviorRegistry.finish();
            var lines=new ArrayList<>(List.of("Stick"));
            assertFalse(LegacyBehaviorRuntime.tooltipEvent(new ItemStack(Items.STICK),lines));
            assertEquals(List.of("Stick"),lines);
        } finally {LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }
}
''',encoding="utf-8")

if not CLIENT_TEST.exists():
    CLIENT_TEST.write_text(r'''package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorClientTooltipComponentsTest {
    @Test void appendedSuffixPreservesOriginalNameComponentStyleAndInsertionsStayFormatted() {
        List<Component> nativeLines=new ArrayList<>(List.of(
                Component.literal("Blade").withStyle(ChatFormatting.GOLD),
                Component.literal("Stats").withStyle(ChatFormatting.GRAY)));
        var working=new LegacyBehaviorClient.TooltipComponents(nativeLines);
        working.set(0,"Blade Level:3");working.add(1,"§bEcho II");

        assertEquals("Blade",nativeLines.get(0).getString(),"working buffer mutated native tooltip before commit");
        working.commitTo(nativeLines);
        assertEquals(List.of("Blade Level:3","Echo II","Stats"),nativeLines.stream().map(Component::getString).toList());
        assertNotNull(nativeLines.get(0).getStyle().getColor());
        assertEquals(ChatFormatting.GOLD.getColor(),nativeLines.get(0).getStyle().getColor().getValue());
    }
}
''',encoding="utf-8")

print("Applied native ItemTooltip event runtime/client bridge")
