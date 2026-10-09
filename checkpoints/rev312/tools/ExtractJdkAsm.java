import java.nio.file.*;
import java.net.URI;
public class ExtractJdkAsm {
 public static void main(String[] a)throws Exception{
  Path src=FileSystems.getFileSystem(URI.create("jrt:/")).getPath("/modules/java.base/jdk/internal/org/objectweb/asm");
  Path out=Path.of(a[0]);
  try(var paths=Files.walk(src)){for(Path p:paths.filter(Files::isRegularFile).toList()){
   Path d=out.resolve(src.relativize(p).toString());Files.createDirectories(d.getParent());Files.copy(p,d,StandardCopyOption.REPLACE_EXISTING);
  }}
 }
}
