
package dev.yinghuang.legacyforgebridge.repair322;
import java.util.*;
/** Small visual-only ABI. No old Forge classes, game code or world writes are loaded. */
public final class VisualSupport322 {
  private VisualSupport322() {}
  public interface World {
    int func_72805_g(int x,int y,int z);
    Block func_147439_a(int x,int y,int z);
  }
  public static class Block {
    public double minX,minY,minZ,maxX=1,maxY=1,maxZ=1;
    public String[][] icons = new String[16][];
    public void func_149676_a(float a,float b,float c,float d,float e,float f) {bounds(a,b,c,d,e,f);}
    public void bounds(double a,double b,double c,double d,double e,double f) {
      double[] bds={a,b,c,d,e,f};
      for(double n:bds) if(!Double.isFinite(n)||n < -0.001||n>1.001) throw new IllegalArgumentException("visual bounds outside unit cell");
      if(a>d||b>e||c>f) throw new IllegalArgumentException("negative visual extent");
      minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;
    }
    public void func_149719_a(World w,int x,int y,int z) {}
    public String func_149691_a(int side,int meta) {return icons[meta][side];}
    public double[] bounds() {return new double[]{minX,minY,minZ,maxX,maxY,maxZ};}
  }
  public interface Drawer {
    boolean renderWorldBlock(World w,int x,int y,int z,Block b,int type,Renderer r);
  }
  public record Draw(String kind,double[] bounds,String[] icons) {
    public Draw {bounds=bounds.clone();icons=icons.clone();}
  }
  public static final class Renderer {
    public double field_147859_h,field_147855_j,field_147851_l,field_147861_i,field_147857_k,field_147853_m;
    public String field_147840_d;
    public final List<Draw> draws = new ArrayList<>();
    public final int meta;
    public Renderer(Block b,int meta) {this.meta=meta;double[] v=b.bounds();func_147782_a(v[0],v[1],v[2],v[3],v[4],v[5]);}
    public void func_147782_a(double a,double b,double c,double d,double e,double f) {field_147859_h=a;field_147855_j=b;field_147851_l=c;field_147861_i=d;field_147857_k=e;field_147853_m=f;}
    public void func_147775_a(Block b) {double[] v=b.bounds();func_147782_a(v[0],v[1],v[2],v[3],v[4],v[5]);}
    public void func_147771_a() {field_147840_d=null;}
    public void func_147757_a(String texture) {field_147840_d=texture;}
    private boolean draw(Block b,String kind) {
      if(draws.size()>=64) throw new IllegalStateException("source draw budget exhausted");
      String[] icons=b.icons[meta].clone();if(field_147840_d!=null) Arrays.fill(icons,field_147840_d);
      double[] bounds={field_147859_h,field_147855_j,field_147851_l,field_147861_i,field_147857_k,field_147853_m};
      draws.add(new Draw(kind,bounds,icons));return true;
    }
    public boolean func_147784_q(Block b,int x,int y,int z) {return draw(b,"BOX");}
    public boolean func_147746_l(Block b,int x,int y,int z) {return draw(b,"CROSS");}
    public boolean func_147796_n(Block b,int x,int y,int z) {return draw(b,"CROPS");}
  }
  public static World probe(Block b,int meta,int x,int y,int z,int connections) {
    return new World(){
      public int func_72805_g(int a,int c,int d) {return func_147439_a(a,c,d)==b?meta:0;}
      public Block func_147439_a(int a,int c,int d) {
        if(a==x&&c==y&&d==z)return b;
        if(c!=y)return null;
        if(a==x+1&&d==z&&(connections&1)!=0)return b;
        if(a==x-1&&d==z&&(connections&2)!=0)return b;
        if(a==x&&d==z+1&&(connections&4)!=0)return b;
        if(a==x&&d==z-1&&(connections&8)!=0)return b;
        return null;
      }
    };
  }
  public record GridIcon(int u,int v,int side) {}
  public abstract static class GridProgram {
    public abstract GridIcon func_149673_e(World w,int x,int y,int z,int side);
  }
  public static GridIcon[][][] gridIcons(int n) {
    GridIcon[][][] v=new GridIcon[n][n][6];
    for(int x=0;x<n;x++)for(int y=0;y<n;y++)for(int f=0;f<6;f++)v[x][y][f]=new GridIcon(x,y,f);
    return v;
  }
}
