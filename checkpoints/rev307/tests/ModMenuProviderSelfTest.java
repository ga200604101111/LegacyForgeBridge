import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import dev.yinghuang.legacyforgebridge.config.LegacyForgeBridgeModMenu;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.metadata.ModMetadata;
import java.nio.file.*;
import java.util.*;

public final class ModMenuProviderSelfTest {
    private static class Converted implements ModContainer {
        private final String id;private final boolean yes;
        Converted(String id,boolean yes) {this.id=id;this.yes=yes;}
        public ModMetadata getMetadata() {return new ModMetadata(){public String getId(){return id;}public String getName(){return id;}};}
        public Optional<Path> findPath(String path) {return yes && path.equals("legacyforgebridge/conversion-manifest.json") ? Optional.of(Path.of("manifest")) : Optional.empty();}
    }
    public static void main(String[] unused) {
        LoaderAccess.instance=new FabricLoader(){
            public Collection<ModContainer> getAllMods(){return List.of(new Converted("bamboomod",true),new Converted("iymts_mod",true),new Converted("twilightforest",true),new Converted("unrelated",false));}
            public Path getConfigDir(){return Path.of("/tmp");}
            public Path getGameDir(){return Path.of("/tmp");}
        };
        LegacyForgeBridgeModMenu provider=new LegacyForgeBridgeModMenu();
        Map<String,ConfigScreenFactory<?>> screens=provider.getProvidedConfigScreenFactories();
        if(screens.size()!=3 || !screens.keySet().containsAll(List.of("bamboomod","iymts_mod","twilightforest")) || screens.containsKey("unrelated"))throw new AssertionError(screens.keySet().toString());
        if(provider.getModConfigScreenFactory()==null) throw new AssertionError("Lost LFG own config");
        System.out.println("ModMenuProviderSelfTest PASS three converted mod gears, unrelated mod excluded, LFG gear retained");
    }
}
