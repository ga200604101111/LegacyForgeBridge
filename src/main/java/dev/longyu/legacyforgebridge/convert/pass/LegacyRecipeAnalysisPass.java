package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.LegacyRecipeAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Emits source-derived recipe/OreDictionary/fuel IR without executing the legacy registrar. */
public final class LegacyRecipeAnalysisPass implements ConversionPass {
    @Override public String id(){return "legacy-recipe-analysis";}
    @Override public void apply(ConversionContext context)throws Exception{
        var result=new LegacyRecipeAnalyzer().analyze(context.sourceJar());
        if(result.registrations().isEmpty())return;
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);JsonArray registrations=new JsonArray();
        for(var r:result.registrations()){
            JsonObject o=new JsonObject();o.addProperty("kind",r.kind().name().toLowerCase());o.addProperty("sourceOwner",r.sourceOwner());o.addProperty("sourceMethod",r.sourceMethod());
            JsonArray a=new JsonArray();r.arguments().forEach(v->a.add(value(v)));o.add("arguments",a);registrations.add(o);
        }
        root.add("registrations",registrations);JsonArray diagnostics=new JsonArray();result.diagnostics().forEach(diagnostics::add);root.add("diagnostics",diagnostics);
        Path output=context.stagingDir().resolve("legacyforgebridge/recipe-analysis.json");Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n",StandardCharsets.UTF_8);
        long crafting=result.of(LegacyRecipeAnalyzer.Kind.SHAPED).size()+result.of(LegacyRecipeAnalyzer.Kind.SHAPELESS).size();
        context.diagnostics().info("LFB-CONVERT-RECIPE-0001",SupportLevel.RUNTIME_BRIDGE,
                "Recovered "+crafting+" crafting, "+result.of(LegacyRecipeAnalyzer.Kind.SMELTING).size()+" smelting, "+result.of(LegacyRecipeAnalyzer.Kind.ORE_REGISTER).size()+" OreDictionary and "+result.of(LegacyRecipeAnalyzer.Kind.FUEL_HANDLER).size()+" fuel registrations.");
        for(String d:result.diagnostics())context.diagnostics().warning("LFB-CONVERT-RECIPE-0002",SupportLevel.MANUAL_REQUIRED,d);
    }
    private static JsonElement value(LegacyRecipeAnalyzer.Value v){JsonObject o=new JsonObject();
        if(v instanceof LegacyRecipeAnalyzer.TextValue x){o.addProperty("kind","text");o.addProperty("value",x.value());}
        else if(v instanceof LegacyRecipeAnalyzer.NumberValue x){o.addProperty("kind","number");o.addProperty("value",x.value());}
        else if(v instanceof LegacyRecipeAnalyzer.CharacterValue x){o.addProperty("kind","character");o.addProperty("value",String.valueOf(x.value()));}
        else if(v instanceof LegacyRecipeAnalyzer.RegistryValue x){o.addProperty("kind","registry");o.addProperty("registryKind",x.kind().name().toLowerCase());o.addProperty("name",x.registryName());if(x.legacyNamespace()!=null)o.addProperty("legacyNamespace",x.legacyNamespace());o.addProperty("sourceOwner",x.sourceOwner());o.addProperty("sourceField",x.sourceField());}
        else if(v instanceof LegacyRecipeAnalyzer.FieldValue x){o.addProperty("kind","field");o.addProperty("owner",x.owner());o.addProperty("name",x.name());o.addProperty("descriptor",x.descriptor());}
        else if(v instanceof LegacyRecipeAnalyzer.ObjectValue x){o.addProperty("kind","object");o.addProperty("type",x.internalName());if(x.constructorDescriptor()!=null)o.addProperty("constructor",x.constructorDescriptor());JsonArray a=new JsonArray();x.constructorArguments().forEach(y->a.add(value(y)));o.add("arguments",a);}
        else if(v instanceof LegacyRecipeAnalyzer.ArrayValue x){o.addProperty("kind","array");JsonArray a=new JsonArray();x.elements().forEach(y->a.add(value(y)));o.add("elements",a);}
        else if(v instanceof LegacyRecipeAnalyzer.ParamValue x){o.addProperty("kind","parameter");o.addProperty("local",x.local());}
        else if(v==LegacyRecipeAnalyzer.NullValue.INSTANCE)o.addProperty("kind","null");else o.addProperty("kind","unknown");return o;}
}
