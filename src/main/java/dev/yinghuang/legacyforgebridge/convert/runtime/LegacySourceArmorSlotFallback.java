package dev.yinghuang.legacyforgebridge.convert.runtime;
/** Runtime adapter for source-verified legacy ItemArmor constructor slot, never a mod/name selector. */
public final class LegacySourceArmorSlotFallback {
    private LegacySourceArmorSlotFallback() { }
    public static int choose(String kind, int preexistingProof) {
        // Existing provenance always takes precedence. The semantic converter emits this kind
        // only if a source-owned ItemArmor constructor has one fixed armorType argument 0..3.
        if (preexistingProof >= 0 && preexistingProof <= 3) return preexistingProof;
        if (kind != null && kind.length() == 12 && kind.startsWith("armor_slot_")) {
            char digit = kind.charAt(11);
            if (digit >= '0' && digit <= '3') return digit - '0';
        }
        return -1;
    }
}
