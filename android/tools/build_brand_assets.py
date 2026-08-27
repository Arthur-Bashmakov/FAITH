from pathlib import Path
from PIL import Image, ImageFilter

RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "faith_brand_icon.png"

source = Image.open(SOURCE).convert("RGB")

# Splash artwork keeps the high-resolution generated brand image.
drawable = RES / "drawable-nodpi"
drawable.mkdir(parents=True, exist_ok=True)
source.save(drawable / "faith_brand_icon.png", optimize=True)

# Launcher artwork is inset into the adaptive-icon safe area. Android applies
# the device-specific circle/squircle mask, so no rounded mask is baked in.
background = source.resize((96, 96)).filter(ImageFilter.GaussianBlur(24)).resize((1024, 1024))
foreground = source.resize((760, 760), Image.Resampling.LANCZOS)
background.paste(foreground, ((1024 - 760) // 2, (1024 - 760) // 2))

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
for folder, size in sizes.items():
    target = RES / folder
    target.mkdir(parents=True, exist_ok=True)
    icon = background.resize((size, size), Image.Resampling.LANCZOS)
    icon.save(target / "ic_launcher.png", optimize=True)
    icon.save(target / "ic_launcher_round.png", optimize=True)

print("Brand assets written to", RES)
