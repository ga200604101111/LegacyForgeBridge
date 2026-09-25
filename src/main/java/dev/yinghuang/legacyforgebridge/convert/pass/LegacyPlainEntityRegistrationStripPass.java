package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityRegistrationStripper;
import dev.yinghuang.legacyforgebridge.convert.LegacyLifecycleAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Strips only exact pure legacy registerModEntity callsites already replaced by a complete runtime rule. */
public final class LegacyPlainEntityRegistrationStripPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record RuntimeRule(String id, String sourceClass, String registryName, int numericId,
                               int trackingRange, int updateFrequency, boolean velocityUpdates) { }

    @Override public String id() { return "legacy-plain-entity-registration-strip"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path runtimePath=context.stagingDir().resolve(LegacyPlainEntityRuntimePass.OUTPUT);
        if(!Files.isRegularFile(runtimePath))return;
        JsonObject runtime=JsonParser.parseString(Files.readString(runtimePath,StandardCharsets.UTF_8)).getAsJsonObject();
        if(integer(runtime,"schemaVersion",-1)!=1||!context.sourceHash().equals(string(runtime,"sourceSha256","")))return;

        List<RuntimeRule> runtimeRules=new ArrayList<>();
        for(JsonElement element:array(runtime,"rules")){
            if(!element.isJsonObject())continue;JsonObject rule=element.getAsJsonObject();
            if(!bool(rule,"runtimeComplete",false))continue;
            String id=string(rule,"id",null),sourceClass=string(rule,"sourceClass",null),registryName=string(rule,"legacyRegistryName",null);
            int numericId=integer(rule,"legacyModEntityTypeId",-1),tracking=integer(rule,"legacyTrackingRangeBlocks",-1),frequency=integer(rule,"updateFrequency",-1);
            if(id==null||sourceClass==null||registryName==null||numericId<0||tracking<=0||frequency<=0)continue;
            runtimeRules.add(new RuntimeRule(id,sourceClass,registryName,numericId,tracking,frequency,bool(rule,"velocityUpdates",false)));
        }
        if(runtimeRules.isEmpty())return;

        LegacyLifecycleAnalyzer.Analysis lifecycle=new LegacyLifecycleAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.addProperty("sourceSha256",context.sourceHash());
        root.addProperty("sourceRegistrationStripWired",true);root.addProperty("sourceClassDeletionWired",false);
        JsonArray rules=new JsonArray();int strippedRules=0,strippedSites=0;
        LegacyEntityRegistrationStripper stripper=new LegacyEntityRegistrationStripper();

        for(RuntimeRule runtimeRule:runtimeRules){
            List<LegacyLifecycleAnalyzer.Registration> matches=lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY).stream()
                    .filter(registration->matches(registration,runtimeRule)).toList();
            JsonObject value=new JsonObject();value.addProperty("id",runtimeRule.id());value.addProperty("sourceClass",runtimeRule.sourceClass());
            value.addProperty("legacyRegistryName",runtimeRule.registryName());value.addProperty("legacyModEntityTypeId",runtimeRule.numericId());
            JsonArray blockers=new JsonArray();int sites=0;String sourceOwner=null,sourceMethod=null,sourceDescriptor=null;
            if(matches.size()!=1){blockers.add(matches.isEmpty()?"exact-lifecycle-registration-proof-missing":"ambiguous-lifecycle-registration-proof:"+matches.size());}
            else{
                var registration=matches.getFirst();sourceOwner=registration.sourceOwner();sourceMethod=registration.sourceMethod();sourceDescriptor=registration.sourceDescriptor();
                Path classPath=context.stagingDir().resolve(sourceOwner+".class");
                if(!Files.isRegularFile(classPath))blockers.add("registration-source-class-missing");
                else{
                    var target=new LegacyEntityRegistrationStripper.Target(sourceMethod,sourceDescriptor,runtimeRule.sourceClass(),runtimeRule.registryName(),runtimeRule.numericId(),runtimeRule.trackingRange(),runtimeRule.updateFrequency(),runtimeRule.velocityUpdates());
                    var result=stripper.strip(Files.readAllBytes(classPath),target);sites=result.strippedSites();
                    for(String blocker:result.blockers())blockers.add(blocker);
                    if(sites==1)Files.write(classPath,result.bytes());
                }
            }
            if(sourceOwner!=null)value.addProperty("sourceOwner",sourceOwner);if(sourceMethod!=null)value.addProperty("sourceMethod",sourceMethod);if(sourceDescriptor!=null)value.addProperty("sourceDescriptor",sourceDescriptor);
            value.addProperty("registrationStripComplete",sites==1&&blockers.isEmpty());value.addProperty("strippedSites",sites);value.add("blockers",blockers);rules.add(value);
            if(sites==1&&blockers.isEmpty()){strippedRules++;strippedSites+=sites;}
        }
        root.add("rules",rules);root.addProperty("evaluatedRuntimeRules",runtimeRules.size());root.addProperty("registrationStripCompleteRules",strippedRules);root.addProperty("strippedRegistrationSites",strippedSites);root.addProperty("blockedRegistrationStripRules",runtimeRules.size()-strippedRules);
        Path output=context.stagingDir().resolve(OUTPUT);Files.createDirectories(output.getParent());Files.writeString(output,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);

        if(strippedRules>0)context.diagnostics().info("LFB-CONVERT-ENTITY-REGSTRIP-0001",SupportLevel.ADAPTED,
                "Removed "+strippedSites+" exact pure legacy registerModEntity callsite(s) for "+strippedRules+" runtime-complete plain Entity rule(s); modern EntityType registration is now the only admitted registration path for those sites.");
        if(strippedRules<runtimeRules.size())context.diagnostics().warning("LFB-CONVERT-ENTITY-REGSTRIP-0002",SupportLevel.RUNTIME_BRIDGE,
                "Left "+(runtimeRules.size()-strippedRules)+" plain Entity registration rule(s) untouched because exact lifecycle identity or a pure contiguous registration stack slice could not be proven.");
        for(String diagnostic:lifecycle.diagnostics())context.diagnostics().warning("LFB-CONVERT-ENTITY-REGSTRIP-0003",SupportLevel.MANUAL_REQUIRED,diagnostic);
    }

    private static boolean matches(LegacyLifecycleAnalyzer.Registration registration,RuntimeRule rule){
        List<LegacyLifecycleAnalyzer.Value> args=registration.arguments();if(args.size()<7)return false;
        if(!(args.get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type)||!rule.sourceClass().equals(type.internalName()))return false;
        if(!(args.get(1) instanceof LegacyLifecycleAnalyzer.TextValue name)||!rule.registryName().equals(name.value()))return false;
        if(!number(args.get(2),rule.numericId())||!number(args.get(4),rule.trackingRange())||!number(args.get(5),rule.updateFrequency()))return false;
        return booleanLike(args.get(6),rule.velocityUpdates());
    }
    private static boolean number(LegacyLifecycleAnalyzer.Value value,int expected){return value instanceof LegacyLifecycleAnalyzer.NumberValue number&&number.value().intValue()==expected;}
    private static boolean booleanLike(LegacyLifecycleAnalyzer.Value value,boolean expected){
        if(value instanceof LegacyLifecycleAnalyzer.BooleanValue bool)return bool.value()==expected;
        return value instanceof LegacyLifecycleAnalyzer.NumberValue number&&(number.value().intValue()!=0)==expected;
    }
    private static JsonArray array(JsonObject root,String name){JsonElement value=root.get(name);return value!=null&&value.isJsonArray()?value.getAsJsonArray():new JsonArray();}
    private static boolean bool(JsonObject root,String name,boolean fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsBoolean():fallback;}
    private static int integer(JsonObject root,String name,int fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsInt():fallback;}
    private static String string(JsonObject root,String name,String fallback){JsonElement value=root.get(name);return value!=null&&value.isJsonPrimitive()?value.getAsString():fallback;}
}
