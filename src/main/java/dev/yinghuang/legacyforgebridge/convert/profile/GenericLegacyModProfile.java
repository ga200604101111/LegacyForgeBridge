package dev.yinghuang.legacyforgebridge.convert.profile;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionPlan;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModProfile;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySeatBedPresentationPass;

public final class GenericLegacyModProfile implements LegacyModProfile {
    @Override public String id(){ return "generic-forge-1.7.10"; }
    @Override public boolean matches(LegacyModMetadata metadata,String sourceHash){ return true; }
    @Override public void configure(ConversionPlan.Builder plan){
        plan.add(new GenericContentPass());
        plan.add(new LegacySeatBedPresentationPass());
    }
}
