package dev.yinghuang.lfb.jumpprobe;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import java.util.*;

/** Adds side-effect-contained observer calls. No operand, argument, return or field is replaced. */
public final class JumpProbeTransformer implements IClassTransformer {
    private static final String PROBE = "dev/yinghuang/lfb/jumpprobe/JumpProbe";
    private static final String EVENT_DESC = "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V";
    private static final Set<String> TARGETS = new HashSet<String>(Arrays.asList(
        "net.minecraft.network.NetHandlerPlayServer", "net.minecraft.client.network.NetHandlerPlayClient",
        "net.minecraft.network.NetworkManager", "net.minecraft.network.play.server.S12PacketEntityVelocity",
        "net.minecraft.entity.EntityLivingBase", "net.minecraftforge.common.ForgeHooks",
        "net.minecraft.client.entity.EntityClientPlayerMP", "net.minecraft.entity.EntityTrackerEntry",
        "net.minecraft.client.Minecraft", "net.minecraft.server.MinecraftServer"));

    public byte[] transform(String name, final String transformedName, byte[] bytes) {
        if (bytes == null || !TARGETS.contains(transformedName) || !JumpProbe.enabled()) return bytes;
        try {
            final List<String> matched = new ArrayList<String>();
            ClassReader reader = new ClassReader(bytes);
            final boolean[] instrumented = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int a,String b,String c,String d,String[] e) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitMethodInsn(int opcode,String owner,String m,String desc,boolean itf) {
                            if(owner.equals(PROBE) && m.equals("event"))instrumented[0]=true;
                        }
                    };
                }
            },0);
            if(instrumented[0]){JumpProbe.status("ALREADY_INSTRUMENTED",transformedName);return bytes;}
            final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
                @Override public MethodVisitor visitMethod(int access, final String method, final String desc, String signature, String[] exceptions) {
                    MethodVisitor base = super.visitMethod(access, method, desc, signature, exceptions);
                    if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_BRIDGE)) != 0) return base;
                    String event = null; int subject = 0, packet = -1; boolean head = true, tail = true, jumpCall = false;
                    String simple = transformedName.substring(transformedName.lastIndexOf('.') + 1);
                    if (simple.equals("NetHandlerPlayServer")) {
                        if (named(method,"processPlayer","func_147347_a") && desc.equals("(Lnet/minecraft/network/play/client/C03PacketPlayer;)V")) {
                            event="SERVER_C03";packet=1;jumpCall=true;
                        } else if (named(method,"sendPacket","func_147359_a") && desc.equals("(Lnet/minecraft/network/Packet;)V")) {
                            event="SERVER_SEND";packet=1;tail=false;
                        }
                    } else if (simple.equals("NetHandlerPlayClient")) {
                        if (named(method,"handleEntityVelocity","func_147244_a") && desc.equals("(Lnet/minecraft/network/play/server/S12PacketEntityVelocity;)V")) {
                            event="CLIENT_S12_APPLY";packet=1;
                        } else if (named(method,"handlePlayerPosLook","func_147258_a") && desc.equals("(Lnet/minecraft/network/play/server/S08PacketPlayerPosLook;)V")) {
                            event="CLIENT_S08_APPLY";packet=1;
                        }
                    } else if (simple.equals("NetworkManager")) {
                        if (method.equals("channelRead0") && desc.equals("(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V")) {
                            event="NET_RECEIVE";packet=2;tail=false;
                        } else if (named(method,"scheduleOutboundPacket","func_150725_a") && desc.equals("(Lnet/minecraft/network/Packet;[Lio/netty/util/concurrent/GenericFutureListener;)V")) {
                            event="NET_SEND_REQUEST";packet=1;tail=false;
                        } else if (named(method,"processReceivedPackets","func_74428_b") && desc.equals("()V")) {
                            event="NET_DRAIN";
                        }
                    } else if (simple.equals("S12PacketEntityVelocity") && method.equals("<init>")) {
                        if (desc.equals("(Lnet/minecraft/entity/Entity;)V")) {event="S12_CREATE_ENTITY";subject=1;packet=0;head=false;}
                        else if(desc.equals("(IDDD)V")){event="S12_CREATE_VALUES";subject=-1;packet=0;head=false;}
                    } else if (simple.equals("EntityLivingBase") && named(method,"jump","func_70664_aZ") && desc.equals("()V")) {
                        event="JUMP";
                    } else if (simple.equals("ForgeHooks") && method.equals("onLivingJump") && desc.equals("(Lnet/minecraft/entity/EntityLivingBase;)V")) {
                        event="FORGE_JUMP_EVENT";subject=0;
                    } else if (simple.equals("EntityClientPlayerMP")) {
                        if((named(method,"onUpdateWalkingPlayer","func_71166_b") || method.equals("sendMotionUpdates")) && desc.equals("()V")) event="CLIENT_WALK_SEND";
                        else if(named(method,"onUpdate","func_70071_h_") && desc.equals("()V")) event="CLIENT_PLAYER_TICK";
                    } else if(simple.equals("EntityTrackerEntry") && named(method,"updatePlayerList","func_73122_a") && desc.equals("(Ljava/util/List;)V")) {
                        event="TRACKER_TICK";
                    } else if(simple.equals("Minecraft") && named(method,"runTick","func_71407_l") && desc.equals("()V")) {
                        event="CLIENT_TICK";
                    } else if(simple.equals("MinecraftServer") && named(method,"tick","func_71217_p") && desc.equals("()V")) {
                        event="SERVER_TICK";
                    }
                    if(event==null) return base;
                    final String stage=event;final int s=subject,p=packet;final boolean start=head,end=tail,wrapJump=jumpCall;
                    matched.add(method+desc+"="+stage);
                    return new MethodVisitor(Opcodes.ASM5,base) {
                        private void observe(String label) {
                            super.visitLdcInsn(label);
                            if(s<0)super.visitInsn(Opcodes.ACONST_NULL);else super.visitVarInsn(Opcodes.ALOAD,s);
                            if(p<0)super.visitInsn(Opcodes.ACONST_NULL);else super.visitVarInsn(Opcodes.ALOAD,p);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,PROBE,"event",EVENT_DESC,false);
                        }
                        @Override public void visitCode() {super.visitCode();if(start)observe(stage+"_HEAD");}
                        @Override public void visitInsn(int opcode) {
                            if(end && opcode==Opcodes.RETURN)observe(stage+"_RETURN");
                            super.visitInsn(opcode);
                        }
                        @Override public void visitMethodInsn(int opcode,String owner,String target,String descriptor,boolean itf) {
                            // Exact call-site identity: this C03 caused this invocation, not a nearest-time guess.
                            boolean mark=wrapJump && opcode==Opcodes.INVOKEVIRTUAL && named(target,"jump","func_70664_aZ") && descriptor.equals("()V")
                                && (owner.equals("net/minecraft/entity/player/EntityPlayerMP") || owner.equals("net/minecraft/entity/player/EntityPlayer") || owner.equals("net/minecraft/entity/EntityLivingBase"));
                            if(mark)observe("SERVER_C03_JUMP_CALL_BEFORE");
                            super.visitMethodInsn(opcode,owner,target,descriptor,itf);
                            if(mark){observe("SERVER_C03_JUMP_CALL_AFTER");matched.add("exact-C03-jump-call");}
                        }
                    };
                }
            },0);
            JumpProbe.status("TRANSFORM",transformedName+" hooks="+matched.toString());
            return matched.isEmpty()?bytes:writer.toByteArray();
        } catch(Throwable error) {
            JumpProbe.status("TRANSFORM_FAILED",transformedName+" "+error.getClass().getName());
            return bytes;
        }
    }
    private static boolean named(String actual,String mcp,String srg){return actual.equals(mcp)||actual.equals(srg);}
}
