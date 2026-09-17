package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballImpactAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballMapBindingAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballSelectorEffectAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",5);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",false);root.addProperty("impactCompilerWired",false);
        JsonArray rules=new JsonArray();int boundFamilies=0,commonImpactFamilies=0,effectDispatchFamilies=0,selectorCompleteFamilies=0;
        for(var rule:analysis.rules()){
            var binding=bindings.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())
                    &&p.mapOwner().equals(rule.selectorMapOwner())&&p.mapField().equals(rule.selectorMapField())).findFirst().orElse(null);
            boolean bindingProven=binding!=null&&binding.bindingProven();if(bindingProven)boundFamilies++;
            var impact=impacts.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())
                    &&p.projectileClass().equals(rule.projectileClass())&&p.selectorClass().equals(rule.selectorClass())).findFirst().orElse(null);
            boolean commonImpact=impact!=null&&impact.commonImpactSemanticsProven();if(commonImpact)commonImpactFamilies++;
            var effects=selectorEffects.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())
                    &&p.projectileClass().equals(rule.projectileClass())&&p.selectorClass().equals(rule.selectorClass())).findFirst().orElse(null);
            boolean effectDispatch=effects!=null&&effects.selectorDispatchProven()&&effects.selectorEffectEdgesProven();if(effectDispatch)effectDispatchFamilies++;
            boolean selectorComplete=effectDispatch&&effects.selectorSpecificImpactSemanticsComplete();if(selectorComplete)selectorCompleteFamilies++;
            boolean impactComplete=commonImpact&&selectorComplete;

            JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceItemClass",rule.itemClass());
            value.addProperty("sourceProjectileClass",rule.projectileClass());value.addProperty("selectorClass",rule.selectorClass());
            value.addProperty("selectorMapOwner",rule.selectorMapOwner());value.addProperty("selectorMapField",rule.selectorMapField());
            value.addProperty("selectorIdGetter",rule.selectorIdGetter());value.addProperty("selectorDamageGetter",rule.selectorDamageGetter());
            value.addProperty("impactMethod",rule.impactMethod());value.addProperty("impactDescriptor",rule.impactDescriptor());
            value.addProperty("metadataSelectorLookupProven",true);value.addProperty("metadataSelectorBindingProven",bindingProven);
            if(!bindingProven&&binding!=null&&binding.blocker()!=null)value.addProperty("metadataSelectorBindingBlocker",binding.blocker());
            value.addProperty("projectileSelectorStorageProven",true);value.addProperty("selectorEnumConstantsProven",true);
            value.addProperty("selectorNullImpactGuardProven",impact!=null&&impact.selectorNullGuardProven());
            value.addProperty("selectorBaseDamageAttackProven",impact!=null&&impact.selectorBaseDamageAttackProven());
            value.addProperty("snowballPoofLoopProven",impact!=null&&impact.snowballPoofLoopProven());
            value.addProperty("serverTerminationProven",impact!=null&&impact.serverTerminationProven());
            value.addProperty("commonImpactSemanticsProven",commonImpact);
            if(impact!=null&&!commonImpact&&!impact.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:impact.blockers())blockers.add(blocker);value.add("commonImpactBlockers",blockers);}
            value.addProperty("selectorEffectDispatchProven",effectDispatch);
            value.addProperty("selectorPotionEffectBranchCount",effects==null?0:effects.potionBranchesProven());
            if(effects!=null&&!effectDispatch&&!effects.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:effects.blockers())blockers.add(blocker);value.add("selectorEffectBlockers",blockers);}
            value.addProperty("selectorSpecificImpactSemanticsComplete",selectorComplete);value.addProperty("impactSemanticsComplete",impactComplete);value.addProperty("runtimeImplementationWired",false);

            JsonArray variants=new JsonArray();for(var variant:rule.variants()){
                JsonObject entry=new JsonObject();entry.addProperty("enumField",variant.enumField());entry.addProperty("selectorId",variant.id());if(bindingProven)entry.addProperty("legacyMeta",variant.id());entry.addProperty("baseDamage",variant.damage());
                var effect=effects==null?null:effects.effects().stream().filter(e->e.enumField().equals(variant.enumField())&&e.selectorId()==variant.id()).findFirst().orElse(null);
                if(effect==null){entry.addProperty("impactEffect","UNCOMPILED");}
                else{
                    entry.addProperty("impactEffect",effect.impactEffect());
                    if(effect.potion()!=null){entry.addProperty("potion",effect.potion());entry.addProperty("duration",effect.duration());entry.addProperty("amplifier",effect.amplifier());}
                    if(effect.helperMethod()!=null)entry.addProperty("sourceImpactHelper",effect.helperMethod());
                }
                variants.add(entry);
            }value.add("variants",variants);value.addProperty("variantCount",variants.size());rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",item.registryName());if(item.itemClass()!=null)value.addProperty("sourceItemClass",item.itemClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);
        root.addProperty("proofCompleteFamilies",rules.size());root.addProperty("metadataBindingFamilies",boundFamilies);root.addProperty("commonImpactFamilies",commonImpactFamilies);root.addProperty("selectorEffectDispatchFamilies",effectDispatchFamilies);root.addProperty("selectorSpecificCompleteFamilies",selectorCompleteFamilies);root.addProperty("skippedFamilies",skipped.size());
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0003",SupportLevel.MANUAL_REQUIRED,diagnostic);
        if(!rules.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0001",SupportLevel.RUNTIME_BRIDGE,
                "Proved metadata-indexed custom snowball family/families: "+rules.size()+"; source map binding="+boundFamilies+", common impact shell="+commonImpactFamilies+", selector effect dispatch="+effectDispatchFamilies+", selector-complete="+selectorCompleteFamilies+"; runtime generation remains intentionally closed.");
        if(boundFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0004",SupportLevel.RUNTIME_BRIDGE,
                "Variant snowball families with metadata lookup but without bounded metadata-to-selector map binding: "+(rules.size()-boundFamilies)+".");
        if(commonImpactFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0005",SupportLevel.RUNTIME_BRIDGE,
                "Variant snowball families without the complete bounded selector-independent impact shell: "+(rules.size()-commonImpactFamilies)+".");
        if(effectDispatchFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0006",SupportLevel.RUNTIME_BRIDGE,
                "Variant snowball families without bounded selector-specific switch dispatch/effect edges: "+(rules.size()-effectDispatchFamilies)+".");
        if(!skipped.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0002",SupportLevel.RUNTIME_BRIDGE,
                "Custom ItemSnowball families still outside the bounded projectile proof: "+skipped.size()+".");
    }
}
