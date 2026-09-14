package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.LegacyBehaviorCompiler;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Common content IR -> source-compiled tooltip/use/equipment callbacks. */
public final class LegacyBehaviorPass implements ConversionPass {
    public static final String MARKER="legacyforgebridge/source-behavior.marker";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-source-behavior";}
    @Override public void apply(ConversionContext c)throws IOException{
        Path manifest=c.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if(!Files.isRegularFile(manifest))return;
        JsonObject root=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        JsonArray items=root.getAsJsonArray("items");if(items==null||items.isEmpty())return;
        Map<String,String> ids=new LinkedHashMap<>();Set<String> ambiguous=new HashSet<>();
        List<LegacyItemRenderAnalyzer.ItemAllocation> proven=new ArrayList<>();
        for(JsonElement e:items){
            JsonObject item=e.getAsJsonObject();String id=item.get("id").getAsString();String path=id.substring(id.indexOf(':')+1);
            if(ids.putIfAbsent(path,id)!=null)ambiguous.add(path);ids.put(id,id);
            if(item.has("legacyRegistryName"))ids.putIfAbsent(item.get("legacyRegistryName").getAsString(),id);
            if(item.has("sourceClass")&&item.has("sourceConstructor")){
                String sourceClass=item.get("sourceClass").getAsString(),ctor=item.get("sourceConstructor").getAsString();
                List<LegacyItemRenderAnalyzer.ConstructorArgument> args=new ArrayList<>();
                JsonArray raw=item.getAsJsonArray("sourceConstructorArgs");
                org.objectweb.asm.Type[] types=org.objectweb.asm.Type.getArgumentTypes(ctor);
                for(int i=0;i<types.length;i++){Object value=null;if(raw!=null&&i<raw.size()&&!raw.get(i).isJsonNull()){JsonPrimitive v=raw.get(i).getAsJsonPrimitive();value=coerce(v,types[i]);}
                    args.add(new LegacyItemRenderAnalyzer.ConstructorArgument(types[i].getDescriptor(),value));}
                boolean sword="sword".equals(item.has("kind")?item.get("kind").getAsString():"");
                proven.add(new LegacyItemRenderAnalyzer.ItemAllocation(item.has("legacyRegistryName")?item.get("legacyRegistryName").getAsString():path,sourceClass,ctor,args,sword,false,false));
            }
        }
        ambiguous.forEach(ids::remove);
        String bootstrap=GeneratedModEntrypointPass.generatedContentClass(c.metadata()).replace('.','/')+"Behavior";
        var result=new LegacyBehaviorCompiler().compile(c.sourceJar(),c.metadata().fabricId(),bootstrap,ids,proven);
        if(!result.items().isEmpty()||!result.events().isEmpty()) {
            for(var e:result.classes().entrySet()){Path p=c.stagingDir().resolve(e.getKey());Files.createDirectories(p.getParent());Files.write(p,e.getValue());}
            Files.writeString(c.stagingDir().resolve(MARKER),bootstrap+"\n",StandardCharsets.UTF_8);
        }
        JsonObject report=new JsonObject();report.add("items",GSON.toJsonTree(result.items()));report.add("events",GSON.toJsonTree(result.events()));report.add("unsupported",GSON.toJsonTree(result.diagnostics()));
        Files.writeString(c.stagingDir().resolve("legacyforgebridge/behavior-analysis.json"),GSON.toJson(report)+"\n",StandardCharsets.UTF_8);
        for(String diagnostic:result.diagnostics())c.diagnostics().warning("LFB-CONVERT-BEHAVIOR-0002",SupportLevel.MANUAL_REQUIRED,diagnostic);
        c.diagnostics().info("LFB-CONVERT-BEHAVIOR-0001",SupportLevel.ADAPTED,
                "Compiled source behavior: items="+result.items().size()+", event callbacks="+result.events().size()+", classes="+result.classes().size()+". Unsupported methods remain explicit in behavior-analysis.json.");
    }
    private static Object coerce(JsonPrimitive value, org.objectweb.asm.Type type){
        if(value.isString())return value.getAsString();
        return switch(type.getSort()){
            case org.objectweb.asm.Type.BOOLEAN,org.objectweb.asm.Type.BYTE,org.objectweb.asm.Type.CHAR,org.objectweb.asm.Type.SHORT,org.objectweb.asm.Type.INT->value.getAsInt();
            case org.objectweb.asm.Type.FLOAT->value.getAsFloat();
            case org.objectweb.asm.Type.LONG->value.getAsLong();
            case org.objectweb.asm.Type.DOUBLE->value.getAsDouble();
            default->null;
        };
    }

}
