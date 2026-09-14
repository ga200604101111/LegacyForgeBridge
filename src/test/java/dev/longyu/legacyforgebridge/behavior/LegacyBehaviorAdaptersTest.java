package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorAdaptersTest {
    @Test void primitiveTypesAndUnknownNestedNbtSurviveSourceMutation() {
        CompoundTag original=new CompoundTag();original.putByte("b",(byte)1);original.putShort("s",(short)2);
        original.putInt("i",123);original.putLong("l",9007199254740993L);original.putFloat("f",2.5F);original.putDouble("d",3.125D);
        original.putString("name","描述§6數值");original.putByteArray("bytes",new byte[]{1,2,3});
        CompoundTag nested=new CompoundTag();nested.putString("nested","must survive");original.put("otherMod",nested);
        ListTag list=new ListTag();list.add(StringTag.valueOf("original"));original.put("list",list);
        var tag=LegacyTagAdapter.read(original);
        assertInstanceOf(Byte.class,tag.values.get("b"));assertInstanceOf(Short.class,tag.values.get("s"));
        assertInstanceOf(Integer.class,tag.values.get("i"));assertInstanceOf(Long.class,tag.values.get("l"));
        assertInstanceOf(Float.class,tag.values.get("f"));assertInstanceOf(Double.class,tag.values.get("d"));
        assertEquals(original,LegacyTagAdapter.write(tag));
        tag.setInteger("i",999);tag.removeTag("name");var output=LegacyTagAdapter.write(tag);
        assertEquals(123,original.getIntOr("i",0));assertEquals(999,output.getIntOr("i",0));
        assertEquals(original.get("otherMod"),output.get("otherMod"));assertEquals(original.get("list"),output.get("list"));
        assertEquals(original.get("bytes"),output.get("bytes"));assertEquals(9007199254740993L,output.getLongOr("l",0));
        assertFalse(output.contains("name"));assertNotSame(original.get("otherMod"),output.get("otherMod"));
    }
    @Test void originalBooleanUsesByteCoercionAndInvalidNewNbtIsRejected() {
        var tag=new LegacyBehaviorApi.Tag(Map.of("b",256));assertFalse(tag.getBoolean("b"));
        tag.setBoolean("b",true);assertTrue(tag.getBoolean("b"));
        tag.values.put("bad",new Object());assertThrows(IllegalArgumentException.class,()->LegacyTagAdapter.write(tag));
    }
    @Test void sectionSignFormattingIsSharedByTooltipAndChat() {
        var component=LegacyText.formatted("§e§l攻擊§r普通§b數值");
        assertEquals("攻擊普通數值",component.getString());
        assertTrue(component.getSiblings().getFirst().getStyle().isBold());
        assertEquals(ChatFormatting.YELLOW.getColor(),component.getSiblings().getFirst().getStyle().getColor().getValue());
        assertFalse(component.getSiblings().get(1).getStyle().isBold());
    }
    @Test void bootstrapFailurePublishesNothingAndCanBeRetried() {
        String id="adapter_test";
        try {
            assertTrue(LegacyBehaviorRegistry.begin(id));
            var item=new LegacyBehaviorApi.Item();LegacyBehaviorRegistry.registerItem(id+":one",item,"use");
            LegacyBehaviorRegistry.registerEvent(id,"hurt",event->event.ammount++);
            assertNull(LegacyBehaviorRegistry.item(id+":one"));
            LegacyBehaviorRegistry.abort();
            assertEquals(0,LegacyBehaviorRegistry.itemCount(id));assertTrue(LegacyBehaviorRegistry.events("hurt").stream().noneMatch(e->e.mod().equals(id)));
            assertTrue(LegacyBehaviorRegistry.begin(id));LegacyBehaviorRegistry.registerItem(id+":one",item,"");LegacyBehaviorRegistry.finish();
            assertSame(item,LegacyBehaviorRegistry.item(id+":one").item());assertTrue(LegacyBehaviorRegistry.item(id+":one").hooks().isEmpty());
            assertFalse(LegacyBehaviorRegistry.begin(id));
            LegacyBehaviorApi.begin(id,(key,args)->key);LegacyBehaviorApi.end();
        } finally {LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(id);}
    }
    @Test void nativeRuntimeContainsRealEffectAndInventoryDispatchRatherThanOnlyDecoding() throws Exception {
        var calls=calls("LegacyBehaviorRuntime$Snapshot");
        for(String required:List.of("hurtServer","hurtAndBreak","addFreshEntity","setPickUpDelay","displayClientMessage","setCount","playSound","setDeltaMovement"))
            assertTrue(calls.contains(required),required);
        assertTrue(calls("ConvertedBehaviorItem").contains("hit"));
    }
    @Test void soundPresentationEventsOnlyOwnNameAndCancellation() {
        String mod="sound_adapter";
        try {
            assertTrue(LegacyBehaviorRegistry.begin(mod));
            LegacyBehaviorRegistry.registerEvent(mod,"sound",event->{
                if("cancel.me".equals(event.name)){event.setCanceled(true);return;}
                event.name="replacement.sound";event.volume=99F;event.pitch=77F;
                event.entity.field_70159_w=123D;
            });
            LegacyBehaviorRegistry.finish();
            var source=new LegacyBehaviorApi.Entity();source.field_70159_w=4D;
            var changed=LegacyBehaviorRuntime.soundPrograms(()->source,"original.sound",.35F,1.25F);
            assertFalse(changed.canceled());assertEquals("replacement.sound",changed.name());
            assertEquals(.35F,changed.volume());assertEquals(1.25F,changed.pitch());
            var canceled=LegacyBehaviorRuntime.soundPrograms(()->new LegacyBehaviorApi.Entity(),"cancel.me",.8F,.9F);
            assertTrue(canceled.canceled());assertEquals("cancel.me",canceled.name());
        } finally { LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod); }
    }
    @Test void commandAndEntityQueryBudgetsRemainBounded() {
        var world=new LegacyBehaviorApi.World();var player=new LegacyBehaviorApi.Player();player.field_70170_p=world;
        for(int i=0;i<1024;i++)world.command(new LegacyBehaviorApi.Sound(0,0,0,"random.break",1,1));
        assertThrows(IllegalStateException.class,()->world.command(new LegacyBehaviorApi.Sound(0,0,0,"random.break",1,1)));
        world.query=(e,b)->Collections.nCopies(513,new LegacyBehaviorApi.Entity());
        assertThrows(IllegalStateException.class,()->world.func_72839_b(player,player.field_70121_D));
    }
    private static Set<String> calls(String type) throws Exception {
        Set<String> result=new HashSet<>();
        try(var stream=LegacyBehaviorAdaptersTest.class.getResourceAsStream(type+".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){result.add(name);}
                    };
                }
            },0);
        }
        return result;
    }
}
