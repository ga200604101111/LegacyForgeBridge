package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

/** Shared LFB-owned menu type used by all generic three-slot converted processors. */
public final class LegacyProcessorMenuSupport {
    private static final Identifier ID=Identifier.fromNamespaceAndPath(LegacyForgeBridge.MOD_ID,"legacy_processor");
    private static volatile MenuType<ConvertedLegacyProcessorMenu> type;
    private LegacyProcessorMenuSupport() { }

    public static synchronized void bootstrap(){
        if(type!=null)return;
        if(BuiltInRegistries.MENU.containsKey(ID)){
            @SuppressWarnings("unchecked")
            MenuType<ConvertedLegacyProcessorMenu> existing=
                    (MenuType<ConvertedLegacyProcessorMenu>)BuiltInRegistries.MENU.getValue(ID);
            type=existing;
            return;
        }
        type=Registry.register(
                BuiltInRegistries.MENU,
                ID,
                new MenuType<>(ConvertedLegacyProcessorMenu::new,FeatureFlagSet.of()));
    }

    public static MenuType<ConvertedLegacyProcessorMenu> type(){
        MenuType<ConvertedLegacyProcessorMenu> value=type;
        if(value==null){bootstrap();value=type;}
        return value;
    }
}
