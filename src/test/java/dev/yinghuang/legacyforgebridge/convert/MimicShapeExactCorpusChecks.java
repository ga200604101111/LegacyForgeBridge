package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Arrays;
import java.util.jar.JarFile;

/** External-fixture presentation checks; never loads or executes the input mod's classes. */
public final class MimicShapeExactCorpusChecks {
    private MimicShapeExactCorpusChecks() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected: source.jar staging-directory");
        run(Path.of(args[0]),Path.of(args[1]));
    }
    public static void run(Path source, Path stage) throws Exception {
        String hash=Hashing.sha256(source);
        if(!hash.equals("bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402"))
            throw new IllegalArgumentException("This regression requires the supplied BambooMod 2.6.8.5 corpus, unchanged");
        Files.createDirectories(stage);
        var context=new ConversionContext(source,stage,stage.resolveSibling("unused-presentation-candidate.jar"),hash,
                Files.size(source),LegacyModMetadata.read(source),new LegacyJarAnalyzer().analyze(source),
                new DiagnosticCollector(),"generic-forge-1.7.10");
        ConversionPass[] passes={new CopyLegacyJarPass(),new LegacyLanguagePass(),new GenericContentPass(),
                new LegacyClientContentBaselinePass(),new LegacyIconPresentationPass(),new LegacyItemNamePass(),
                new LegacyCreativeVariantsPass(),new LegacyBlockGeometryPass(),new LegacyTextureAtlasPass()};
        for(var pass:passes){pass.apply(context);System.out.println("PASS "+pass.id());}
        var root=JsonParser.parseString(Files.readString(stage.resolve(LegacyGeometrySpec.PATH))).getAsJsonObject();
        var rules=LegacyGeometrySpec.parse(root);
        require(rules.size()==21,"geometry families");
        require(rules.values().stream().mapToInt(r->r.variants().size()).sum()==327,"metadata coverage");
        require(root.get("inventoryMaterialFallbackVariants").getAsInt()==8,"material fallback count");
        for(String name:new String[]{"delude_width","delude_height","delude_stair","delude_plate","decocarpet"})
            require(rules.get("bamboomod:"+name).variants().size()==16,"lost state: "+name);
        require(rules.get("bamboomod:bamboopanel").pane(),"panel became cube family");
        require(rules.get("bamboomod:bamboopanel").variants().size()==7,"invented unsupported panel states");
        int count=0;
        try(var jar=new JarFile(source.toFile())){
            var entries=jar.entries();
            while(entries.hasMoreElements()){
                var entry=entries.nextElement();String name=entry.getName();
                if(!name.endsWith(".png")&&!name.endsWith(".png.mcmeta"))continue;
                Path asset=stage.resolve(name).normalize();
                require(asset.startsWith(stage.normalize()),"unsafe asset path");
                try(var in=jar.getInputStream(entry)){require(Arrays.equals(in.readAllBytes(),Files.readAllBytes(asset)),"modified source asset: "+name);}
                count++;
            }
        }
        require(count==223,"source image/animation count");
        System.out.println("PASS 21 rules / 327 states / 8 explicit material fallbacks / 223 unchanged source assets");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
