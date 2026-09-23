package dev.yinghuang.legacyforgebridge.convert;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import java.nio.file.*;
import java.util.*;
final class LegacyPresentationBatchTestContext {
    static ConversionContext create(Path jar,Path staging)throws Exception{
        Files.createDirectories(staging);
        return new ConversionContext(jar,staging,staging.resolveSibling("candidate.jar"),"fixture-sha",Files.size(jar),
            new LegacyModMetadata("source.jar","fixture",List.of(new LegacyModMetadata.ModEntry("Example","Example","1","1.7.10",List.of()))),
            new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of()),
            new DiagnosticCollector(),"generic-fixture");
    }
}
