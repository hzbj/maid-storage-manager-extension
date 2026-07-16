"""Generate crisp 16x16 inventory sprites for the extension.

The silhouettes were selected from built-in image-generation concept drafts and
then redrawn one pixel at a time against Maid Storage Manager 1.15.6 references.
No generated high-resolution image is downscaled into the game: every output
pixel is intentional, fully opaque or fully transparent, and uses a compact
vanilla-style palette.
"""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
ITEM_DIR = (ROOT / "src/main/resources/assets/maid_storage_manager_extension"
            / "textures/item")

OUTLINE = "#25150d"
DARK_WOOD = "#432619"
WOOD = "#6b3f22"
WOOD_LIGHT = "#99602f"
WOOD_SHINE = "#bd7b3d"
GOLD_DARK = "#82510b"
GOLD = "#d79b18"
GOLD_LIGHT = "#f4cf55"
GOLD_SHINE = "#fff0a1"
PAPER_DARK = "#ad8053"
PAPER = "#dfbd86"
PAPER_LIGHT = "#f3ddb0"
CYAN_DARK = "#176f78"
CYAN = "#35b9bd"
GREEN_DARK = "#1f5b3a"
GREEN = "#3f9b5c"
GREEN_LIGHT = "#89cd88"


def image() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def rect(draw: ImageDraw.ImageDraw, box, color: str) -> None:
    draw.rectangle(box, fill=color)


def mailbox() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # Clear side-view mailbox silhouette on a wooden post.
    d.polygon([(3, 4), (5, 2), (10, 2), (13, 4), (13, 8),
               (11, 10), (3, 10), (2, 9), (2, 5)], fill=OUTLINE)
    d.polygon([(4, 4), (5, 3), (10, 3), (12, 4), (12, 8),
               (10, 9), (3, 9), (3, 5)], fill=WOOD)
    rect(d, (5, 3, 9, 3), WOOD_LIGHT)
    rect(d, (4, 4, 4, 8), WOOD_LIGHT)
    rect(d, (10, 4, 11, 4), WOOD_LIGHT)
    rect(d, (3, 8, 10, 9), DARK_WOOD)
    rect(d, (4, 5, 4, 7), DARK_WOOD)
    rect(d, (5, 6, 7, 6), GOLD)
    rect(d, (5, 5, 6, 5), GOLD_LIGHT)
    rect(d, (12, 4, 13, 6), GOLD_DARK)
    rect(d, (13, 4, 13, 5), GOLD_LIGHT)

    rect(d, (7, 10, 9, 14), OUTLINE)
    rect(d, (8, 10, 8, 13), WOOD_LIGHT)
    rect(d, (6, 14, 10, 14), OUTLINE)
    rect(d, (7, 14, 9, 14), DARK_WOOD)
    return out


def logistics_tracker() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # A short ledger rather than a screen or tool-shaped device.
    d.polygon([(3, 1), (12, 2), (14, 4), (13, 13),
               (4, 14), (2, 12), (2, 3)], fill=OUTLINE)
    d.polygon([(4, 2), (11, 3), (13, 4), (12, 12),
               (4, 13), (3, 11), (3, 4)], fill=DARK_WOOD)
    d.polygon([(5, 3), (10, 3), (12, 5), (11, 11),
               (5, 12), (4, 10), (4, 4)], fill=PAPER_DARK)
    d.polygon([(5, 4), (10, 4), (11, 5), (10, 10),
               (5, 11), (5, 5)], fill=PAPER)
    rect(d, (6, 4, 9, 4), PAPER_LIGHT)
    rect(d, (5, 5, 5, 8), PAPER_LIGHT)

    # One low-detail route is readable at actual inventory scale.
    for x, y in [(6, 6), (7, 7), (8, 7), (9, 8), (10, 8)]:
        rect(d, (x, y, x, y), CYAN)
    rect(d, (6, 6, 6, 6), CYAN_DARK)
    rect(d, (10, 8, 10, 8), CYAN_DARK)
    rect(d, (3, 3, 4, 4), GOLD_DARK)
    rect(d, (4, 3, 4, 3), GOLD_LIGHT)
    rect(d, (11, 11, 12, 12), GOLD_DARK)
    rect(d, (11, 11, 11, 11), GOLD_LIGHT)
    return out


