package dev.yinghuang.legacyforgebridge.config;

import dev.yinghuang.legacyforgebridge.convert.Rev227Compat;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Cloth Config editor for the global Via/vanilla BLOCK-pose visual correction. */
public final class LegacyBlockingPoseConfigScreen {
    private static final int TRANSLATION_SCALE = 100;
    private static final int TRANSLATION_MIN = Math.round(LegacyBlockingPoseConfig.TRANSLATION_MIN * TRANSLATION_SCALE);
    private static final int TRANSLATION_MAX = Math.round(LegacyBlockingPoseConfig.TRANSLATION_MAX * TRANSLATION_SCALE);
    private static final int ROTATION_MIN = Math.round(LegacyBlockingPoseConfig.ROTATION_MIN);
    private static final int ROTATION_MAX = Math.round(LegacyBlockingPoseConfig.ROTATION_MAX);

    private LegacyBlockingPoseConfigScreen() { }

    public static Screen create(Screen parent) {
        LegacyBlockingPoseConfig.Settings value = LegacyBlockingPoseConfig.current();
        LegacyBlockingPoseConfig.Settings defaults = LegacyBlockingPoseConfig.Settings.defaults();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("legacyforgebridge.config.blocking_pose.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("legacyforgebridge.config.blocking_pose.general"));
        BooleanListEntry enabled = entries.startBooleanToggle(
                        Component.translatable("legacyforgebridge.config.blocking_pose.enabled"), value.enabled())
                .setDefaultValue(defaults.enabled())
                .setTooltip(Component.translatable("legacyforgebridge.config.blocking_pose.enabled.tooltip"))
                .build();
        BooleanListEntry mirror = entries.startBooleanToggle(
                        Component.translatable("legacyforgebridge.config.blocking_pose.mirror_left"), value.mirrorLeftHand())
                .setDefaultValue(defaults.mirrorLeftHand())
                .setTooltip(Component.translatable("legacyforgebridge.config.blocking_pose.mirror_left.tooltip"))
                .build();
        general.addEntry(enabled);
        general.addEntry(mirror);
        general.addEntry(entries.startTextDescription(
                Component.translatable("legacyforgebridge.config.blocking_pose.scope")).build());

        ConfigCategory first = builder.getOrCreateCategory(
                Component.translatable("legacyforgebridge.config.blocking_pose.first_person"));
        SliderSet firstSliders = addTransformSliders(entries, first, "first_person", value.firstPerson(), defaults.firstPerson());

        ConfigCategory third = builder.getOrCreateCategory(
                Component.translatable("legacyforgebridge.config.blocking_pose.third_person"));
        SliderSet thirdSliders = addTransformSliders(entries, third, "third_person", value.thirdPerson(), defaults.thirdPerson());

        builder.setSavingRunnable(() -> LegacyBlockingPoseConfig.save(new LegacyBlockingPoseConfig.Settings(
                Rev227Compat.booleanEntryValue(enabled),
                Rev227Compat.booleanEntryValue(mirror),
                firstSliders.transform(),
                thirdSliders.transform()
        )));
        return builder.build();
    }

    private static SliderSet addTransformSliders(
            ConfigEntryBuilder entries,
            ConfigCategory category,
            String view,
            LegacyBlockingPoseConfig.Transform value,
            LegacyBlockingPoseConfig.Transform defaults
    ) {
        IntegerSliderEntry tx = translation(entries, view, "translation_x", value.translation().x(), defaults.translation().x());
        IntegerSliderEntry ty = translation(entries, view, "translation_y", value.translation().y(), defaults.translation().y());
        IntegerSliderEntry tz = translation(entries, view, "translation_z", value.translation().z(), defaults.translation().z());
        IntegerSliderEntry rx = rotation(entries, view, "rotation_x", value.rotationDegrees().x(), defaults.rotationDegrees().x());
        IntegerSliderEntry ry = rotation(entries, view, "rotation_y", value.rotationDegrees().y(), defaults.rotationDegrees().y());
        IntegerSliderEntry rz = rotation(entries, view, "rotation_z", value.rotationDegrees().z(), defaults.rotationDegrees().z());
        category.addEntry(entries.startTextDescription(Component.translatable(
                "legacyforgebridge.config.blocking_pose.translation.tooltip")).build());
        category.addEntry(tx);
        category.addEntry(ty);
        category.addEntry(tz);
        category.addEntry(entries.startTextDescription(Component.translatable(
                "legacyforgebridge.config.blocking_pose.rotation.tooltip")).build());
        category.addEntry(rx);
        category.addEntry(ry);
        category.addEntry(rz);
        return new SliderSet(tx, ty, tz, rx, ry, rz);
    }

    private static IntegerSliderEntry translation(
            ConfigEntryBuilder entries, String view, String axis, float value, float defaultValue) {
        return entries.startIntSlider(
                        Component.translatable("legacyforgebridge.config.blocking_pose." + axis),
                        Math.round(value * TRANSLATION_SCALE), TRANSLATION_MIN, TRANSLATION_MAX)
                .setDefaultValue(Math.round(defaultValue * TRANSLATION_SCALE))
                .setTextGetter(number -> Component.literal(String.format(Locale.ROOT, "%.2f", number / (float) TRANSLATION_SCALE)))
                .build();
    }

    private static IntegerSliderEntry rotation(
            ConfigEntryBuilder entries, String view, String axis, float value, float defaultValue) {
        return entries.startIntSlider(
                        Component.translatable("legacyforgebridge.config.blocking_pose." + axis),
                        Math.round(value), ROTATION_MIN, ROTATION_MAX)
                .setDefaultValue(Math.round(defaultValue))
                .setTextGetter(number -> Component.literal(number + "°"))
                .build();
    }

    private record SliderSet(
            IntegerSliderEntry tx,
            IntegerSliderEntry ty,
            IntegerSliderEntry tz,
            IntegerSliderEntry rx,
            IntegerSliderEntry ry,
            IntegerSliderEntry rz
    ) {
        LegacyBlockingPoseConfig.Transform transform() {
            return new LegacyBlockingPoseConfig.Transform(
                    new LegacyBlockingPoseConfig.Vec3(
                            Rev227Compat.intEntryValue(tx) / (float) TRANSLATION_SCALE,
                            Rev227Compat.intEntryValue(ty) / (float) TRANSLATION_SCALE,
                            Rev227Compat.intEntryValue(tz) / (float) TRANSLATION_SCALE),
                    new LegacyBlockingPoseConfig.Vec3(
                            Rev227Compat.intEntryValue(rx),
                            Rev227Compat.intEntryValue(ry),
                            Rev227Compat.intEntryValue(rz))
            );
        }
    }
}
