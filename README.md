## 🌿 Biome Scanner Mod Description

Biome Scanner is a utility mod that analyzes the biomes surrounding the player and reports how much space each biome occupies inside a chosen radius. It performs a structured sampling pass around the player and measures the relative coverage of every biome it finds. The mod can scan a single dimension or every dimension available on the server. It identifies the first position where each biome appears, counts how many samples belong to each biome, and computes the coverage percentage of each biome within the scanned area. It also detects which biomes never appeared during the scan even if the dimension is capable of generating them. Results can be printed in chat or saved to a timestamped text file for further analysis.

---

## 🧭 Command Description  
### `/biome_scan_all <radius> [step] [save]`

This command performs a complete biome scan around the player in every dimension on the server. The scan measures the biome distribution inside a circular radius centered on the player. The scanner walks through the area with a configurable step that controls how dense the sampling grid will be.

### **Parameters**

**radius**  
Minimum 100 and maximum 60000. Defines the distance around the player that will be scanned.

**step**  
Optional. Minimum 4 and maximum 512. Controls how far apart sample points are. A smaller step produces a more detailed scan but takes longer.

**save**  
Optional literal keyword. When present the mod writes the scan results to a text file stored inside the biome_scan_results folder of the world.

---

### **What the command reports**

For each dimension the output includes

- the number of detected biomes  
- the number of missing biomes  
- the total number of samples taken  
- the step used for the scan  
- the closest position where each biome was found  
- the coverage percentage of each biome inside the scanned radius  

The twenty nearest biomes are shown directly in chat. Additional biomes are counted but hidden to keep the output readable. When the save option is used the full report is written to a text file.
