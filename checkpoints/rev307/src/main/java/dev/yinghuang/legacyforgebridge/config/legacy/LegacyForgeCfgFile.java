package dev.yinghuang.legacyforgebridge.config.legacy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A loss-minimizing Forge 1.7.10 Configuration-file editor. Never executes legacy classes.
 * Reads the real .cfg if present; unsupported properties and comments remain byte-identical
 * on UTF-8 input. Refuses concurrent modifications, duplicates and non-UTF-8 source files.
 * The caller must restrict the target path to the current instance's config/ directory.
 */
public final class LegacyForgeCfgFile {
    private static final Pattern CATEGORY = Pattern.compile("^\\s*(\\\"(?:\\\\.|[^\\\"])*\\\"|[^{}]+?)\\s*\\{\\s*$");
    private static final Pattern PROPERTY = Pattern.compile("^(\\s*)([BIDS]):(.+?)=(.*)$");
    private static final Pattern VALID_KEY = Pattern.compile("^[^\\r\\n{}=<>]+$");
    private final Path file;
    private final byte[] original;
    private final String originalText;
    private final String newline;
    private final Map<Key, Located> located;
    private final Map<String, Integer> categoryEnds;

    public record Property(String fileName, String category, String key, char type, String defaultValue, String comment) {
        public Property {
            if (fileName == null || !fileName.matches("[A-Za-z0-9_.-]+\\.cfg") || fileName.contains("..")) {
                throw new IllegalArgumentException("Unsafe .cfg filename: " + fileName);
            }
            if (category == null || !VALID_KEY.matcher(category).matches() ||
                    key == null || !VALID_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Unsupported category/property name");
            }
            if ("BIDS".indexOf(type) < 0) throw new IllegalArgumentException("Unsupported config type");
            if (defaultValue == null || comment == null) throw new IllegalArgumentException("Null default/comment");
            try { validate(type, defaultValue); }
            catch (IOException invalid) { throw new IllegalArgumentException("Invalid default value", invalid); }
        }
    }
    private record Key(String category, String name) { }
    private record Located(int lineIndex, char type, String value, String prefix) { }

