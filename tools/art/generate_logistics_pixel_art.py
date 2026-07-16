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
CYAN_LIGHT = "#8be2d6"
GREEN_DARK = "#1f5b3a"
GREEN = "#3f9b5c"
GREEN_LIGHT = "#89cd88"
RED_DARK = "#7a2926"
RED = "#c84a3b"
RED_LIGHT = "#ef7d55"
IRON_DARK = "#59626c"
IRON = "#a9b0b5"
IRON_LIGHT = "#e2e1d7"


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

    # Front-facing shipping manifest: parcel, route arrow, destination, and rows.
    d.polygon([(3, 1), (12, 1), (13, 2), (13, 13),
               (12, 14), (3, 14), (2, 13), (2, 2)], fill=OUTLINE)
    rect(d, (3, 2, 12, 13), DARK_WOOD)
    rect(d, (3, 3, 11, 12), PAPER_DARK)
    rect(d, (4, 3, 11, 12), PAPER)
    rect(d, (4, 3, 10, 3), PAPER_LIGHT)
    rect(d, (4, 4, 4, 11), PAPER_LIGHT)

    # Brass clip makes the silhouette read as a viewing sheet at inventory scale.
    rect(d, (6, 0, 9, 1), OUTLINE)
    rect(d, (5, 1, 10, 3), OUTLINE)
    rect(d, (6, 1, 9, 2), GOLD)
    rect(d, (7, 1, 8, 1), GOLD_LIGHT)

    # A boxed shipment moves along the cyan arrow into a marked destination.
    rect(d, (4, 5, 6, 7), OUTLINE)
    rect(d, (5, 5, 6, 6), WOOD_LIGHT)
    rect(d, (5, 5, 5, 5), WOOD_SHINE)
    rect(d, (7, 6, 9, 6), CYAN_DARK)
    rect(d, (8, 5, 10, 7), CYAN)
    rect(d, (10, 4, 11, 8), CYAN_DARK)
    rect(d, (10, 5, 10, 7), CYAN_LIGHT)
    rect(d, (11, 6, 11, 6), GOLD_LIGHT)

    # Two compact ledger rows preserve the "table" reading without tiny text.
    rect(d, (5, 9, 5, 9), GOLD_DARK)
    rect(d, (7, 9, 10, 9), WOOD)
    rect(d, (5, 11, 5, 11), CYAN_DARK)
    rect(d, (7, 11, 10, 11), WOOD)
    return out


def inspection_device() -> Image.Image:
    out = image()
    d = ImageDraw.Draw(out)

    # A storage crate under a diagonal magnifier stays legible at native 16px size.
    d.polygon([(1, 7), (7, 7), (9, 9), (9, 14),
               (8, 15), (1, 15), (0, 14), (0, 8)], fill=OUTLINE)
    rect(d, (1, 8, 7, 14), WOOD)
    rect(d, (2, 8, 6, 9), WOOD_LIGHT)
    rect(d, (1, 10, 7, 11), DARK_WOOD)
    rect(d, (3, 10, 5, 12), GOLD_DARK)
    rect(d, (4, 10, 4, 11), GOLD_LIGHT)
    rect(d, (2, 12, 6, 14), WOOD_LIGHT)

    # The heavy diagonal handle avoids the old lollipop-like centered silhouette.
    d.polygon([(8, 7), (10, 9), (5, 15), (2, 15), (2, 13)], fill=OUTLINE)
    d.polygon([(8, 8), (9, 9), (4, 14), (3, 14)], fill=WOOD_LIGHT)

    d.polygon([(8, 0), (12, 0), (15, 3), (15, 6),
               (12, 9), (8, 9), (5, 6), (5, 3)], fill=OUTLINE)
    d.polygon([(8, 1), (12, 1), (14, 3), (14, 6),
               (12, 8), (8, 8), (6, 6), (6, 3)], fill=GOLD_DARK)
    d.polygon([(8, 2), (11, 2), (13, 4), (13, 6),
               (11, 7), (8, 7), (7, 6), (7, 3)], fill=GREEN_DARK)
    rect(d, (8, 3, 11, 6), GREEN)
    rect(d, (8, 3, 9, 4), GREEN_LIGHT)
    rect(d, (7, 2, 9, 2), GOLD_LIGHT)
    rect(d, (7, 3, 7, 4), GOLD_SHINE)
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

    # Mixed bottle, scrap, and apple silhouettes make the open crate read as "misc".
    rect(d, (2, 2, 4, 3), OUTLINE)
    rect(d, (3, 1, 4, 2), OUTLINE)
    d.polygon([(2, 3), (5, 3), (6, 6), (5, 8), (2, 8), (1, 6)], fill=OUTLINE)
    rect(d, (3, 2, 3, 3), CYAN_LIGHT)
    rect(d, (2, 4, 4, 6), CYAN)
    rect(d, (3, 4, 4, 4), CYAN_LIGHT)
    rect(d, (2, 7, 5, 7), CYAN_DARK)

    d.polygon([(6, 2), (7, 1), (8, 2), (11, 5),
               (10, 7), (8, 6), (5, 3)], fill=OUTLINE)
    rect(d, (6, 2, 7, 3), IRON_LIGHT)
    rect(d, (8, 3, 9, 5), IRON)
    rect(d, (9, 5, 10, 6), IRON_DARK)

    rect(d, (11, 2, 13, 2), GREEN_DARK)
    rect(d, (12, 1, 12, 2), GREEN)
    d.polygon([(10, 3), (13, 3), (15, 5), (14, 8),
               (10, 8), (9, 5)], fill=OUTLINE)
    rect(d, (10, 4, 13, 7), RED)
    rect(d, (11, 4, 12, 4), RED_LIGHT)
    rect(d, (14, 5, 14, 6), RED_DARK)

    # Deep open top and crossed wooden slats keep the lower half unmistakably a crate.
    d.polygon([(2, 6), (13, 6), (14, 8), (13, 13),
               (12, 14), (3, 14), (1, 13), (1, 8)], fill=OUTLINE)
    d.polygon([(2, 7), (13, 7), (13, 12), (12, 13),
               (3, 13), (2, 12)], fill=WOOD)
    rect(d, (2, 7, 13, 8), DARK_WOOD)
    rect(d, (3, 9, 12, 12), WOOD_LIGHT)
    rect(d, (3, 9, 4, 12), WOOD_SHINE)
    for x, y in [(5, 9), (6, 10), (7, 10), (8, 11), (9, 12), (10, 12)]:
        rect(d, (x, y, x, y), DARK_WOOD)
    for x, y in [(10, 9), (9, 10), (8, 10), (7, 11), (6, 12), (5, 12)]:
        rect(d, (x, y, x, y), DARK_WOOD)
    rect(d, (2, 13, 13, 13), DARK_WOOD)
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
