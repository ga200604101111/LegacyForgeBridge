package dev.yinghuang.legacyforgebridge.config.legacy;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.Rev227Compat;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Source-bound per-converted-mod Forge .cfg editor via Cloth Config. */
public final class LegacyForgeConfigScreen {
    private LegacyForgeConfigScreen() { }

    public static Screen create(Screen parent, ModContainer converted) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal(converted.getMetadata().getName() + " · 舊版模組配置"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory notice = builder.getOrCreateCategory(Component.literal("配置說明"));
        notice.addEntry(entries.startTextDescription(Component.literal(
                "此介面只編輯目前用戶端的 Forge 1.7.10 .cfg；不會變更舊版伺服器，也不會自動啟用 LFG 尚未支援的模組功能。")).build());
        notice.addEntry(entries.startTextDescription(Component.literal(
                "大部分設定須重啟後才會重新讀取。舊版專用功能的實際生效情況仍取決於轉換支援程度。")).build());

        try {
            LegacyForgeConfigRegistry.Profile profile = LegacyForgeConfigRegistry.load(converted);
            if (profile.worldScopedProperties() > 0) {
                notice.addEntry(entries.startTextDescription(Component.literal(
                        "已排除 " + profile.worldScopedProperties() +
                        " 個世界存檔專用設定；這些欄位只能在原 1.7.10 世界／伺服器的對應位置編輯。")).build());
            }
            if (profile.properties().isEmpty()) {
                notice.addEntry(entries.startTextDescription(Component.literal(
                        "這個模組未找到來源相符的配置定義，也沒有可安全辨認的既有 .cfg。為保護其他模組設定，LFG 不會猜測或產生假的選項。")).build());
                return builder.build();
            }
            Map<Path, LegacyForgeCfgFile> documents = new LinkedHashMap<>();
            Map<LegacyForgeCfgFile.Property, Supplier<String>> fields = new LinkedHashMap<>();
            Map<Path, List<LegacyForgeCfgFile.Property>> groups = profile.properties();
            for (var fileGroup : groups.entrySet()) {
                LegacyForgeCfgFile file = new LegacyForgeCfgFile(fileGroup.getKey());
                documents.put(fileGroup.getKey(), file);
                Map<String, ConfigCategory> categories = new LinkedHashMap<>();
                for (LegacyForgeCfgFile.Property property : fileGroup.getValue()) {
                    String categoryName = property.fileName() + " · " + property.category().trim();
                    ConfigCategory category = categories.computeIfAbsent(categoryName, label ->
                            builder.getOrCreateCategory(Component.literal(label)));
                    String current = file.value(property);
                    String label = property.key().trim();
                    Component title = Component.literal(label.isBlank() ? property.key() : label);
                    Component tooltip = Component.literal(property.comment().isBlank()
                            ? "Forge 配置鍵：" + property.key()
                            : property.comment());
                    if (property.type() == 'B') {
                        BooleanListEntry checkbox = entries.startBooleanToggle(title, Boolean.parseBoolean(current))
                                .setDefaultValue(Boolean.parseBoolean(property.defaultValue()))
                                .setTooltip(tooltip).build();
                        category.addEntry(checkbox);
                        fields.put(property, () -> Boolean.toString(Rev227Compat.booleanEntryValue(checkbox)));
                    } else {
                        // Use text instead of bounded numeric sliders: legacy source does not prove
                        // a safe range for every property, e.g. negative dimension/provider IDs.
                        StringListEntry input = entries.startStrField(title, current)
                                .setDefaultValue(property.defaultValue())
                                .setTooltip(tooltip)
                                .setErrorSupplier(candidate -> {
                                    try {
                                        LegacyForgeCfgFile.validate(property.type(), candidate);
                                        return Optional.empty();
                                    } catch (IOException invalid) {
                                        return Optional.of(Component.literal("數值格式不正確：" + property.type()));
                                    }
                                }).build();
                        category.addEntry(input);
                        fields.put(property, () -> Rev227Compat.stringEntryValue(input));
                    }
                }
            }
            builder.setSavingRunnable(() -> {
                Map<LegacyForgeCfgFile.Property, String> pending = new LinkedHashMap<>();
                for (var field : fields.entrySet()) pending.put(field.getKey(), field.getValue().get());
                try {
                    // Check every selected value before writing any file.
                    for (var pendingEntry : pending.entrySet()) {
                        LegacyForgeCfgFile.validate(pendingEntry.getKey().type(), pendingEntry.getValue());
                    }
                    for (var fileGroup : groups.entrySet()) {
                        documents.get(fileGroup.getKey()).save(fileGroup.getValue(), pending);
                    }
                    LegacyForgeBridge.LOGGER.info("Saved local Forge config for {} from source {}",
                            converted.getMetadata().getId(), profile.sha256());
                } catch (IOException | RuntimeException invalid) {
                    // Never overwrite a contested file. A backup is created for each edited .cfg.
                    LegacyForgeBridge.LOGGER.error("Could not save Forge config for {}; changes were not fully applied",
                            converted.getMetadata().getId(), invalid);
                }
            });
        } catch (IOException | RuntimeException problem) {
            LegacyForgeBridge.LOGGER.warn("Cannot prepare Forge config screen for {}",
                    converted.getMetadata().getId(), problem);
            notice.addEntry(entries.startTextDescription(Component.literal(
                    "無法安全讀取模組配置：" + problem.getMessage())).build());
        }
        return builder.build();
    }
}
