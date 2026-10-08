package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyFixedModelProjectilePreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyModelBoxMesh1710;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Independent source-only fixed cuboid projectile evidence; no new renderer/EntityType is wired. */
public final class LegacyProjectileFixedModelPreflightPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/projectile-fixed-model-preflight.json";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-projectile-fixed-model-preflight";}
    @Override public void apply(ConversionContext context){
        try {
            var proof=new LegacyFixedModelProjectilePreflight().analyze(context.sourceJar());
            if(proof.candidates().isEmpty()&&proof.skipped().isEmpty())return;
            JsonObject result=manifest(context.sourceHash(),context.metadata().primary().modId(),proof);
            Path out=context.stagingDir().resolve(OUTPUT);
            Files.createDirectories(out.getParent());
            Files.writeString(out,JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
            context.diagnostics().info("LFB-REV290-PROJECTILE-0001",SupportLevel.AUTO,
                    "Source-only fixed projectile mesh proofs="+proof.candidates().size()
                            +"; no new runtime renderer, launcher or FML spawn adapter is enabled.");
        }catch(Exception unproved){
            context.diagnostics().warning("LFB-REV290-PROJECTILE-0002",SupportLevel.AUTO,
                    "Optional fixed projectile model audit unavailable ("
                            +unproved.getClass().getSimpleName()+"); active conversion remains unchanged.");
        }
    }
    public static JsonObject manifest(String sourceSha, String modId,
                                  LegacyFixedModelProjectilePreflight.Analysis proof){
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);
        root.addProperty("sourceSha256",sourceSha);root.addProperty("legacyModId",modId);
        root.addProperty("family","FIXED_CUBOID_PROJECTILE_SOURCE_PREFLIGHT");
        root.addProperty("sourceOnly",true);root.addProperty("runtimeWired",false);
        root.addProperty("launcherDataflowProven",false);
        root.addProperty("clientFullbrightRuntimeWired",false);
        root.addProperty("fmlSpawnRuntimeProven",false);
        int launch=0,fullbright=0;
        JsonArray candidates=new JsonArray();
        for(var candidate:proof.candidates()){
            var geom=candidate.geometry();
            var mesh=LegacyModelBoxMesh1710.build(geom);
            JsonObject row=new JsonObject();
            row.addProperty("legacyRegistryName",candidate.registryName());
            row.addProperty("sourceClass",candidate.entityClass());
            row.addProperty("sourceRendererClass",candidate.rendererClass());
            row.addProperty("sourceModelClass",geom.modelClass());
            row.addProperty("sourceTexture",mesh.texture());
            row.addProperty("atlasWidth",mesh.atlasWidth());row.addProperty("atlasHeight",mesh.atlasHeight());
            row.addProperty("modelBoxFaceCount",mesh.quads().size());
            row.addProperty("modelBoxVertexCount",mesh.vertexCount());
            row.addProperty("modelBoxTriangleCount",mesh.triangleCount());
            row.addProperty("legacyScale",geom.scale());row.addProperty("legacyRotationAngle",geom.angle());
            row.addProperty("legacyRotationAxisX",geom.axisX());
            row.addProperty("legacyRotationAxisY",geom.axisY());
            row.addProperty("legacyRotationAxisZ",geom.axisZ());
            row.addProperty("sourceAxisAngleBakedIntoMesh",mesh.sourceAxisAngleBaked());
            row.addProperty("sourceLauncherDataflowProven",candidate.launcherProof().isPresent());
            candidate.launcherProof().ifPresent(item->{
                row.addProperty("sourceLauncherRegistryName",item.registryName());
                row.addProperty("sourceLauncherItemClass",item.sourceItemClass());
                row.addProperty("sourceLauncherCallback",item.callbackName());
                row.addProperty("sourceLauncherCallbackKind",item.callback().name());
            });
            if(candidate.launcherProof().isPresent())launch++;
            row.addProperty("sourceConstantFullbright1710Proven",candidate.fullbrightProof().isPresent());
            candidate.fullbrightProof().ifPresent(light->{
                row.addProperty("sourceLegacyBrightness",light.brightness());
                row.addProperty("sourceLegacyPackedLight",light.packedLight());
            });
            if(candidate.fullbrightProof().isPresent())fullbright++;
            JsonArray cuboids=new JsonArray();
            for(var box:geom.cuboids()){
                JsonObject part=new JsonObject();
                part.addProperty("name",box.name());part.addProperty("u",box.u());part.addProperty("v",box.v());
                part.addProperty("x",box.x());part.addProperty("y",box.y());part.addProperty("z",box.z());
                part.addProperty("width",box.width());part.addProperty("height",box.height());part.addProperty("depth",box.depth());
                part.addProperty("pivotX",box.pivotX());part.addProperty("pivotY",box.pivotY());part.addProperty("pivotZ",box.pivotZ());
                cuboids.add(part);
            }
            row.add("sourceCuboids",cuboids);
            row.addProperty("runtimeReady",false);
            candidates.add(row);
        }
        root.add("candidates",candidates);
        root.addProperty("provenSourceGeometryCandidateCount",candidates.size());
        root.addProperty("provenSourceLauncherCount",launch);
        root.addProperty("provenSourceFullbrightCount",fullbright);
        JsonArray skipped=new JsonArray();
        for(var rejected:proof.skipped()){
            JsonObject entry=new JsonObject();
            if(rejected.registryName()!=null)entry.addProperty("registryName",rejected.registryName());
            if(rejected.entityClass()!=null)entry.addProperty("entityClass",rejected.entityClass());
            entry.addProperty("reason",rejected.reason());skipped.add(entry);
        }
        root.add("skipped",skipped);
        return root;
    }
}
