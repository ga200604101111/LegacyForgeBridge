package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySingleInputProcessorPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyProcessorBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime catalogue for source-proven three-slot single-input processors. */
public final class LegacySingleInputProcessorRegistry {
    private static final Map<Identifier, Rule> RULES=new ConcurrentHashMap<>();
    private static final Map<Identifier, BlockEntityType<ConvertedLegacyProcessorBlockEntity>> TYPES=new ConcurrentHashMap<>();
    private static final java.util.Set<Identifier> WORLD_PRESENTATION=ConcurrentHashMap.newKeySet();
    private LegacySingleInputProcessorRegistry() { }

    public record LegacyInput(String kind,String namespace,String registryName,int count,int meta,boolean wildcardMeta){
        public LegacyInput{
            if(kind==null||namespace==null||registryName==null||count<=0)throw new IllegalArgumentException("Invalid processor legacy input");
        }
        public String registryId(){return namespace+":"+registryName;}
        public boolean matchesLegacy(String id,int damage){return id!=null&&registryId().equalsIgnoreCase(id)&&(wildcardMeta||meta==damage);}
    }
    public record Recipe(LegacyInput legacyInput,List<JsonObject> inputAlternatives,JsonObject output,JsonObject bonus,float bonusChance){
        public Recipe{
            inputAlternatives=inputAlternatives.stream().map(JsonObject::deepCopy).toList();
            output=output.deepCopy();bonus=bonus==null?null:bonus.deepCopy();
            if(inputAlternatives.isEmpty()||bonusChance<0F||!Float.isFinite(bonusChance))throw new IllegalArgumentException("Invalid processor recipe");
        }
    }
    public record ParticleRule(float blockVelocityMultiplier,float blockScale,int itemParticleCount,double itemVelocityYOffset){
        public ParticleRule{
            if(!Float.isFinite(blockVelocityMultiplier)||blockVelocityMultiplier<=0F||!Float.isFinite(blockScale)||blockScale<=0F
                    ||itemParticleCount<1||itemParticleCount>16||!Double.isFinite(itemVelocityYOffset)||itemVelocityYOffset<0D)
                throw new IllegalArgumentException("Invalid processor particle rule");
        }
    }
    public record BlockConstruction(float destroyTime,float explosionResistance,String soundType,String mapColor,int lightLevel){
        public BlockConstruction{
            if(!Float.isFinite(destroyTime)||destroyTime<0F
                    ||!Float.isFinite(explosionResistance)||explosionResistance<0F
                    ||soundType==null||soundType.isBlank()||mapColor==null||mapColor.isBlank()
                    ||lightLevel<0||lightLevel>15)
                throw new IllegalArgumentException("Invalid processor block construction rule");
        }
    }
    public record Rule(Identifier id,int slots,int stackLimit,int inputSlot,int[] outputSlots,int[] topSlots,int[] bottomSlots,int[] sideSlots,
                       int processTicks,double interactionDistanceSq,boolean comparator,boolean dropContents,
                       boolean legacyEnergyApiPresent,int minUseEnergy,int maxUseEnergy,String energyNbtKey,
                       boolean energyIngressRuntimeComplete,boolean metadataDrivesRoll,ParticleRule particle,BlockConstruction blockConstruction,List<Recipe> recipes){
        public Rule{
            outputSlots=outputSlots.clone();topSlots=topSlots.clone();bottomSlots=bottomSlots.clone();sideSlots=sideSlots.clone();
            recipes=List.copyOf(recipes);energyNbtKey=energyNbtKey==null?"":energyNbtKey;
            if(id==null||slots!=3||stackLimit<1||stackLimit>64||inputSlot<0||inputSlot>=slots||processTicks<=0
                    ||interactionDistanceSq<=0||interactionDistanceSq>4096||recipes.isEmpty())throw new IllegalArgumentException("Invalid processor rule");
            if(legacyEnergyApiPresent&&(minUseEnergy<=0||maxUseEnergy<minUseEnergy||energyNbtKey.isBlank()))throw new IllegalArgumentException("Incomplete processor energy rule");
            if(energyIngressRuntimeComplete&&!legacyEnergyApiPresent)throw new IllegalArgumentException("Energy ingress cannot be enabled without a legacy energy contract");
        }
        @Override public int[] outputSlots(){return outputSlots.clone();}
        @Override public int[] topSlots(){return topSlots.clone();}
        @Override public int[] bottomSlots(){return bottomSlots.clone();}
        @Override public int[] sideSlots(){return sideSlots.clone();}
    }
    public record ResolvedRecipe(int index,LegacyInput legacyInput,ItemStack input,ItemStack output,ItemStack bonus,float bonusChance){public int requiredInputCount(){return input.getCount();}}

