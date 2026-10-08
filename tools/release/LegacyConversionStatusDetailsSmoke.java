import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.desktop.LegacyConversionStatusDetails;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyConversionStatusDetailsSmoke {
    private static int tests;
    private static void verify(boolean pass, String message) { tests++; if (!pass) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var mixed = JsonParser.parseString("""
                {"modules":[
                {"sourceFile":"one.jar","status":"FAILED","outcome":"FAILED","conversion":{"diagnostics":[{"ruleId":"LFB-CONVERT-ENGINE-0001","severity":"ERROR","message":"source bytecode exception"}]}},
                {"sourceFile":"two.jar","status":"CONVERTED","outcome":"NOT_LOADER_SAFE","message":"loader guard rejected"},
                {"sourceFile":"three.jar","status":"CONVERTED","outcome":"LOAD_NOT_CONFIRMED"},
                {"sourceFile":"four.jar","status":"CONVERTED","outcome":"CONFLICT"},
                {"sourceFile":"ready.jar","status":"CONVERTED","outcome":"LOADED_REPORTED"},
                {"sourceFile":"next.jar","status":"CONVERTED","outcome":"PENDING_RESTART"}]}
                """).getAsJsonObject();
        String text = LegacyConversionStatusDetails.explain(mixed);
        verify(text.contains("one.jar") && text.contains("FAILED"), "failed item");
        verify(text.contains("source bytecode exception") && text.contains("LFB-CONVERT-ENGINE-0001"), "diagnostic proof");
        verify(text.contains("two.jar") && text.contains("NOT_LOADER_SAFE"), "loader safe context");
        verify(text.contains("three.jar") && text.contains("重新啟動"), "missing load confirmation");
        verify(text.contains("four.jar") && text.contains("衝突"), "fabric conflict");
        verify(!text.contains("ready.jar") && !text.contains("next.jar"), "no misreport of loaded or waiting restart");
        verify(text.contains("conversion-state.json") && text.contains("latest.log"), "exact report file guidance");
        verify(text.length() < 5000, "bounded message");
        String noData = LegacyConversionStatusDetails.explain(JsonParser.parseString("{}").getAsJsonObject());
        verify(noData.contains("conversion-state.json"), "empty diagnostics actionable");
        String ok = LegacyConversionStatusDetails.explain(JsonParser.parseString("{\"modules\":[{\"outcome\":\"LOADED_REPORTED\"}]}").getAsJsonObject());
        verify(ok.contains("沒有標記"), "not a gameplay proof");
        var polluted = JsonParser.parseString("{\"modules\":[{\"sourceFile\":\"x\\ny.jar\",\"status\":\"BLOCKED\",\"outcome\":\"BLOCKED\"}]}").getAsJsonObject();
        verify(!LegacyConversionStatusDetails.explain(polluted).contains("x\ny"), "sanitized line breaks");
        if (args.length > 0) Files.writeString(Path.of(args[0]), text);
        System.out.println("Smoke " + tests + "/" + tests + " PASS");
        System.out.println(text);
    }
}
