package dev.yinghuang.legacyforgebridge.behavior;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Runs on the log writer, never on a render/Netty producer. Reads version metadata only. */
final class Rev243RuntimeMeta {
    static String capture() {
        StringBuilder b=new StringBuilder("RUNTIME java="+System.getProperty("java.version")
                +" build="+dev.yinghuang.legacyforgebridge.BuildInfo.VERSION);
        try {
            Path path=Path.of(LegacyMotionTraceLog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(Files.isRegularFile(path)) {
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);}
                b.append(" loadedContainerSha256=").append(HexFormat.of().formatHex(digest.digest()));
            } else b.append(" loadedContainerSha256=unavailable-directory");
        } catch(Exception e) { b.append(" loadedContainerSha256=unavailable-").append(e.getClass().getSimpleName()); }
        for(String mod:new String[]{"minecraft","fabricloader","fabric-api","viafabricplus"}) {
            b.append(' ').append(mod).append('=');
            try {
                Class<?> loader=Class.forName("net.fabricmc.loader.api.FabricLoader");Object instance=loader.getMethod("getInstance").invoke(null);
                Optional<?> container=(Optional<?>)loader.getMethod("getModContainer",String.class).invoke(instance,mod);
                if(container.isEmpty()){b.append("not-loaded");continue;}
                Object metadata=Class.forName("net.fabricmc.loader.api.ModContainer").getMethod("getMetadata").invoke(container.get());
                Object version=Class.forName("net.fabricmc.loader.api.metadata.ModMetadata").getMethod("getVersion").invoke(metadata);
                b.append(Class.forName("net.fabricmc.loader.api.Version").getMethod("getFriendlyString").invoke(version));
            } catch(ReflectiveOperationException|RuntimeException|LinkageError e) { b.append("unavailable"); }
        }
        return b.toString();
    }
}
