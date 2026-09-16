package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedPresentationRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlock;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_11659;
import net.minecraft.class_11683;
import net.minecraft.class_11954;
import net.minecraft.class_12075;
import net.minecraft.class_12249;
import net.minecraft.class_1921;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_4587;
import net.minecraft.class_4608;
import net.minecraft.class_5614;
import net.minecraft.class_630;
import net.minecraft.class_7833;
import net.minecraft.class_827;

/** Exact world renderer for a source-proven two-texture, four-cuboid legacy bed/seat TESR. */
public final class ConvertedLegacySeatBedRenderer implements class_827<ConvertedLegacySeatBedBlockEntity,ConvertedLegacySeatBedRenderer.State> {
    private final LegacySeatBedPresentationRegistry.Rule rule;private final Map<String,class_630> parts=new LinkedHashMap<>();private final class_1921 footRenderType,headRenderType;
    public ConvertedLegacySeatBedRenderer(class_5614.class_5615 context,LegacySeatBedPresentationRegistry.Rule rule){this.rule=rule;for(var source:rule.parts())parts.put(source.field(),part(source,rule.modelTextureWidth(),rule.modelTextureHeight()));footRenderType=class_12249.method_75990(rule.footTexture());headRenderType=class_12249.method_75990(rule.headTexture());}
    @Override public State method_74335(){return new State();}
    @Override public void extractRenderState(ConvertedLegacySeatBedBlockEntity blockEntity,State state,float tickProgress,class_243 cameraPos,@Nullable class_11683.class_11792 crumblingOverlay){class_827.super.method_74331(blockEntity,state,tickProgress,cameraPos,crumblingOverlay);state.legacyMeta=ConvertedLegacySeatBedBlock.legacyMeta(blockEntity.method_11010());}
    @Override public void submit(State state,class_4587 matrices,class_11659 queue,class_12075 cameraState){int direction=state.legacyMeta&3;matrices.method_22903();matrices.method_46416(rule.translateXByDirection().get(direction),0F,rule.translateZByDirection().get(direction));float yaw=rule.yawDegreesByDirection().get(direction);if(yaw!=0F)matrices.method_22907(class_7833.field_40716.rotationDegrees(yaw));renderGroup(rule.footParts(),footRenderType,state,matrices,queue);renderGroup(rule.headParts(),headRenderType,state,matrices,queue);matrices.method_22909();}
    private void renderGroup(List<String> group,class_1921 type,State state,class_4587 matrices,class_11659 queue){for(String field:group){class_630 part=parts.get(field);if(part==null)throw new IllegalStateException("Missing seat-bed model part "+field);queue.method_73492(part,matrices,type,state.field_62676,class_4608.field_21444,null,0xFFFFFFFF,state.field_62677);}}
    @Override public boolean method_3563(){return rule.expandedRenderBoundsProven();}
    static class_630 part(LegacySeatBedPresentationRegistry.Part s,int tw,int th){class_630.class_628 cube=new class_630.class_628(s.u(),s.v(),s.x(),s.y(),s.z(),s.width(),s.height(),s.depth(),0F,0F,0F,s.mirror(),tw,th,EnumSet.allOf(class_2350.class));class_630 part=new class_630(List.of(cube),Map.of());part.field_3657=s.pivotX();part.field_3656=s.pivotY();part.field_3655=s.pivotZ();part.field_3654=s.xRot();part.field_3675=s.yRot();part.field_3674=s.zRot();return part;}
    public static final class State extends class_11954 { int legacyMeta; }
}
