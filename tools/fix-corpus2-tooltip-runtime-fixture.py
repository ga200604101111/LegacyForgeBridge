#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorTooltipEventRuntimeTest.java"
text=p.read_text(encoding="utf-8")
if "@BeforeAll" in text:
    print("tooltip runtime fixture already bootstraps Minecraft")
    raise SystemExit(0)
text=text.replace('import net.minecraft.core.component.DataComponents;\n','import net.minecraft.SharedConstants;\nimport net.minecraft.core.component.DataComponents;\nimport net.minecraft.server.Bootstrap;\n')
text=text.replace('import org.junit.jupiter.api.Test;\n','import org.junit.jupiter.api.BeforeAll;\nimport org.junit.jupiter.api.Test;\n')
needle='class LegacyBehaviorTooltipEventRuntimeTest {\n'
replacement='class LegacyBehaviorTooltipEventRuntimeTest {\n    @BeforeAll static void bootstrapMinecraft(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}\n\n'
if needle not in text: raise SystemExit("runtime fixture class shape not found")
p.write_text(text.replace(needle,replacement,1),encoding="utf-8")
print("Bootstrapped Minecraft before native tooltip runtime fixture")
