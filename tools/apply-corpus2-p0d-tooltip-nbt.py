#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
API=ROOT/'src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi.java'
ADAPTER=ROOT/'src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyTagAdapter.java'
RUNTIME=ROOT/'src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRuntime.java'
COMPILER=ROOT/'src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java'
TEST=ROOT/'src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorAdaptersTest.java'

def replace_once(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f'{label}: expected one match, found {count}')
    return text.replace(old,new,1)

api=API.read_text(encoding='utf-8')
if 'public static final class StatCollector' not in api:
    api=replace_once(api,
'''    public static final class I18n {
        public static String func_135052_a(String key,Object[] args) { return translate(key,args); }
        public static String format(String key,Object... args) { return translate(key,args); }
    }
''',
'''    public static final class I18n {
        public static String func_135052_a(String key,Object[] args) { return translate(key,args); }
        public static String format(String key,Object... args) { return translate(key,args); }
    }
    public static final class StatCollector {
        public static String func_74838_a(String key) { return translate(key,new Object[0]); }
        public static String translateToLocal(String key) { return func_74838_a(key); }
    }
''','StatCollector adapter')
if 'public Tag field_77990_d;' not in api:
    api=replace_once(api,
'''        public Item item;
        public Tag tag;
        public Object handle;
''',
'''        public Item item;
        /** Compatibility alias retained for existing bridge tests; legacy bytecode uses field_77990_d. */
        public Tag tag;
        public Tag field_77990_d;
        public Object handle;
''','legacy stack tag field')
    api=replace_once(api,
'''        public Stack(Item item,Tag tag) { this.item=item;this.tag=tag; }
        public Tag func_77978_p() { return tag; }
        public Tag getTagCompound() { return tag; }
        public boolean func_77942_o() { return tag!=null; }
''',
'''        public Stack(Item item,Tag tag) { this.item=item;this.tag=tag;this.field_77990_d=tag; }
        public Tag func_77978_p() { return field_77990_d; }
        public Tag getTagCompound() { return field_77990_d; }
        public boolean func_77942_o() { return field_77990_d!=null; }
''','canonical legacy stack tag reads')
    api=replace_once(api,
'''        public void func_77982_d(Tag value) { tag=value; }
        public void setTagCompound(Tag value) { tag=value; }
''',
'''        public void func_77982_d(Tag value) { tag=value;field_77990_d=value; }
        public void setTagCompound(Tag value) { func_77982_d(value); }
''','canonical legacy stack tag writes')
if 'public short func_74765_d' not in api:
    api=replace_once(api,
'''        public int func_74762_e(String key) { Object v=values.get(key);return v instanceof Number n?n.intValue():0; }
        public int getInteger(String key) { return func_74762_e(key); }
''',
'''        public int func_74762_e(String key) { Object v=values.get(key);return v instanceof Number n?n.intValue():0; }
        public int getInteger(String key) { return func_74762_e(key); }
        public short func_74765_d(String key) { Object v=values.get(key);return v instanceof Number n?n.shortValue():0; }
        public short getShort(String key) { return func_74765_d(key); }
        public TagList func_150295_c(String key,int type) { Object v=values.get(key);return type==10&&v instanceof TagList list?list:new TagList(); }
        public TagList getTagList(String key,int type) { return func_150295_c(key,type); }
''','Tag short/list reads')
if 'public static class TagList' not in api:
    api=replace_once(api,
'''        public void removeTag(String key) { func_82580_o(key); }
    }
    public static class Entity {
''',
'''        public void removeTag(String key) { func_82580_o(key); }
    }
    public static class TagList {
        public final List<Tag> values=new ArrayList<>();
        public TagList() { }
        public TagList(Collection<Tag> values) {
            if(values.size()>4096)throw new IllegalArgumentException("Source NBT list budget exceeded");
            this.values.addAll(values);
        }
        public int func_74745_c() { return values.size(); }
        public int tagCount() { return func_74745_c(); }
        public Tag func_150305_b(int index) { return index>=0&&index<values.size()?values.get(index):new Tag(); }
        public Tag getCompoundTagAt(int index) { return func_150305_b(index); }
    }
    public static class Entity {
''','TagList adapter')
API.write_text(api,encoding='utf-8')

compiler=COMPILER.read_text(encoding='utf-8')
if 'net/minecraft/nbt/NBTTagList' not in compiler:
    compiler=replace_once(compiler,
'''            Map.entry("net/minecraft/creativetab/CreativeTabs","CreativeTab"),Map.entry("net/minecraft/nbt/NBTTagCompound","Tag"),
''',
'''            Map.entry("net/minecraft/creativetab/CreativeTabs","CreativeTab"),Map.entry("net/minecraft/nbt/NBTTagCompound","Tag"),Map.entry("net/minecraft/nbt/NBTTagList","TagList"),
''','NBTTagList type mapping')
if 'net/minecraft/util/StatCollector' not in compiler:
    compiler=replace_once(compiler,
'''            Map.entry("net/minecraft/util/DamageSource","Damage"),Map.entry("net/minecraft/client/resources/I18n","I18n"),
''',
'''            Map.entry("net/minecraft/util/DamageSource","Damage"),Map.entry("net/minecraft/client/resources/I18n","I18n"),Map.entry("net/minecraft/util/StatCollector","StatCollector"),
''','StatCollector type mapping')
COMPILER.write_text(compiler,encoding='utf-8')

