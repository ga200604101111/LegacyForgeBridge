#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
text=p.read_text(encoding="utf-8")
old='''        }else {
            String owner=mapped(r.owner);if(JDK.contains(r.owner))return;
            try {var field=Class.forName(owner.replace('/','.')).getField(r.name);if(!Type.getDescriptor(field.getType()).equals(descriptor(r.desc)))throw new NoSuchFieldException();}
            catch(ReflectiveOperationException ex){throw new IllegalArgumentException("Unsupported API field "+r);}
        }
'''
new='''        }else {
            String owner=mapped(r.owner);if(JDK.contains(r.owner))return;
            String fieldName=canonicalField(r.owner,r.name,r.desc);
            try {var field=Class.forName(owner.replace('/','.')).getField(fieldName);if(!Type.getDescriptor(field.getType()).equals(descriptor(r.desc)))throw new NoSuchFieldException();}
            catch(ReflectiveOperationException ex){throw new IllegalArgumentException("Unsupported API field "+r);}
        }
'''
if old not in text:
    if new in text:
        print("canonical external field validation already present")
        raise SystemExit(0)
    raise SystemExit("external field validation shape not found")
p.write_text(text.replace(old,new,1),encoding="utf-8")
print("Applied canonical external field validation")
