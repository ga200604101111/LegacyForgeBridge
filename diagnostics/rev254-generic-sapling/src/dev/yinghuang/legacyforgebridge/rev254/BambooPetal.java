package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import org.joml.Quaternionf;
/** Modern visual adaptation of the source client-only petal Entity; no network entity. */
public final class BambooPetal extends class_3940 {
 private float rx,ry,rz,rad;
 private boolean xp=true,yp=true,zp=true,stopFall;
 private final Quaternionf rotation=new Quaternionf();
 public BambooPetal(class_638 w,double x,double y,double z,class_1058 sprite,int meta,class_5819 random){
  super(w,x,y,z,sprite);
  method_3080(.2F,.2F);method_3063(x,y,z);
  field_3862=true;field_17867=5F/64F;
  rad=(float)(.001F+.025D*random.method_43057());
  field_3852=(random.method_43057()-.5D)*.1D;field_3869=-.01D;field_3850=(random.method_43057()-.5D)*.1D;
  field_3847=random.method_43048(120)+60;
  rx=random.method_43057();ry=random.method_43057();rz=random.method_43057();
  int c=BambooRules.color(meta);method_74305((c>>16)/255F,((c>>8)&255)/255F,(c&255)/255F);
 }
 @Override protected class_11941 method_74255(){return class_11941.field_62639;}
 @Override public void method_3070(){
  field_3858=field_3874;field_3838=field_3854;field_3856=field_3871;
  if(field_3866++>=field_3847)method_3085();
  field_3869=stopFall?field_3869/1.2D:BambooRules.fallingVelocity(field_3869);
  method_3069(field_3852,field_3869,field_3850);
  field_3852=BambooRules.damping(field_3852);field_3869=BambooRules.damping(field_3869);field_3850=BambooRules.damping(field_3850);
  if(!stopFall&&field_3851.method_8320(new class_2338((int)(field_3874+.5D),(int)field_3854,(int)(field_3871+.5D))).method_26204()==class_2246.field_10382){
   stopFall=true;field_3866=0;rad=.002F;field_3852=field_3850=0D;
  }
  if(field_3845)rad=.0001F;
 }
 @Override public void method_3074(class_11944 sink,class_4184 camera,float partial){
  rx+=xp?rad:-rad;xp=xp?rx<=1F:rx<=-1F;
  ry+=yp?rad:-rad;yp=yp?ry<=1F:ry<=-1F;
  rz+=zp?rad:-rad;zp=zp?rz<=1F:rz<=-1F;
  float n=rx*rx+ry*ry+rz*rz;
  rotation.rotationAxis((float)Math.PI,n<1E-12F?1F:rx,n<1E-12F?0F:ry,n<1E-12F?0F:rz);
  method_60373(sink,camera,rotation,partial);
 }
}