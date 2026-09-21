package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVisibleEntityRegistryTest {

    @Test
    void slidePanelAcceptsSourceProvenNonvisualWatchersButRejectsMissingWireContract() {
        JsonObject value=base("testmod:door","SLIDE_PANEL",2,64,64);
        JsonObject watchers=new JsonObject();watchers.addProperty("direction",17);watchers.addProperty("mirror",18);watchers.addProperty("texture",20);value.add("watchers",watchers);
        JsonObject types=new JsonObject();
        types.addProperty("17",0);types.addProperty("18",0);types.addProperty("19",0);types.addProperty("20",1);types.addProperty("22",0);types.addProperty("23",0);
        value.add("watcherTypes",types);
        JsonArray textures=new JsonArray();textures.add(texture(0,"testmod:textures/a.png"));textures.add(texture(1,"testmod:textures/b.png"));value.add("textureVariants",textures);

        var rule=LegacyVisibleEntityRegistry.parseForTests(value,"LegacyTest");
        assertNotNull(rule);assertEquals(6,rule.watcherTypes().size());
        assertEquals(0,rule.watcherTypes().get(19));assertEquals(0,rule.watcherTypes().get(22));assertEquals(0,rule.watcherTypes().get(23));

        types.remove("20");
        assertNull(LegacyVisibleEntityRegistry.parseForTests(value,"LegacyTest"));
    }

    @Test
    void trayRequiresFiveConsecutiveTypeFiveWatcherSlots() {
        JsonObject value=base("testmod:tray","TRAY_ITEMS",12,64,32);
        value.addProperty("fixedTexture","testmod:textures/tray.png");
        value.addProperty("itemWatcherBase",17);value.addProperty("itemWatcherCount",5);
        JsonObject types=new JsonObject();for(int i=17;i<=21;i++)types.addProperty(String.valueOf(i),5);value.add("watcherTypes",types);

        var rule=LegacyVisibleEntityRegistry.parseForTests(value,"LegacyTest");
        assertNotNull(rule);assertEquals(17,rule.itemWatcherBase());assertEquals(5,rule.itemWatcherCount());

        types.addProperty("19",0);
        assertNull(LegacyVisibleEntityRegistry.parseForTests(value,"LegacyTest"));
    }

    private static JsonObject base(String id,String adapter,int legacyId,int tw,int th){
        JsonObject value=new JsonObject();value.addProperty("id",id);value.addProperty("adapter",adapter);
        value.addProperty("legacyNumericId",legacyId);value.addProperty("trackingRange",80);value.addProperty("updateFrequency",1);
        value.addProperty("velocityUpdates",true);value.addProperty("width",1F);value.addProperty("height",1F);
        value.addProperty("modelTextureWidth",tw);value.addProperty("modelTextureHeight",th);
        JsonArray parts=new JsonArray();JsonObject part=new JsonObject();part.addProperty("field","box");part.addProperty("u",0);part.addProperty("v",0);
        part.addProperty("x",0F);part.addProperty("y",0F);part.addProperty("z",0F);part.addProperty("width",16);part.addProperty("height",16);part.addProperty("depth",1);
        part.addProperty("pivotX",0F);part.addProperty("pivotY",0F);part.addProperty("pivotZ",0F);
        part.addProperty("xRot",0F);part.addProperty("yRot",0F);part.addProperty("zRot",0F);part.addProperty("mirror",false);parts.add(part);value.add("parts",parts);
        value.add("watchers",new JsonObject());value.add("watcherTypes",new JsonObject());value.add("textureVariants",new JsonArray());value.add("palette",new JsonArray());
        value.addProperty("itemWatcherBase",-1);value.addProperty("itemWatcherCount",0);return value;
    }
    private static JsonObject texture(int value,String id){JsonObject t=new JsonObject();t.addProperty("value",value);t.addProperty("texture",id);t.addProperty("translucent",false);return t;}
}
