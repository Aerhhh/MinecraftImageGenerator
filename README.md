# MinecraftImageGenerator

A Java library for programmatically generating Minecraft-themed images, including item tooltips, inventories, individual items, and player heads. Supports both static PNG and animated GIF output.

## Features

- **Tooltip rendering** - Item tooltips with full Minecraft formatting code support (colors, bold, italic, obfuscation, strikethrough, underline), text wrapping, rarity coloring, and configurable padding/borders
- **Inventory rendering** - Complete inventory grids with item placement, stack counts, titles, and configurable rows/columns
- **Item rendering** - Individual items with enchantment glint animations, hover effects, durability bars, and colored overlays
- **Player head rendering** - Player heads from skin textures, supporting player names, texture URLs, base64 data, and hex texture hashes
- **NBT parsing** - Auto-detects and parses multiple Minecraft NBT formats (1.20.5+ components, 1.13-1.20.4 post-flattening, and pre-1.13)
- **Animation** - Animated GIF output with frame-by-frame compositing for effects like enchantment glints
- **Caching** - Automatic caching of rendered objects via Caffeine
- **Alternate fonts** - Galactic (Standard Galactic Alphabet) and Illageralt font support

## Requirements

- Java 21+
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
