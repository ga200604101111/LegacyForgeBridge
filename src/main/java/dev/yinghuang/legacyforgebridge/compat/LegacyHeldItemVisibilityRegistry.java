package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyHeldItemVisibilityPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-loaded runtime catalogue for source-proven held-own-BlockItem visibility toggles. */
public final class LegacyHeldItemVisibilityRegistry {
    private static final Map<Identifier,Rule> RULES=new ConcurrentHashMap<>();private LegacyHeldItemVisibilityRegistry(){}
    public record Rule(Identifier id,int visibleOrMask,int hiddenAndMask,boolean emptyCollision,boolean metaZeroSelectionElseEmpty,LegacyHeldSelectionSpec selection){
        public Rule(Identifier id,int visibleOrMask,int hiddenAndMask,boolean emptyCollision,boolean metaZeroSelectionElseEmpty){
            this(id,visibleOrMask,hiddenAndMask,emptyCollision,metaZeroSelectionElseEmpty,null);
        }
        public net.minecraft.world.phys.shapes.VoxelShape selectionShape(boolean holdingOwnItem){
            if(selection==null)throw new IllegalStateException("Selection bounds are not proven");
            var b=selection.select(holdingOwnItem);
            return net.minecraft.world.phys.shapes.Shapes.box(b.get(0),b.get(1),b.get(2),b.get(3),b.get(4),b.get(5));
        }
        public Rule{
            if(id==null||visibleOrMask<=0||visibleOrMask>15||hiddenAndMask<0||hiddenAndMask>15||(visibleOrMask&hiddenAndMask)!=0)
                throw new IllegalArgumentException("Invalid held visibility rule");
        }
    }
    public static void loadMod(String modId){var container=FabricLoader.getInstance().getModContainer(modId).orElse(null);if(container==null)return;var path=container.findPath(LegacyHeldItemVisibilityPass.OUTPUT);if(path.isEmpty())return;try(Reader reader=Files.newBufferedReader(path.get(),StandardCharsets.UTF_8)){JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(!bool(root,"runtimeComplete"))return;JsonArray values=root.getAsJsonArray("rules");if(values==null)return;int loaded=0;for(JsonElement e:values){if(!e.isJsonObject())continue;JsonObject v=e.getAsJsonObject();Rule rule=parse(v);if(rule==null||!modId.equals(rule.id().getNamespace()))continue;Rule old=RULES.putIfAbsent(rule.id(),rule);if(old!=null&&!old.equals(rule))throw new IllegalStateException("Conflicting held visibility rule "+rule.id());loaded++;}if(loaded>0)LegacyForgeBridge.LOGGER.info("Loaded held-item visibility runtime rules: mod={}, blocks={}",modId,loaded);}catch(Exception e){LegacyForgeBridge.LOGGER.error("Failed to load held-item visibility rules for {}",modId,e);}}
    public static Rule rule(Identifier id){return id==null?null:RULES.get(id);}static void clearForTests(){RULES.clear();}
    private static Rule parse(JsonObject v){try{return new Rule(Identifier.parse(v.get("id").getAsString()),v.get("visibleOrMask").getAsInt(),v.get("hiddenAndMask").getAsInt(),bool(v,"emptyCollision"),bool(v,"metaZeroSelectionElseEmpty"),LegacyHeldSelectionSpec.parse(v));}catch(RuntimeException e){return null;}}
    private static boolean bool(JsonObject v,String key){JsonElement e=v.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsBoolean();}
}
