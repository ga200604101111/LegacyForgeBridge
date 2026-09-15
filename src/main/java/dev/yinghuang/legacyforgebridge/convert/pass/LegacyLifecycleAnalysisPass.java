package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.LegacyLifecycleAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Writes source-derived lifecycle registration IR for later BlockEntity/Entity/GUI/worldgen passes. */
public final class LegacyLifecycleAnalysisPass implements ConversionPass {
    @Override public String id(){ return "legacy-lifecycle-analysis"; }
    @Override public void apply(ConversionContext context) throws Exception {
        var result=new LegacyLifecycleAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);JsonArray registrations=new JsonArray();
        for(var registration:result.registrations()){
            JsonObject value=new JsonObject();value.addProperty("kind",registration.kind().name().toLowerCase());
            value.addProperty("sourceOwner",registration.sourceOwner());value.addProperty("sourceMethod",registration.sourceMethod());
            JsonArray args=new JsonArray();for(var argument:registration.arguments())args.add(argument(argument));value.add("arguments",args);registrations.add(value);
        }
        root.add("registrations",registrations);JsonArray diagnostics=new JsonArray();result.diagnostics().forEach(diagnostics::add);root.add("diagnostics",diagnostics);
        Path output=context.stagingDir().resolve("legacyforgebridge/lifecycle-analysis.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n", StandardCharsets.UTF_8);
        if(!result.registrations().isEmpty()) context.diagnostics().info("LFB-CONVERT-LIFECYCLE-0001",SupportLevel.RUNTIME_BRIDGE,
                "Recovered "+result.registrations().size()+" source lifecycle registrations for generic Entity/BlockEntity/GUI/worldgen/dimension migration.");
        for(String diagnostic:result.diagnostics())context.diagnostics().warning("LFB-CONVERT-LIFECYCLE-0002",SupportLevel.MANUAL_REQUIRED,diagnostic);
    }
    private static JsonElement argument(LegacyLifecycleAnalyzer.Value value){
        JsonObject out=new JsonObject();
        if(value instanceof LegacyLifecycleAnalyzer.TextValue v){out.addProperty("kind","text");out.addProperty("value",v.value());}
        else if(value instanceof LegacyLifecycleAnalyzer.NumberValue v){out.addProperty("kind","number");out.addProperty("value",v.value());}
        else if(value instanceof LegacyLifecycleAnalyzer.BooleanValue v){out.addProperty("kind","boolean");out.addProperty("value",v.value());}
        else if(value instanceof LegacyLifecycleAnalyzer.TypeValue v){out.addProperty("kind","type");out.addProperty("value",v.internalName());}
        else if(value instanceof LegacyLifecycleAnalyzer.ObjectValue v){out.addProperty("kind","object");out.addProperty("value",v.internalName());}
        else if(value instanceof LegacyLifecycleAnalyzer.FieldValue v){out.addProperty("kind","field");out.addProperty("owner",v.owner());out.addProperty("name",v.name());out.addProperty("descriptor",v.descriptor());}
        else if(value==LegacyLifecycleAnalyzer.NullValue.INSTANCE)out.addProperty("kind","null");
        else out.addProperty("kind","unknown");
        return out;
    }
}
