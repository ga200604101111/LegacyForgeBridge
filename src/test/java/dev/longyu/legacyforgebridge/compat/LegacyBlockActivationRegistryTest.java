package dev.longyu.legacyforgebridge.compat;

import dev.longyu.legacyforgebridge.convert.LegacyBlockActivationCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyBlockActivationRegistryTest {
    @AfterEach
    void clearRegistry() {
        LegacyBlockActivationRegistry.clearForTests();
    }

    @Test
    void mapsModernHitDirectionToLegacySideAndEvaluatesBoundedRule() {
        Identifier id = Identifier.fromNamespaceAndPath("fixture", "side_gate");
        LegacyBlockActivationCompiler.Program program = new LegacyBlockActivationCompiler.Program(
                "side_gate", "fixture", "foreign/use/SideGate",
                "foreign/use/SideGate", "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z",
                List.of(
                        instruction(LegacyBlockActivationCompiler.Op.LOAD_INT, 6),
                        instruction(LegacyBlockActivationCompiler.Op.CONST_INT, 1),
                        instruction(LegacyBlockActivationCompiler.Op.IAND, 0),
                        instruction(LegacyBlockActivationCompiler.Op.IRETURN, 0)));
        LegacyBlockActivationRegistry.installForTests(id, program);

        assertEquals(Boolean.FALSE, LegacyBlockActivationRegistry.handled(id, hit(Direction.DOWN)));
        assertEquals(Boolean.TRUE, LegacyBlockActivationRegistry.handled(id, hit(Direction.UP)));
        assertEquals(Boolean.FALSE, LegacyBlockActivationRegistry.handled(id, hit(Direction.NORTH)));
        assertEquals(Boolean.TRUE, LegacyBlockActivationRegistry.handled(id, hit(Direction.SOUTH)));
        assertEquals(Boolean.FALSE, LegacyBlockActivationRegistry.handled(id, hit(Direction.WEST)));
        assertEquals(Boolean.TRUE, LegacyBlockActivationRegistry.handled(id, hit(Direction.EAST)));
    }

    @Test
    void absentRuleLeavesModernBlockBehaviorUntouched() {
        Identifier id = Identifier.fromNamespaceAndPath("fixture", "missing");
        assertNull(LegacyBlockActivationRegistry.handled(id, hit(Direction.UP)));
    }

    private static LegacyBlockActivationCompiler.Instruction instruction(LegacyBlockActivationCompiler.Op op,
                                                                         int operand) {
        return new LegacyBlockActivationCompiler.Instruction(op, operand, -1, List.of(), List.of());
    }

    private static BlockHitResult hit(Direction direction) {
        return new BlockHitResult(Vec3.ZERO, direction, BlockPos.ZERO, false);
    }
}
