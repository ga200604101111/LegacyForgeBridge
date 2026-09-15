package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.util.*;

/**
 * Non-executing compiler for held-item-gated, single current-position metadata writes.
 * It evaluates typed symbolic inputs, never a source class, World, Player or ItemStack.
 * Only notification flag 2 and a discarded setter result are admitted. Unsupported
 * instructions reject the WHOLE method, even when an enumerated path would not visit them.
 */
public final class LegacyBlockActivationEffectCompiler {
    public static final String DESCRIPTOR = "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z";
    private static final int MAX_INSTRUCTIONS = 96;
    private static final String WORLD = "net/minecraft/world/World";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String ITEM_DESC = "Lnet/minecraft/item/Item;";

    public record FieldKey(String owner, String name, String descriptor) { }
    private enum Token { WORLD, PLAYER, X, Y, Z, NULL, HELD_STACK, MATCH_ITEM, OTHER_ITEM, WRITE_RESULT }

    /** bindings must contain only unambiguous source-proven, generated item identities. */
    public LegacyBlockActivationEffectPlan compile(MethodNode method, Map<FieldKey, String> bindings) {
        int forbidden = Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED;
        require(DESCRIPTOR.equals(method.desc) && (method.access & Opcodes.ACC_PUBLIC) != 0
                && (method.access & forbidden) == 0, "invalid activation callback descriptor/access");
        require(method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty(), "exception handlers unsupported");
        require(method.maxLocals >= 10 && method.maxLocals <= 256
                && method.maxStack >= 1 && method.maxStack <= MAX_INSTRUCTIONS, "source frame budget");
        List<AbstractInsnNode> code = new ArrayList<>();
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) continue;
            require(code.size() < MAX_INSTRUCTIONS, "instruction budget exceeded");
            indices.put(instruction, code.size());
            code.add(instruction);
        }
        require(!code.isEmpty(), "empty callback");
        Set<String> heldIds = new LinkedHashSet<>();
        int setters = 0;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof MethodInsnNode call) {
                require(call.getOpcode() == Opcodes.INVOKEVIRTUAL && !call.itf, "invocation kind unsupported");
                require(isHeld(call) || isItem(call) || isMeta(call) || isWrite(call) || isSneaking(call),
                        "unsupported call " + call.owner + "." + call.name + call.desc);
                if (isWrite(call)) {
                    setters++;
                    require(i + 1 < code.size() && code.get(i + 1).getOpcode() == Opcodes.POP,
                            "metadata setter return must be discarded");
                }
            } else if (instruction instanceof FieldInsnNode field) {
                if (isRemote(field)) continue;
                require(field.getOpcode() == Opcodes.GETSTATIC && ITEM_DESC.equals(field.desc), "unsupported source field");
                String identity = bindings.get(new FieldKey(field.owner, field.name, field.desc));
                require(identity != null, "unproven held-item field " + field.owner + "." + field.name);
                heldIds.add(identity);
            } else if (instruction instanceof VarInsnNode variable) {
                int op = variable.getOpcode();
                require(variable.var >= 0 && variable.var < method.maxLocals, "invalid local slot");
                require((op == Opcodes.ALOAD && (variable.var == 1 || variable.var == 5 || variable.var >= 10))
                        || (op == Opcodes.ILOAD && (variable.var >= 2 && variable.var <= 4 || variable.var == 6 || variable.var >= 10))
                        || ((op == Opcodes.ASTORE || op == Opcodes.ISTORE) && variable.var >= 10),
                        "unsupported local access " + op + "/" + variable.var);
            } else if (instruction instanceof JumpInsnNode jump) {
                require(supportedJump(jump.getOpcode()), "unsupported jump");
                target(jump.label, indices, i, code.size());
            } else if (instruction instanceof IntInsnNode integer) {
                require(integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH, "unsupported int opcode");
            } else if (instruction instanceof LdcInsnNode ldc) {
                require(ldc.cst instanceof Integer, "non-integer constant");
            } else {
                require(instruction instanceof InsnNode && supportedSimple(instruction.getOpcode()),
                        "unsupported opcode " + instruction.getOpcode());
            }
        }
        require(setters == 1, "exactly one metadata setter instruction required");
        require(heldIds.size() == 1, "exactly one proven held-item identity required");
        try {
            new Analyzer<>(new BasicVerifier()).analyze("lfb/SourceActivation", method);
        } catch (AnalyzerException | RuntimeException exception) {
            throw new IllegalArgumentException("invalid source stack/local dataflow: " + exception.getMessage(), exception);
        }
        List<Integer> outcomes = new ArrayList<>(LegacyBlockActivationEffectPlan.INPUT_COUNT);
        for (int side = 0; side < 6; side++) for (int meta = 0; meta < 16; meta++)
            for (int client = 0; client < 2; client++) for (int sneak = 0; sneak < 2; sneak++)
                for (int held = 0; held < 3; held++) {
                    try {
                        outcomes.add(evaluate(code, indices, side, meta, client != 0, sneak != 0, held).encode());
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException("effect proof failed: side=" + side + ", meta=" + meta
                                + ", client=" + client + ", sneak=" + sneak + ", held=" + held + ": " + exception.getMessage(), exception);
                    }
                }
        return new LegacyBlockActivationEffectPlan(heldIds.iterator().next(), outcomes);
    }

    private static LegacyBlockActivationEffectPlan.Decision evaluate(List<AbstractInsnNode> code,
            Map<AbstractInsnNode, Integer> indices, int side, int meta, boolean client, boolean sneak, int held) {
        Map<Integer, Object> locals = new HashMap<>();
        locals.put(1, Token.WORLD); locals.put(2, Token.X); locals.put(3, Token.Y); locals.put(4, Token.Z);
        locals.put(5, Token.PLAYER); locals.put(6, side);
        Deque<Object> stack = new ArrayDeque<>();
        int write = -1;
        int pc = 0;
        int steps = 0;
        while (pc < code.size()) {
            require(pc >= 0 && ++steps <= MAX_INSTRUCTIONS, "execution budget/target");
            AbstractInsnNode instruction = code.get(pc);
            int op = instruction.getOpcode();
            if (instruction instanceof VarInsnNode var) {
                if (op == Opcodes.ILOAD || op == Opcodes.ALOAD) {
                    Object value = locals.get(var.var);
                    require(value != null, "uninitialized local " + var.var);
                    stack.push(value);
                } else locals.put(var.var, stack.pop());
            } else if (instruction instanceof IntInsnNode val) {
                stack.push(val.operand);
            } else if (instruction instanceof LdcInsnNode val) {
                stack.push(val.cst);
            } else if (instruction instanceof FieldInsnNode field) {
                if (isRemote(field)) { expect(stack.pop(), Token.WORLD); stack.push(client ? 1 : 0); }
                else stack.push(Token.MATCH_ITEM);
            } else if (instruction instanceof MethodInsnNode call) {
                if (isHeld(call)) {
                    expect(stack.pop(), Token.PLAYER);
                    stack.push(held == LegacyBlockActivationEffectPlan.EMPTY_HAND ? Token.NULL : Token.HELD_STACK);
                } else if (isItem(call)) {
                    expect(stack.pop(), Token.HELD_STACK);
                    stack.push(held == LegacyBlockActivationEffectPlan.MATCHING_ITEM ? Token.MATCH_ITEM : Token.OTHER_ITEM);
                } else if (isSneaking(call)) {
                    expect(stack.pop(), Token.PLAYER); stack.push(sneak ? 1 : 0);
                } else if (isMeta(call)) {
                    require(write < 0, "read-after-write requires transactional world semantics");
                    position(stack); stack.push(meta);
                } else {
                    require(write < 0, "multiple writes unsupported");
                    int flags = integer(stack.pop());
                    int next = integer(stack.pop());
                    position(stack);
                    require(flags == LegacyBlockActivationEffectPlan.LEGACY_NOTIFY_FLAGS, "only notification flag 2 supported");
                    require(next >= 0 && next <= 15, "metadata outside 0..15");
                    write = next;
                    stack.push(Token.WRITE_RESULT); // Never guessed as true or false.
                }
            } else if (instruction instanceof JumpInsnNode jump) {
                boolean take;
                if (op == Opcodes.GOTO) take = true;
                else if (op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) {
                    Object value = stack.pop();
                    require(value == Token.NULL || value == Token.HELD_STACK, "unsupported null predicate");
                    take = (value == Token.NULL) == (op == Opcodes.IFNULL);
                } else if (op == Opcodes.IF_ACMPEQ || op == Opcodes.IF_ACMPNE) {
                    Object right = stack.pop(), left = stack.pop();
                    require(isItemToken(left) && isItemToken(right), "unsupported reference identity predicate");
                    take = (left == right) == (op == Opcodes.IF_ACMPEQ);
                } else {
                    int right = integer(stack.pop());
                    int left = op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ICMPLE ? integer(stack.pop()) : right;
                    if (op < Opcodes.IF_ICMPEQ) right = 0;
                    take = compare(op, left, right);
                }
                if (take) { pc = target(jump.label, indices, pc, code.size()); continue; }
            } else if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
                stack.push(op - Opcodes.ICONST_0);
            } else if (op == Opcodes.ACONST_NULL) stack.push(Token.NULL);
            else if (op == Opcodes.NOP) { }
            else if (op == Opcodes.POP) stack.pop();
            else if (op == Opcodes.INEG) stack.push(-integer(stack.pop()));
            else if (op == Opcodes.IRETURN) {
                return new LegacyBlockActivationEffectPlan.Decision((integer(stack.pop()) & 1) != 0, write);
            } else {
                int right = integer(stack.pop()), left = integer(stack.pop());
                stack.push(switch (op) {
                    case Opcodes.IADD -> left + right;
                    case Opcodes.ISUB -> left - right;
                    case Opcodes.IMUL -> left * right;
                    case Opcodes.IDIV -> left / right;
                    case Opcodes.IREM -> left % right;
                    case Opcodes.ISHL -> left << right;
                    case Opcodes.ISHR -> left >> right;
                    case Opcodes.IUSHR -> left >>> right;
                    case Opcodes.IAND -> left & right;
                    case Opcodes.IOR -> left | right;
                    case Opcodes.IXOR -> left ^ right;
                    default -> throw new IllegalArgumentException("unsupported arithmetic");
                });
            }
            pc++;
        }
        throw new IllegalArgumentException("path has no boolean return");
    }

    private static void position(Deque<Object> stack) {
        expect(stack.pop(), Token.Z); expect(stack.pop(), Token.Y); expect(stack.pop(), Token.X); expect(stack.pop(), Token.WORLD);
    }
    private static int integer(Object value) {
        require(value instanceof Integer, "expected integer, not position/object/setter-result token");
        return (Integer) value;
    }
    private static void expect(Object value, Token token) { require(value == token, "receiver/position is not callback argument " + token); }
    private static boolean isItemToken(Object value) { return value == Token.MATCH_ITEM || value == Token.OTHER_ITEM; }
    private static boolean named(String name, String mcp, String srg) { return name.equals(mcp) || name.equals(srg); }
    private static boolean isHeld(MethodInsnNode call) {
        return PLAYER.equals(call.owner) && named(call.name, "getCurrentEquippedItem", "func_71045_bC")
                && ("()L" + STACK + ";").equals(call.desc);
    }
    private static boolean isItem(MethodInsnNode call) {
        return STACK.equals(call.owner) && named(call.name, "getItem", "func_77973_b") && ("()" + ITEM_DESC).equals(call.desc);
    }
    private static boolean isMeta(MethodInsnNode call) {
        return WORLD.equals(call.owner) && named(call.name, "getBlockMetadata", "func_72805_g") && "(III)I".equals(call.desc);
    }
    private static boolean isWrite(MethodInsnNode call) {
        return WORLD.equals(call.owner) && named(call.name, "setBlockMetadataWithNotify", "func_72921_c") && "(IIIII)Z".equals(call.desc);
    }
    private static boolean isSneaking(MethodInsnNode call) {
        return (PLAYER.equals(call.owner) || ENTITY.equals(call.owner))
                && named(call.name, "isSneaking", "func_70093_af") && "()Z".equals(call.desc);
    }
    private static boolean isRemote(FieldInsnNode field) {
        return field.getOpcode() == Opcodes.GETFIELD && WORLD.equals(field.owner)
                && named(field.name, "isRemote", "field_72995_K") && "Z".equals(field.desc);
    }
    private static int target(LabelNode label, Map<AbstractInsnNode, Integer> indices, int from, int size) {
        for (AbstractInsnNode cursor = label; cursor != null; cursor = cursor.getNext()) {
            Integer value = indices.get(cursor);
            if (value != null) { require(value > from && value < size, "backward/invalid branch target"); return value; }
        }
        throw new IllegalArgumentException("missing branch target");
    }
    private static boolean supportedJump(int op) {
        return op >= Opcodes.IFEQ && op <= Opcodes.GOTO || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
    }
    private static boolean supportedSimple(int op) {
        return op == Opcodes.NOP || op == Opcodes.ACONST_NULL || op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5
                || op == Opcodes.POP || op == Opcodes.IRETURN || op == Opcodes.INEG
                || Set.of(Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR).contains(op);
    }
    private static boolean compare(int op, int a, int b) {
        return switch (op) {
            case Opcodes.IFEQ, Opcodes.IF_ICMPEQ -> a == b;
            case Opcodes.IFNE, Opcodes.IF_ICMPNE -> a != b;
            case Opcodes.IFLT, Opcodes.IF_ICMPLT -> a < b;
            case Opcodes.IFGE, Opcodes.IF_ICMPGE -> a >= b;
            case Opcodes.IFGT, Opcodes.IF_ICMPGT -> a > b;
            case Opcodes.IFLE, Opcodes.IF_ICMPLE -> a <= b;
            default -> throw new IllegalArgumentException("unsupported comparison");
        };
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
