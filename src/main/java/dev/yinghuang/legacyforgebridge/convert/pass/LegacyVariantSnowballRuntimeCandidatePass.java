package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Admits source-complete metadata-indexed snowballs into a normalized modern runtime candidate IR. */
public final class LegacyVariantSnowballRuntimeCandidatePass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/variant-snowball-runtime-candidates.json";
    public static final int SCHEMA=2;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> POTIONS=Set.of("poison","confusion","regeneration");

    @Override public String id(){return "legacy-variant-snowball-runtime-candidates";}

    @Override public void apply(ConversionContext context)throws Exception{
        Path proofPath=context.stagingDir().resolve(LegacyVariantSnowballPass.OUTPUT);
        Path launchPath=context.stagingDir().resolve(LegacyVariantSnowballLaunchPass.OUTPUT);
        if(!Files.isRegularFile(proofPath))return;
        JsonObject proof=read(proofPath);JsonObject launch=Files.isRegularFile(launchPath)?read(launchPath):null;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",false);root.addProperty("projectileRuntimeWired",false);
        JsonArray rules=new JsonArray(),rejected=new JsonArray();root.add("rules",rules);root.add("rejected",rejected);

        if(integer(proof,"schemaVersion",0)!=9||!context.sourceHash().equals(string(proof,"sourceSha256",null))){
            reject(rejected,null,null,"variant-snowball-proof-schema-or-source-hash-mismatch");finish(context,root,rules,rejected);return;
        }
        boolean launchRootValid=launch!=null&&integer(launch,"schemaVersion",0)==LegacyVariantSnowballLaunchPass.SCHEMA&&context.sourceHash().equals(string(launch,"sourceSha256",null))&&!bool(launch,"runtimeImplementationWired");
        JsonObject content=readIfPresent(context.stagingDir().resolve("legacyforgebridge/converted-content.json"));
        JsonArray proofRules=proof.getAsJsonArray("rules");
        if(proofRules!=null)for(JsonElement element:proofRules){
            if(!element.isJsonObject())continue;JsonObject source=element.getAsJsonObject();String registry=string(source,"legacyRegistryName",null),itemClass=string(source,"sourceItemClass",null);
            String sourceProjectile=string(source,"sourceProjectileClass",null),selectorClass=string(source,"selectorClass",null);
            String blocker=admissionBlocker(source);
            JsonObject launchRule=blocker==null&&launchRootValid?uniqueLaunchRule(launch,registry,itemClass,sourceProjectile,selectorClass):null;
            if(blocker==null&&!launchRootValid)blocker="item-use-launch-proof-missing-or-stale";
            if(blocker==null&&launchRule==null)blocker="item-use-launch-family-not-uniquely-bound";
            if(blocker==null&&!bool(launchRule,"itemUseSemanticsComplete"))blocker="item-use-semantics-incomplete";
            if(blocker==null&&bool(launchRule,"runtimeImplementationWired"))blocker="launch-proof-unexpectedly-claims-runtime";
            JsonObject manifestItem=blocker==null?uniqueManifestItem(content,registry,itemClass):null;
            if(blocker==null&&manifestItem==null)blocker="converted-item-identity-not-uniquely-bound";
            String itemId=manifestItem==null?null:string(manifestItem,"id",null);
            if(blocker==null&&(itemId==null||!validModernId(itemId)))blocker="converted-item-id-invalid";
            JsonArray variants=source.getAsJsonArray("variants");JsonArray normalized=blocker==null?normalizeVariants(variants):null;
            if(blocker==null&&normalized==null)blocker="variant-runtime-ir-not-fully-normalizable";
            if(blocker!=null){reject(rejected,registry,itemClass,blocker);continue;}

            int split=itemId.indexOf(':');String projectileId=itemId.substring(0,split)+":"+itemId.substring(split+1)+"_projectile";
            JsonObject rule=new JsonObject();rule.addProperty("id",itemId);rule.addProperty("projectileId",projectileId);
            rule.addProperty("legacyRegistryName",registry);rule.addProperty("sourceItemClass",itemClass);rule.addProperty("sourceProjectileClass",sourceProjectile);rule.addProperty("selectorClass",selectorClass);
            rule.addProperty("adapter","VARIANT_SNOWBALL");rule.addProperty("metadataRoutingComplete",true);rule.addProperty("itemUseSemanticsComplete",true);rule.addProperty("impactSemanticsComplete",true);rule.addProperty("sourceSemanticsComplete",true);
            rule.addProperty("rendererAdapter","THROWN_ITEM");rule.addProperty("runtimeCandidateReady",true);rule.addProperty("runtimeImplementationWired",false);
            rule.addProperty("launchSound","random.bow");rule.addProperty("launchVolume",0.5F);rule.addProperty("launchPitchNumerator",0.4F);rule.addProperty("launchPitchRandomScale",0.4F);rule.addProperty("launchPitchBase",0.8F);rule.addProperty("consumeOutsideCreative",true);rule.addProperty("serverAuthoritativeLaunch",true);
            rule.add("variants",normalized);rule.addProperty("variantCount",normalized.size());rules.add(rule);
        }
        finish(context,root,rules,rejected);
    }

    private static String admissionBlocker(JsonObject source){
        if(!bool(source,"metadataSelectorLookupProven")||!bool(source,"metadataSelectorBindingProven"))return "metadata-routing-not-complete";
        if(!bool(source,"projectileSelectorStorageProven")||!bool(source,"selectorEnumConstantsProven"))return "selector-projectile-binding-not-complete";
        if(!bool(source,"commonImpactSemanticsProven"))return "common-impact-semantics-incomplete";
        if(!bool(source,"selectorEffectDispatchProven")||!bool(source,"selectorSpecificImpactSemanticsComplete")||!bool(source,"impactSemanticsComplete"))return "source-impact-semantics-incomplete";
        if(bool(source,"runtimeImplementationWired"))return "proof-sidecar-unexpectedly-claims-runtime";
        if(string(source,"sourceItemClass",null)==null||string(source,"sourceProjectileClass",null)==null||string(source,"selectorClass",null)==null)return "source-family-identity-incomplete";
        JsonArray variants=source.getAsJsonArray("variants");if(variants==null||variants.isEmpty()||integer(source,"variantCount",-1)!=variants.size())return "variant-set-incomplete";
        return null;
    }

    private static JsonArray normalizeVariants(JsonArray variants){
        if(variants==null||variants.isEmpty())return null;JsonArray out=new JsonArray();Set<Integer> metas=new HashSet<>();
        for(JsonElement element:variants){
            if(!element.isJsonObject())return null;JsonObject source=element.getAsJsonObject();
            if(!source.has("legacyMeta")||!source.has("baseDamage"))return null;int meta=integer(source,"legacyMeta",-1),damage=integer(source,"baseDamage",-1);
            if(meta<0||damage<0||damage>127||!metas.add(meta))return null;String effect=string(source,"impactEffect",null);if(effect==null)return null;
            JsonObject value=new JsonObject();value.addProperty("metadata",meta);value.addProperty("baseDamage",damage);
            switch(effect){
                case "NONE" -> value.addProperty("effect","NONE");
                case "POTION" -> {
                    String potion=string(source,"potion",null);int duration=integer(source,"duration",-1),amplifier=integer(source,"amplifier",-1);
                    if(!POTIONS.contains(potion)||duration<0||amplifier<0)return null;value.addProperty("effect","POTION");value.addProperty("potion",potion);value.addProperty("duration",duration);value.addProperty("amplifier",amplifier);
                }
                case "RANDOM_TELEPORT_SEMANTICS_PROVEN" -> {
                    if(!exactDouble(source,"horizontalRandomRadius",16D)||integer(source,"verticalRandomRadius",-1)!=4||integer(source,"portalParticleCount",-1)!=128
                            ||!"mob.endermen.portal".equals(string(source,"portalSound",null))||!bool(source,"savedPositionProven")||!bool(source,"candidateAssignmentProven")
                            ||!bool(source,"guardedRollbackFalseProven")||!bool(source,"successTrueReturnProven")||!bool(source,"flooredCoordinatesProven")||!bool(source,"blockExistsGateProven")
                            ||!bool(source,"downwardGroundSearchProven")||!bool(source,"groundGuardedRepositionProven")||!bool(source,"collisionEmptyGateProven")||!bool(source,"nonLiquidGateProven")
                            ||!bool(source,"successFlagBindingProven")||!bool(source,"portalParticleLoopProven")||!bool(source,"portalInterpolationProven")||!bool(source,"portalRandomizationProven")
                            ||!bool(source,"portalOriginSoundProven")||!bool(source,"portalEntitySoundProven")||!bool(source,"teleportPresentationComplete"))return null;
                    value.addProperty("effect","RANDOM_TELEPORT");value.addProperty("horizontalRandomRadius",16D);value.addProperty("verticalRandomBound",8);value.addProperty("verticalRandomOffset",-4);
                    value.addProperty("portalParticleCount",128);value.addProperty("portalSound","mob.endermen.portal");
                }
                default -> {return null;}
            }
            out.add(value);
        }
        return out;
    }

    private static JsonObject uniqueLaunchRule(JsonObject launch,String registry,String itemClass,String projectile,String selector){
        JsonArray rules=launch.getAsJsonArray("rules");if(rules==null)return null;JsonObject found=null;
        for(JsonElement element:rules){if(!element.isJsonObject())continue;JsonObject rule=element.getAsJsonObject();if(!Objects.equals(registry,string(rule,"legacyRegistryName",null))||!Objects.equals(itemClass,string(rule,"sourceItemClass",null))||!Objects.equals(projectile,string(rule,"sourceProjectileClass",null))||!Objects.equals(selector,string(rule,"selectorClass",null)))continue;if(found!=null)return null;found=rule;}
        return found;
    }
    private static JsonObject uniqueManifestItem(JsonObject content,String registry,String itemClass){
        if(content==null)return null;JsonArray items=content.getAsJsonArray("items");if(items==null)return null;JsonObject found=null;
        for(JsonElement element:items){if(!element.isJsonObject())continue;JsonObject item=element.getAsJsonObject();if(!Objects.equals(registry,string(item,"legacyRegistryName",null))||!Objects.equals(itemClass,string(item,"sourceClass",null)))continue;if(found!=null)return null;found=item;}
        return found;
    }

    private static boolean validModernId(String id){int colon=id.indexOf(':');return colon>0&&colon==id.lastIndexOf(':')&&colon<id.length()-1&&id.substring(0,colon).matches("[a-z0-9_.-]+")&&id.substring(colon+1).matches("[a-z0-9/._-]+");}
    private static void reject(JsonArray rejected,String registry,String itemClass,String reason){JsonObject value=new JsonObject();if(registry!=null)value.addProperty("legacyRegistryName",registry);if(itemClass!=null)value.addProperty("sourceItemClass",itemClass);value.addProperty("reason",reason);rejected.add(value);}
    private static void finish(ConversionContext context,JsonObject root,JsonArray rules,JsonArray rejected)throws Exception{
        root.addProperty("runtimeCandidateFamilies",rules.size());root.addProperty("rejectedFamilies",rejected.size());Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!rules.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0001",SupportLevel.RUNTIME_BRIDGE,"Variant snowball launch+impact semantics admitted to normalized runtime candidates: "+rules.size()+"; modern projectile/item runtime is not wired by this pass.");
        if(!rejected.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0002",SupportLevel.RUNTIME_BRIDGE,"Variant snowball families rejected from runtime candidacy: "+rejected.size()+".");
    }
    private static JsonObject readIfPresent(Path path)throws Exception{return Files.isRegularFile(path)?read(path):null;}
    private static JsonObject read(Path path)throws Exception{try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}}
    private static boolean bool(JsonObject o,String k){JsonElement v=o==null?null:o.get(k);return v!=null&&v.isJsonPrimitive()&&v.getAsBoolean();}
    private static int integer(JsonObject o,String k,int f){JsonElement v=o==null?null:o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsInt():f;}
    private static String string(JsonObject o,String k,String f){JsonElement v=o==null?null:o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsString():f;}
    private static boolean exactDouble(JsonObject o,String k,double expected){JsonElement v=o==null?null:o.get(k);return v!=null&&v.isJsonPrimitive()&&Double.compare(v.getAsDouble(),expected)==0;}
}