def inspection_device() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # Round brass inspection lens with a short centered wooden grip.
    d.polygon([(6, 1), (10, 1), (13, 4), (14, 7), (13, 10),
               (10, 12), (6, 12), (3, 10), (2, 7), (3, 4)], fill=OUTLINE)
    d.polygon([(6, 2), (10, 2), (12, 4), (13, 7), (12, 9),
               (10, 11), (6, 11), (4, 9), (3, 7), (4, 4)], fill=GOLD_DARK)
    d.polygon([(6, 3), (10, 3), (12, 5), (12, 8), (10, 10),
               (6, 10), (4, 8), (4, 5)], fill=GOLD)
    rect(d, (5, 5, 10, 8), GREEN_DARK)
    rect(d, (6, 4, 9, 9), GREEN_DARK)
    rect(d, (6, 5, 9, 8), GREEN)
    rect(d, (6, 5, 7, 6), GREEN_LIGHT)
    rect(d, (5, 3, 8, 3), GOLD_LIGHT)
    rect(d, (5, 4, 5, 5), GOLD_SHINE)

    rect(d, (6, 11, 10, 12), OUTLINE)
    rect(d, (7, 12, 9, 15), OUTLINE)
    rect(d, (8, 12, 8, 14), WOOD_LIGHT)
    rect(d, (7, 14, 9, 15), DARK_WOOD)
    return out


def task_bell() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # Service-bell body only: no label, cord, handle, or accessory.
    rect(d, (7, 2, 8, 2), OUTLINE)
    rect(d, (6, 3, 9, 4), OUTLINE)
    rect(d, (7, 3, 8, 3), GOLD_LIGHT)
    d.polygon([(5, 4), (10, 4), (12, 7), (13, 10),
               (12, 11), (3, 11), (2, 10), (3, 7)], fill=OUTLINE)
    d.polygon([(5, 5), (10, 5), (11, 7), (12, 10),
               (3, 10), (4, 7)], fill=GOLD)
    rect(d, (5, 5, 7, 6), GOLD_LIGHT)
    rect(d, (4, 7, 5, 8), GOLD_SHINE)
    rect(d, (10, 7, 11, 9), GOLD_DARK)
    rect(d, (2, 11, 13, 13), OUTLINE)
    rect(d, (3, 11, 12, 12), DARK_WOOD)
    rect(d, (4, 11, 11, 11), WOOD_LIGHT)
    rect(d, (4, 13, 11, 13), OUTLINE)
    return out


def misc_storage_marker() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # Square crate marker, deliberately unlike Minecraft's name-tag silhouette.
    rect(d, (7, 1, 8, 2), OUTLINE)
    rect(d, (7, 1, 7, 1), GOLD_LIGHT)
    d.polygon([(3, 2), (12, 2), (14, 4), (14, 12),
               (12, 14), (3, 14), (1, 12), (1, 4)], fill=OUTLINE)
    rect(d, (3, 3, 12, 13), DARK_WOOD)
    rect(d, (2, 5, 3, 11), WOOD)
    rect(d, (12, 4, 13, 12), WOOD)
    rect(d, (4, 4, 11, 12), WOOD_LIGHT)
    rect(d, (5, 5, 10, 10), PAPER_DARK)
    rect(d, (6, 5, 9, 9), PAPER)
    rect(d, (6, 6, 8, 8), PAPER_LIGHT)
    rect(d, (7, 9, 9, 10), OUTLINE)
    rect(d, (8, 9, 8, 9), WOOD)
    rect(d, (3, 3, 4, 3), WOOD_SHINE)
    return out


def main() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    sprites = {
        "courier_warehouse_station.png": mailbox(),
        "logistics_tracker.png": logistics_tracker(),
        "inventory_maintenance_device.png": inspection_device(),
        "task_bell.png": task_bell(),
        "misc_storage.png": misc_storage_marker(),
    }
    for name, sprite in sprites.items():
        sprite.save(ITEM_DIR / name)


if __name__ == "__main__":
    main()
