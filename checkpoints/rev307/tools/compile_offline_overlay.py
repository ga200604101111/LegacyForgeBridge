#!/usr/bin/env python3
"""Offline ABI exercise for remapped main JAR. Never package API stubs."""
from pathlib import Path
import shutil,subprocess,sys
root=Path(__file__).resolve().parents[1]
src=root/'src/main/java'
work=root/'offline-build'; sources=work/'src'; stubs=work/'stub-src'; classes=work/'classes';stubclasses=work/'stub-classes'
for p in [sources,stubs,classes,stubclasses]:p.mkdir(parents=True,exist_ok=True)
for s in src.rglob('*.java'):
    dest=sources/s.relative_to(src);dest.parent.mkdir(parents=True,exist_ok=True)
    content=s.read_text()
    content=content.replace('import net.minecraft.client.gui.screens.Screen;', 'import net.minecraft.class_437;')
    content=content.replace('import net.minecraft.network.chat.Component;', 'import net.minecraft.class_2561;')
    content=content.replace('Component.literal(', 'class_2561.method_43470(')
    content=content.replace('Screen create(Screen parent','class_437 create(class_437 parent')
    content=content.replace('public static Screen create(', 'public static class_437 create(')
    content=content.replace('Component title =', 'class_2561 title =')
    content=content.replace('Component tooltip =', 'class_2561 tooltip =')
    dest.write_text(content)

def stub(package,name,content):
    p=stubs/Path(package.replace('.','/'))/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text('package '+package+';\n'+content+'\n')
stub('net.minecraft','class_437','public class class_437 {}')
stub('net.minecraft','class_2561','public interface class_2561 {static class_5250 method_43470(String s) { return null; }}')
stub('net.minecraft','class_5250','public interface class_5250 extends class_2561 {}')
stub('com.terraformersmc.modmenu.api','ConfigScreenFactory','public interface ConfigScreenFactory<S extends net.minecraft.class_437> {S create(net.minecraft.class_437 parent);}')
stub('com.terraformersmc.modmenu.api','ModMenuApi','public interface ModMenuApi {default ConfigScreenFactory<?> getModConfigScreenFactory() { return null; } default java.util.Map<String,ConfigScreenFactory<?>> getProvidedConfigScreenFactories() { return java.util.Map.of(); }}')
stub('net.fabricmc.loader.api','FabricLoader','public interface FabricLoader { static FabricLoader getInstance() {return LoaderAccess.instance;} java.util.Collection<ModContainer> getAllMods(); java.nio.file.Path getGameDir(); java.nio.file.Path getConfigDir();}')
stub('net.fabricmc.loader.api','LoaderAccess','public final class LoaderAccess {public static FabricLoader instance;}')
stub('net.fabricmc.loader.api','ModContainer','public interface ModContainer { net.fabricmc.loader.api.metadata.ModMetadata getMetadata(); java.util.Optional<java.nio.file.Path> findPath(String path);}')
stub('net.fabricmc.loader.api.metadata','ModMetadata','public interface ModMetadata {String getId(); String getName();}')
stub('com.google.gson','JsonElement','public class JsonElement { public JsonObject getAsJsonObject() {return null;} public String getAsString(){return null;} }')
stub('com.google.gson','JsonObject','public class JsonObject extends JsonElement { public JsonElement get(String n){return null;} public JsonArray getAsJsonArray(String n){return null;} public boolean has(String n){return false;} }')
stub('com.google.gson','JsonArray','public class JsonArray extends JsonElement implements java.lang.Iterable<JsonElement>{public int size(){return 0;}public java.util.Iterator<JsonElement> iterator(){return java.util.Collections.emptyIterator();}}')
stub('com.google.gson','JsonParser','public class JsonParser {public static JsonElement parseReader(java.io.Reader reader){return null;}}')
stub('org.slf4j','Logger','public interface Logger {void info(String s,Object...v);void warn(String s,Object...v);void error(String s,Object...v);}')
stub('me.shedaniel.clothconfig2.api','AbstractConfigListEntry','public abstract class AbstractConfigListEntry<T> {}')
stub('me.shedaniel.clothconfig2.gui.entries','BooleanListEntry','public class BooleanListEntry extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<Boolean>{}')
stub('me.shedaniel.clothconfig2.gui.entries','IntegerSliderEntry','public class IntegerSliderEntry extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<Integer>{}')
stub('me.shedaniel.clothconfig2.gui.entries','StringListEntry','public class StringListEntry extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<String>{}')
stub('me.shedaniel.clothconfig2.gui.entries','TextListEntry','public class TextListEntry extends me.shedaniel.clothconfig2.api.AbstractConfigListEntry<net.minecraft.class_2561>{}')
stub('me.shedaniel.clothconfig2.api','ConfigCategory','public interface ConfigCategory {ConfigCategory addEntry(AbstractConfigListEntry<?> e);}')
stub('me.shedaniel.clothconfig2.api','ConfigEntryBuilder','public interface ConfigEntryBuilder {me.shedaniel.clothconfig2.impl.builders.TextDescriptionBuilder startTextDescription(net.minecraft.class_2561 text); me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder startStrField(net.minecraft.class_2561 text,String value); me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder startBooleanToggle(net.minecraft.class_2561 text,boolean value);}')
stub('me.shedaniel.clothconfig2.api','ConfigBuilder','public interface ConfigBuilder {static ConfigBuilder create(){return null;} ConfigBuilder setParentScreen(net.minecraft.class_437 parent); ConfigBuilder setTitle(net.minecraft.class_2561 text); ConfigEntryBuilder entryBuilder(); ConfigCategory getOrCreateCategory(net.minecraft.class_2561 text); ConfigBuilder setSavingRunnable(Runnable r); net.minecraft.class_437 build(); }')
stub('me.shedaniel.clothconfig2.impl.builders','TextDescriptionBuilder','public class TextDescriptionBuilder { public me.shedaniel.clothconfig2.gui.entries.TextListEntry build(){return null;} }')
stub('me.shedaniel.clothconfig2.impl.builders','StringFieldBuilder','public class StringFieldBuilder { public StringFieldBuilder setDefaultValue(String s){return this;} public StringFieldBuilder setTooltip(net.minecraft.class_2561...ts){return this;} public StringFieldBuilder setErrorSupplier(java.util.function.Function<String,java.util.Optional<net.minecraft.class_2561>> f){return this;} public me.shedaniel.clothconfig2.gui.entries.StringListEntry build(){return null;} }')
stub('me.shedaniel.clothconfig2.impl.builders','BooleanToggleBuilder','public class BooleanToggleBuilder { public BooleanToggleBuilder setDefaultValue(boolean v){return this;} public BooleanToggleBuilder setTooltip(net.minecraft.class_2561...ts){return this;} public me.shedaniel.clothconfig2.gui.entries.BooleanListEntry build(){return null;} }')
cmd=['javac','--release','21','-d',str(stubclasses)]+[str(x) for x in stubs.rglob('*.java')]
r=subprocess.run(cmd,capture_output=True,text=True);print('stub compile',r.returncode);print(r.stderr[-5000:]);
if r.returncode:sys.exit(r.returncode)
original=Path('/mnt/data/legacyforgebridge-0.2.0-alpha.27-rev306-source-bow-presentation.jar')
cmd=['javac','--release','21','-cp',str(original)+':'+str(stubclasses),'-d',str(classes)]+[str(x) for x in sources.rglob('*.java')]
r=subprocess.run(cmd,capture_output=True,text=True);print('overlay compile',r.returncode);print(r.stderr[-5000:]);sys.exit(r.returncode)
