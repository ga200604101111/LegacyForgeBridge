package dev.yinghuang.legacyforgebridge.convert.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedLegacyVariantSnowballImpactRuntimeTest {
    @Test
    void impactRuntimeContainsSourceProvenDamageEffectsTeleportPresentationAndTermination() throws Exception {
        String resource = "/" + ConvertedLegacyVariantSnowballProjectile.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input =
                     ConvertedLegacyVariantSnowballProjectile.class.getResourceAsStream(resource)) {
            assertNotNull(input);

            boolean[] thrownDamage = {false};
            boolean[] hurtServer = {false};
            boolean[] addEffect = {false};
            boolean[] potionInstance = {false};
            boolean[] blockCollisions = {false};
            boolean[] entityCollisions = {false};
            boolean[] liquid = {false};
            boolean[] particles = {false};
            boolean[] sounds = {false};
            boolean[] discard = {false};
            boolean[] poison = {false};
            boolean[] nausea = {false};
            boolean[] regeneration = {false};

            new ClassReader(input.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("onHit")
                            && !name.equals("randomTeleport")
                            && !name.equals("portalPresentation")
                            && !name.equals("effect")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (owner.endsWith("/DamageSources")
                                    && methodName.equals("thrown")) thrownDamage[0] = true;
                            if (methodName.equals("hurtServer")
                                    && methodDescriptor.endsWith(";F)Z")) hurtServer[0] = true;
                            if (methodName.equals("addEffect")
                                    && methodDescriptor.contains("/MobEffectInstance;")) addEffect[0] = true;
                            if (owner.endsWith("/MobEffectInstance")
                                    && methodName.equals("<init>")
                                    && methodDescriptor.contains("Holder;II")) potionInstance[0] = true;
                            if (methodName.equals("getBlockCollisions")) blockCollisions[0] = true;
                            if (methodName.equals("getEntityCollisions")) entityCollisions[0] = true;
                            if (methodName.equals("containsAnyLiquid")) liquid[0] = true;
                            if (methodName.equals("sendParticles")) particles[0] = true;
                            if (methodName.equals("playSound")) sounds[0] = true;
                            if (methodName.equals("discard")) discard[0] = true;
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name,
                                                   String descriptor) {
                            if (!owner.endsWith("/MobEffects")) return;
                            if (name.equals("POISON")) poison[0] = true;
                            if (name.equals("NAUSEA")) nausea[0] = true;
                            if (name.equals("REGENERATION")) regeneration[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(thrownDamage[0]);
            assertTrue(hurtServer[0]);
            assertTrue(addEffect[0]);
            assertTrue(potionInstance[0]);
            assertTrue(blockCollisions[0]);
            assertTrue(entityCollisions[0]);
            assertTrue(liquid[0]);
            assertTrue(particles[0]);
            assertTrue(sounds[0]);
            assertTrue(discard[0]);
            assertTrue(poison[0]);
            assertTrue(nausea[0]);
            assertTrue(regeneration[0]);
        }
    }
}
