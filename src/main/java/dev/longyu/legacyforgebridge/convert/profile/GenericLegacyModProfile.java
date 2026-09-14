package dev.longyu.legacyforgebridge.convert.profile;

import dev.longyu.legacyforgebridge.convert.api.ConversionPlan;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.api.LegacyModProfile;
import dev.longyu.legacyforgebridge.convert.pass.GenericContentPass;

public final class GenericLegacyModProfile implements LegacyModProfile {
    @Override
    public String id() {
        return "generic-forge-1.7.10";
    }

    @Override
    public boolean matches(LegacyModMetadata metadata, String sourceHash) {
        return true;
    }

    @Override
    public void configure(ConversionPlan.Builder plan) {
        plan.add(new GenericContentPass());
    }
}
