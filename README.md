<div align="center">

# MinecraftImageGenerator

**A Java library for programmatically generating Minecraft-themed images:** item tooltips, inventories, individual items, and player heads, as static PNGs or animated GIFs.

[![JitPack](https://jitpack.io/v/Aerhhh/MinecraftImageGenerator.svg)](https://jitpack.io/#Aerhhh/MinecraftImageGenerator)
![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)

<br>

<img src="docs/images/tooltip-legendary.png" height="300" alt="Legendary item tooltip with full formatting">
&nbsp;&nbsp;
<img src="docs/images/glint.gif" height="300" alt="Animated enchantment glint">
&nbsp;&nbsp;
<img src="docs/images/player-head.png" height="240" alt="Rendered player head">

</div>

Everything below was rendered by the library itself. Each snippet is the actual code that produces the image next to it.

## Showcase

### Item tooltips

Full Minecraft formatting: colors, bold, italic, strikethrough, underline, obfuscation, text wrapping, rarity coloring, and configurable padding and borders.

<div align="center">
<img src="docs/images/tooltip-legendary.png" height="320" alt="Legendary tooltip">
</div>

```java
GeneratedObject tooltip = new GeneratorImageBuilder()
    .addGenerator(new MinecraftTooltipGenerator.Builder()
        .withName("Aegis of the Fallen Star")
        .withRarity(Rarity.byName("LEGENDARY"))
        .withType("SWORD")
        .withItemLore("""
            &7A blade forged in the heart of a dying star.

            &6⚔ Damage: &c+340
            &6✦ Attack Speed: &aVery Fast

            &f&lBold &7&mStrike&r &b&nUnderline &d&oItalic

            &e&lRIGHT-CLICK &7to unleash &6Starfall""")
        .withMaxLineLength(48)
        .withRenderBorder(true)
        .build())
    .build();
```

### Gradient and hex color text

Use `%%gradient:#start:#end%%...%%/gradient%%` for smooth per-character gradients, and `&#RRGGBB` for inline hex colors.

<div align="center">
<img src="docs/images/tooltip-gradient.png" height="280" alt="Gradient and hex color tooltip">
</div>

```java
new MinecraftTooltipGenerator.Builder()
    .withName("Prism of Eternity")
    .withRarity(Rarity.byName("MYTHIC"))
    .withType("RELIC")
    .withItemLore("""
        %%gradient:#ff3cac:#784ba0%%A shard of the very first dawn.%%/gradient%%
        %%gradient:#2af598:#009efd%%Its light bends reality itself.%%/gradient%%

        &#ffd700✦ Radiance: &#ffec8b+500
        &#7fdbff❉ Clarity: &#7fdbff+250""")
    .withRenderBorder(true)
    .build();
```

### Alternate fonts

Render text in the Standard Galactic Alphabet (`&g`) or Illageralt (`&h`).

<div align="center">
<img src="docs/images/tooltip-galactic.png" height="220" alt="Standard Galactic Alphabet tooltip">
</div>

```java
new MinecraftTooltipGenerator.Builder()
    .withName("Tome of Forbidden Runes")
    .withRarity(Rarity.byName("EPIC"))
    .withType("ARTIFACT")
    .withItemLore("&b&gThe stars remember your name\n&d&gSpeak the word and be unmade")
    .build();
```

### Items and enchantment glint

Render any vanilla item from its id, optionally with an animated enchantment glint. When a generator is animated, `getGifData()` returns the encoded GIF.

<div align="center">
<img src="docs/images/item-diamond-sword.png" height="200" alt="Diamond sword item">
&nbsp;&nbsp;&nbsp;&nbsp;
<img src="docs/images/glint.gif" height="200" alt="Animated enchant glint">
</div>

```java
// Static item
new MinecraftItemGenerator.Builder()
    .withItem("diamond_sword")
    .isBigImage()
    .build();

// Animated enchant glint
GeneratedObject glint = new GeneratorImageBuilder()
    .addGenerator(new MinecraftItemGenerator.Builder()
        .withItem("netherite_sword")
        .isEnchanted(true)
        .isBigImage()
        .build())
    .build();

Files.write(Path.of("glint.gif"), glint.getGifData());
```

### Inventories

Complete inventory grids with item placement, stack counts, titles, and configurable rows and columns. The inventory string is `material:slot` per item (`{slot:amount}` sets a stack count), joined with `%%`.

<div align="center">
<img src="docs/images/inventory.png" width="720" alt="Inventory grid with items and stack counts">
</div>

```java
new MinecraftInventoryGenerator.Builder()
    .withRows(3)
    .withSlotsPerRow(9)
    .withContainerTitle("Legendary Loot")
    .withInventoryString(
        "diamond_sword:1%%nether_star:5%%elytra:6%%"
        + "emerald:{11:64}%%diamond:{12:64}%%dragon_head:27")
    .build();
```

### Player heads

Render a 3D player head from a player name, texture URL, base64 texture data, or a texture hash.

<div align="center">
<img src="docs/images/player-head.png" height="200" alt="Rendered player head">
</div>

```java
new MinecraftPlayerHeadGenerator.Builder()
    .withSkin("Aerh") // name, texture URL, base64, or hash
    .withScale(-2)    // positive upscales, negative downscales
    .build();
```

## Quick start

Compose one or more generators with `GeneratorImageBuilder` and write the result to disk. Static output is a `BufferedImage`, and animated output is a GIF byte array.

```java
GeneratedObject result = new GeneratorImageBuilder()
    .addGenerator(new MinecraftTooltipGenerator.Builder()
        .withName("Hello World")
        .withRarity(Rarity.byName("RARE"))
        .withItemLore("&7Your first &brendered &7tooltip.")
        .build())
    .build();

if (result.isAnimated()) {
    Files.write(Path.of("output.gif"), result.getGifData());
} else {
    ImageIO.write(result.getImage(), "png", new File("output.png"));
}
```

## Resource pack support

By default everything renders with vanilla textures. You can also load a resource pack and render any generator with its item textures, tooltip styles, custom glyphs, and color palette. This is what drives Hypixel SkyBlock item rendering, for example.

Register a pack once, from a directory or a zip, then pass its id to any generator:

```java
// Load a pack from disk (directory or .zip). Registered once, reused for the process.
PackId skyblock = PackRepository.global().register(
    "hypixel:skyblock",
    PackSource.directory(Path.of("packs/hypixel-skyblock"), PackLimits.fromSystemProperties()));

// A tooltip in one of the pack's rarity styles, with a gradient and hex colors
new MinecraftTooltipGenerator.Builder()
    .withName("&#c77dffMaelstrom")
    .withRarity(Rarity.byName("MYTHIC"))
    .withType("STAFF")
    .withItemLore("""
        %%gradient:#f857a6:#ff5858%%The sky answers only to its bearer.%%/gradient%%

        &7Intelligence: &b+900
        &7Ability Damage: &d+65%""")
    .withPack("hypixel:skyblock")
    .withTooltipStyle("hypixel_skyblock:mythic")
    .build();

// A custom item texture from the pack
new MinecraftItemGenerator.Builder()
    .withItem("hypixel_skyblock:item/uncategorized/aurora_staff")
    .withPack("hypixel:skyblock")
    .isBigImage()
    .build();
```

The items below are invented, rendered with this pack's real textures and rarity tooltip styles (`common` through `mythic`, plus `special`, `supreme`, `ultimate`, and more):

<table>
<tr>
<td align="center" width="50%"><img src="docs/images/fantasy-verdant-item.png" height="72"><br><img src="docs/images/fantasy-verdant-tooltip.png" width="400" alt="Verdant Edge, a rare sword"></td>
<td align="center" width="50%"><img src="docs/images/fantasy-whisperwind-item.png" height="72"><br><img src="docs/images/fantasy-whisperwind-tooltip.png" width="400" alt="Whisperwind, an epic longbow"></td>
</tr>
<tr>
<td align="center"><img src="docs/images/fantasy-wrathflame-item.png" height="72"><br><img src="docs/images/fantasy-wrathflame-tooltip.png" width="400" alt="Wrathflame Greatsword, a legendary greatsword"></td>
<td align="center"><img src="docs/images/fantasy-maelstrom-item.png" height="72"><br><img src="docs/images/fantasy-maelstrom-tooltip.png" width="400" alt="Maelstrom, a mythic staff"></td>
</tr>
<tr>
<td align="center"><img src="docs/images/fantasy-soulrend-item.png" height="72"><br><img src="docs/images/fantasy-soulrend-tooltip.gif" width="400" alt="Soulrend, a supreme demon blade with animated obfuscated text"></td>
<td align="center"><img src="docs/images/fantasy-frozenstar-item.png" height="72"><br><img src="docs/images/fantasy-frozenstar-tooltip.png" width="400" alt="Heart of the Frozen Star, a special relic"></td>
</tr>
<tr>
<td align="center"><img src="docs/images/fantasy-aeon-item.png" height="72"><br><img src="docs/images/fantasy-aeon-tooltip.png" width="400" alt="Aeon the Last Hour, an ultimate artifact"></td>
<td align="center"></td>
</tr>
</table>

- Vanilla (`minecraft:minecraft`) is always available and is the default, so `withPack` is opt-in.
- Pack ids are `namespace:name` at the library level (e.g. `hypixel:skyblock`), separate from the asset namespace a pack declares internally (e.g. `hypixel_skyblock`).
- Item refs use the pack's asset namespace and item path (e.g. `hypixel_skyblock:item/uncategorized/aurora_staff`).
- `withTooltipStyle(...)` selects the pack's `minecraft:tooltip_style` (e.g. `hypixel_skyblock:mythic`).
- The item and inventory generators accept `withPack(...)` too, so a whole inventory can render in a pack's style.

## Features

- **Tooltip rendering** with full Minecraft formatting code support (colors, bold, italic, obfuscation, strikethrough, underline), hex colors, gradients, text wrapping, rarity coloring, and configurable padding and borders
- **Resource pack support** to render with a loaded resource pack's item textures, tooltip styles, and glyphs (for example, Hypixel SkyBlock), with vanilla as the default
- **Inventory rendering** with item placement, stack counts, titles, and configurable rows and columns
- **Item rendering** with enchantment glint animations, hover effects, durability bars, and colored overlays
- **Player head rendering** from player names, texture URLs, base64 data, or hex texture hashes
- **NBT parsing** that auto-detects multiple Minecraft NBT formats (1.20.5+ components, 1.13-1.20.4 post-flattening, and pre-1.13)
- **Animation** via animated GIF output with frame-by-frame compositing
- **Caching** of rendered objects via Caffeine
- **Alternate fonts** including Galactic (Standard Galactic Alphabet) and Illageralt

## Requirements

- Java 25
- Maven

## Installation

### Maven (via JitPack)

Add the JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>com.github.Aerhhh</groupId>
    <artifactId>MinecraftImageGenerator</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

### Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Aerhhh:MinecraftImageGenerator:master-SNAPSHOT'
}
```

## Building

```bash
mvn clean package
```

### Font generation

Minecraft font files are not included in the repository. They are generated at build time using [minecraft-fontgen](https://github.com/SkyBlock-Simplified/minecraft-fontgen). When consuming this library via JitPack, fonts are generated automatically.

For local development, you need Python 3.10+ installed. Then run:

```bash
mvn -pl generator -Pgenerate-fonts package
```

This installs `minecraft-fontgen` and generates all font styles into the resources directory. You can specify a Minecraft version with:

```bash
mvn -pl generator -Pgenerate-fonts -Dmc.version=26.1 package
```

The default is `latest`, which resolves to the most recent Minecraft release.

## Project structure

```
MinecraftImageGenerator/
├── generator/          # Core image generation library
│   └── src/main/
│       ├── java/       # Source code
│       └── resources/  # Fonts, spritesheets, textures, JSON configs
├── tooling/            # Asset pipeline tools (spritesheet generation, item rendering)
└── jitpack.yml         # JitPack build configuration
```

## Dependencies

- [Marmalade](https://github.com/SkyBlock-Nerds/Marmalade) - Shared image utilities
- [Caffeine](https://github.com/ben-manes/caffeine) - Caching
- [Gson](https://github.com/google/gson) - JSON parsing
- [SLF4J](https://www.slf4j.org/) - Logging
- [Lombok](https://projectlombok.org/) - Boilerplate reduction

## Asset pipeline

The project includes a GitHub Actions workflow for generating and updating Minecraft item spritesheets and overlays. This is triggered manually via `workflow_dispatch` and supports:

- Downloading Minecraft assets for any version
- Rendering items at configurable sizes (requires .NET 10)
- Generating sprite atlases with coordinate metadata
- Generating item overlays for colored variants (armor, potions, etc.)
