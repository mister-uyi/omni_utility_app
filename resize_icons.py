import os
from PIL import Image

logo_path = "/Users/uyioriaghan/.gemini/antigravity-cli/brain/4b2cfe4d-b635-4154-a27f-e71a60e7e0ef/laresto_omni_utility_logo_1782702516889.jpg"
res_dir = "/Users/uyioriaghan/Documents/data/my_stuff/my_utility_app/app/src/main/res"

# mipmap densities and their standard launcher icon sizes
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

try:
    img = Image.open(logo_path)
    # Ensure it's square
    w, h = img.size
    min_dim = min(w, h)
    img = img.crop(((w - min_dim) // 2, (h - min_dim) // 2, (w + min_dim) // 2, (h + min_dim) // 2))

    for density, size in densities.items():
        density_dir = os.path.join(res_dir, density)
        os.makedirs(density_dir, exist_ok=True)
        
        # Resize
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save as ic_launcher.png and ic_launcher_round.png
        resized_img.save(os.path.join(density_dir, "ic_launcher.png"), "PNG")
        resized_img.save(os.path.join(density_dir, "ic_launcher_round.png"), "PNG")
        
    print("Icons successfully generated and replaced.")
except Exception as e:
    print(f"Error: {e}")
