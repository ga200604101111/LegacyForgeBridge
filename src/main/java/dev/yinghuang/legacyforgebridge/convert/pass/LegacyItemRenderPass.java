package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyHandSpace;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Binding;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Context;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Draw;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Operation;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Cross-mod source renderer -> ordinary modern item-model context selection and OBJ transforms. */
public final class LegacyItemRenderPass implements ConversionPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Override public String id() { return "legacy-item-render-semantics"; }

    @Override public void apply(ConversionContext context) throws IOException {
        var analysis = new LegacyItemRenderAnalyzer().analyze(context.sourceJar());
        var itemAnalysis = new LegacyItemRenderAnalyzer().analyzeItems(context.sourceJar());
        var allocations = new java.util.LinkedHashMap<String,LegacyItemRenderAnalyzer.ItemAllocation>();
        itemAnalysis.items().forEach(item -> allocations.put(item.itemName(),item));
        for (String message : analysis.diagnostics()) context.diagnostics().warning(
                "LFB-CONVERT-ITEM-RENDER-0002", SupportLevel.MANUAL_REQUIRED, message);
        int materialized=materializeSourceObjDefinitions(context,analysis.bindings());
        int replaced = 0;
        Path assets = context.stagingDir().resolve("assets");
        if (Files.isDirectory(assets)) {
            List<Path> files;
            try (var walk = Files.walk(assets)) {
                files = walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> { Path r = assets.relativize(p); return r.getNameCount() >= 3 && r.getName(1).toString().equals("items"); })
                        .sorted().toList();
            }
            for (Path file : files) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject old = object(root, "model"), special = old == null ? null : object(old, "model");
                if (old == null || !"minecraft:special".equals(string(old, "type")) || special == null
                        || !"legacyforgebridge:obj".equals(string(special, "type"))) continue;
                List<Binding> candidates = analysis.bindings().stream().filter(b -> matches(b, special)).toList();
                if (candidates.isEmpty()) continue;
                Binding source = candidates.getFirst();
                if (candidates.stream().anyMatch(b -> !b.contexts().equals(source.contexts()))) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0003", SupportLevel.MANUAL_REQUIRED,
                            "Ambiguous source renderer binding for " + assets.relativize(file) + "; existing model retained.");
                    continue;
                }
                String base = string(old, "base");
                if (base == null || !supported(source)) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0004", SupportLevel.MANUAL_REQUIRED,
                            "Incomplete/context-dependent helper semantics for " + source.rendererClass() + "; existing model retained.");
                    continue;
                }
                boolean resourcesExist = source.contexts().values().stream().flatMap(c -> c.draws().stream())
                        .allMatch(d -> exists(context.stagingDir(), canonical(d.model())) && exists(context.stagingDir(), canonical(d.texture())));
                if (!resourcesExist) {
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0005", SupportLevel.MANUAL_REQUIRED,
                            "Source renderer has missing modern resources: " + source.rendererClass()); continue;
                }
                JsonObject nativeIcon = new JsonObject(); nativeIcon.addProperty("type", "minecraft:model"); nativeIcon.addProperty("model", base);
                JsonObject select = new JsonObject(); select.addProperty("type", "minecraft:select"); select.addProperty("property", "minecraft:display_context");
                JsonArray cases = new JsonArray();
                addCase(cases, List.of("gui"), source.contexts().get("INVENTORY"), "INVENTORY", old, nativeIcon);
                addCase(cases, List.of("ground", "fixed"), source.contexts().get("ENTITY"), "ENTITY", old, nativeIcon);
                String sourceName = file.getFileName().toString().replaceFirst("\\.json$", "");
                var item = allocations.get(sourceName);
                if (item != null && !source.contexts().get("EQUIPPED").helpers().getOrDefault("EQUIPPED_BLOCK", false)
                        && !source.contexts().get("EQUIPPED").helpers().getOrDefault("BLOCK_3D", false)) {
                    String neutralBase = writeNeutralHandBase(context.stagingDir(), base);
                    for (boolean left : List.of(false,true)) {
                        addHandCase(cases, left?"firstperson_lefthand":"firstperson_righthand", source.contexts().get("EQUIPPED_FIRST_PERSON"),
                                old,nativeIcon,neutralBase,LegacyHandSpace.firstPerson(left),left);
                        addHandCase(cases, left?"thirdperson_lefthand":"thirdperson_righthand", source.contexts().get("EQUIPPED"),
                                old,nativeIcon,neutralBase,LegacyHandSpace.thirdPerson(item.full3D(),item.rotates(),left),left);
                    }
                } else {
                    addCase(cases,List.of("firstperson_righthand","firstperson_lefthand"),source.contexts().get("EQUIPPED_FIRST_PERSON"),"EQUIPPED_FIRST_PERSON",old,nativeIcon);
                    addCase(cases,List.of("thirdperson_righthand","thirdperson_lefthand"),source.contexts().get("EQUIPPED"),"EQUIPPED",old,nativeIcon);
                    context.diagnostics().warning("LFB-CONVERT-ITEM-RENDER-0006",SupportLevel.MANUAL_REQUIRED,
                            "Hand basis retained: no proven ordinary item allocation for " + sourceName);
                }
                select.add("cases", cases); select.add("fallback", nativeIcon.deepCopy()); root.add("model", select);
                Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8); replaced++;
            }
        }
        Path report = context.stagingDir().resolve("legacyforgebridge/item-render-analysis.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, GSON.toJson(analysis) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-ITEM-RENDER-0001", SupportLevel.ADAPTED,
                "Source IItemRenderer bindings=" + analysis.bindings().size()+", source OBJ definitions materialized="+materialized
                        + ", modern context-select models=" + replaced
                        + ". Source operations are preserved in order; unknown runtime behavior is not invented.");
    }

    private static int materializeSourceObjDefinitions(ConversionContext context,List<Binding> bindings)throws IOException{
        Path manifest=context.stagingDir().resolve(LegacyClientContentBaselinePass.CONTENT);
        if(!Files.isRegularFile(manifest)||bindings.isEmpty())return 0;
        JsonObject content=JsonParser.parseString(Files.readString(manifest,StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray items=content.getAsJsonArray("items");if(items==null)return 0;
        Map<String,List<JsonObject>> byLegacy=new LinkedHashMap<>();
        for(JsonElement element:items)if(element.isJsonObject()){
            JsonObject item=element.getAsJsonObject();String legacy=string(item,"legacyRegistryName");
            if(legacy!=null)byLegacy.computeIfAbsent(legacy,ignored->new ArrayList<>()).add(item);
        }

        var registryAnalysis=new LegacyRegistryAnalyzer().analyze(context.sourceJar());
        Map<String,List<LegacyRegistryAnalyzer.FieldBinding>> fields=new LinkedHashMap<>();
        for(var binding:registryAnalysis.fieldBindings())if(binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM)
            fields.computeIfAbsent(binding.owner()+"\u0000"+binding.name(),ignored->new ArrayList<>()).add(binding);

        int emitted=0;
        for(Binding source:bindings){
            List<LegacyRegistryAnalyzer.FieldBinding> identities=fields.getOrDefault(source.fieldOwner()+"\u0000"+source.fieldName(),List.of());
            if(identities.size()!=1)continue;
            List<JsonObject> converted=byLegacy.getOrDefault(identities.getFirst().registryName(),List.of());
            if(converted.size()!=1)continue;
            LinkedHashSet<ResourcePair> pairs=new LinkedHashSet<>();
            for(Context render:source.contexts().values())if(render.custom())
                for(Draw draw:render.draws())pairs.add(new ResourcePair(draw.model(),draw.texture()));
            if(pairs.size()!=1)continue;
            ResourcePair pair=pairs.getFirst();
            String model=materializeCaseExactResource(context.stagingDir(),pair.model());
            String texture=materializeCaseExactResource(context.stagingDir(),pair.texture());
            if(model==null||texture==null)continue;

            String id=string(converted.getFirst(),"id");if(id==null)continue;
            int split=id.indexOf(':');if(split<=0)continue;
            String namespace=id.substring(0,split),path=id.substring(split+1);
            Path definition=context.stagingDir().resolve("assets/"+namespace+"/items/"+path+".json");
            if(!Files.isRegularFile(definition))continue;
            JsonObject root=JsonParser.parseString(Files.readString(definition,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject ordinary=object(root,"model");
            if(ordinary==null||!"minecraft:model".equals(string(ordinary,"type")))continue;
            String base=string(ordinary,"model");if(base==null)continue;
            JsonObject special=new JsonObject();special.addProperty("type","legacyforgebridge:obj");
            special.addProperty("model",model);special.addProperty("texture",texture);special.addProperty("scale",1F);
            JsonObject wrapper=new JsonObject();wrapper.addProperty("type","minecraft:special");wrapper.addProperty("base",base);wrapper.add("model",special);
            root.add("model",wrapper);
            Files.writeString(definition,GSON.toJson(root)+"\n",StandardCharsets.UTF_8);
            emitted++;
        }
        return emitted;
    }

    private static String materializeCaseExactResource(Path staging,String id)throws IOException{
        if(id==null)return null;int split=id.indexOf(':');if(split<=0||split==id.length()-1)return null;
        String namespace=id.substring(0,split),path=id.substring(split+1).replace('\\','/');
        if(path.startsWith("/")||path.contains(".."))return null;
        String canonicalNamespace=namespace.toLowerCase(Locale.ROOT),canonicalPath=path.toLowerCase(Locale.ROOT);
        Path assets=staging.resolve("assets").toAbsolutePath().normalize();
        Path target=assets.resolve(canonicalNamespace).resolve(canonicalPath).normalize();
        if(!target.startsWith(assets))return null;
        if(Files.isRegularFile(target))return canonicalNamespace+":"+canonicalPath;
        String expected=(namespace+"/"+path).toLowerCase(Locale.ROOT);
        List<Path> matches;
        try(var walk=Files.walk(assets)){
            matches=walk.filter(Files::isRegularFile)
                    .filter(file->assets.relativize(file).toString().replace('\\','/').toLowerCase(Locale.ROOT).equals(expected))
                    .toList();
        }
        if(matches.size()!=1)return null;
        Files.createDirectories(target.getParent());
        Files.copy(matches.getFirst(),target,StandardCopyOption.REPLACE_EXISTING);
        return canonicalNamespace+":"+canonicalPath;
    }

    private record ResourcePair(String model,String texture) { }

    private static boolean supported(Binding b) {
        for (String key : List.of("INVENTORY", "ENTITY", "EQUIPPED", "EQUIPPED_FIRST_PERSON")) {
            Context c = b.contexts().get(key); if (c == null) return false;
            if (key.equals("INVENTORY") && c.custom()) return false;
            if (key.equals("ENTITY") && c.custom() && (c.helpers().getOrDefault("BLOCK_3D", false)
                    || !c.helpers().getOrDefault("ENTITY_BOBBING", false)
                    || !c.helpers().getOrDefault("ENTITY_ROTATION", false))) return false;
        }
        return true;
    }

    private static boolean matches(Binding binding, JsonObject special) {
        String model = string(special, "model"), texture = string(special, "texture");
        return binding.contexts().values().stream().flatMap(c -> c.draws().stream())
                .anyMatch(d -> canonical(d.model()).equals(model) && canonical(d.texture()).equals(texture));
    }
    private static void addCase(JsonArray cases, List<String> names, Context context, String legacyType,
                                JsonObject template, JsonObject nativeIcon) {
        JsonObject entry = new JsonObject(); JsonArray when = new JsonArray(); names.forEach(when::add); entry.add("when", when);
        if (!context.custom()) entry.add("model", nativeIcon.deepCopy());
        else {
            JsonArray models = new JsonArray();
            for (Draw draw : context.draws()) {
                JsonObject outer = template.deepCopy(); JsonObject special = outer.getAsJsonObject("model");
                special.addProperty("model", canonical(draw.model())); special.addProperty("texture", canonical(draw.texture()));
                special.addProperty("scale", 1.0F);
                boolean nativeHandSpace = legacyType.startsWith("EQUIPPED")
                        && !context.helpers().getOrDefault("EQUIPPED_BLOCK", false);
                special.addProperty("centered", !nativeHandSpace);
                special.addProperty("coordinateSpace", nativeHandSpace ? "native_item" : "legacy_centered");
                List<Operation> operations = nativeHandSpace ? new ArrayList<>() : helperOperations(legacyType, context);
                operations.addAll(draw.operations()); special.add("transforms", GSON.toJsonTree(operations)); models.add(outer);
            }
            if (models.size() == 1) entry.add("model", models.get(0));
            else { JsonObject composite = new JsonObject(); composite.addProperty("type", "minecraft:composite"); composite.add("models", models); entry.add("model", composite); }
        }
        cases.add(entry);
    }

    private static String writeNeutralHandBase(Path staging,String base) throws IOException {
        int split=base.indexOf(':');String namespace=base.substring(0,split),path=base.substring(split+1)+"_lfb_hand";
        JsonObject neutral=new JsonObject();neutral.addProperty("parent",base);
        JsonObject display=new JsonObject();
        for(String context:List.of("firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"))
            display.add(context,JsonParser.parseString("{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}").getAsJsonObject());
        neutral.add("display",display);
        Path file=staging.resolve("assets").resolve(namespace).resolve("models").resolve(path+".json");
        Files.createDirectories(file.getParent());Files.writeString(file,GSON.toJson(neutral)+"\n",StandardCharsets.UTF_8);
        return namespace+":"+path;
    }
    private static void addHandCase(JsonArray cases,String name,Context context,JsonObject template,JsonObject icon,
                                    String base,List<Operation> normal,boolean left) {
        JsonObject entry=new JsonObject();entry.addProperty("when",name);
        if(!context.custom()){entry.add("model",icon.deepCopy());cases.add(entry);return;}
        entry.add("model",handModel(context,template,base,normal,left));
        cases.add(entry);
    }
    private static JsonElement handModel(Context context,JsonObject template,String base,List<Operation> prefix,boolean left){
        JsonArray models=new JsonArray();
        for(Draw draw:context.draws()){
            JsonObject outer=template.deepCopy();outer.addProperty("base",base);JsonObject special=outer.getAsJsonObject("model");
            special.addProperty("model",canonical(draw.model()));special.addProperty("texture",canonical(draw.texture()));
            special.addProperty("scale",1F);special.addProperty("centered",true);special.addProperty("coordinateSpace","legacy_hand_basis");
            List<Operation> operations=new ArrayList<>(prefix);operations.addAll(left?LegacyHandSpace.mirror(draw.operations()):draw.operations());
            special.add("transforms",GSON.toJsonTree(operations));models.add(outer);
        }
        if(models.size()==1)return models.get(0);
        JsonObject composite=new JsonObject();composite.addProperty("type","minecraft:composite");composite.add("models",models);return composite;
    }

    /** Prefixes from Forge 1.7.10 ForgeHooksClient, not corpus-specific visual tuning. */
    private static List<Operation> helperOperations(String type, Context context) {
        List<Operation> result = new ArrayList<>();
        if (type.equals("ENTITY")) result.add(Operation.of("scale", 0.5F, 0.5F, 0.5F));
        else if (type.startsWith("EQUIPPED")) {
            if (context.helpers().getOrDefault("EQUIPPED_BLOCK", false)) result.add(Operation.of("translate", -0.5F, -0.5F, -0.5F));
            else {
                result.add(Operation.of("translate", 0, -0.3F, 0));
                result.add(Operation.of("scale", 1.5F, 1.5F, 1.5F));
                result.add(Operation.of("rotate", 50, 0, 1, 0));
                result.add(Operation.of("rotate", 335, 0, 0, 1));
                result.add(Operation.of("translate", -0.9375F, -0.0625F, 0));
            }
        }
        return result;
    }
    private static String canonical(String id) { return id.toLowerCase(Locale.ROOT); }
    private static boolean exists(Path staging, String id) {
        int split = id.indexOf(':'); if (split <= 0 || id.contains("..") || id.indexOf('\\') >= 0) return false;
        Path assets = staging.resolve("assets").toAbsolutePath().normalize();
        Path resource = assets.resolve(id.substring(0, split)).resolve(id.substring(split + 1)).normalize();
        return resource.startsWith(assets) && Files.isRegularFile(resource);
    }
    private static String string(JsonObject object, String key) {
        JsonElement e = object.get(key); return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
    private static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent.get(key); return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
