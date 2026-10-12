
package dev.yinghuang.legacyforgebridge.repair322;
import java.util.*;
/** Data-only 1.7 ModelBiped/ModelRenderer construction ABI. No rendering or world mutation. */
public final class ArmorAbi323 {
 public static class Base {public int field_78090_t=64,field_78089_u=32;public boolean field_78091_s;}
 public record Box(int u,int v,float x,float y,float z,int dx,int dy,int dz,float expand,boolean mirror){}
 public static class Part {
  public float field_78798_e,field_78800_c,field_78797_d; // pivot z,x,y
  public float field_78795_f,field_78796_g,field_78808_h; // pitch,yaw,roll
  public boolean field_78809_i,field_78806_j=true,field_78807_k;
  public int u,v;public final List<Box> boxes=new ArrayList<>();public final List<Part> children=new ArrayList<>();
  public Part(Base b,int u,int v){this.u=u;this.v=v;}public Part(Base b){this(b,0,0);}
  public Part func_78784_a(int u,int v){this.u=u;this.v=v;return this;}
  public Part func_78787_b(int w,int h){return this;}
  public Part func_78789_a(float x,float y,float z,int dx,int dy,int dz){func_78790_a(x,y,z,dx,dy,dz,0);return this;}
  public void func_78790_a(float x,float y,float z,int dx,int dy,int dz,float expand){if(boxes.size()>128||dx<0||dy<0||dz<0||dx>128||dy>128||dz>128)throw new IllegalArgumentException("invalid source cuboid");boxes.add(new Box(u,v,x,y,z,dx,dy,dz,expand,field_78809_i));}
  public void func_78793_a(float x,float y,float z){field_78800_c=x;field_78797_d=y;field_78798_e=z;}
  public void func_78792_a(Part child){if(children.size()>128||child==this)throw new IllegalArgumentException("invalid bone hierarchy");children.add(child);}
 }
 public static class Biped extends Base {
  public Part field_78116_c,field_78114_d,field_78115_e,field_78112_f,field_78113_g,field_78123_h,field_78124_i;public boolean field_78117_n;public int field_78120_m;
  public Biped(float expand){this(expand,0,64,32);}public Biped(){this(0);}
  public Biped(float expand,float y,int w,int h){field_78090_t=w;field_78089_u=h;
   field_78116_c=part(0,0,-4,-8,-4,8,8,8,expand,0,y,0,false);field_78114_d=part(32,0,-4,-8,-4,8,8,8,expand+.5f,0,y,0,false);
   field_78115_e=part(16,16,-4,0,-2,8,12,4,expand,0,y,0,false);
   field_78112_f=part(40,16,-3,-2,-2,4,12,4,expand,-5,2+y,0,false);field_78113_g=part(40,16,-1,-2,-2,4,12,4,expand,5,2+y,0,true);
   field_78123_h=part(0,16,-2,0,-2,4,12,4,expand,-1.9f,12+y,0,false);field_78124_i=part(0,16,-2,0,-2,4,12,4,expand,1.9f,12+y,0,true);
  }
  private Part part(int u,int v,float x,float y,float z,int a,int b,int c,float ex,float px,float py,float pz,boolean mirror){Part p=new Part(this,u,v);p.field_78809_i=mirror;p.func_78790_a(x,y,z,a,b,c,ex);p.func_78793_a(px,py,pz);return p;}
  public Map<String,Part> roots(){Map<String,Part> p=new LinkedHashMap<>();p.put("head",field_78116_c);p.put("hat",field_78114_d);p.put("body",field_78115_e);p.put("right_arm",field_78112_f);p.put("left_arm",field_78113_g);p.put("right_leg",field_78123_h);p.put("left_leg",field_78124_i);return p;}
 }
}
