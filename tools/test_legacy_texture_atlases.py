"""Resource-only regressions for the Minecraft 1.21.11 legacy texture fix.

Run with: python3 tools/test_legacy_texture_atlases.py
Uses only the Python standard library; does not start or simulate the client renderer.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
ATLASES = ROOT / "src/main/resources/assets/minecraft/atlases"


def load_atlas(name: str) -> dict:
    return json.loads((ATLASES / (name + ".json")).read_text(encoding="utf-8"))


def directory_sprites(name: str, resources: list[str]) -> set[str]:
    """Interpret only the directory source used by this fix, not the game engine."""
    sprites: set[str] = set()
    for source in load_atlas(name)["sources"]:
        if source["type"] != "minecraft:directory":
            raise ValueError("This test supports directory sources only")
        prefix = source["source"] + "/"
        for resource in resources:
            path = PurePosixPath(resource)
            parts = path.parts
            if len(parts) < 5 or parts[0] != "assets" or parts[2] != "textures":
                continue
            relative = "/".join(parts[3:])
            if relative.startswith(prefix) and relative.endswith(".png"):
                suffix = relative[len(prefix):-4]
                sprites.add(parts[1] + ":" + source["prefix"] + suffix)
    return sprites


class LegacyTextureAtlasTests(unittest.TestCase):
    def test_block_atlas_is_additive_and_scoped_to_legacy_blocks(self):
        self.assertEqual({"sources": [{"type": "minecraft:directory",
                         "source": "blocks", "prefix": "blocks/"}]}, load_atlas("blocks"))

    def test_item_atlas_uses_12111_items_atlas_not_blocks_atlas(self):
        self.assertEqual({"sources": [{"type": "minecraft:directory",
                         "source": "items", "prefix": "items/"}]}, load_atlas("items"))

    def test_all_namespaces_are_supported_without_mod_name_rules(self):
        resources = ["assets/first/textures/blocks/ore.png",
                     "assets/second/textures/blocks/ore.png"]
        self.assertEqual({"first:blocks/ore", "second:blocks/ore"},
                         directory_sprites("blocks", resources))

    def test_nested_texture_ids_are_preserved(self):
        self.assertEqual({"legacy:items/tools/blade"}, directory_sprites("items", [
            "assets/legacy/textures/items/tools/blade.png"]))

    def test_legacy_block_sprites_stay_available_for_item_block_models(self):
        resources = ["assets/legacy/textures/blocks/panel.png"]
        self.assertEqual({"legacy:blocks/panel"}, directory_sprites("blocks", resources))
        self.assertEqual(set(), directory_sprites("items", resources))

    def test_native_singular_paths_are_not_collected_a_second_time(self):
        resources = ["assets/example/textures/block/ore.png",
                     "assets/example/textures/item/blade.png"]
        self.assertEqual(set(), directory_sprites("blocks", resources))
        self.assertEqual(set(), directory_sprites("items", resources))

    def test_gui_entity_and_non_png_resources_are_not_stitched(self):
        resources = ["assets/legacy/textures/guis/inventory.png",
                     "assets/legacy/textures/entitys/statue.png",
                     "assets/legacy/textures/items3d/sword.png",
                     "assets/legacy/textures/items/blade.png.mcmeta",
                     "assets/legacy/textures/blocks/ore.png.mcmeta"]
        self.assertEqual(set(), directory_sprites("blocks", resources))
        self.assertEqual(set(), directory_sprites("items", resources))

    def test_blocks_and_items_have_disjoint_sprite_names(self):
        resources = ["assets/legacy/textures/blocks/shared.png",
                     "assets/legacy/textures/items/shared.png"]
        blocks = directory_sprites("blocks", resources)
        items = directory_sprites("items", resources)
        self.assertEqual({"legacy:blocks/shared"}, blocks)
        self.assertEqual({"legacy:items/shared"}, items)
        self.assertFalse(blocks & items)


if __name__ == "__main__":
    unittest.main(verbosity=2)
