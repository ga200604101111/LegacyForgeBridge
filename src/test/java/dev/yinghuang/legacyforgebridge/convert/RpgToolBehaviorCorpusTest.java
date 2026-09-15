package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.behavior.*;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi.*;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi.Stack;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi.Tag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real external corpus. Run with -Dlfb.corpus.jar=/path/to/original.jar; never commit the binary. */
class RpgToolBehaviorCorpusTest {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static JsonObject report;
    static Path candidate;
    static final Map<String,String> language = new HashMap<>();

    @BeforeAll static void prepare() throws Exception {
        String input = System.getProperty("lfb.corpus.jar", System.getenv("LFB_CORPUS_JAR"));
        Assumptions.assumeTrue(input != null, "External RPGTool corpus not supplied; not a gameplay pass");
        Path source = Path.of(input);
        assertEquals("b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d", Hashing.sha256(source));
        Path named = temp.resolve("RPGTool1-1.1-1.7.10.jar"); Files.copy(source, named);
        var engine = new LegacyConversionEngine();
        var first = engine.convert(named,temp.resolve("a"),temp.resolve("ma"));
        var second = engine.convert(named,temp.resolve("b"),temp.resolve("mb"));
        candidate = first.candidateJar().orElseThrow();
        assertEquals(Hashing.sha256(candidate),Hashing.sha256(second.candidateJar().orElseThrow()),"Real conversion must be deterministic");
        try(var jar = new JarFile(candidate.toFile())) {
            try(var stream = jar.getInputStream(jar.getJarEntry("legacyforgebridge/behavior-analysis.json"))) {
                report = JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for(var entry : jar.stream().filter(e->e.getName().matches("assets/.*/lang/zh_cn.json")).toList()) {
                try(var stream = jar.getInputStream(entry)) {
                    JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                            .entrySet().forEach(e->language.put(e.getKey(),e.getValue().getAsString()));
                }
            }
            assertFalse(jar.stream().anyMatch(e->e.getName().contains("/lang/zh_tw.json")),"Do not fabricate a source locale");
        }
        loader = new URLClassLoader(new java.net.URL[]{candidate.toUri().toURL()},RpgToolBehaviorCorpusTest.class.getClassLoader());
        Class.forName("dev.longyu.legacyforgebridge.generated.rpgtool1.GeneratedContentBehavior",true,loader).getMethod("initialize").invoke(null);
    }
    @AfterAll static void finish() throws Exception { LegacyBehaviorRegistry.removeMod("rpgtool1");if(loader!=null)loader.close(); }
    @BeforeEach void begin() { LegacyBehaviorApi.begin("rpgtool1",(key,args)->language.getOrDefault(key,key)); }
    @AfterEach void end() { LegacyBehaviorApi.end(); }
    static Stack stack(String id) { return new Stack(Objects.requireNonNull(LegacyBehaviorRegistry.item("rpgtool1:"+id),id).item(),null); }
    static Stack skill(String value) { Stack stack=stack("dark_sword");stack.tag=new Tag(Map.of("RPGTOOL_SKILLS",value));return stack; }
    static Player player(World world) { var player=new Player();player.field_70170_p=world;return player; }
    static Living victim(World world) { var target=new Living();target.field_70170_p=world;return target; }
    static List<Attack> attacks(World world) { return world.commands.stream().filter(Attack.class::isInstance).map(Attack.class::cast).toList(); }
    static int wear(World world) { return world.commands.stream().filter(Durability.class::isInstance).map(Durability.class::cast).mapToInt(Durability::amount).sum(); }
    static void use(Stack input,Player player) { input.item.func_77659_a(input,player.field_70170_p,player); }
    static List<String> tooltip(Stack stack,Player player) { var lines=new ArrayList<String>();stack.item.func_77624_a(stack,player,lines,false);return lines; }

    @Test void originalConstructorsAndEverySelectedCallbackAreAdmitted() {
        assertEquals(71,LegacyBehaviorRegistry.itemCount("rpgtool1"));
        assertEquals(71,report.getAsJsonArray("items").size());assertTrue(report.getAsJsonArray("unsupported").isEmpty(),report.get("unsupported").toString());
        assertEquals(3,report.getAsJsonArray("events").size());
        for(var item:report.getAsJsonArray("items")) {
            String id=item.getAsJsonObject().get("id").getAsString();
            assertEquals(id,LegacyBehaviorRegistry.identity(LegacyBehaviorRegistry.item(id).item()).orElseThrow());
        }
    }
    @Test void allOriginalItemDescriptionsExecuteAndUseCurrentNbt() {
        Player player=player(new World());
        for(var item:report.getAsJsonArray("items")) {
            String id=item.getAsJsonObject().get("id").getAsString();
            var d=LegacyBehaviorRegistry.item(id);List<String> lines=new ArrayList<>();
            d.item().func_77624_a(new Stack(d.item(),null),player,lines,false);
            assertTrue(lines.size()<128,id);
        }
        Stack sword=stack("dark_sword");sword.tag=new Tag(Map.of("RPGTOOL_DIG",3,"RPGTOOL_USEDIG",2,
                "RPGTOOL_GEMS","attack1,lifesteal2,","RPGTOOL_DAMAGE",3,"RPGTOOL_LIFESTEAL",5,"RPGTOOL_SKILLS","right_range"));
        List<String> first=tooltip(sword,player);
        assertTrue(first.stream().anyMatch(s->s.contains("§")),first.toString());
        sword.tag.setInteger("RPGTOOL_DAMAGE",137);
        List<String> second=tooltip(sword,player);
        assertNotEquals(first,second);assertTrue(second.stream().anyMatch(s->s.contains("137")),second.toString());
        assertTrue(second.stream().anyMatch(s->s.contains("技能")),second.toString());
    }
    @Test void ordinarySwordsBlockButChargeSkillsUseSourceBowAnd72000Ticks() {
        World world=new World();Player player=player(world);Stack ordinary=stack("dark_sword");
        assertEquals(UseAction.block,ordinary.item.func_77661_b(ordinary));use(ordinary,player);
        assertSame(ordinary,player.requestedUse);assertEquals(72000,player.requestedDuration);
        for(String id:List.of("right_range","light_range")) {
            Stack charged=skill(id);assertEquals(UseAction.bow,charged.item.func_77661_b(charged));
            player.requestedUse=null;use(charged,player);assertSame(charged,player.requestedUse);assertEquals(72000,player.requestedDuration);
        }
        player.requestedUse=null;use(skill("night_vision"),player);assertNull(player.requestedUse,"Do not invent unconditional blocking");
    }
    @Test void drillThreeSocketsSocketAttackLifestealAndCleanDropsOriginalGems() {
        World world=new World();Player player=player(world);Stack sword=stack("dark_sword");player.field_71071_by.slots[0]=sword;
        Stack drill=stack("dig");drill.field_77994_a=5;
        for(int i=0;i<3;i++)use(drill,player);
        assertEquals(3,sword.tag.getInteger("RPGTOOL_DIG"));assertEquals(2,drill.field_77994_a);
        use(drill,player);assertEquals(2,drill.field_77994_a,"Max sockets do not consume another drill");
        Stack attack=stack("attack1"),steal=stack("lifesteal2");use(attack,player);use(steal,player);
        assertEquals(3,sword.tag.getInteger("RPGTOOL_DAMAGE"));assertEquals(5,sword.tag.getInteger("RPGTOOL_LIFESTEAL"));
        assertEquals(2,sword.tag.getInteger("RPGTOOL_USEDIG"));assertEquals(0,attack.field_77994_a);assertEquals(0,steal.field_77994_a);
        world.commands.clear();Stack clean=stack("cleangp");use(clean,player);
        assertEquals(0,sword.tag.getInteger("RPGTOOL_DAMAGE"));assertEquals(0,sword.tag.getInteger("RPGTOOL_LIFESTEAL"));
        assertEquals(0,sword.tag.getInteger("RPGTOOL_USEDIG"));assertEquals(3,sword.tag.getInteger("RPGTOOL_DIG"));
        var drops=world.commands.stream().filter(Spawn.class::isInstance).map(Spawn.class::cast).map(Spawn::entity).filter(Drop.class::isInstance).map(Drop.class::cast).toList();
        assertEquals(2,drops.size());
        assertEquals(Set.of("rpgtool1:attack1","rpgtool1:lifesteal2"),new HashSet<>(drops.stream().map(d->LegacyBehaviorRegistry.identity(d.stack.item).orElseThrow()).toList()));
        assertTrue(drops.stream().allMatch(d->d.field_145804_b==10));
    }
    @Test void armorDefenseAndSkillSocketUseOriginalItemTypeRules() {
        World world=new World();Player player=player(world);Stack wing=stack("wing01");player.field_71071_by.slots[0]=wing;
        use(stack("dig"),player);Stack defense=stack("defense1");use(defense,player);
        assertEquals(2,wing.tag.getInteger("RPGTOOL_DEFENSE"));assertEquals(0,defense.field_77994_a);
        Stack sword=stack("dark_sword");player.field_71071_by.slots[0]=sword;Stack gem=stack("right_range");use(gem,player);
        assertEquals("right_range",sword.tag.getString("RPGTOOL_SKILLS"));assertEquals(0,gem.field_77994_a);
    }
    @Test void chargedReleaseKeepsThresholdAreaDamageWearAndLightning() {
        for(String id:List.of("right_range","light_range")) {
            World world=new World();Player player=player(world);Living target=victim(world);
            world.query=(excluded,box)->{assertSame(player,excluded);assertEquals(-5.3,box.minX(),1E-9);return List.of(target);};
            Stack sword=skill(id);
            sword.item.func_77615_a(sword,world,player,71981);assertTrue(world.commands.isEmpty());
            sword.item.func_77615_a(sword,world,player,71980);
            assertEquals(1,attacks(world).size());assertEquals(id.equals("right_range")?21F:31.5F,attacks(world).getFirst().amount(),1E-6);
            assertEquals(id.equals("right_range")?15:30,wear(world));
            assertEquals(id.equals("light_range")?1:0,world.commands.stream().filter(Spawn.class::isInstance).count());
        }
    }
    @Test void clientReleaseDoesNotQueueAuthoritativeAreaDamageOrLightning() {
        World world=new World();world.field_72995_K=true;Player player=player(world);world.query=(e,b)->List.of(victim(world));
        for(String id:List.of("right_range","light_range"))skill(id).item.func_77615_a(skill(id),world,player,71980);
        assertTrue(attacks(world).isEmpty());assertFalse(world.commands.stream().anyMatch(Spawn.class::isInstance));
    }
    @Test void hitCallbacksApplySourceAreaPoisonKnockbackAndSingleBaseWear() {
        World world=new World();Player player=player(world);Living target=victim(world);world.query=(e,b)->List.of(target);
        Stack sword=skill("range_attack");sword.item.func_77644_a(sword,target,player);
        assertEquals(10.5F,attacks(world).getFirst().amount(),1E-6);assertEquals(21,wear(world));
        world.commands.clear();sword=skill("fire_range");sword.item.func_77644_a(sword,target,player);
        assertTrue(attacks(world).isEmpty());assertEquals(1,wear(world));
        world.commands.clear();target.burning=true;sword.item.func_77644_a(sword,target,player);
        assertEquals(14.7F,attacks(world).getFirst().amount(),1E-5);assertEquals(16,wear(world));
        world.commands.clear();sword=skill("bad_gem");sword.item.func_77644_a(sword,target,player);
        assertEquals(Set.of(19,20),target.effects.keySet());assertEquals(200,target.effects.get(19).duration);assertEquals(1,target.effects.get(20).amplifier);
        world.commands.clear();player.look=new Vec(3,0,4);sword=skill("high_kick");sword.item.func_77644_a(sword,target,player);
        assertEquals(.6D,target.field_70159_w,1E-9);assertEquals(1D,target.field_70181_x,1E-9);assertEquals(.8D,target.field_70179_y,1E-9);assertEquals(6,wear(world));
    }
    @Test void chanceSkillNeverInventsGuaranteedDamageAndAlwaysKeepsSourceWear() {
        World world=new World();Player player=player(world);Living target=victim(world);Stack sword=skill("one_shot");
        for(int i=0;i<32;i++) {
            world.commands.clear();target.health=20;sword.item.func_77644_a(sword,target,player);
            assertTrue(target.health==20||target.health==15);assertEquals(31,wear(world));
        }
    }
    @Test void sourceWingRemovalFallAndDamageLifestealStayConditional() {
        World world=new World();Player player=player(world);player.equipment[3]=stack("wing01");player.field_70181_x=.42D;
        Event event=new Event();event.entityLiving=player;
        for(var hook:LegacyBehaviorRegistry.events("jump"))hook.program().run(event);
        assertEquals(.57D,player.field_70181_x,1E-9);
        for(var hook:LegacyBehaviorRegistry.events("fall"))hook.program().run(event);
        assertTrue(event.isCanceled());assertEquals(10,world.particles.size());
        player.equipment[3]=null;event=new Event();event.entityLiving=player;
        for(var hook:LegacyBehaviorRegistry.events("fall"))hook.program().run(event);assertFalse(event.isCanceled());
        Player attacker=player(world),victim=player(world);attacker.health=10;
        attacker.equipment[0]=stack("dark_sword");attacker.equipment[0].tag=new Tag(Map.of("RPGTOOL_DAMAGE",3,"RPGTOOL_LIFESTEAL",5));
        victim.equipment[3]=stack("wing01");victim.equipment[3].tag=new Tag(Map.of("RPGTOOL_DEFENSE",2));
        event=new Event();event.entityLiving=victim;event.source.attacker=attacker;event.ammount=10;
        for(var hook:LegacyBehaviorRegistry.events("hurt"))hook.program().run(event);
        assertEquals(11F,event.ammount,1E-6);assertEquals(10.55F,attacker.health,1E-5);
    }
    @Test void passiveEffectsRequireSelectionAndRespectRefreshThreshold() {
        for(var entry:Map.of("night_vision",16,"under_water",13).entrySet()) {
            World world=new World();Player player=player(world);Stack sword=skill(entry.getKey());
            sword.item.func_77663_a(sword,world,player,0,false);assertTrue(player.effects.isEmpty());
            sword.item.func_77663_a(sword,world,player,0,true);assertEquals(400,player.effects.get(entry.getValue()).duration);
            player.appliedEffects.clear();sword.item.func_77663_a(sword,world,player,0,true);assertTrue(player.appliedEffects.isEmpty());
            player.effects.put(entry.getValue(),new Effect(entry.getValue(),200,0));sword.item.func_77663_a(sword,world,player,0,true);
            assertEquals(400,player.effects.get(entry.getValue()).duration);
        }
    }
}
