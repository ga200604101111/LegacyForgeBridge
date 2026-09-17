package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyVariantSnowballLaunchAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Materializes the bounded item-use launch shell independently from projectile impact semantics. */
public final class LegacyVariantSnowballLaunchPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/variant-snowball-launch-proof.json";
    public static final int SCHEMA=1;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

    @Override public String id(){return "legacy-variant-snowball-launch-proof";}

    @Override public void apply(ConversionContext context)throws Exception{
        var analysis=new LegacyVariantSnowballLaunchAnalyzer().analyze(context.sourceJar());
        if(analysis.proofs().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA);root.addProperty("sourceSha256",context.sourceHash());root.addProperty("runtimeImplementationWired",false);JsonArray rules=new JsonArray();int complete=0;
        for(var proof:analysis.proofs()){
            JsonObject rule=new JsonObject();rule.addProperty("legacyRegistryName",proof.registryName());rule.addProperty("sourceItemClass",proof.itemClass());rule.addProperty("sourceProjectileClass",proof.projectileClass());rule.addProperty("selectorClass",proof.selectorClass());
            rule.addProperty("creativeConsumptionGuardProven",proof.creativeConsumptionGuardProven());rule.addProperty("serverOnlyLaunchGateProven",proof.serverOnlyLaunchGateProven());rule.addProperty("legacyBowSoundProven",proof.legacyBowSoundProven());rule.addProperty("metadataProjectileSpawnInsideGateProven",proof.metadataProjectileSpawnInsideGateProven());rule.addProperty("originalStackReturnProven",proof.originalStackReturnProven());rule.addProperty("itemUseSemanticsComplete",proof.itemUseSemanticsComplete());rule.addProperty("runtimeImplementationWired",false);
            if(!proof.blockers().isEmpty()){JsonArray blockers=new JsonArray();for(String blocker:proof.blockers())blockers.add(blocker);rule.add("blockers",blockers);}if(proof.itemUseSemanticsComplete())complete++;rules.add(rule);
        }
        root.add("rules",rules);root.addProperty("launchProofFamilies",rules.size());root.addProperty("launchCompleteFamilies",complete);Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        for(String diagnostic:analysis.diagnostics())context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-LAUNCH-0002",SupportLevel.MANUAL_REQUIRED,diagnostic);
        context.diagnostics().warning("LFB-CONVERT-VARIANT-SNOWBALL-LAUNCH-0001",SupportLevel.RUNTIME_BRIDGE,"Variant snowball item-use launch proofs: families="+rules.size()+", complete="+complete+"; runtime remains intentionally closed.");
    }
}