    public LegacyForgeCfgFile(Path file) throws IOException {
        this.file = file;
        original = Files.isRegularFile(file) ? Files.readAllBytes(file) : new byte[0];
        if (original.length > 2_000_000) throw new IOException("Legacy config file is unexpectedly large");
        try {
            originalText = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(original)).toString();
        } catch (CharacterCodingException badEncoding) {
            throw new IOException("Non-UTF-8 config; refusing to rewrite with lossy conversion", badEncoding);
        }
        newline = originalText.contains("\r\n") ? "\r\n" : "\n";
        located = new LinkedHashMap<>();
        categoryEnds = new LinkedHashMap<>();
        parse();
    }

    private void parse() throws IOException {
        Deque<String> stack = new ArrayDeque<>();
        boolean inList = false;
        String[] lines = originalText.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trim = line.trim();
            if (inList) {
                if (trim.equals(">")) inList = false;
                continue;
            }
            if (trim.isEmpty() || trim.startsWith("#") || trim.startsWith("//")) continue;
            Matcher category = CATEGORY.matcher(line);
            if (category.matches()) {
                stack.push(unquote(category.group(1).trim()));
                continue;
            }
            if (trim.equals("}")) {
                if (stack.isEmpty()) throw new IOException("Unexpected closing category at line " + (i + 1));
                categoryEnds.put(stack.peek(), i);
                stack.pop();
                continue;
            }
            if (stack.isEmpty()) continue;
            Matcher p = PROPERTY.matcher(line);
            if (p.matches()) {
                String rawValue = p.group(4);
                if (rawValue.trim().equals("<")) {
                    inList = true; // preserve lists unchanged; no guessed editing support
                    continue;
                }
                Key key = new Key(stack.peek(), unquote(p.group(3).trim()));
                Located previous = located.putIfAbsent(key,
                        new Located(i, p.group(2).charAt(0), rawValue.trim(),
                                line.substring(0, p.start(4))));
                if (previous != null) throw new IOException("Duplicate property " + key + " in " + file);
            }
        }
        if (!stack.isEmpty() || inList) throw new IOException("Malformed/unclosed legacy configuration file: " + file);
    }

    public String value(Property property) throws IOException {
        Located entry = located.get(new Key(property.category(), property.key()));
        if (entry == null) return property.defaultValue();
        if (entry.type() != property.type()) throw new IOException("Configuration type mismatch: " + property.key());
        validate(entry.type(), entry.value());
        return entry.value();
    }

    /** Discover properties from an existing .cfg when bytecode-derived defaults are unavailable. */
    public List<Property> existingProperties() {
        List<Property> properties = new ArrayList<>();
        for (var entry : located.entrySet()) {
            try {
                properties.add(new Property(file.getFileName().toString(),
                        entry.getKey().category(), entry.getKey().name(),
                        entry.getValue().type(), entry.getValue().value(), ""));
            } catch (IllegalArgumentException invalid) {
                // Leave this original line untouched; never create an unsafe input control.
            }
        }
        return List.copyOf(properties);
    }

    /** Persists only the profile-backed fields; all other source lines are preserved. */
    public void save(List<Property> supported, Map<Property, String> selected) throws IOException {
        if (!Arrays.equals(original, Files.isRegularFile(file) ? Files.readAllBytes(file) : new byte[0])) {
            throw new IOException("Configuration changed on disk; reopen the screen before saving: " + file);
        }
        Map<Key, Property> unique = new LinkedHashMap<>();
        for (Property property : supported) {
            if (!property.fileName().equals(file.getFileName().toString())) throw new IOException("Cross-file property in config transaction");
            Key key = new Key(property.category(), property.key());
            if (unique.putIfAbsent(key, property) != null) throw new IOException("Duplicate profile property " + key);
        }
        String[] initial = originalText.split("\\r?\\n", -1);
        List<String> lines = new ArrayList<>(Arrays.asList(initial));
        Map<Integer, String> updatedLines = new LinkedHashMap<>();
        Map<String, List<Property>> newEntries = new LinkedHashMap<>();
        for (Property property : supported) {
            String value = selected.getOrDefault(property, value(property));
            validate(property.type(), value);
            Key key = new Key(property.category(), property.key());
            Located match = located.get(key);
            if (match != null) {
                if (match.type() != property.type()) throw new IOException("Configuration type changed on disk");
                updatedLines.put(match.lineIndex(), match.prefix() + value);
            } else {
                newEntries.computeIfAbsent(property.category(), ignored -> new ArrayList<>()).add(property);
            }
        }
        for (var updated : updatedLines.entrySet()) lines.set(updated.getKey(), updated.getValue());
        // Insert missing keys inside existing categories. Work in descending insertion order.
        List<Map.Entry<String, List<Property>>> existing = new ArrayList<>();
        for (var entry : newEntries.entrySet()) if (categoryEnds.containsKey(entry.getKey())) existing.add(entry);
        existing.sort((a, b) -> Integer.compare(categoryEnds.get(b.getKey()), categoryEnds.get(a.getKey())));
        for (var entry : existing) {
            List<String> block = formatted(entry.getValue(), selected, "    ");
            lines.addAll(categoryEnds.get(entry.getKey()), block);
        }
        for (var entry : newEntries.entrySet()) {
            if (categoryEnds.containsKey(entry.getKey())) continue;
            if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
            lines.add("");
            lines.add(quote(entry.getKey()) + " {");
            lines.addAll(formatted(entry.getValue(), selected, "    "));
            lines.add("}");
            lines.add("");
        }
        if (original.length == 0 && lines.size()==1 && lines.getFirst().isEmpty()) {
            lines.set(0, "# Forge 1.7.10 configuration; generated by LegacyForgeBridge");
            lines.add("");
        }
        String output = String.join(newline, lines);
        if (output.isEmpty() || !output.endsWith(newline)) output += newline;
        byte[] newBytes = output.getBytes(StandardCharsets.UTF_8);
        if (Arrays.equals(newBytes, original)) return;
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".lfb-tmp");
        try {
            Files.write(temp, newBytes);
            if (original.length > 0) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".lfb-backup"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static List<String> formatted(List<Property> items, Map<Property, String> chosen, String indent) throws IOException {
        List<String> out = new ArrayList<>();
        for (Property property : items) {
            String value = chosen.getOrDefault(property, property.defaultValue());
            validate(property.type(), value);
            if (!property.comment().isBlank()) {
                for (String line : property.comment().split("\\R")) {
                    String clean = line.replace("\r", " ").replace("\n", " ");
                    out.add(indent + "# " + clean);
                }
            }
            out.add(indent + property.type() + ":" + quote(property.key()) + "=" + value);
        }
        return out;
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }
    public static void validate(char type, String value) throws IOException {
        if (value == null || value.contains("\n") || value.contains("\r") || value.length() > 8192) {
            throw new IOException("Invalid multiline/long config value");
        }
        try {
            switch (type) {
                case 'B' -> { if (!Set.of("true", "false").contains(value.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Boolean"); }
                case 'I' -> Integer.parseInt(value);
                case 'D' -> { double number = Double.parseDouble(value); if (!Double.isFinite(number)) throw new IllegalArgumentException("Not finite"); }
                case 'S' -> { if (value.indexOf('"') >= 0 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) throw new IllegalArgumentException("Reserved config syntax"); }
                default -> throw new IllegalArgumentException("Unknown type");
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Forge type " + type + " value: " + value, error);
        }
    }
}
