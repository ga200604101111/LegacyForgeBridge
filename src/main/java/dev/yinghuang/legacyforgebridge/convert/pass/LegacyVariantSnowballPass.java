package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballMapBindingAnalyzer;
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
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",3);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("runtimeImplementationWired",false);root.addProperty("impactCompilerWired",false);
        JsonArray rules=new JsonArray();int boundFamilies=0;
        for(var rule:analysis.rules()){
            var binding=bindings.proofs().stream().filter(p->p.registryName().equals(rule.registryName())&&p.itemClass().equals(rule.itemClass())
                    &&p.mapOwner().equals(rule.selectorMapOwner())&&p.mapField().equals(rule.selectorMapField())).findFirst().orElse(null);
            boolean bindingProven=binding!=null&&binding.bindingProven();if(bindingProven)boundFamilies++;
            JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",rule.registryName());value.addProperty("sourceItemClass",rule.itemClass());
            value.addProperty("sourceProjectileClass",rule.projectileClass());value.addProperty("selectorClass",rule.selectorClass());
            value.addProperty("selectorMapOwner",rule.selectorMapOwner());value.addProperty("selectorMapField",rule.selectorMapField());
            value.addProperty("selectorIdGetter",rule.selectorIdGetter());value.addProperty("selectorDamageGetter",rule.selectorDamageGetter());
            value.addProperty("impactMethod",rule.impactMethod());value.addProperty("impactDescriptor",rule.impactDescriptor());
            value.addProperty("metadataSelectorLookupProven",true);value.addProperty("metadataSelectorBindingProven",bindingProven);
            if(!bindingProven&&binding!=null&&binding.blocker()!=null)value.addProperty("metadataSelectorBindingBlocker",binding.blocker());
            value.addProperty("projectileSelectorStorageProven",true);value.addProperty("selectorEnumConstantsProven",true);
            value.addProperty("impactSemanticsComplete",false);value.addProperty("runtimeImplementationWired",false);
            JsonArray variants=new JsonArray();for(var variant:rule.variants()){JsonObject entry=new JsonObject();entry.addProperty("enumField",variant.enumField());entry.addProperty("selectorId",variant.id());if(bindingProven)entry.addProperty("legacyMeta",variant.id());entry.addProperty("baseDamage",variant.damage());entry.addProperty("impactEffect","UNCOMPILED");variants.add(entry);}value.add("variants",variants);value.addProperty("variantCount",variants.size());rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(var item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("legacyRegistryName",item.registryName());if(item.itemClass()!=null)value.addProperty("sourceItemClass",item.itemClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);
        root.addProperty("proofCompleteFamilies",rules.size());root.addProperty("metadataBindingFamilies",boundFamilies);root.addProperty("skippedFamilies",skipped.size());
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0003",SupportLevel.MANUAL_REQUIRED,diagnostic);
        if(!rules.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0001",SupportLevel.RUNTIME_BRIDGE,
                "Proved metadata-indexed custom snowball lookup shape for family/families: "+rules.size()+"; source map binding is proven for "+boundFamilies+", while custom impact semantics and runtime generation remain intentionally closed.");
        if(boundFamilies<rules.size())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0004",SupportLevel.RUNTIME_BRIDGE,
                "Variant snowball families with metadata lookup but without bounded metadata-to-selector map binding: "+(rules.size()-boundFamilies)+".");
        if(!skipped.isEmpty())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-0002",SupportLevel.RUNTIME_BRIDGE,
                "Custom ItemSnowball families still outside the bounded projectile proof: "+skipped.size()+".");
    }
}
