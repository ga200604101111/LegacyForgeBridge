package dev.yinghuang.legacyforgebridge.convert.runtime;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shared current-runtime implementation of the source-proven legacy cloning recipe family.
 *
 * <p>The old recipe counts occupied crafting slots, not ItemStack counts. Exactly one filled source
 * item and one-or-more blank items are accepted; all other non-empty inputs reject. Output count is
 * blank-slot-count + 1. Only the legacy data value and, when source-proven, CUSTOM_NAME are copied.
 * Arbitrary modern components are deliberately not cloned.</p>
 */
public final class ConvertedLegacyCloneRecipe extends CustomRecipe {
    public static final Identifier SERIALIZER_ID = Identifier.fromNamespaceAndPath(
            LegacyForgeBridge.MOD_ID, "legacy_clone");

    private static final MapCodec<ConvertedLegacyCloneRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("full_item").forGetter(ConvertedLegacyCloneRecipe::fullItemId),
                    Identifier.CODEC.fieldOf("blank_item").forGetter(ConvertedLegacyCloneRecipe::blankItemId),
                    Codec.BOOL.optionalFieldOf("copy_custom_name", false)
                            .forGetter(ConvertedLegacyCloneRecipe::copyCustomName)
            ).apply(instance, ConvertedLegacyCloneRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ConvertedLegacyCloneRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, ConvertedLegacyCloneRecipe::fullItemId,
                    Identifier.STREAM_CODEC, ConvertedLegacyCloneRecipe::blankItemId,
                    ByteBufCodecs.BOOL, ConvertedLegacyCloneRecipe::copyCustomName,
                    ConvertedLegacyCloneRecipe::new);

    private static final Serializer SERIALIZER_IMPLEMENTATION = new Serializer();
    private static volatile RecipeSerializer<ConvertedLegacyCloneRecipe> serializer = SERIALIZER_IMPLEMENTATION;

    private final Identifier fullItemId;
    private final Identifier blankItemId;
    private final boolean copyCustomName;

    public ConvertedLegacyCloneRecipe(Identifier fullItemId, Identifier blankItemId, boolean copyCustomName) {
        super(CraftingBookCategory.MISC);
        if (fullItemId == null || blankItemId == null || fullItemId.equals(blankItemId)) {
            throw new IllegalArgumentException("Legacy clone recipe requires two distinct item identifiers");
        }
        this.fullItemId = fullItemId;
        this.blankItemId = blankItemId;
        this.copyCustomName = copyCustomName;
    }

    public static synchronized void bootstrap() {
        if (BuiltInRegistries.RECIPE_SERIALIZER.containsKey(SERIALIZER_ID)) {
            RecipeSerializer<?> existing = BuiltInRegistries.RECIPE_SERIALIZER.getValue(SERIALIZER_ID);
            if (existing != SERIALIZER_IMPLEMENTATION) {
                throw new IllegalStateException("Recipe serializer id already occupied: " + SERIALIZER_ID);
            }
            serializer = SERIALIZER_IMPLEMENTATION;
            return;
        }
        serializer = Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                SERIALIZER_ID,
                SERIALIZER_IMPLEMENTATION);
    }

    public Identifier fullItemId() {
        return fullItemId;
    }

    public Identifier blankItemId() {
        return blankItemId;
    }

    public boolean copyCustomName() {
        return copyCustomName;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return scan(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = scan(input);
        if (match == null) return ItemStack.EMPTY;

        Item fullItem = BuiltInRegistries.ITEM.getValue(fullItemId);
        if (fullItem == null) return ItemStack.EMPTY;
        ItemStack output = new ItemStack(fullItem, match.blankSlots() + 1);

        LegacyStackComponents.set(output, LegacyStackComponents.get(match.fullStack()));
        if (copyCustomName) {
            var customName = match.fullStack().get(DataComponents.CUSTOM_NAME);
            if (customName != null) output.set(DataComponents.CUSTOM_NAME, customName);
        }
        return output;
    }

    @Override
    public RecipeSerializer<ConvertedLegacyCloneRecipe> getSerializer() {
        return serializer;
    }

    private Match scan(CraftingInput input) {
        if (input == null) return null;
        if (!BuiltInRegistries.ITEM.containsKey(fullItemId)
                || !BuiltInRegistries.ITEM.containsKey(blankItemId)) return null;

        Item fullItem = BuiltInRegistries.ITEM.getValue(fullItemId);
        Item blankItem = BuiltInRegistries.ITEM.getValue(blankItemId);
        if (fullItem == null || blankItem == null || fullItem == blankItem) return null;

        ItemStack fullStack = null;
        int blankSlots = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.is(fullItem)) {
                if (fullStack != null) return null;
                fullStack = stack;
            } else if (stack.is(blankItem)) {
                blankSlots++;
            } else {
                return null;
            }
        }
        return fullStack != null && blankSlots > 0 ? new Match(fullStack, blankSlots) : null;
    }

    private record Match(ItemStack fullStack, int blankSlots) { }

    private static final class Serializer implements RecipeSerializer<ConvertedLegacyCloneRecipe> {
        @Override
        public MapCodec<ConvertedLegacyCloneRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ConvertedLegacyCloneRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
