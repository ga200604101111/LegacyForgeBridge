#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"src/test/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorTooltipEventCompilerTest.java"
text=p.read_text(encoding="utf-8")
old='t.visitLdcInsn("enchantment.level.2");t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);'
new='t.visitLdcInsn("enchantment.level.2");t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);'
if old not in text:
    if new in text:
        print("tooltip event fixture concatenation already fixed")
        raise SystemExit(0)
    raise SystemExit("tooltip event fixture concatenation shape not found")
p.write_text(text.replace(old,new,1),encoding="utf-8")
print("Fixed tooltip event fixture concatenation")
