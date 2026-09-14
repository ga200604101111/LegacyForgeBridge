package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ViaJsonObjectAccessTest {
    @Test
    void readsAndWritesGsonCompatibleObjectsWithoutStaticJsonObjectDependency() {
        JsonObject object = new JsonObject();
        object.addProperty("translate", "commands.help.header");

        assertEquals(
                "commands.help.header",
                ViaJsonObjectAccess.getStringProperty(object, "translate")
        );

        ViaJsonObjectAccess.setStringProperty(object, "translate", "commands.help.footer");
        assertEquals(
                "commands.help.footer",
                ViaJsonObjectAccess.getStringProperty(object, "translate")
        );
        assertNull(ViaJsonObjectAccess.getStringProperty(object, "missing"));
    }
}
