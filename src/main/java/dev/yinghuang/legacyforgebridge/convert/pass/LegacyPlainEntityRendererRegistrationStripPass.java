package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityRendererConstructionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityRendererRegistrationStripper;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strips only proven no-op renderer registrations whose no-arg constructor chain is side-effect-free. */
public final class LegacyPlainEntityRendererRegistrationStripPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-renderer-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plain-entity-renderer-registration-strip"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path runtimePath=context.stagingDir().resolve(LegacyPlainEntityRuntimePass.OUTPUT);
        Path presentationPath=context.stagingDir().resolve(LegacyEntityPresentationPass.OUTPUT);
        if(!Files.isRegularFile(runtimePath)||!Files.isRegularFile(presentationPath))return;
        JsonObject runtime=read(runtimePath),presentation=read(presentationPath);
        if(!valid(runtime,context.sourceHash())||!valid(presentation,context.sourceHash()))return;

        Map<String,JsonObject> presentationBySource=new LinkedHashMap<>();
        for(JsonElement element:array(presentation,"rules")){
            if(!element.isJsonObject())continue;JsonObject rule=element.getAsJsonObject();String sourceClass=string(rule,"sourceClass",null);
            if(sourceClass!=null)presentationBySource.putIfAbsent(sourceClass,rule);
        }

        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("rendererConstructionProofWired",true);root.addProperty("sourceRendererRegistrationStripWired",true);root.addProperty("rendererClassDeletionWired",false);
        JsonArray rules=new JsonArray();int evaluated=0,complete=0,sites=0;
        LegacyEntityRendererConstructionAnalyzer construction=new LegacyEntityRendererConstructionAnalyzer();
        LegacyEntityRendererRegistrationStripper stripper=new LegacyEntityRendererRegistrationStripper();

        for(JsonElement element:array(runtime,"rules")){
            if(!element.isJsonObject())continue;JsonObject runtimeRule=element.getAsJsonObject();if(!bool(runtimeRule,"runtimeComplete",false))continue;
            String id=string(runtimeRule,"id",null),sourceClass=string(runtimeRule,"sourceClass",null);if(sourceClass==null)continue;evaluated++;
            JsonObject value=new JsonObject();if(id!=null)value.addProperty("id",id);value.addProperty("sourceClass",sourceClass);JsonArray blockers=new JsonArray();
            JsonObject presentationRule=presentationBySource.get(sourceClass);String rendererClass=null,sourceOwner=null,sourceMethod=null,sourceDescriptor=null;
            LegacyEntityRendererConstructionAnalyzer.Proof proof=null;
            if(presentationRule==null||!bool(presentationRule,"sourceNoOpRendererProven",false))blockers.add("source-noop-renderer-proof-missing");
            else{
                JsonArray registrations=array(presentationRule,"registrations");
                if(registrations.size()!=1||!registrations.get(0).isJsonObject())blockers.add("ambiguous-source-renderer-registration");
                else{
                    JsonObject registration=registrations.get(0).getAsJsonObject();
                    rendererClass=string(registration,"rendererClass",null);sourceOwner=string(registration,"sourceOwner",null);sourceMethod=string(registration,"sourceMethod",null);sourceDescriptor=string(registration,"sourceDescriptor",null);
                    if(rendererClass==null||sourceOwner==null||sourceMethod==null||sourceDescriptor==null)blockers.add("renderer-registration-source-identity-missing");
                    else if(!bool(registration,"rendererClassPresent",false)||!bool(registration,"noOpRenderProven",false))blockers.add("renderer-registration-not-proven-noop");
                    else{
                        proof=construction.prove(context.sourceJar(),rendererClass);
                        if(!proof.proven())blockers.add("renderer-constructor-chain-not-proven-side-effect-free:"+proof.reason());
                    }
                }
            }

            int stripped=0;
            if(blockers.isEmpty()){
                Path classPath=context.stagingDir().resolve(sourceOwner+".class");
                if(!Files.isRegularFile(classPath))blockers.add("renderer-registration-source-class-missing");
                else{
                    var target=new LegacyEntityRendererRegistrationStripper.Target(sourceMethod,sourceDescriptor,sourceClass,rendererClass);
                    var result=stripper.strip(Files.readAllBytes(classPath),target);stripped=result.strippedSites();
                    for(String blocker:result.blockers())blockers.add(blocker);
                    if(stripped==1)Files.write(classPath,result.bytes());
                }
            }

            if(rendererClass!=null)value.addProperty("rendererClass",rendererClass);if(sourceOwner!=null)value.addProperty("sourceOwner",sourceOwner);if(sourceMethod!=null)value.addProperty("sourceMethod",sourceMethod);if(sourceDescriptor!=null)value.addProperty("sourceDescriptor",sourceDescriptor);
            if(proof!=null){value.addProperty("rendererConstructorChainSideEffectFree",proof.proven());value.addProperty("rendererConstructorProofReason",proof.reason());if(proof.terminalBaseClass()!=null)value.addProperty("rendererConstructorTerminalBase",proof.terminalBaseClass());JsonArray chain=new JsonArray();proof.sourceConstructorChain().forEach(chain::add);value.add("rendererSourceConstructorChain",chain);}
            boolean done=stripped==1&&blockers.isEmpty();value.addProperty("rendererRegistrationStripComplete",done);value.addProperty("strippedSites",stripped);value.add("blockers",blockers);rules.add(value);
            if(done){complete++;sites+=stripped;}
        }

        root.add("rules",rules);root.addProperty("evaluatedRuntimeRules",evaluated);root.addProperty("rendererRegistrationStripCompleteRules",complete);root.addProperty("strippedRendererRegistrationSites",sites);root.addProperty("blockedRendererRegistrationStripRules",evaluated-complete);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
        if(complete>0)context.diagnostics().info("LFB-CONVERT-ENTITY-RENDERSTRIP-0001",SupportLevel.ADAPTED,
                "Removed "+sites+" exact legacy no-op renderer registration callsite(s) after proving side-effect-free no-arg constructor chains for "+complete+" runtime-complete plain Entity rule(s).");
        if(complete<evaluated)context.diagnostics().warning("LFB-CONVERT-ENTITY-RENDERSTRIP-0002",SupportLevel.RUNTIME_BRIDGE,
                "Left "+(evaluated-complete)+" plain Entity renderer registration rule(s) untouched because no-op presentation, constructor purity, source identity, or exact expression shape remained unproven.");
    }

    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static boolean valid(JsonObject root,String hash){return integer(root,"schemaVersion",-1)==1&&hash.equals(string(root,"sourceSha256",""));}
    private static JsonArray array(JsonObject root,String name){JsonElement value=root.get(name);return value!=null&&value.isJsonArray()?value.getAsJsonArray():new JsonArray();}
    private static boolean bool(JsonObject root,String name,boolean fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsBoolean():fallback;}
    private static int integer(JsonObject root,String name,int fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsInt():fallback;}
    private static String string(JsonObject root,String name,String fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsString():fallback;}
}
