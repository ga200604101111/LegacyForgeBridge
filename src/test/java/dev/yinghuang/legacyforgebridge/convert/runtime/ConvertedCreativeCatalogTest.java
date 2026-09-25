package dev.yinghuang.legacyforgebridge.convert.runtime;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ConvertedCreativeCatalogTest {
 @Test void preservesSourceOrderUnsignedDataAndProvenEmpty(){
  var root=JsonParser.parseString("{\"schemaVersion\":1,\"items\":{\"example:visible\":[5,1,5,65535],\"example:hidden\":[]}}").getAsJsonObject();
  var parsed=ConvertedCreativeCatalog.parse(root);
  assertEquals(List.of(5,1,65535),parsed.get("example:visible"));assertEquals(List.of(),parsed.get("example:hidden"));assertFalse(parsed.containsKey("example:unproven"));
 }
 @Test void rejectsFractionalAndOutOfRangeMetadata(){
  for(String meta:List.of("1.5","-1","65536","null"))assertThrows(RuntimeException.class,()->ConvertedCreativeCatalog.parse(JsonParser.parseString("{\"schemaVersion\":1,\"items\":{\"example:item\":["+meta+"]}}").getAsJsonObject()));
 }
 @Test void rejectsWrongSchemaAndIdentity(){
  assertThrows(RuntimeException.class,()->ConvertedCreativeCatalog.parse(JsonParser.parseString("{\"schemaVersion\":1.5,\"items\":{}}").getAsJsonObject()));
  assertThrows(RuntimeException.class,()->ConvertedCreativeCatalog.parse(JsonParser.parseString("{\"schemaVersion\":1,\"items\":{\"Bad Namespace:item\":[0]}}").getAsJsonObject()));
 }
}
