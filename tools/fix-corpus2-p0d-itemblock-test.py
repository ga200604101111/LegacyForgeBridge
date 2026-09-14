#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "src/test/java/dev/longyu/legacyforgebridge/convert/pass/LegacyBehaviorItemBlockPassTest.java"
text = TEST.read_text(encoding="utf-8")
old = "            var sourceEvent=new LegacyBehaviorApi.Event();event.program().run(sourceEvent);\n"
new = "            var sourceEvent=new LegacyBehaviorApi.Event();\n            LegacyBehaviorApi.begin(mod,(key,args)->key);\n            try{event.program().run(sourceEvent);}\n            finally{LegacyBehaviorApi.end();}\n"
if new in text:
    print("ItemBlock event fixture already uses behavior invocation context")
    raise SystemExit(0)
if text.count(old) != 1:
    raise SystemExit(f"expected one raw event invocation, found {text.count(old)}")
TEST.write_text(text.replace(old,new,1),encoding="utf-8")
print("Updated ItemBlock event fixture to use runtime invocation context")
