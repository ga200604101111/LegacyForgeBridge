#!/usr/bin/env python3
"""Apply the P0.D self-registration sanitization + item event target slice.

This is a one-shot, exact-text source patch. CI applies it, runs the full build,
and commits LegacyBehaviorCompiler.java only after all tests pass. The script is
idempotent so the tested source commit can run normal verification afterwards.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
text = TARGET.read_text(encoding="utf-8")

MARKER = "String targetItemId"
if MARKER in text and "LegacySelfRegistrationStripper stripper" in text:
    print("P0.D item event target integration already present")
    raise SystemExit(0)


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one source match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    '    public record EventBinding(String owner,String method,String descriptor,String kind) { }\n',
    '    public record EventBinding(String owner,String method,String descriptor,String kind,String targetItemId) { }\n',
    "event binding shape",
)

replace_once(
'''        List<EventBinding> events=new ArrayList<>();
        Set<String> eventKeys=new LinkedHashSet<>();
        LegacyEventAnalyzer.Analysis eventAnalysis=new LegacyEventAnalyzer().analyze(source);
        diagnostics.addAll(eventAnalysis.diagnostics());
        LegacyEventHandlerConstructionAnalyzer constructionAnalyzer=new LegacyEventHandlerConstructionAnalyzer();
''',
'''        List<EventBinding> events=new ArrayList<>();
        Set<String> eventKeys=new LinkedHashSet<>();
        LegacyEventHandlerConstructionAnalyzer constructionAnalyzer=new LegacyEventHandlerConstructionAnalyzer();
''',
    "late event analysis",
)

replace_once(
'''        classes.clear();selected.clear();fields.clear();included.clear();syntheticParentConstructors.clear();diagnostics.clear();
        prefix=bootstrap+"Source/";
        try(JarFile jar=new JarFile(source.toFile())) {
            var entries=jar.entries();while(entries.hasMoreElements()) {var e=entries.nextElement();
                if(!e.getName().endsWith(".class"))continue;
                try(InputStream input=jar.getInputStream(e)){readClass(input.readAllBytes());}
            }
        }
        var itemAnalysis=new LegacyItemRenderAnalyzer().analyzeItems(source);
''',
'''        classes.clear();selected.clear();fields.clear();included.clear();syntheticParentConstructors.clear();diagnostics.clear();
        prefix=bootstrap+"Source/";
        LegacyEventAnalyzer.Analysis eventAnalysis=new LegacyEventAnalyzer().analyze(source);
        diagnostics.addAll(eventAnalysis.diagnostics());
        Map<String,Set<String>> selfRegisterCtors=new LinkedHashMap<>();
        for(LegacyEventAnalyzer.Binding binding:eventAnalysis.bindings()) {
            if(binding.handlerClass().equals(binding.registrationOwner())&&"<init>".equals(binding.registrationMethod()))
                selfRegisterCtors.computeIfAbsent(binding.handlerClass(),ignored->new LinkedHashSet<>()).add(binding.registrationDescriptor());
        }
        LegacySelfRegistrationStripper stripper=new LegacySelfRegistrationStripper();
        try(JarFile jar=new JarFile(source.toFile())) {
            var entries=jar.entries();while(entries.hasMoreElements()) {var e=entries.nextElement();
                if(!e.getName().endsWith(".class"))continue;
                String className=e.getName().substring(0,e.getName().length()-6);
                try(InputStream input=jar.getInputStream(e)){
                    byte[] bytes=input.readAllBytes();Set<String> constructors=selfRegisterCtors.getOrDefault(className,Set.of());
                    if(!constructors.isEmpty()) {
                        var stripped=stripper.strip(bytes,constructors);
                        if(stripped.strippedSites()==0) diagnostics.add("Self-registration source site not safely strippable for "+className+" constructors "+constructors);
                        else bytes=stripped.bytes();
                    }
                    readClass(bytes);
                }
            }
        }
        var itemAnalysis=new LegacyItemRenderAnalyzer().analyzeItems(source);
''',
    "source load and sanitizer wiring",
)

replace_once(
'''            String eventKey=type+'\\u0000'+binding.method()+'\\u0000'+binding.descriptor()+'\\u0000'+kind;
            if(!eventKeys.add(eventKey))continue;

            LegacyEventHandlerConstructionAnalyzer.Strategy construction=null;
            if((m.access&Opcodes.ACC_STATIC)==0){
                var plan=constructionAnalyzer.analyze(source,type,"()V");construction=plan.strategy();
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SOURCE_CONSTRUCTOR){
                    if(!admit(new Ref(type,"<init>","()V"),"event constructor"))continue;
                }else if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY){
                    Clazz handler=classes.get(type);
                    if(handler.parent==null||!admit(new Ref(handler.parent,"<init>","()V"),"event parent constructor"))continue;
                }else{
                    diagnostics.add("event constructor "+type+": "+plan.diagnostic());continue;
                }
            }
            if(admit(r,"event "+kind)){
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY)syntheticParentConstructors.add(type);
                events.add(new EventBinding(type,r.name,r.desc,kind));
            }
''',
'''            String eventKey=type+'\\u0000'+binding.method()+'\\u0000'+binding.descriptor()+'\\u0000'+kind;
            if(!eventKeys.add(eventKey))continue;

            boolean instance=(m.access&Opcodes.ACC_STATIC)==0;
            List<ItemBinding> itemTargets=instance&&binding.handlerClass().equals(binding.registrationOwner())
                    &&"<init>".equals(binding.registrationMethod())
                    ?items.stream().filter(item->item.sourceClass().equals(type)
                    &&item.allocation().constructorDescriptor().equals(binding.registrationDescriptor())).toList():List.of();
            if(!itemTargets.isEmpty()){
                if(!admit(r,"event "+kind))continue;
                for(ItemBinding itemTarget:itemTargets)
                    events.add(new EventBinding(type,r.name,r.desc,kind,itemTarget.id()));
                continue;
            }

            LegacyEventHandlerConstructionAnalyzer.Strategy construction=null;
            if(instance){
                var plan=constructionAnalyzer.analyze(source,type,"()V");construction=plan.strategy();
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SOURCE_CONSTRUCTOR){
                    if(!admit(new Ref(type,"<init>","()V"),"event constructor"))continue;
                }else if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY){
                    Clazz handler=classes.get(type);
                    if(handler.parent==null||!admit(new Ref(handler.parent,"<init>","()V"),"event parent constructor"))continue;
                }else{
                    diagnostics.add("event constructor "+type+": "+plan.diagnostic());continue;
                }
            }
            if(admit(r,"event "+kind)){
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY)syntheticParentConstructors.add(type);
                events.add(new EventBinding(type,r.name,r.desc,kind,null));
            }
''',
    "event target selection",
)

replace_once(
'''        Map<String,Integer> locals=new LinkedHashMap<>();
        for(int i=0;i<events.size();i++){EventBinding e=events.get(i);String receiver=mapped(e.owner);if(!locals.containsKey(e.owner)){
                int local=locals.size();locals.put(e.owner,local);boolean stat=events.stream().filter(x->x.owner.equals(e.owner)).allMatch(x->(classes.get(x.owner).methods.get(new Ref(x.owner,x.method,x.descriptor)).access&Opcodes.ACC_STATIC)!=0);
                if(stat)m.visitInsn(Opcodes.ACONST_NULL);else{m.visitTypeInsn(Opcodes.NEW,receiver);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,receiver,"<init>","()V",false);}m.visitVarInsn(Opcodes.ASTORE,local);}
            m.visitLdcInsn(mod);m.visitLdcInsn(e.kind);m.visitTypeInsn(Opcodes.NEW,name+"Event"+i);m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,locals.get(e.owner));m.visitMethodInsn(Opcodes.INVOKESPECIAL,name+"Event"+i,"<init>","(L"+receiver+";)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerEvent","(Ljava/lang/String;Ljava/lang/String;L"+API+"$EventProgram;)V",false);
        }
''',
'''        Map<String,Integer> locals=new LinkedHashMap<>();
        for(int i=0;i<events.size();i++){EventBinding e=events.get(i);String receiver=mapped(e.owner);
            if(e.targetItemId()==null&&!locals.containsKey(e.owner)){
                int local=locals.size();locals.put(e.owner,local);boolean stat=events.stream().filter(x->x.owner.equals(e.owner)&&x.targetItemId()==null).allMatch(x->(classes.get(x.owner).methods.get(new Ref(x.owner,x.method,x.descriptor)).access&Opcodes.ACC_STATIC)!=0);
                if(stat)m.visitInsn(Opcodes.ACONST_NULL);else{m.visitTypeInsn(Opcodes.NEW,receiver);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,receiver,"<init>","()V",false);}m.visitVarInsn(Opcodes.ASTORE,local);}
            m.visitLdcInsn(mod);m.visitLdcInsn(e.kind);m.visitTypeInsn(Opcodes.NEW,name+"Event"+i);m.visitInsn(Opcodes.DUP);
            if(e.targetItemId()!=null){
                m.visitLdcInsn(e.targetItemId());m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"bootstrapItem","(Ljava/lang/String;)L"+REG+"$ItemDefinition;",false);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,REG+"$ItemDefinition","item","()L"+API+"$Item;",false);m.visitTypeInsn(Opcodes.CHECKCAST,receiver);
            }else m.visitVarInsn(Opcodes.ALOAD,locals.get(e.owner));
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,name+"Event"+i,"<init>","(L"+receiver+";)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerEvent","(Ljava/lang/String;Ljava/lang/String;L"+API+"$EventProgram;)V",false);
        }
''',
    "bootstrap target reuse",
)

TARGET.write_text(text, encoding="utf-8")
print("Applied P0.D item event target compiler integration")
