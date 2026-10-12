
package dev.yinghuang.legacyforgebridge.repair322;
import dev.yinghuang.legacyforgebridge.repair322.VisualSupport322.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.*;
import java.util.*;import java.util.concurrent.*;import java.util.function.*;
/** Dynamic source numeric geometry, built into LFB and backed by converted JAR resources. */
public final class VisualClient322 implements ClientModInitializer {
 static final class_2350[] F={class_2350.field_11033,class_2350.field_11036,class_2350.field_11043,class_2350.field_11035,class_2350.field_11039,class_2350.field_11034};
 static final Set<String> traced=ConcurrentHashMap.newKeySet();
 static void once(String s){if(traced.add(s))System.out.println("[LFB visual322] "+s);}
 public void onInitializeClient(){VisualRuntime322.load();for(String id:VisualRuntime322.PLANTS.keySet()){class_2960 n=class_2960.method_60654(id);if(class_7923.field_41175.method_10250(n))BlockRenderLayerMap.putBlock(class_7923.field_41175.method_63535(n),class_11515.field_60925);}
  ModelLoadingPlugin.register(ctx->ctx.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,c)->{String id=class_7923.field_41175.method_10221(c.state().method_26204()).toString();var p=VisualRuntime322.PLANTS.get(id);if(p!=null){Map<String,class_1058> sprites=new HashMap<>();for(String texture:p.textures)sprites.put(texture,c.baker().method_65732().method_65739(new class_4730(class_1059.field_5275,class_2960.method_60654(texture)),()->id));return new PlantModel(model,p,sprites);}var grid=VisualRuntime322.GRIDS.get(id);return grid!=null?new GridModel(model,grid):model;}));
  VisualSelfTest322.register();once("CLIENT_READY source geometry + position-dependent grid UV");
 }
 static int face(class_2350 f){for(int i=0;i<6;i++)if(F[i]==f)return i;return -1;}
 static int color(class_2680 s,class_1920 w,class_2338 p){int rgb=class_310.method_1551().method_1505().method_1697(s,w,p,0);return rgb==-1?0xffffffff:0xff000000|rgb;}
 static void emit(QuadEmitter e,class_1058 sprite,int side,double[] positions,double[] uv,int color){
  e.cullFace(null).nominalFace(side<0?null:F[side]).renderLayer(class_11515.field_60925).tintIndex(-1).ambientOcclusion(TriState.FALSE);
  for(int i=0;i<4;i++){e.pos(i,(float)positions[i*3],(float)positions[i*3+1],(float)positions[i*3+2]);e.uv(i,(float)uv[i*2],(float)uv[i*2+1]);e.color(i,color);}e.spriteBake(sprite,32);e.emit();
 }
 static void plane(QuadEmitter e,class_1058 sprite,double[] v,int color){double[] uv={0,0,0,1,1,1,1,0};emit(e,sprite,-1,v,uv,color);double[] reverse=new double[12];for(int i=0;i<4;i++)System.arraycopy(v,(3-i)*3,reverse,i*3,3);emit(e,sprite,-1,reverse,uv,color);}
 static final class PlantModel extends WrapperBlockStateModel {
  final VisualRuntime322.Plant rule;final Map<String,class_1058> sprites;
  PlantModel(class_1087 wrapped,VisualRuntime322.Plant p,Map<String,class_1058> sprites){super(wrapped);rule=p;this.sprites=Map.copyOf(sprites);}
  @Override public Object createGeometryKey(class_1920 w,class_2338 p,class_2680 s,class_5819 r){return null;}
  @Override public void emitQuads(QuadEmitter emitter,class_1920 world,class_2338 pos,class_2680 state,class_5819 random,Predicate<class_2350> cull){int meta=ConvertedLegacyBlock.legacyMeta(state);if(!rule.icons.containsKey(meta)){super.emitQuads(emitter,world,pos,state,random,cull);return;}
   try {var instance=rule.instances.get();Block b=instance.block();World view=VisualRuntime322.view(b,world,pos,state);int x=pos.method_10263(),y=pos.method_10264(),z=pos.method_10260();b.func_149719_a(view,x,y,z);Renderer renderer=new Renderer(b,meta);instance.drawer().renderWorldBlock(view,x,y,z,b,0,renderer);int tint=color(state,world,pos);
    for(Draw d:renderer.draws){if(d.kind().equals("BOX")){double[] a=d.bounds();if(a[1]==a[4]){double[] top={a[0],a[1],a[2],a[0],a[1],a[5],a[3],a[1],a[5],a[3],a[1],a[2]};double[] uv={a[0],a[2],a[0],a[5],a[3],a[5],a[3],a[2]};emit(emitter,Objects.requireNonNull(sprites.get(d.icons()[1])),1,top,uv,tint);double[] bottom=new double[12],backUv=new double[8];for(int i=0;i<4;i++){System.arraycopy(top,(3-i)*3,bottom,i*3,3);System.arraycopy(uv,(3-i)*2,backUv,i*2,2);}emit(emitter,Objects.requireNonNull(sprites.get(d.icons()[0])),0,bottom,backUv,tint);continue;}for(var f:LegacyGeometry.surfaces(List.of(new LegacyGeometry.Box(a[0],a[1],a[2],a[3],a[4],a[5])))){double[] p=f.positions().stream().mapToDouble(Double::doubleValue).toArray(),uv=f.uv().stream().mapToDouble(Double::doubleValue).toArray();emit(emitter,Objects.requireNonNull(sprites.get(d.icons()[f.side()])),f.side(),p,uv,tint);}}
     else if(d.kind().equals("CROPS")){class_1058 sprite=Objects.requireNonNull(sprites.get(d.icons()[0]));for(double v:new double[]{.25,.75}){plane(emitter,sprite,new double[]{v,1,0,v,0,0,v,0,1,v,1,1},tint);plane(emitter,sprite,new double[]{0,1,v,0,0,v,1,0,v,1,1,v},tint);}}
     else {class_1058 sprite=Objects.requireNonNull(sprites.get(d.icons()[0]));plane(emitter,sprite,new double[]{.05,1,.05,.05,0,.05,.95,0,.95,.95,1,.95},tint);plane(emitter,sprite,new double[]{.95,1,.05,.95,0,.05,.05,0,.95,.05,1,.95},tint);}}
    once("WORLD_GEOMETRY "+rule.id+" meta="+meta+" draws="+renderer.draws.size());
   }catch(RuntimeException ex){once("WORLD_GEOMETRY_ERROR "+rule.id+" "+ex);super.emitQuads(emitter,world,pos,state,random,cull);}
  }
 }
 static final class GridModel extends WrapperBlockStateModel {
  final VisualRuntime322.Grid rule;GridModel(class_1087 model,VisualRuntime322.Grid rule){super(model);this.rule=rule;}
  @Override public Object createGeometryKey(class_1920 w,class_2338 p,class_2680 s,class_5819 r){return null;}
  @Override public void emitQuads(QuadEmitter emitter,class_1920 world,class_2338 pos,class_2680 state,class_5819 random,Predicate<class_2350> cull){int x=pos.method_10263(),y=pos.method_10264(),z=pos.method_10260(),n=rule.size();
   emitter.pushTransform(q->{int side=face(q.lightFace());if(side<0)return true;class_2338 neighbor=pos.method_10093(F[side]);if(Math.floorDiv(x,n)==Math.floorDiv(neighbor.method_10263(),n)&&Math.floorDiv(y,n)==Math.floorDiv(neighbor.method_10264(),n)&&Math.floorDiv(z,n)==Math.floorDiv(neighbor.method_10260(),n)&&world.method_8320(neighbor).method_26204()==state.method_26204())return false;
    GridIcon slice=rule.program().func_149673_e(null,x,y,z,side);float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;for(int i=0;i<4;i++){minU=Math.min(minU,q.u(i));maxU=Math.max(maxU,q.u(i));minV=Math.min(minV,q.v(i));maxV=Math.max(maxV,q.v(i));}float du=maxU-minU,dv=maxV-minV;for(int i=0;i<4;i++)q.uv(i,minU+(q.u(i)-minU+du*slice.u())/n,minV+(q.v(i)-minV+dv*slice.v())/n);return true;});
   try{super.emitQuads(emitter,world,pos,state,random,cull);once("WORLD_GRID "+rule.id()+" grid="+n);}finally{emitter.popTransform();}
  }
 }
}
