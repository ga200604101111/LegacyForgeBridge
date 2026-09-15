package dev.yinghuang.legacyforgebridge.convert.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConversionPlan {
    private final String profileId;
    private final List<ConversionPass> passes;

    private ConversionPlan(String profileId, List<ConversionPass> passes) {
        this.profileId = profileId;
        this.passes = List.copyOf(passes);
    }

    public static Builder builder(String profileId) {
        return new Builder(profileId);
    }

    public String profileId() {
        return profileId;
    }

    public List<ConversionPass> passes() {
        return passes;
    }

    public static final class Builder {
        private final String profileId;
        private final List<ConversionPass> passes = new ArrayList<>();
        private final Set<String> passIds = new HashSet<>();

        private Builder(String profileId) {
            this.profileId = profileId;
        }

        public Builder add(ConversionPass pass) {
            if (!passIds.add(pass.id())) {
                throw new IllegalArgumentException("Duplicate conversion pass id: " + pass.id());
            }
            passes.add(pass);
            return this;
        }

        public ConversionPlan build() {
            return new ConversionPlan(profileId, passes);
        }
    }
}