ADAPTER.write_text(r'''package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Preserve exact primitive types; expose only proven compound lists through the source API. */
public final class LegacyTagAdapter {
    private LegacyTagAdapter() { }

    public static LegacyBehaviorApi.Tag read(CompoundTag input) {
        if (input == null) return null;
        var result = new LegacyBehaviorApi.Tag();
        for (var field : input.entrySet()) result.values.put(field.getKey(), readValue(field.getValue()));
        return result;
    }

    private static Object readValue(Tag value) {
        if (value instanceof NumericTag number) return number.box();
        if (value instanceof StringTag string) return string.value();
        if (value instanceof CompoundTag compound) return read(compound);
        if (value instanceof ListTag list) {
            var compounds = compoundList(list);
            if (compounds != null) return compounds;
        }
        return value.copy();
    }

    private static LegacyBehaviorApi.TagList compoundList(ListTag input) {
        if (input.size() > 4096) throw new IllegalArgumentException("Source NBT list budget exceeded");
        var values = new java.util.ArrayList<LegacyBehaviorApi.Tag>();
        for (Tag value : input) {
            if (!(value instanceof CompoundTag compound)) return null;
            values.add(read(compound));
        }
        return new LegacyBehaviorApi.TagList(values);
    }

    public static CompoundTag write(LegacyBehaviorApi.Tag input) {
        CompoundTag result = new CompoundTag();
        if (input == null) return result;
        for (var field : input.values.entrySet()) result.put(field.getKey(), writeValue(field.getKey(),field.getValue()));
        return result;
    }

    private static Tag writeValue(String key,Object value) {
        if (value instanceof String text) return StringTag.valueOf(text);
        if (value instanceof Byte number) return net.minecraft.nbt.ByteTag.valueOf(number);
        if (value instanceof Short number) return net.minecraft.nbt.ShortTag.valueOf(number);
        if (value instanceof Integer number) return net.minecraft.nbt.IntTag.valueOf(number);
        if (value instanceof Long number) return net.minecraft.nbt.LongTag.valueOf(number);
        if (value instanceof Float number) return net.minecraft.nbt.FloatTag.valueOf(number);
        if (value instanceof Double number) return net.minecraft.nbt.DoubleTag.valueOf(number);
        if (value instanceof LegacyBehaviorApi.Tag compound) return write(compound);
        if (value instanceof LegacyBehaviorApi.TagList list) {
            ListTag result=new ListTag();
            if(list.values.size()>4096)throw new IllegalArgumentException("Source NBT list budget exceeded");
            for(LegacyBehaviorApi.Tag compound:list.values)result.add(write(compound));
            return result;
        }
        if (value instanceof Tag tag) return tag.copy();
        throw new IllegalArgumentException("Unsupported source NBT value for " + key);
    }
}
''',encoding='utf-8')

runtime=RUNTIME.read_text(encoding='utf-8')
runtime=runtime.replace('view.tag','view.field_77990_d')
RUNTIME.write_text(runtime,encoding='utf-8')

test=TEST.read_text(encoding='utf-8')
if 'compoundListAndLegacyStackTagFieldRoundTrip' not in test:
    insertion='''    @Test void compoundListAndLegacyStackTagFieldRoundTrip() {
        CompoundTag original=new CompoundTag();
        CompoundTag enchant=new CompoundTag();enchant.putShort("id",(short)8);enchant.putShort("lvl",(short)3);
        ListTag compounds=new ListTag();compounds.add(enchant);original.put("spench",compounds);
        ListTag opaqueStrings=new ListTag();opaqueStrings.add(StringTag.valueOf("keep-opaque"));original.put("otherList",opaqueStrings);
        var source=LegacyTagAdapter.read(original);var list=source.func_150295_c("spench",10);
        assertEquals(1,list.func_74745_c());assertEquals((short)8,list.func_150305_b(0).func_74765_d("id"));
        assertEquals((short)3,list.getCompoundTagAt(0).getShort("lvl"));
        assertEquals(original,LegacyTagAdapter.write(source));
        assertInstanceOf(net.minecraft.nbt.ListTag.class,source.values.get("otherList"));
        var stack=new LegacyBehaviorApi.Stack(new LegacyBehaviorApi.Item());assertNull(stack.field_77990_d);
        stack.field_77990_d=source;assertTrue(stack.hasTagCompound());assertSame(source,stack.getTagCompound());
        var replacement=new LegacyBehaviorApi.Tag();stack.setTagCompound(replacement);
        assertSame(replacement,stack.field_77990_d);assertSame(replacement,stack.tag);
    }
    @Test void statCollectorUsesTheSameConvertedTranslationBoundary() {
        LegacyBehaviorApi.begin("translation_fixture",(key,args)->"translated:"+key);
        try { assertEquals("translated:lfb.converted.translation_fixture.bambooEnch.chain",LegacyBehaviorApi.StatCollector.func_74838_a("bambooEnch.chain")); }
        finally { LegacyBehaviorApi.end(); }
    }
'''
    marker='    @Test void originalBooleanUsesByteCoercionAndInvalidNewNbtIsRejected() {\n'
    if test.count(marker)!=1:raise SystemExit('adapter test insertion point changed')
    TEST.write_text(test.replace(marker,insertion+marker,1),encoding='utf-8')

print('Applied tooltip NBT list, legacy tag field, and StatCollector adapters')
