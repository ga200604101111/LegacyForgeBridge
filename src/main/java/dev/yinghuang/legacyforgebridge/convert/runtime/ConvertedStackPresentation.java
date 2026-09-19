package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The same proven subtype name/model is used for local creative and source-produced stacks. */
public final class ConvertedStackPresentation {
    private ConvertedStackPresentation() { }
    public static ItemStack create(Item item,int metadata) {
        ItemStack stack=new ItemStack(item);LegacyStackComponents.set(stack,metadata);return stack;
    }
    public static void apply(ItemStack stack,int metadata) {
        if(stack==null||stack.isEmpty())return;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name=ConvertedItemNameCatalog.translationKey(id,metadata);
        if(name!=null&&!name.isBlank())stack.set(DataComponents.ITEM_NAME,Component.translatable(name));
        String model=ConvertedIconModelCatalog.itemModel(id,metadata);
        if(model!=null)stack.set(DataComponents.ITEM_MODEL,Identifier.parse(model));
        // CUSTOM_NAME is deliberately untouched. Metadata is not durability and is never set as
        // minecraft:damage here; the wire adapter handles genuinely damageable source items.
    }
}
