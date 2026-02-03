from PIL import Image
import os

path = r'd:\Code\Project\MinecraftServer\plugins\MiniMOTD\icons\homie.png'
try:
    if os.path.exists(path):
        img = Image.open(path)
        img = img.resize((64, 64), Image.Resampling.NEAREST)
        img.save(path)
        print("Resized successfully to 64x64")
    else:
        print("File not found")
except Exception as e:
    print(f"Error: {e}")
