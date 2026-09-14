#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MAPPER=ROOT/'src/main/java/dev/longyu/legacyforgebridge/protocol/ViaLegacySoundMappings.java'
MIXIN=ROOT/'src/main/java/dev/longyu/legacyforgebridge/mixin/client/LegacyLocalPlayerSoundMixin.java'
MIXINS=ROOT/'src/main/resources/legacyforgebridge.client.mixins.json'
MAPPER_TEST=ROOT/'src/test/java/dev/longyu/legacyforgebridge/protocol/ViaLegacySoundMappingsTest.java'
MIXIN_TEST=ROOT/'src/test/java/dev/longyu/legacyforgebridge/mixin/client/LegacyLocalPlayerSoundMixinBytecodeTest.java'

MAPPER.parent.mkdir(parents=True,exist_ok=True)
MAPPER.write_text(r'''package dev.longyu.legacyforgebridge.protocol;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.FullMappings;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Reversible vanilla sound-identifier path recovered from the active ViaVersion pipeline. */
public final class ViaLegacySoundMappings {
    private static final String BACKWARDS_MAPPING_DATA = "com.viaversion.viabackwards.api.data.BackwardsMappingData";

    private ViaLegacySoundMappings() { }

    /**
     * Converts one modern built-in sound identifier down to the exact pre-1.9 name visible to
     * Forge 1.7.10, while retaining the inverse mapping path for a source callback replacement.
     */
    public static Optional<SoundPath> toLegacy(String modernIdentifier) {
        UserConnection connection = legacyConnection();
        if (connection == null) return Optional.empty();
        ProtocolInfo info = connection.getProtocolInfo();
        if (info == null || info.getPipeline() == null) return Optional.empty();
        List<Stage> stages = new ArrayList<>();
        for (Protocol<?, ?, ?, ?> protocol : info.getPipeline().pipes()) {
            MappingData data = protocol.getMappingData();
            if (data == null) continue;
            FullMappings sounds = data.getFullSoundMappings();
            if (sounds == null) continue;
            if (isBackwards(data)) {
                stages.add(new Stage(sounds::mappedIdentifier, sounds::identifier));
            } else {
                stages.add(new Stage(sounds::identifier, sounds::mappedIdentifier));
            }
        }
        return trace(modernIdentifier, stages);
    }

    static Optional<SoundPath> trace(String modernIdentifier, List<Stage> stages) {
        if (modernIdentifier == null || modernIdentifier.isBlank() || stages.isEmpty()) return Optional.empty();
        String current = namespaced(modernIdentifier);
        List<Stage> applied = new ArrayList<>();
        for (Stage stage : stages) {
            String next = stage.down().apply(current);
            if (next == null) return Optional.empty();
            current = next;
            applied.add(stage);
        }
        return Optional.of(new SoundPath(stripMinecraftNamespace(current), List.copyOf(applied)));
    }

    static Stage stageForTest(Function<String,String> down, Function<String,String> up) {
        return new Stage(down, up);
    }

    public static final class SoundPath {
        private final String legacyName;
        private final List<Stage> stages;

        private SoundPath(String legacyName, List<Stage> stages) {
            this.legacyName = legacyName;
            this.stages = stages;
        }

        public String legacyName() { return legacyName; }

        public Optional<String> toModern(String legacyReplacement) {
            if (legacyReplacement == null || legacyReplacement.isBlank()) return Optional.empty();
            String current = namespaced(legacyReplacement);
            List<Stage> reversed = new ArrayList<>(stages);
            Collections.reverse(reversed);
            for (Stage stage : reversed) {
                String next = stage.up().apply(current);
                if (next == null) return Optional.empty();
                current = next;
            }
            return Optional.of(current);
        }
    }

    static record Stage(Function<String,String> down, Function<String,String> up) { }

    private static UserConnection legacyConnection() {
        try {
            if (!Via.isLoaded()) return null;
            for (UserConnection connection : Via.getManager().getConnectionManager().getConnections()) {
                if (!connection.isClientSide() || connection.getChannel() == null || !connection.getChannel().isActive()) continue;
                ProtocolInfo info = connection.getProtocolInfo();
                ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
                if (serverVersion != null && LegacyProtocolVersions.isMinecraft1710(serverVersion.getVersion())) return connection;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Via can still be initializing while the client world is changing. Fail closed.
        }
        return null;
    }

    private static boolean isBackwards(MappingData data) {
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            if (BACKWARDS_MAPPING_DATA.equals(type.getName())) return true;
        }
        return false;
    }

    private static String namespaced(String identifier) {
        return identifier.indexOf(':') < 0 ? "minecraft:" + identifier : identifier;
    }

    private static String stripMinecraftNamespace(String identifier) {
        return identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
    }
}
''',encoding='utf-8')

