package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacySeatBedPresentationRegistryTest {
    @AfterEach void clear(){LegacySeatBedPresentationRegistry.clearForTests();}
    @Test void proofCompleteTwoTextureRuleIsAccepted(){var rule=LegacySeatBedPresentationRegistry.parseForTests(valid());assertNotNull(rule);assertEquals("sample:bed",rule.id().toString());assertEquals(4,rule.parts().size());assertEquals(java.util.List.of("p1","p2"),rule.footParts());assertEquals(java.util.List.of(90F,0F,270F,180F),rule.yawDegreesByDirection());}
    @Test void malformedOrOverlappingPresentationFailsClosed(){JsonObject x=valid();x.addProperty("modelScale",.125F);assertNull(LegacySeatBedPresentationRegistry.parseForTests(x));x=valid();JsonArray head=new JsonArray();head.add("p1");head.add("p3");x.add("headParts",head);assertNull(LegacySeatBedPresentationRegistry.parseForTests(x));}
    private static JsonObject valid(){JsonObject p=new JsonObject();p.addProperty("id","sample:bed");p.addProperty("footTexture","sample:textures/entitys/bed.png");p.addProperty("headTexture","sample:textures/entitys/pillow.png");p.addProperty("imageWidth",64);p.addProperty("imageHeight",32);p.addProperty("modelTextureWidth",64);p.addProperty("modelTextureHeight",32);p.addProperty("modelScale",.0625F);p.addProperty("expandedRenderBoundsProven",true);JsonArray parts=new JsonArray();for(int i=0;i<4;i++){JsonObject q=new JsonObject();q.addProperty("field","p"+i);q.addProperty("u",0);q.addProperty("v",i==3?19:0);q.addProperty("x",-8F);q.addProperty("y",0F);q.addProperty("z",-8F);q.addProperty("width",8);q.addProperty("height",2);q.addProperty("depth",16);q.addProperty("pivotX",0F);q.addProperty("pivotY",0F);q.addProperty("pivotZ",0F);q.addProperty("xRot",0F);q.addProperty("yRot",i==1?(float)Math.PI:0F);q.addProperty("zRot",0F);q.addProperty("mirror",false);parts.add(q);}p.add("parts",parts);p.add("footParts",strings("p1","p2"));p.add("headParts",strings("p0","p3"));p.add("translateXByDirection",floats(.5F,0F,.5F,1F));p.add("translateZByDirection",floats(1F,.5F,0F,.5F));p.add("yawDegreesByDirection",floats(90F,0F,270F,180F));return p;}
    private static JsonArray strings(String...v){JsonArray a=new JsonArray();for(String x:v)a.add(x);return a;}private static JsonArray floats(float...v){JsonArray a=new JsonArray();for(float x:v)a.add(x);return a;}
}
