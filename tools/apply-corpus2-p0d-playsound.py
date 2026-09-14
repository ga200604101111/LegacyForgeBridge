#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
API=ROOT/'src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi.java'
COMPILER=ROOT/'src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java'
RUNTIME=ROOT/'src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRuntime.java'
ITEMBLOCK_TEST=ROOT/'src/test/java/dev/longyu/legacyforgebridge/convert/pass/LegacyBehaviorItemBlockPassTest.java'
ADAPTER_TEST=ROOT/'src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorAdaptersTest.java'

def replace_once(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f'{label}: expected one match, found {count}')
    return text.replace(old,new,1)

api=API.read_text(encoding='utf-8')
if 'public String name;' not in api:
    api=replace_once(api,
'''    public static class Event {
        public Living entityLiving;
        public Damage source=new Damage();
        public float ammount,distance,damageMultiplier=1;
        private boolean canceled;
''',
'''    public static class Event {
        public Entity entity;
        public Living entityLiving;
        public Damage source=new Damage();
        public String name;
        public float volume,pitch;
        public float ammount,distance,damageMultiplier=1;
        private boolean canceled;
''','event presentation fields')
    API.write_text(api,encoding='utf-8')

compiler=COMPILER.read_text(encoding='utf-8')
if 'PlaySoundAtEntityEvent","Event"' not in compiler:
    compiler=replace_once(compiler,
'''            Map.entry("net/minecraftforge/event/entity/living/LivingHurtEvent","Event"));''',
'''            Map.entry("net/minecraftforge/event/entity/living/LivingHurtEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/EntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"));''','playsound type mapping')
if 'PlaySoundAtEntityEvent"->"sound"' not in compiler:
    compiler=replace_once(compiler,
'''                case "net/minecraftforge/event/entity/living/LivingHurtEvent"->"hurt";
                default->null;''',
'''                case "net/minecraftforge/event/entity/living/LivingHurtEvent"->"hurt";
                case "net/minecraftforge/event/entity/PlaySoundAtEntityEvent"->"sound";
                default->null;''','playsound kind mapping')
COMPILER.write_text(compiler,encoding='utf-8')

runtime=RUNTIME.read_text(encoding='utf-8')
if 'record SoundOutcome' not in runtime:
    runtime=replace_once(runtime,
'''    public static void jump(LivingEntity entity) {
''',
'''    public record SoundOutcome(boolean canceled,String name,float volume,float pitch) { }
    public static SoundOutcome soundEvent(Entity entity,String name,float volume,float pitch) {
        if(entity==null||name==null||!Float.isFinite(volume)||!Float.isFinite(pitch))
            return new SoundOutcome(false,name,volume,pitch);
        return soundPrograms(()->new Snapshot(entity.level()).entity(entity),name,volume,pitch);
    }
    static SoundOutcome soundPrograms(Supplier<LegacyBehaviorApi.Entity> entitySource,String name,float volume,float pitch) {
        String current=name;
        for(var program:LegacyBehaviorRegistry.events("sound")) {
            var event=new LegacyBehaviorApi.Event();
            event.entity=Objects.requireNonNull(entitySource.get());
            if(event.entity instanceof LegacyBehaviorApi.Living living)event.entityLiving=living;
            event.name=current;event.volume=volume;event.pitch=pitch;
            if(invoke(program.mod(),"event/sound",()->{program.program().run(event);return true;},false)) {
                if(event.isCanceled())return new SoundOutcome(true,current,volume,pitch);
                if(event.name!=null)current=event.name;
            }
        }
        return new SoundOutcome(false,current,volume,pitch);
    }
    public static void jump(LivingEntity entity) {
''','sound runtime adapter')
    RUNTIME.write_text(runtime,encoding='utf-8')