    public static void loadMod(String modId){
        ModContainer container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;
        var path=container.findPath(LegacySingleInputProcessorPass.OUTPUT);if(path.isEmpty())return;
        try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();JsonArray machines=root.getAsJsonArray("machines");if(machines==null)return;int loaded=0;
            for(JsonElement element:machines){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();
                if(!bool(value,"runtimeProofComplete")||!bool(value,"baseRuntimeComplete")||integer(value,"materializedRecipeCount",-1)!=integer(value,"sourceRecipeCount",-2))continue;
                Rule rule=parse(value);if(rule==null||!rule.id().getNamespace().equals(modId))continue;
                Rule previous=RULES.putIfAbsent(rule.id(),rule);if(previous!=null&&!sameRule(previous,rule))throw new IllegalStateException("Conflicting converted processor rule for "+rule.id());
                if(bool(value,"worldPresentationRuntimeComplete"))WORLD_PRESENTATION.add(rule.id());loaded++;
            }
            if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded converted processor runtime rules: mod={}, machines={}",modId,loaded);
        }catch(Exception exception){LegacyForgeBridge.LOGGER.error("Failed to load converted processor rules for {}",modId,exception);}
    }

    public static boolean hasRule(Identifier id){return id!=null&&RULES.containsKey(id);}
    public static boolean hasWorldPresentation(Identifier id){return id!=null&&WORLD_PRESENTATION.contains(id);}
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}
    public static Rule requireRule(Block block){Identifier id=BuiltInRegistries.BLOCK.getKey(block);Rule rule=RULES.get(id);if(rule==null)throw new IllegalStateException("No converted processor rule for "+id);return rule;}
    public static int presentationKey(Identifier id){if(id==null)return 0;int value=id.toString().hashCode();return value==0?1:value;}
    public static BlockBehaviour.Properties applyBlockProperties(Identifier id,BlockBehaviour.Properties properties){
        Rule rule=RULES.get(id);if(rule==null||rule.blockConstruction()==null)return properties;
        BlockConstruction source=rule.blockConstruction();
        properties.destroyTime(source.destroyTime()).explosionResistance(source.explosionResistance());
        properties.sound(switch(source.soundType()){
            case "STONE"->SoundType.STONE;
            case "WOOD"->SoundType.WOOD;
            case "GRAVEL"->SoundType.GRAVEL;
            case "GRASS"->SoundType.GRASS;
            case "METAL"->SoundType.METAL;
            case "GLASS"->SoundType.GLASS;
            case "WOOL"->SoundType.WOOL;
            case "SAND"->SoundType.SAND;
            case "SNOW"->SoundType.SNOW;
            case "LADDER"->SoundType.LADDER;
            case "ANVIL"->SoundType.ANVIL;
            default->throw new IllegalStateException("Unsupported converted processor sound type "+source.soundType()+" for "+id);
        });
        properties.mapColor(switch(source.mapColor()){
            case "STONE"->MapColor.STONE;
            default->throw new IllegalStateException("Unsupported converted processor map color "+source.mapColor()+" for "+id);
        });
        if(source.lightLevel()>0){int light=source.lightLevel();properties.lightLevel(state->light);}
        return properties;
    }

    public static synchronized void registerType(Identifier id,Block block){
        Rule rule=RULES.get(id);if(rule==null)return;
        BlockEntityType<ConvertedLegacyProcessorBlockEntity> type=TYPES.get(id);
        if(type==null){
            if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)){
                @SuppressWarnings("unchecked") BlockEntityType<ConvertedLegacyProcessorBlockEntity> existing=(BlockEntityType<ConvertedLegacyProcessorBlockEntity>)BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);type=existing;
            }else{
                type=FabricBlockEntityTypeBuilder.create(ConvertedLegacyProcessorBlockEntity::new,block).build();
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,type);
            }
            TYPES.put(id,type);
        }
        LegacyProcessorEnergyCompat.register(rule,type);
    }
    public static BlockEntityType<ConvertedLegacyProcessorBlockEntity> type(Identifier id){return id==null?null:TYPES.get(id);}
    public static BlockEntityType<ConvertedLegacyProcessorBlockEntity> requireType(Block block){Identifier id=BuiltInRegistries.BLOCK.getKey(block);BlockEntityType<ConvertedLegacyProcessorBlockEntity> type=TYPES.get(id);if(type==null)throw new IllegalStateException("No converted processor BlockEntityType for "+id);return type;}

    public static ResolvedRecipe findInput(Rule rule,ItemStack stack,HolderLookup.Provider registries){
        if(rule==null||stack==null||stack.isEmpty()||registries==null)return null;
        for(int index=0;index<rule.recipes().size();index++){Recipe recipe=rule.recipes().get(index);for(JsonObject value:recipe.inputAlternatives()){
            ItemStack input=decode(value,registries);if(input.isEmpty()||stack.getCount()<input.getCount())continue;
            if(ItemStack.isSameItemSameComponents(stack,input)){ResolvedRecipe resolved=resolve(rule,index,registries);if(resolved!=null)return resolved;}
        }}return null;
    }
    public static ResolvedRecipe resolveActive(Rule rule,int storedIndex,String legacyName,int legacyMeta,HolderLookup.Provider registries){
        if(rule==null||registries==null)return null;if(storedIndex>=0&&storedIndex<rule.recipes().size())return resolve(rule,storedIndex,registries);
        for(int index=0;index<rule.recipes().size();index++)if(rule.recipes().get(index).legacyInput().matchesLegacy(legacyName,legacyMeta))return resolve(rule,index,registries);return null;
    }
    private static ResolvedRecipe resolve(Rule rule,int index,HolderLookup.Provider registries){
        Recipe recipe=rule.recipes().get(index);ItemStack input=ItemStack.EMPTY;for(JsonObject alternative:recipe.inputAlternatives()){input=decode(alternative,registries);if(!input.isEmpty())break;}
        ItemStack output=decode(recipe.output(),registries);ItemStack bonus=recipe.bonus()==null?ItemStack.EMPTY:decode(recipe.bonus(),registries);
        if(input.isEmpty()||output.isEmpty()||recipe.bonus()!=null&&bonus.isEmpty())return null;return new ResolvedRecipe(index,recipe.legacyInput(),input,output,bonus,recipe.bonusChance());
    }
    private static ItemStack decode(JsonObject json,HolderLookup.Provider registries){try{return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,registries),json).result().orElse(ItemStack.EMPTY);}catch(RuntimeException invalid){return ItemStack.EMPTY;}}

    private static Rule parse(JsonObject value){
        try{
            Identifier id=Identifier.parse(required(value,"id"));JsonArray recipeValues=value.getAsJsonArray("recipes");if(recipeValues==null||recipeValues.isEmpty())return null;List<Recipe> recipes=new ArrayList<>();
            for(JsonElement element:recipeValues){if(!element.isJsonObject())return null;Recipe recipe=parseRecipe(element.getAsJsonObject());if(recipe==null)return null;recipes.add(recipe);}
            boolean metadataRoll=false;JsonObject presentation=value.has("presentation")&&value.get("presentation").isJsonObject()?value.getAsJsonObject("presentation"):null;
            if(bool(value,"presentationProofComplete")&&presentation!=null)metadataRoll=bool(presentation,"metadataDrivesRoll");
            ParticleRule particle=null;JsonObject particleValue=value.has("particle")&&value.get("particle").isJsonObject()?value.getAsJsonObject("particle"):null;
            if(bool(value,"particlePresentationRuntimeComplete")&&particleValue!=null)particle=new ParticleRule((float)decimal(particleValue,"blockVelocityMultiplier",0D),(float)decimal(particleValue,"blockScale",0D),integer(particleValue,"itemParticleCount",0),decimal(particleValue,"itemVelocityYOffset",-1D));
            boolean legacyEnergy=bool(value,"legacyEnergyApiPresent");
            boolean ingress=legacyEnergy&&bool(value,"energyIngressRuntimeComplete");
            BlockConstruction blockConstruction=null;JsonObject construction=value.has("blockConstruction")&&value.get("blockConstruction").isJsonObject()?value.getAsJsonObject("blockConstruction"):null;
            if(bool(value,"blockConstructorReplacementProven")&&bool(value,"blockConstructionRuntimeWired")&&construction!=null)
                blockConstruction=new BlockConstruction((float)decimal(construction,"destroyTime",-1D),(float)decimal(construction,"explosionResistance",-1D),string(construction,"soundType",""),string(construction,"mapColor",""),integer(construction,"lightLevel",-1));
            return new Rule(id,integer(value,"slots",0),integer(value,"stackLimit",0),integer(value,"inputSlot",-1),ints(value.getAsJsonArray("outputSlots")),ints(value.getAsJsonArray("topSlots")),ints(value.getAsJsonArray("bottomSlots")),ints(value.getAsJsonArray("sideSlots")),integer(value,"processTicks",0),decimal(value,"interactionDistanceSq",0),bool(value,"comparator"),bool(value,"dropContents"),legacyEnergy,integer(value,"minUseEnergy",0),integer(value,"maxUseEnergy",0),string(value,"energyNbtKey",""),ingress,metadataRoll,particle,blockConstruction,recipes);
        }catch(RuntimeException invalid){return null;}
    }
    private static Recipe parseRecipe(JsonObject value){
        JsonObject source=value.has("legacyInput")&&value.get("legacyInput").isJsonObject()?value.getAsJsonObject("legacyInput"):null;JsonArray alternatives=value.getAsJsonArray("inputAlternatives");JsonObject output=value.has("output")&&value.get("output").isJsonObject()?value.getAsJsonObject("output"):null;
        if(source==null||alternatives==null||alternatives.isEmpty()||output==null)return null;LegacyInput legacy=new LegacyInput(required(source,"kind"),required(source,"namespace"),required(source,"registryName"),integer(source,"count",0),integer(source,"meta",0),bool(source,"wildcardMeta"));
        List<JsonObject> inputs=new ArrayList<>();for(JsonElement element:alternatives){if(!element.isJsonObject())return null;inputs.add(element.getAsJsonObject().deepCopy());}
        JsonObject bonus=value.has("bonus")&&value.get("bonus").isJsonObject()?value.getAsJsonObject("bonus"):null;return new Recipe(legacy,inputs,output,bonus,value.has("bonusChance")?value.get("bonusChance").getAsFloat():0F);
    }
    private static boolean sameRule(Rule left,Rule right){return left.id().equals(right.id())&&left.processTicks()==right.processTicks()&&left.energyIngressRuntimeComplete()==right.energyIngressRuntimeComplete()&&left.metadataDrivesRoll()==right.metadataDrivesRoll()&&Objects.equals(left.particle(),right.particle())&&Objects.equals(left.blockConstruction(),right.blockConstruction())&&left.recipes().size()==right.recipes().size();}
    private static int[] ints(JsonArray array){if(array==null)return new int[0];int[] values=new int[array.size()];for(int i=0;i<values.length;i++)values[i]=array.get(i).getAsInt();return values;}
    private static String required(JsonObject object,String key){String value=string(object,key,null);if(value==null)throw new IllegalArgumentException("Missing "+key);return value;}
    private static String string(JsonObject object,String key,String fallback){JsonElement value=object.get(key);return value!=null&&value.isJsonPrimitive()?value.getAsString():fallback;}
    private static int integer(JsonObject object,String key,int fallback){JsonElement value=object.get(key);return value!=null&&value.isJsonPrimitive()?value.getAsInt():fallback;}
    private static double decimal(JsonObject object,String key,double fallback){JsonElement value=object.get(key);return value!=null&&value.isJsonPrimitive()?value.getAsDouble():fallback;}
    private static boolean bool(JsonObject object,String key){JsonElement value=object.get(key);return value!=null&&value.isJsonPrimitive()&&value.getAsBoolean();}
    static synchronized void clearForTests(){RULES.clear();TYPES.clear();WORLD_PRESENTATION.clear();}
}
