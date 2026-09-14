package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.LegacyEventAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyEventPolicy;
import dev.longyu.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Writes source-proven FML/Forge event registration IR for the generic event compiler. */
public final class LegacyEventAnalysisPass implements ConversionPass {
    @Override public String id(){ return "legacy-event-analysis"; }

    @Override public void apply(ConversionContext context) throws Exception {
        var result=new LegacyEventAnalyzer().analyze(context.sourceJar());
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);JsonArray bindings=new JsonArray();
        for(var binding:result.bindings()){
            JsonObject value=new JsonObject();
            value.addProperty("handlerClass",binding.handlerClass());value.addProperty("method",binding.method());
            value.addProperty("descriptor",binding.descriptor());value.addProperty("eventType",binding.eventType());
            value.addProperty("bus",binding.bus().name().toLowerCase());value.addProperty("side",binding.side().name().toLowerCase());
            value.addProperty("priority",binding.priority());value.addProperty("receiveCanceled",binding.receiveCanceled());
            value.addProperty("executionPolicy",LegacyEventPolicy.execution(binding.eventType()).name().toLowerCase());
            JsonObject registration=new JsonObject();registration.addProperty("owner",binding.registrationOwner());
            registration.addProperty("method",binding.registrationMethod());registration.addProperty("descriptor",binding.registrationDescriptor());
            value.add("registration",registration);bindings.add(value);
        }
        root.add("bindings",bindings);JsonArray diagnostics=new JsonArray();result.diagnostics().forEach(diagnostics::add);root.add("diagnostics",diagnostics);
        Path output=context.stagingDir().resolve("legacyforgebridge/event-analysis.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n",StandardCharsets.UTF_8);
        if(!result.bindings().isEmpty()) context.diagnostics().info("LFB-CONVERT-EVENT-0001",SupportLevel.RUNTIME_BRIDGE,
                "Recovered "+result.bindings().size()+" source-proven FML/Forge event subscriptions for generic event migration.");
        for(String diagnostic:result.diagnostics()) context.diagnostics().warning("LFB-CONVERT-EVENT-0002",SupportLevel.MANUAL_REQUIRED,diagnostic);
    }
}