# Turn the existing unrelated ItemBlock fixture into the exact API shape exercised by Bamboo's
# ItemVillagerBlock sound listener, while retaining generic namespace and IDs.
test=ITEMBLOCK_TEST.read_text(encoding='utf-8')
if 'events("sound")' not in test:
    test=replace_once(test,
'''            var event=LegacyBehaviorRegistry.events("jump").stream().filter(e->e.mod().equals(mod)).findFirst().orElseThrow();
            var sourceEvent=new LegacyBehaviorApi.Event();
            LegacyBehaviorApi.begin(mod,(key,args)->key);
            try{event.program().run(sourceEvent);}
            finally{LegacyBehaviorApi.end();}
''',
'''            var event=LegacyBehaviorRegistry.events("sound").stream().filter(e->e.mod().equals(mod)).findFirst().orElseThrow();
            var player=new LegacyBehaviorApi.Player();player.health=6F;
            player.equipment[4]=new LegacyBehaviorApi.Stack(item,null);
            var sourceEvent=new LegacyBehaviorApi.Event();sourceEvent.entity=player;sourceEvent.name="game.player.hurt";sourceEvent.volume=.7F;sourceEvent.pitch=1.1F;
            LegacyBehaviorApi.begin(mod,(key,args)->key);
            try{event.program().run(sourceEvent);}
            finally{LegacyBehaviorApi.end();}
            assertEquals("mob.villager.idle",sourceEvent.name);
''','fixture event assertion')
    old='''        MethodVisitor jump=writer.visitMethod(Opcodes.ACC_PUBLIC,"onJump","(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent;)V",null,null);
        jump.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();jump.visitCode();jump.visitInsn(Opcodes.RETURN);jump.visitMaxs(0,2);jump.visitEnd();
'''
    new='''        MethodVisitor sound=writer.visitMethod(Opcodes.ACC_PUBLIC,"onSound","(Lnet/minecraftforge/event/entity/PlaySoundAtEntityEvent;)V",null,null);
        sound.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();sound.visitCode();
        Label done=new Label(),hit=new Label();
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.INSTANCEOF,"net/minecraft/entity/player/EntityPlayer");sound.visitJumpInsn(Opcodes.IFEQ,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");sound.visitInsn(Opcodes.ICONST_4);sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","func_71124_b","(I)Lnet/minecraft/item/ItemStack;",false);sound.visitVarInsn(Opcodes.ASTORE,2);
        sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitJumpInsn(Opcodes.IFNULL,done);sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);sound.visitVarInsn(Opcodes.ALOAD,0);sound.visitJumpInsn(Opcodes.IF_ACMPNE,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");sound.visitLdcInsn("game.player.hurt");sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);sound.visitJumpInsn(Opcodes.IFEQ,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","func_110143_aJ","()F",false);sound.visitLdcInsn(3F);sound.visitInsn(Opcodes.FREM);sound.visitInsn(Opcodes.FCONST_0);sound.visitInsn(Opcodes.FCMPG);sound.visitJumpInsn(Opcodes.IFNE,hit);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitLdcInsn("mob.villager.idle");sound.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");sound.visitJumpInsn(Opcodes.GOTO,done);
        sound.visitLabel(hit);sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitLdcInsn("mob.villager.hit");sound.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");
        sound.visitLabel(done);sound.visitInsn(Opcodes.RETURN);sound.visitMaxs(0,0);sound.visitEnd();
'''
    test=replace_once(test,old,new,'fixture PlaySound bytecode')
    ITEMBLOCK_TEST.write_text(test,encoding='utf-8')

adapter=ADAPTER_TEST.read_text(encoding='utf-8')
if 'soundPresentationEventsOnlyOwnNameAndCancellation' not in adapter:
    insertion='''    @Test void soundPresentationEventsOnlyOwnNameAndCancellation() {
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
'''
    adapter=replace_once(adapter,'    @Test void commandAndEntityQueryBudgetsRemainBounded() {\n',insertion+'    @Test void commandAndEntityQueryBudgetsRemainBounded() {\n','sound runtime regression')
    ADAPTER_TEST.write_text(adapter,encoding='utf-8')

print('Applied generic PlaySoundAtEntity compiler/runtime slice')
