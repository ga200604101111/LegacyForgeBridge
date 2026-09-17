package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyGridPotBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyGridPotNegativeBranchAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyGridPotVanillaInsertion1710;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegisteredBlockRenderTypeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Materializes proof-complete GridPot core plus source/platform-proven positive and negative insertion subsets. */
public final class LegacyGridPotBlockPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/grid-pot-block-rules.json";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-grid-pot-blocks";}

    @Override public void apply(ConversionContext context)throws Exception{
        LegacyGridPotBlockAnalyzer.Analysis analysis=new LegacyGridPotBlockAnalyzer().analyze(context.sourceJar());
        if(analysis.rules().isEmpty()&&analysis.skipped().isEmpty())return;
        Map<String,String> ids=generatedBlockIds(context.stagingDir());
        LegacyRegisteredBlockRenderTypeAnalyzer.Analysis renderTypes=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(context.sourceJar());
        LegacyGridPotNegativeBranchAnalyzer.Analysis negativeAnalysis=new LegacyGridPotNegativeBranchAnalyzer().analyze(context.sourceJar());
        Map<String,LegacyGridPotNegativeBranchAnalyzer.Proof> negativeProofs=new LinkedHashMap<>();
        for(var proof:negativeAnalysis.proofs())negativeProofs.putIfAbsent(proof.sourceBlockClass(),proof);

        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("sourceProvenModInsertionEligibilityWired",true);root.addProperty("sourceProvenSymbolicRenderIdentityMatchingWired",true);
        root.addProperty("platformVanilla1710InsertionEligibilityWired",true);root.addProperty("platformVanilla1710MetadataDemultiplexWired",true);
        root.addProperty("sourceProvenNegativeModRenderClassificationWired",true);root.addProperty("sourceNegativeInsertionBranchProofWired",true);
        JsonArray rules=new JsonArray();int coreRuntime=0,insertionBlocked=0,insertionSubset=0,modSubset=0,vanillaSubset=0,negativeClassified=0,negativeRuntimeRules=0,unmapped=0;
        for(LegacyGridPotBlockAnalyzer.Rule rule:analysis.rules()){
            String id=ids.get(rule.sourceBlockClass());if(id==null){unmapped++;continue;}
            JsonObject value=new JsonObject();
            value.addProperty("id",id);value.addProperty("sourceBlockClass",rule.sourceBlockClass());value.addProperty("sourceItemBlockClass",rule.sourceItemBlockClass());
            value.addProperty("sourceTileClass",rule.sourceTileClass());value.addProperty("legacyTileId",rule.legacyTileId());
            value.addProperty("cells",rule.cells());value.addProperty("gridWidth",rule.gridWidth());value.addProperty("baseHeight",rule.baseHeight());value.addProperty("cellHeight",rule.cellHeight());
            value.addProperty("placementCreatesCell",rule.placementCreatesCell());value.addProperty("emptyHandRemovalProven",rule.emptyHandRemovalProven());
            value.addProperty("selfItemAddsCellProven",rule.selfItemAddsCellProven());value.addProperty("breakDropsEveryEnabledCell",rule.breakDropsEveryEnabledCell());
            value.addProperty("normalBlockDropDisabled",rule.normalBlockDropDisabled());value.addProperty("persistenceProven",rule.persistenceProven());
            value.addProperty("dynamicCellShapeProven",rule.dynamicCellShapeProven());value.addProperty("nonOpaqueProven",rule.nonOpaqueProven());
            value.addProperty("legacyInsertionPredicateProven",rule.contentInsertionPredicateProven());
            JsonArray symbolic=new JsonArray();rule.contentInsertionSymbolicRenderFields().forEach(symbolic::add);value.add("sourceInsertionSymbolicRenderFields",symbolic);
            boolean core=rule.cells()==9&&rule.gridWidth()==3&&rule.placementCreatesCell()&&rule.emptyHandRemovalProven()&&rule.selfItemAddsCellProven()
                    &&rule.breakDropsEveryEnabledCell()&&rule.normalBlockDropDisabled()&&rule.persistenceProven()&&rule.dynamicCellShapeProven()&&rule.nonOpaqueProven();
            value.addProperty("coreRuntimeComplete",core);

            Set<String> symbolicSet=new LinkedHashSet<>(rule.contentInsertionSymbolicRenderFields());
            List<String> modEligibleIds=rule.contentInsertionPredicateProven()?eligibleGeneratedIds(renderTypes,ids,symbolicSet):List.of();
            List<String> modNegativeIds=rule.contentInsertionPredicateProven()?negativeGeneratedIds(renderTypes,ids,symbolicSet):List.of();
            List<String> vanillaEligibleIds=rule.contentInsertionPredicateProven()?eligibleVanilla1710Ids(Set.of(1,13,40)):List.of();
            TreeSet<String> combined=new TreeSet<>();combined.addAll(modEligibleIds);combined.addAll(vanillaEligibleIds);
            JsonArray modEligible=new JsonArray();modEligibleIds.forEach(modEligible::add);JsonArray vanillaEligible=new JsonArray();vanillaEligibleIds.forEach(vanillaEligible::add);JsonArray eligible=new JsonArray();combined.forEach(eligible::add);JsonArray negative=new JsonArray();modNegativeIds.forEach(negative::add);
            boolean positiveSubset=core&&rule.contentInsertionPredicateProven()&&!combined.isEmpty();boolean modWired=core&&rule.contentInsertionPredicateProven()&&!modEligibleIds.isEmpty();boolean vanillaWired=core&&rule.contentInsertionPredicateProven()&&!vanillaEligibleIds.isEmpty();
            var negativeProof=negativeProofs.get(rule.sourceBlockClass());boolean negativeBranchProven=negativeProof!=null&&negativeProof.negativeBranchProven();
            boolean negativeRuntime=core&&rule.contentInsertionPredicateProven()&&negativeBranchProven;
            value.add("sourceProvenModInsertionBlockIds",modEligible);value.addProperty("sourceProvenModInsertionBlockCount",modEligibleIds.size());
            value.add("platformVanilla1710InsertionBlockIds",vanillaEligible);value.addProperty("platformVanilla1710InsertionBlockCount",vanillaEligibleIds.size());
            value.addProperty("positiveContentInsertionSubsetWired",positiveSubset);value.addProperty("platformVanilla1710ContentInsertionWired",vanillaWired);
            value.addProperty("platformVanilla1710MetadataDemultiplexWired",vanillaWired&&!LegacyGridPotVanillaInsertion1710.variantFamilies().isEmpty());
            value.addProperty("sourceProvenModContentInsertionWired",positiveSubset);
            value.add("sourceProvenInsertionBlockIds",eligible);value.addProperty("sourceProvenInsertionBlockCount",combined.size());
            value.add("sourceProvenNegativeBlockIds",negative);value.addProperty("sourceProvenNegativeBlockCount",modNegativeIds.size());
            value.addProperty("sourceProvenNegativeRenderClassificationWired",core&&rule.contentInsertionPredicateProven());
            value.addProperty("sourceNegativeInsertionBranchProven",negativeBranchProven);
            JsonArray negativeBlockers=new JsonArray();if(negativeProof!=null)negativeProof.blockers().forEach(negativeBlockers::add);value.add("sourceNegativeInsertionBlockers",negativeBlockers);
            value.addProperty("negativeContentInsertionRuntimeWired",negativeRuntime);value.addProperty("negativeNonBlockItemRuntimeWired",negativeRuntime);
            value.addProperty("contentInsertionRuntimeComplete",false);value.addProperty("presentationRuntimeComplete",false);value.addProperty("runtimeComplete",false);
            if(core)coreRuntime++;if(rule.contentInsertionPredicateProven())insertionBlocked++;if(positiveSubset)insertionSubset++;if(modWired)modSubset++;if(vanillaWired)vanillaSubset++;negativeClassified+=modNegativeIds.size();if(negativeRuntime)negativeRuntimeRules++;rules.add(value);
        }
        root.add("rules",rules);JsonArray skipped=new JsonArray();for(LegacyGridPotBlockAnalyzer.Skipped item:analysis.skipped()){JsonObject value=new JsonObject();value.addProperty("registryName",item.registryName());value.addProperty("sourceBlockClass",item.sourceBlockClass());value.addProperty("reason",item.reason());skipped.add(value);}root.add("skipped",skipped);
        root.addProperty("coreRuntimeCompleteRules",coreRuntime);root.addProperty("positiveContentInsertionSubsetRules",insertionSubset);root.addProperty("sourceProvenModContentInsertionRules",modSubset);root.addProperty("platformVanilla1710InsertionRules",vanillaSubset);root.addProperty("sourceProvenNegativeModBlockIdentityCount",negativeClassified);root.addProperty("negativeContentInsertionRuntimeRules",negativeRuntimeRules);root.addProperty("legacyInsertionPredicateProvenButRuntimeClosedRules",insertionBlocked);root.addProperty("runtimeCompleteRules",0);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        LinkedHashSet<String> diagnostics=new LinkedHashSet<>(analysis.diagnostics());diagnostics.addAll(negativeAnalysis.diagnostics());diagnostics.forEach(message->context.diagnostics().warning("LFB-CONVERT-GRIDPOT-0002",SupportLevel.MANUAL_REQUIRED,message));
        if(unmapped>0)context.diagnostics().warning("LFB-CONVERT-GRIDPOT-0003",SupportLevel.MANUAL_REQUIRED,"Source-proven grid-pot blocks without generated block identity: "+unmapped+".");
        if(coreRuntime>0)context.diagnostics().info("LFB-CONVERT-GRIDPOT-0001",SupportLevel.ADAPTED,"Proof-complete grid-pot core runtimes: "+coreRuntime+"; positive insertion identities plus "+negativeClassified+" known-negative mod Block identity classification(s) materialized; source-proven negative interaction runtime wired for "+negativeRuntimeRules+" rule(s), while unknown BlockItems and presentation remain fail-closed.");
    }

    static List<String> eligibleGeneratedIds(LegacyRegisteredBlockRenderTypeAnalyzer.Analysis analysis,Map<String,String> generatedIds){return eligibleGeneratedIds(analysis,generatedIds,Set.of());}
    static List<String> eligibleGeneratedIds(LegacyRegisteredBlockRenderTypeAnalyzer.Analysis analysis,Map<String,String> generatedIds,Set<String> symbolicFields){return classifiedGeneratedIds(analysis,generatedIds,symbolicFields,true);}
    static List<String> negativeGeneratedIds(LegacyRegisteredBlockRenderTypeAnalyzer.Analysis analysis,Map<String,String> generatedIds,Set<String> symbolicFields){return classifiedGeneratedIds(analysis,generatedIds,symbolicFields,false);}
    private static List<String> classifiedGeneratedIds(LegacyRegisteredBlockRenderTypeAnalyzer.Analysis analysis,Map<String,String> generatedIds,Set<String> symbolicFields,boolean positive){List<String> output=new ArrayList<>();Set<String> symbols=symbolicFields==null?Set.of():Set.copyOf(symbolicFields);for(LegacyRegisteredBlockRenderTypeAnalyzer.Rule rule:analysis.rules()){var identity=rule.renderIdentity();boolean matches=identity.isConstant(1)||identity.isConstant(13)||identity.isConstant(40)||(identity.fieldOwner()!=null&&symbols.contains(identity.fieldOwner()+"#"+identity.fieldName()));if(matches!=positive)continue;String id=generatedIds.get(rule.sourceBlockClass());if(id!=null&&!output.contains(id))output.add(id);}output.sort(Comparator.naturalOrder());return List.copyOf(output);}
    static List<String> eligibleVanilla1710Ids(Set<Integer> provenRenderTypes){return LegacyGridPotVanillaInsertion1710.modernIdsForRenderTypes(provenRenderTypes);}
    private static Map<String,String> generatedBlockIds(Path staging)throws Exception{Path path=staging.resolve("legacyforgebridge/converted-content.json");if(!Files.isRegularFile(path))return Map.of();Map<String,String> result=new LinkedHashMap<>();try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonObject content=JsonParser.parseReader(reader).getAsJsonObject();JsonArray blocks=content.getAsJsonArray("blocks");if(blocks!=null)for(var element:blocks){if(!element.isJsonObject())continue;JsonObject block=element.getAsJsonObject();if(block.has("sourceClass")&&block.has("id"))result.put(block.get("sourceClass").getAsString(),block.get("id").getAsString());}}return result;}
}
