package dev.yinghuang.legacyforgebridge.convert.profile;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionPlan;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModProfile;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityBehaviorSurfacePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityConstructionPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherAccessPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherGlobalClosurePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityRuntimeAdmissionPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityRuntimePlanPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityCodegenPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySeatBedPresentationPass;

public final class GenericLegacyModProfile implements LegacyModProfile {
    @Override public String id(){ return "generic-forge-1.7.10"; }
    @Override public boolean matches(LegacyModMetadata metadata,String sourceHash){ return true; }
    @Override public void configure(ConversionPlan.Builder plan){
        plan.add(new GenericContentPass());
        plan.add(new LegacyEntityDataWatcherPass());
        plan.add(new LegacyEntityDataWatcherAccessPass());
        plan.add(new LegacyEntityDataWatcherGlobalClosurePass());
        plan.add(new LegacyEntityRuntimePlanPass());
        plan.add(new LegacyEntityBehaviorSurfacePass());
        plan.add(new LegacyEntityConstructionPass());
        plan.add(new LegacyEntityRuntimeAdmissionPass());
        plan.add(new LegacyPlainEntityCodegenPass());
        plan.add(new LegacySeatBedPresentationPass());
    }
}
