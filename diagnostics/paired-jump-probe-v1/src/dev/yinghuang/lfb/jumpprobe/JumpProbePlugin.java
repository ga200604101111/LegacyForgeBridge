package dev.yinghuang.lfb.jumpprobe;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.io.File;
import java.util.Map;

/** Observation-only Forge 1.7.10 coremod; no Minecraft classes loaded here. */
@IFMLLoadingPlugin.Name("LFB Paired Jump Probe 1.0.0")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1101)
@IFMLLoadingPlugin.TransformerExclusions({"dev.yinghuang.lfb.jumpprobe."})
public final class JumpProbePlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[]{"dev.yinghuang.lfb.jumpprobe.JumpProbeTransformer"}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public String getAccessTransformerClass() { return null; }
    public void injectData(Map<String,Object> data) {
        Object root = data.get("mcLocation");
        JumpProbe.configure(root instanceof File ? (File)root : new File("."));
    }
}
