import dev.yinghuang.legacyforgebridge.convert.LegacyFixedModelProjectileAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyModelBoxMesh1710;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Source-only reference: 2017 upstream ModelTFMoonworm / RenderTFMoonwormShot.
 * This example is intentionally NOT a production mod-name dispatch rule and not proof of
 * byte-for-byte parity with translated twilightforest-1.7.10-2.3.8-tw.jar.
 */
public final class LegacyMoonwormReferenceMeshExport {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: ... <output.obj>");
        var source=new LegacyFixedModelProjectileAnalyzer.Proof(
                "upstream/twilightforest/client/renderer/entity/RenderTFMoonwormShot",
                "upstream/twilightforest/client/model/ModelTFMoonworm",
                "twilightforest:textures/model/moonworm.png",32,32,.075F,90F,1F,0F,1F,
                List.of(
                    new LegacyFixedModelProjectileAnalyzer.Cuboid("Shape1",0,4,-1,-1,-1,4,2,2,-1,7,3),
                    new LegacyFixedModelProjectileAnalyzer.Cuboid("Shape2",0,8,-1,-1,-1,2,2,4,3,7,0),
                    new LegacyFixedModelProjectileAnalyzer.Cuboid("Shape3",0,14,-1,-1,-1,2,2,2,2,7,-2),
                    new LegacyFixedModelProjectileAnalyzer.Cuboid("head",0,0,-1,-1,-1,2,2,2,-3,7,2)));
        var mesh=LegacyModelBoxMesh1710.build(source);
        StringBuilder obj=new StringBuilder();
        obj.append("# Structural review asset only; not an executable Minecraft/Fabric renderer\n");
        obj.append("# Upstream 1.7.10 ModelTFMoonworm, not verified against translated 2.3.8-tw JAR\n");
        obj.append("# Source GL axis-angle (90deg, 1,0,1) already baked; DO NOT rotate twice\n");
        obj.append("# Original texture identity: ").append(mesh.texture()).append(" (PNG not embedded)\n");
        int start=1;
        for(var face:mesh.quads()) {
            obj.append("g ").append(face.part()).append("_").append(face.side()).append("\n");
            for(var v:List.of(face.a(),face.b(),face.c(),face.d()))
                obj.append(String.format(Locale.ROOT,"v %.9f %.9f %.9f\n",v.x(),v.y(),v.z()));
            for(var v:List.of(face.a(),face.b(),face.c(),face.d()))
                obj.append(String.format(Locale.ROOT,"vt %.9f %.9f\n",v.u(),1.0-v.v()));
            for(var v:List.of(face.a(),face.b(),face.c(),face.d()))
                obj.append(String.format(Locale.ROOT,"vn %.9f %.9f %.9f\n",v.nx(),v.ny(),v.nz()));
            obj.append("f");
            for(int k=0;k<4;k++)obj.append(' ').append(start+k).append('/').append(start+k).append('/').append(start+k);
            obj.append('\n');start+=4;
        }
        Path out=Path.of(args[0]);
        if(out.getParent()!=null)Files.createDirectories(out.getParent());
        Files.writeString(out,obj.toString(),StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT,"Exported %s: %d quads, %d triangles, %d vertices%n",
                out,mesh.quads().size(),mesh.triangleCount(),mesh.vertexCount());
    }
}
