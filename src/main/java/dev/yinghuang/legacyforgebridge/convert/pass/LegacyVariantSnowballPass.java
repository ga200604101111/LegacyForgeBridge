package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballImpactAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballMapBindingAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballSelectorEffectAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballTeleportPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballTeleportSafetyAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballTeleportStateAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballTeleportWrapperAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Materializes proof IR for metadata-indexed custom ItemSnowball/EntitySnowball families. */
public final class LegacyVariantSnowballPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/variant-snowball-proof.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-variant-snowball-proof";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyVariantSnowballAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        var bindings=new LegacyVariantSnowballMapBindingAnalyzer().analyze(context.sourceJar());
        var impacts=new LegacyVariantSnowballImpactAnalyzer().analyze(context.sourceJar());
        var selectorEffects=new LegacyVariantSnowballSelectorEffectAnalyzer().analyze(context.sourceJar());
        var teleports=new LegacyVariantSnowballTeleportWrapperAnalyzer().analyze(context.sourceJar());
        var teleportStates=new LegacyVariantSnowballTeleportStateAnalyzer().analyze(context.sourceJar());
        var teleportSafety=new LegacyVariantSnowballTeleportSafetyAnalyzer().analyze(context.sourceJar());
        var teleportPresentation=new LegacyVariantSnowballTeleportPresentationAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",9);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",false);root.addProperty("impactCompilerWired",false);
        JsonArray rules=new JsonArray();int boundFamilies=0,commonImpactFamilies=0,effectDispatchFamilies=0,selectorCompleteFamilies=0,teleportWrapperFamilies=0,teleportStateFamilies=0,teleportSafetyFamilies=0,teleportPresentationFamilies=0;
        for(var rule:analysis.rules()){
            var binding=bindings.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())&&p.mapOwner().equals(rule.selectorMapOwner())&&p.mapField().equals(rule.selectorMapField())).findFirst().orElse(null);
            boolean bindingProven=binding!=null&&binding.bindingProven();if(bindingProven)boundFamilies++;
            var impact=impacts.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())&&p.projectileClass().equals(rule.projectileClass())&&p.selectorClass().equals(rule.selectorClass())).findFirst().orElse(null);
            boolean commonImpact=impact!=null&&impact.commonImpactSemanticsProven();if(commonImpact)commonImpactFamilies++;
            var effects=selectorEffects.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())&&p.projectileClass().equals(rule.projectileClass())&&p.selectorClass().equals(rule.selectorClass())).findFirst().orElse(null);
            boolean effectDispatch=effects!=null&&effects.selectorDispatchProven()&&effects.selectorEffectEdgesProven();if(effectDispatch)effectDispatchFamilies++;
            var familyTeleports=teleports.proofs().stream().filter(p->sameFamily(p.registryName(),p.itemClass(),p.projectileClass(),p.selectorClass(),rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass())).toList();
            boolean teleportWrapper=familyTeleports.stream().anyMatch(LegacyVariantSnowballTeleportWrapperAnalyzer.Proof::wrapperProven);if(teleportWrapper)teleportWrapperFamilies++;
            var familyStates=teleportStates.proofs().stream().filter(p->sameFamily(p.registryName(),p.itemClass(),p.projectileClass(),p.selectorClass(),rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass())).toList();
            boolean teleportState=familyStates.stream().anyMatch(LegacyVariantSnowballTeleportStateAnalyzer.Proof::stateSkeletonProven);if(teleportState)teleportStateFamilies++;
            var familySafety=teleportSafety.proofs().stream().filter(p->sameFamily(p.registryName(),p.itemClass(),p.projectileClass(),p.selectorClass(),rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass())).toList();
            boolean teleportGameplaySafety=familySafety.stream().anyMatch(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof::gameplaySafetyCoreProven);if(teleportGameplaySafety)teleportSafetyFamilies++;
            var familyPresentation=teleportPresentation.proofs().stream().filter(p->sameFamily(p.registryName(),p.itemClass(),p.projectileClass(),p.selectorClass(),rule.registryName(),rule.itemClass(),rule.projectileClass(),rule.selectorClass())).toList();
            boolean presentationProven=familyPresentation.stream().anyMatch(LegacyVariantSnowballTeleportPresentationAnalyzer.Proof::presentationProven);if(presentationProven)teleportPresentationFamilies++;
            boolean selectorComplete=selectorEffectsComplete(effects,familyPresentation);if(selectorComplete)selectorCompleteFamilies++;
            boolean impactComplete=commonImpact&&selectorComplete;

            JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceItemClass",rule.itemClass());value.addProperty("sourceProjectileClass",rule.projectileClass());value.addProperty("selectorClass",rule.selectorClass());
            value.addProperty("selectorMapOwner",rule.selectorMapOwner());value.addProperty("selectorMapField",rule.selectorMapField());value.addProperty("selectorIdGetter",rule.selectorIdGetter());value.addProperty("selectorDamageGetter",rule.selectorDamageGetter());value.addProperty("impactMethod",rule.impactMethod());value.addProperty("impactDescriptor",rule.impactDescriptor());
            value.addProperty("metadataSelectorLookupProven",true);value.addProperty("metadataSelectorBindingProven",bindingProven);if(!bindingProven&&binding!=null&&binding.blocker()!=null)value.addProperty("metadataSelectorBindingBlocker",binding.blocker());
            value.addProperty("projectileSelectorStorageProven",true);value.addProperty("selectorEnumConstantsProven",true);
            value.addProperty("selectorNullImpactGuardProven",impact!=null&&impact.selectorNullGuardProven());value.addProperty("selectorBaseDamageAttackProven",impact!=null&&impact.selectorBaseDamageAttackProven());value.addProperty("snowballPoofLoopProven",impact!=null&&impact.snowballPoofLoopProven());value.addProperty("serverTerminationProven",impact!=null&&impact.serverTerminationProven());value.addProperty("commonImpactSemanticsProven",commonImpact);
            if(impact!=null&&!commonImpact&&!impact.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:impact.blockers())blockers.add(blocker);value.add("commonImpactBlockers",blockers);}
            value.addProperty("selectorEffectDispatchProven",effectDispatch);value.addProperty("selectorPotionEffectBranchCount",effects==null?0:effects.potionBranchesProven());if(effects!=null&&!effectDispatch&&!effects.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:effects.blockers())blockers.add(blocker);value.add("selectorEffectBlockers",blockers);}
            value.addProperty("randomTeleportWrapperProven",teleportWrapper);value.addProperty("randomTeleportWrapperCount",familyTeleports.stream().filter(LegacyVariantSnowballTeleportWrapperAnalyzer.Proof::wrapperProven).count());
            value.addProperty("teleportStateSkeletonProven",teleportState);value.addProperty("teleportStateSkeletonCount",familyStates.stream().filter(LegacyVariantSnowballTeleportStateAnalyzer.Proof::stateSkeletonProven).count());
            value.addProperty("teleportGameplaySafetyProven",teleportGameplaySafety);value.addProperty("teleportGameplaySafetyCount",familySafety.stream().filter(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof::gameplaySafetyCoreProven).count());
            value.addProperty("teleportPresentationProven",presentationProven);value.addProperty("teleportPresentationCount",familyPresentation.stream().filter(LegacyVariantSnowballTeleportPresentationAnalyzer.Proof::presentationProven).count());
            value.addProperty("selectorSpecificImpactSemanticsComplete",selectorComplete);value.addProperty("impactSemanticsComplete",impactComplete);value.addProperty("runtimeImplementationWired",false);

            JsonArray variants=new JsonArray();for(var variant:rule.variants()){
                JsonObject entry=new JsonObject();entry.addProperty("enumField",variant.enumField());entry.addProperty("selectorId",variant.id());if(bindingProven)entry.addProperty("legacyMeta",variant.id());entry.addProperty("baseDamage",variant.damage());
                var effect=effects==null?null:effects.effects().stream().filter(e->e.enumField().equals(variant.enumField())&&e.selectorId()==variant.id()).findFirst().orElse(null);
                var teleport=familyTeleports.stream().filter(p->p.enumField().equals(variant.enumField())&&p.selectorId()==variant.id()).findFirst().orElse(null);
                var state=familyStates.stream().filter(p->p.enumField().equals(variant.enumField())&&p.selectorId()==variant.id()).findFirst().orElse(null);
                var safety=familySafety.stream().filter(p->p.enumField().equals(variant.enumField())&&p.selectorId()==variant.id()).findFirst().orElse(null);
                var presentation=familyPresentation.stream().filter(p->p.enumField().equals(variant.enumField())&&p.selectorId()==variant.id()).findFirst().orElse(null);
                if(effect==null){entry.addProperty("impactEffect","UNCOMPILED");}
                else if(presentation!=null&&presentation.presentationProven()){
                    entry.addProperty("impactEffect","RANDOM_TELEPORT_SEMANTICS_PROVEN");entry.addProperty("sourceImpactHelper",teleport.wrapperMethod());entry.addProperty("sourceTeleportHelper",presentation.teleportMethod());entry.addProperty("horizontalRandomRadius",16.0D);entry.addProperty("verticalRandomRadius",4);
                    entry.addProperty("savedPositionProven",true);entry.addProperty("candidateAssignmentProven",true);entry.addProperty("guardedRollbackFalseProven",true);entry.addProperty("successTrueReturnProven",true);entry.addProperty("flooredCoordinatesProven",true);entry.addProperty("blockExistsGateProven",true);entry.addProperty("downwardGroundSearchProven",true);entry.addProperty("groundGuardedRepositionProven",true);entry.addProperty("collisionEmptyGateProven",true);entry.addProperty("nonLiquidGateProven",true);entry.addProperty("successFlagBindingProven",true);
                    entry.addProperty("portalParticleCount",128);entry.addProperty("portalParticleLoopProven",true);entry.addProperty("portalInterpolationProven",true);entry.addProperty("portalRandomizationProven",true);entry.addProperty("portalSound","mob.endermen.portal");entry.addProperty("portalOriginSoundProven",true);entry.addProperty("portalEntitySoundProven",true);entry.addProperty("teleportPresentationComplete",true);
                }else if(safety!=null&&safety.gameplaySafetyCoreProven()){
                    entry.addProperty("impactEffect","RANDOM_TELEPORT_GAMEPLAY_SAFETY_PROVEN");entry.addProperty("sourceImpactHelper",teleport.wrapperMethod());entry.addProperty("sourceTeleportHelper",safety.teleportMethod());entry.addProperty("horizontalRandomRadius",16.0D);entry.addProperty("verticalRandomRadius",4);entry.addProperty("savedPositionProven",true);entry.addProperty("candidateAssignmentProven",true);entry.addProperty("guardedRollbackFalseProven",true);entry.addProperty("successTrueReturnProven",true);entry.addProperty("flooredCoordinatesProven",true);entry.addProperty("blockExistsGateProven",true);entry.addProperty("downwardGroundSearchProven",true);entry.addProperty("groundGuardedRepositionProven",true);entry.addProperty("collisionEmptyGateProven",true);entry.addProperty("nonLiquidGateProven",true);entry.addProperty("successFlagBindingProven",true);entry.addProperty("teleportPresentationComplete",false);if(presentation!=null&&!presentation.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:presentation.blockers())blockers.add(blocker);entry.add("teleportPresentationBlockers",blockers);}
                }else if(state!=null&&state.stateSkeletonProven()){
                    entry.addProperty("impactEffect","RANDOM_TELEPORT_STATE_SKELETON_PROVEN");entry.addProperty("sourceImpactHelper",teleport.wrapperMethod());entry.addProperty("sourceTeleportHelper",state.teleportMethod());entry.addProperty("horizontalRandomRadius",16.0D);entry.addProperty("verticalRandomRadius",4);entry.addProperty("savedPositionProven",true);entry.addProperty("candidateAssignmentProven",true);entry.addProperty("guardedRollbackFalseProven",true);entry.addProperty("successTrueReturnProven",true);if(safety!=null&&!safety.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:safety.blockers())blockers.add(blocker);entry.add("teleportSafetyBlockers",blockers);}
                }else if(teleport!=null&&teleport.wrapperProven()){
                    entry.addProperty("impactEffect","RANDOM_TELEPORT_WRAPPER_PROVEN");entry.addProperty("sourceImpactHelper",teleport.wrapperMethod());entry.addProperty("sourceTeleportHelper",teleport.teleportMethod());entry.addProperty("horizontalRandomRadius",16.0D);entry.addProperty("verticalRandomRadius",4);if(state!=null&&!state.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:state.blockers())blockers.add(blocker);entry.add("teleportStateBlockers",blockers);}
                }else{
                    entry.addProperty("impactEffect",effect.impactEffect());if(effect.potion()!=null){entry.addProperty("potion",effect.potion());entry.addProperty("duration",effect.duration());entry.addProperty("amplifier",effect.amplifier());}if(effect.helperMethod()!=null)entry.addProperty("sourceImpactHelper",effect.helperMethod());if(teleport!=null&&!teleport.wrapperProven()&&!teleport.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:teleport.blockers())blockers.add(blocker);entry.add("teleportWrapperBlockers",blockers);}
                }
                variants.add(entry);
            }value.add("variants",variants);value.addProperty("variantCount",variants.size());rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",item.registryName());if(item.itemClass()!=null)value.addProperty("sourceItemClass",item.itemClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);
        root.addProperty("proofCompleteFamilies",rules.size());root.addProperty("metadataBindingFamilies",boundFamilies);root.addProperty("commonImpactFamilies",commonImpactFamilies);root.addProperty("selectorEffectDispatchFamilies",effectDispatchFamilies);root.addProperty("selectorSpecificCompleteFamilies",selectorCompleteFamilies);root.addProperty("randomTeleportWrapperFamilies",teleportWrapperFamilies);root.addProperty("teleportStateSkeletonFamilies",teleportStateFamilies);root.addProperty("teleportGameplaySafetyFamilies",teleportSafetyFamilies);root.addProperty("teleportPresentationFamilies",teleportPresentationFamilies);root.addProperty("skippedFamilies",skipped.size());
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0003",SupportLevel.MANUAL_REQUIRED,diagnostic);
        if(!rules.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0001",SupportLevel.RUNTIME_BRIDGE,"Proved metadata-indexed custom snowball family/families: "+rules.size()+"; source map binding="+boundFamilies+", common impact shell="+commonImpactFamilies+", selector effect dispatch="+effectDispatchFamilies+", random teleport wrapper="+teleportWrapperFamilies+", teleport state skeleton="+teleportStateFamilies+", teleport gameplay safety="+teleportSafetyFamilies+", teleport presentation="+teleportPresentationFamilies+", selector-complete="+selectorCompleteFamilies+"; runtime generation remains intentionally closed.");
        if(boundFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0004",SupportLevel.RUNTIME_BRIDGE,"Variant snowball families with metadata lookup but without bounded metadata-to-selector map binding: "+(rules.size()-boundFamilies)+".");
        if(commonImpactFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0005",SupportLevel.RUNTIME_BRIDGE,"Variant snowball families without the complete bounded selector-independent impact shell: "+(rules.size()-commonImpactFamilies)+".");
        if(effectDispatchFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0006",SupportLevel.RUNTIME_BRIDGE,"Variant snowball families without bounded selector-specific switch dispatch/effect edges: "+(rules.size()-effectDispatchFamilies)+".");
        if(!teleports.proofs().isEmpty()&&teleports.proofs().stream().anyMatch(p->!p.wrapperProven()))context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0007",SupportLevel.RUNTIME_BRIDGE,"One or more source-mapped custom snowball impact helpers are not the bounded random-teleport wrapper family.");
        if(!familyStatesAreComplete(teleportStates))context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0008",SupportLevel.RUNTIME_BRIDGE,"One or more proven random-teleport wrappers do not yet have the bounded target-state save/candidate/rollback skeleton.");
        if(!familySafetyIsComplete(teleportStates,teleportSafety))context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0009",SupportLevel.RUNTIME_BRIDGE,"One or more proven random-teleport state skeletons do not yet have the bounded ground/collision/liquid gameplay-safety core.");
        if(!familyPresentationIsComplete(teleportSafety,teleportPresentation))context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0010",SupportLevel.RUNTIME_BRIDGE,"One or more proven random-teleport gameplay cores do not yet have the bounded 128-particle/two-sound portal presentation.");
        if(!skipped.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0002",SupportLevel.RUNTIME_BRIDGE,"Custom ItemSnowball families still outside the bounded projectile proof: "+skipped.size()+".");
    }

    private static boolean sameFamily(String ar,String ai,String ap,String as,String br,String bi,String bp,String bs){return ar.equals(br)&&ai.equals(bi)&&ap.equals(bp)&&as.equals(bs);}
    private static boolean selectorEffectsComplete(LegacyVariantSnowballSelectorEffectAnalyzer.Proof effects,List<LegacyVariantSnowballTeleportPresentationAnalyzer.Proof> presentation){
        if(effects==null||!effects.selectorDispatchProven()||!effects.selectorEffectEdgesProven())return false;
        for(var effect:effects.effects()){
            if("UNCOMPILED".equals(effect.impactEffect()))return false;
            if("CUSTOM_HELPER_UNCOMPILED".equals(effect.impactEffect())){
                boolean proven=presentation.stream().anyMatch(p->p.enumField().equals(effect.enumField())&&p.selectorId()==effect.selectorId()&&p.presentationProven());
                if(!proven)return false;
            }
        }
        return true;
    }
    private static boolean familyStatesAreComplete(LegacyVariantSnowballTeleportStateAnalyzer.Analysis states){return states.proofs().isEmpty()||states.proofs().stream().allMatch(LegacyVariantSnowballTeleportStateAnalyzer.Proof::stateSkeletonProven);}
    private static boolean familySafetyIsComplete(LegacyVariantSnowballTeleportStateAnalyzer.Analysis states,LegacyVariantSnowballTeleportSafetyAnalyzer.Analysis safety){long expected=states.proofs().stream().filter(LegacyVariantSnowballTeleportStateAnalyzer.Proof::stateSkeletonProven).count();long proven=safety.proofs().stream().filter(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof::gameplaySafetyCoreProven).count();return expected==0||proven==expected;}
    private static boolean familyPresentationIsComplete(LegacyVariantSnowballTeleportSafetyAnalyzer.Analysis safety,LegacyVariantSnowballTeleportPresentationAnalyzer.Analysis presentation){long expected=safety.proofs().stream().filter(LegacyVariantSnowballTeleportSafetyAnalyzer.Proof::gameplaySafetyCoreProven).count();long proven=presentation.proofs().stream().filter(LegacyVariantSnowballTeleportPresentationAnalyzer.Proof::presentationProven).count();return expected==0||proven==expected;}
}
