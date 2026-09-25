package dev.yinghuang.legacyforgebridge.convert.pass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ProcessorRetirementStorage {
    private ProcessorRetirementStorage() { }

    static Map<String,byte[]> snapshot(ProcessorRetirementPlan plan) throws Exception {
        Map<String,byte[]> result = new LinkedHashMap<>();
        for (String target : plan.cohort())
            result.put(target, Files.readAllBytes(plan.paths().get(target)));
        return result;
    }

    static void delete(ProcessorRetirementPlan plan) throws Exception {
        for (Path path : plan.paths().values()) Files.delete(path);
    }

    static void restore(Map<String,Path> paths, Map<String,byte[]> bytes) throws Exception {
        for (var entry : bytes.entrySet()) {
            Path path = paths.get(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, entry.getValue());
        }
    }
}
