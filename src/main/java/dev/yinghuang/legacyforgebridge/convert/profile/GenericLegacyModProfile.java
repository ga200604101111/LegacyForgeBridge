package dev.yinghuang.legacyforgebridge.convert.profile;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionPlan;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModProfile;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityBehaviorSurfacePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityConstantOverridePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityConstructionPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherAccessPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherGlobalClosurePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityInstantiationPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityPresentationPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityRuntimeAdmissionPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityRuntimePlanPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyGridPotPresentationProofPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityCodegenPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityConstantOverrideCodegenPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRegistrationStripPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRendererRegistrationStripPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRuntimeCandidatePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyPlainEntityRuntimePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySeatBedPresentationPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballLaunchPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimeCandidatePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRegistrationStripPass;

public final class GenericLegacyModProfile implements LegacyModProfile {
    @Override public String id(){ return "generic-forge-1.7.10"; }
    @Override public boolean matches(LegacyModMetadata metadata,String sourceHash){ return true; }
    @Override public void configure(ConversionPlan.Builder plan){
        plan.add(new GenericContentPass());
        plan.add(new LegacyVariantSnowballPass());
        plan.add(new LegacyVariantSnowballLaunchPass());
        plan.add(new LegacyVariantSnowballRuntimeCandidatePass());
        plan.add(new LegacyGridPotPresentationProofPass());
        plan.add(new LegacyEntityDataWatcherPass());
        plan.add(new LegacyVariantSnowballRuntimePass());
        plan.add(new LegacyVariantSnowballRegistrationStripPass());
        plan.add(new LegacyEntityDataWatcherAccessPass());
        plan.add(new LegacyEntityDataWatcherGlobalClosurePass());
        plan.add(new LegacyEntityRuntimePlanPass());
        plan.add(new LegacyEntityBehaviorSurfacePass());
        plan.add(new LegacyEntityConstantOverridePass());
        plan.add(new LegacyEntityConstructionPass());
        plan.add(new LegacyEntityRuntimeAdmissionPass());
        plan.add(new LegacyPlainEntityCodegenPass());
        plan.add(new LegacyPlainEntityConstantOverrideCodegenPass());
        plan.add(new LegacyEntityPresentationPass());
        plan.add(new LegacyPlainEntityRuntimeCandidatePass());
        plan.add(new LegacyPlainEntityRuntimePass());
        plan.add(new LegacyEntityInstantiationPass());
        plan.add(new LegacyPlainEntityRegistrationStripPass());
        plan.add(new LegacyPlainEntityRendererRegistrationStripPass());
        plan.add(new LegacySeatBedPresentationPass());
    }
}