MIXIN.parent.mkdir(parents=True,exist_ok=True)
MIXIN.write_text(r'''package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import dev.longyu.legacyforgebridge.protocol.ViaLegacySoundMappings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LocalPlayer.class)
public abstract class LegacyLocalPlayerSoundMixin {
    @Unique private static final ThreadLocal<Boolean> legacyforgebridge$soundReentry = ThreadLocal.withInitial(() -> false);

    @Inject(method="playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",at=@At("HEAD"),cancellable=true)
    private void legacyforgebridge$sourcePlaySound(SoundEvent sound,float volume,float pitch,CallbackInfo ci) {
        if(Boolean.TRUE.equals(legacyforgebridge$soundReentry.get())
                || !ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()
                || LegacyBehaviorRegistry.events("sound").isEmpty()) return;
        Identifier modernId=BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if(modernId==null)return;
        var path=ViaLegacySoundMappings.toLegacy(modernId.toString()).orElse(null);
        if(path==null)return;
        var outcome=LegacyBehaviorRuntime.soundEvent((LocalPlayer)(Object)this,path.legacyName(),volume,pitch);
        if(outcome.canceled()){ci.cancel();return;}
        if(Objects.equals(outcome.name(),path.legacyName()))return;
        String mapped=path.toModern(outcome.name()).orElse(null);
        if(mapped==null)return;
        final Identifier replacementId;
        try{replacementId=Identifier.parse(mapped);}catch(IllegalArgumentException invalid){return;}
        if(!BuiltInRegistries.SOUND_EVENT.containsKey(replacementId))return;
        SoundEvent replacement=BuiltInRegistries.SOUND_EVENT.getValue(replacementId);
        if(replacement==null||replacement==sound)return;
        legacyforgebridge$soundReentry.set(true);
        try{((LocalPlayer)(Object)this).playSound(replacement,volume,pitch);}
        finally{legacyforgebridge$soundReentry.remove();}
        ci.cancel();
    }
}
''',encoding='utf-8')

mixins=MIXINS.read_text(encoding='utf-8')
if '"LegacyLocalPlayerSoundMixin"' not in mixins:
    old='    "LegacyHurtBehaviorMixin"\n'
    new='    "LegacyHurtBehaviorMixin",\n    "LegacyLocalPlayerSoundMixin"\n'
    if mixins.count(old)!=1: raise SystemExit('mixin list insertion point changed')
    MIXINS.write_text(mixins.replace(old,new,1),encoding='utf-8')

MAPPER_TEST.parent.mkdir(parents=True,exist_ok=True)
MAPPER_TEST.write_text(r'''package dev.longyu.legacyforgebridge.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViaLegacySoundMappingsTest {
    @Test void tracedSoundPathExposesLegacyNameAndReversesReplacementWithoutHardcodedSoundTable() {
        Map<String,String> newestToMiddle=Map.of(
                "minecraft:entity.player.hurt","minecraft:entity.player.hurt_old",
                "minecraft:entity.villager.ambient","minecraft:entity.villager.ambient_old");
        Map<String,String> middleToLegacy=Map.of(
                "minecraft:entity.player.hurt_old","minecraft:game.player.hurt",
                "minecraft:entity.villager.ambient_old","minecraft:mob.villager.idle");
        var first=stage(newestToMiddle);
        var second=stage(middleToLegacy);
        var path=ViaLegacySoundMappings.trace("minecraft:entity.player.hurt",List.of(first,second)).orElseThrow();
        assertEquals("game.player.hurt",path.legacyName());
        assertEquals("minecraft:entity.villager.ambient",path.toModern("mob.villager.idle").orElseThrow());
    }

    @Test void missingStageMappingFailsClosedInBothDirections() {
        var only=stage(Map.of("minecraft:a","minecraft:b"));
        assertTrue(ViaLegacySoundMappings.trace("minecraft:missing",List.of(only)).isEmpty());
        var path=ViaLegacySoundMappings.trace("minecraft:a",List.of(only)).orElseThrow();
        assertTrue(path.toModern("missing").isEmpty());
    }

    private static ViaLegacySoundMappings.Stage stage(Map<String,String> down){
        return ViaLegacySoundMappings.stageForTest(down::get,value->down.entrySet().stream()
                .filter(entry->entry.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null));
    }
}
''',encoding='utf-8')

MIXIN_TEST.parent.mkdir(parents=True,exist_ok=True)
MIXIN_TEST.write_text(r'''package dev.longyu.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LegacyLocalPlayerSoundMixinBytecodeTest {
    @Test void localPlayerSoundHookPinsExactModernSoundBoundaryAndUsesViaMapping() throws Exception {
        String resource="/dev/longyu/legacyforgebridge/mixin/client/LegacyLocalPlayerSoundMixin.class";
        try(InputStream stream=LegacyLocalPlayerSoundMixinBytecodeTest.class.getResourceAsStream(resource)){
            assertNotNull(stream,"Missing compiled LocalPlayer sound mixin");
            byte[] bytes=stream.readAllBytes();
            String pool=new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(pool.contains("net/minecraft/client/player/LocalPlayer"));
            assertTrue(pool.contains("playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"));
            assertTrue(pool.contains("ViaLegacySoundMappings"));
            AtomicBoolean seen=new AtomicBoolean();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if("legacyforgebridge$sourcePlaySound".equals(name)){
                        seen.set(true);
                        assertEquals("(Lnet/minecraft/sounds/SoundEvent;FFLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",descriptor);
                    }
                    return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            assertTrue(seen.get());
        }
    }
}
''',encoding='utf-8')

print('Applied reversible Via sound mapping and LocalPlayer PlaySound boundary')
