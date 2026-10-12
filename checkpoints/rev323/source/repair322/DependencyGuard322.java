
package dev.yinghuang.legacyforgebridge.bootstrap;
import java.nio.file.*;import java.util.*;import java.net.*;
/** Isolated converter must not mix pre-ASM9 aggregators or obsolete SLF4J providers with modern modules. */
public final class DependencyGuard322 {
 public static List<Path> filter(List<Path> libraries){List<Path> result=new ArrayList<>();for(Path p:libraries){String name=p.getFileName().toString().toLowerCase(Locale.ROOT);boolean oldAsm=name.startsWith("asm-all-")||name.startsWith("asm-debug-all-")||name.startsWith("asm-deprecated-");boolean oldLogging=name.startsWith("log4j-slf4j")&&!name.startsWith("log4j-slf4j2-impl-");if(oldAsm||oldLogging){System.out.println("[LFB visual322] EXCLUDE_SHADOWING_LIBRARY "+name);continue;}result.add(p);}
  try(var test=new URLClassLoader(result.stream().map(p->{try{return p.toUri().toURL();}catch(Exception e){throw new IllegalArgumentException(e);}}).toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){test.loadClass("org.objectweb.asm.tree.ClassNode").getField("recordComponents");}catch(Exception e){throw new IllegalStateException("Converter requires a coherent ASM9 classpath",e);}
  return List.copyOf(result);
 }
}
