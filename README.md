# Jigsaw

A modular Minecraft image generation library for rendering items, tooltips, inventories, and player heads with pixel-perfect accuracy.

## Modules

- **jigsaw** - Core rendering engine. Generic Minecraft image generation with no game-specific dependencies.
- **skyblock** - Hypixel SkyBlock support. Data types, registries, and tooltip formatting with stat/flavor placeholder resolution.

## Requirements

- Java 25+
- Maven

## Installation

### Maven (JitPack)

Add the JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Core module only:

```xml
<dependency>
    <groupId>com.github.Aerhhh.jigsaw</groupId>
    <artifactId>jigsaw</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

With SkyBlock support:

```xml
<dependency>
    <groupId>com.github.Aerhhh.jigsaw</groupId>
    <artifactId>jigsaw-skyblock</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Create an Engine

The `Engine` is the entry point for all rendering. Build one at startup and reuse it.

```java
Engine engine = Engine.builder().build();
```

### Render an Item

```java
GeneratorResult result = engine.render(
    ItemRequest.builder()
        .itemId("diamond_sword")
        .enchanted(true)
        .scale(10)
        .build()
);

// Static image
BufferedImage image = result.firstFrame();

// Animated (enchanted items produce multiple frames)
if (result.isAnimated()) {
    // Animated WebP (recommended for pixel art: lossless, preserves alpha, smaller than GIF)
    byte[] webp = ((GeneratorResult.AnimatedImage) result).toWebpBytes();

    // Legacy animated GIF
    byte[] gif = ((GeneratorResult.AnimatedImage) result).toGifBytes();
}
```

### Render a Tooltip

```java
GeneratorResult result = engine.render(
    TooltipRequest.builder()
        .line("&aHyperion")
        .line("&7Damage: &c+500")
        .line("&7Strength: &c+200")
        .line("")
        .line("&6&lLEGENDARY SWORD")
        .renderBorder(true)
        .build()
);
```

### Render an Item with Tooltip

Use `CompositeRequest` to combine multiple renders side by side:

```java
GeneratorResult result = engine.render(
    CompositeRequest.builder()
        .add(ItemRequest.builder()
            .itemId("diamond_sword")
            .enchanted(true)
            .scale(2)
            .build())
        .add(TooltipRequest.builder()
            .line("&aHyperion")
            .line("&7Damage: &c+500")
            .line("")
            .line("&6&lLEGENDARY SWORD")
            .build())
        .build()
);
```

Images are centered vertically by default. Use `.layout(CompositeRequest.Layout.VERTICAL)` to stack them top-to-bottom instead.

### Render a Player Head

```java
// From Base64 skin texture
GeneratorResult result = engine.render(
    PlayerHeadRequest.fromBase64(base64TextureValue)
        .scale(4)
        .build()
);

// From URL
GeneratorResult result = engine.render(
    PlayerHeadRequest.fromUrl("https://textures.minecraft.net/texture/...")
        .scale(4)
        .build()
);
```

### Render an Inventory

```java
GeneratorResult result = engine.render(
    InventoryRequest.builder()
        .rows(3)
        .slotsPerRow(3)
        .title("Crafting")
        .drawBorder(true)
        .item(InventoryItem.builder().slot(0).itemId("diamond").build())
        .item(InventoryItem.builder().slot(4).itemId("stick").build())
        .build()
);
```

You can also use the inventory string format:

```java
InventoryRequest.builder()
    .rows(3)
    .slotsPerRow(3)
    .withInventoryString("diamond,air,air,air,stick,air,air,air,air")
    .build()
```

### Parse NBT

Parse Minecraft NBT (JSON or SNBT format) into item data:

```java
ParsedItem item = engine.parseNbt(nbtString);

System.out.println(item.itemId());      // "diamond_sword"
System.out.println(item.enchanted());   // true
System.out.println(item.lore());        // ["&7Damage: &c+500", ...]
```

Render directly from NBT:

```java
GeneratorResult result = engine.renderFromNbt(nbtString);
```

### Item Overlays and Dye Colors

Leather armor and potions are automatically colored when dye color data is present:

```java
GeneratorResult result = engine.render(
    ItemRequest.builder()
        .itemId("leather_chestplate")
        .color("#FF0000")     // accepts hex
        .build()
);

// Named colors also work
ItemRequest.builder()
    .itemId("leather_boots")
    .color("blue")            // accepts Minecraft color names
    .build()
```

### Search for Textures

```java
// Find all textures matching a query
List<Map.Entry<String, BufferedImage>> results = engine.sprites().searchAll("diamond");

// Get all available texture IDs (for autocomplete)
Map<String, BufferedImage> all = engine.sprites().getAllSprites();
```

## SkyBlock Module

The `skyblock` module adds Hypixel SkyBlock data types and tooltip formatting on top of the core engine.

### SkyBlock Tooltip with Placeholders

Use `SkyBlockTooltipBuilder` to build tooltips with automatic stat and flavor placeholder resolution:

```java
TooltipRequest request = SkyBlockTooltipBuilder.builder()
    .name("Hyperion")
    .rarity(Rarity.byName("legendary").orElse(null))
    .lore("%%damage:500%%\n%%strength:200%%\n\n&7Ability: &6Wither Impact")
    .type("SWORD")
    .build();

GeneratorResult result = engine.render(request);
```

Placeholders use the `%%key:value%%` format:

- `%%damage:500%%` resolves to a formatted damage stat line
- `%%speed:100%%` resolves to a formatted speed stat line
- `%%soulbound%%` resolves to a Soulbound flavor line

### Data Registries

SkyBlock data types are loaded from bundled JSON resources:

```java
Optional<Rarity> legendary = Rarity.byName("legendary");
Optional<Stat> damage = Stat.byName("damage");
Optional<PowerStrength> master = PowerStrength.byName("master");

Collection<String> allRarities = Rarity.getRarityNames();
```

## Formatting Codes

Text uses Minecraft's formatting system with `&` or `\u00a7` as the prefix:

| Code | Color | Code | Format |
|------|-------|------|--------|
| `&0` | Black | `&k` | Obfuscated |
| `&1` | Dark Blue | `&l` | Bold |
| `&2` | Dark Green | `&m` | Strikethrough |
| `&3` | Dark Aqua | `&n` | Underline |
| `&4` | Dark Red | `&o` | Italic |
| `&5` | Dark Purple | `&r` | Reset |
| `&6` | Gold | | |
| `&7` | Gray | | |
| `&8` | Dark Gray | | |
| `&9` | Blue | | |
| `&a` | Green | | |
| `&b` | Aqua | | |
| `&c` | Red | | |
| `&d` | Light Purple | | |
| `&e` | Yellow | | |
| `&f` | White | | |

## Extending Jigsaw

Jigsaw is designed for extensibility via the SPI layer.

### Custom Effect

```java
public class MyEffect implements ImageEffect {
    @Override public String id() { return "my_effect"; }
    @Override public int priority() { return 150; }
    @Override public boolean appliesTo(EffectContext ctx) { return true; }

    @Override
    public EffectContext apply(EffectContext ctx) {
        BufferedImage modified = // ... transform ctx.image()
        return ctx.withImage(modified);
    }
}

Engine engine = Engine.builder()
    .effect(new MyEffect())
    .build();
```

### Custom NBT Format Handler

```java
public class MyNbtHandler implements NbtFormatHandler {
    @Override public String id() { return "my_handler"; }
    @Override public int priority() { return 50; }

    @Override
    public boolean canHandle(String input) {
        // return true if this handler can parse the input
    }

    @Override
    public ParsedItem parse(String input, NbtFormatHandlerContext ctx) throws ParseException {
        // parse and return item data
    }
}

Engine engine = Engine.builder()
    .nbtHandler(new MyNbtHandler())
    .build();
```

Handlers can also be discovered automatically via `ServiceLoader` by adding a file at `META-INF/services/net.aerh.jigsaw.spi.NbtFormatHandler`.

## Project Structure

```
Jigsaw/
├── jigsaw/             # Core rendering engine
│   └── src/main/java/net/aerh/jigsaw/
│       ├── api/        # Public interfaces (Engine, Generator, DataRegistry, etc.)
│       ├── core/       # Default implementations
│       ├── spi/        # Service provider interfaces for extensibility
│       └── exception/  # Exception hierarchy
├── skyblock/           # SkyBlock-specific module
│   └── src/main/java/net/aerh/jigsaw/skyblock/
│       ├── data/       # SkyBlock data types (Stat, Rarity, Flavor, etc.)
│       └── tooltip/    # SkyBlockTooltipBuilder, placeholder resolution
└── .github/workflows/  # CI and font generation
```

## Dependencies

- [Marmalade](https://github.com/SkyBlock-Nerds/Marmalade) - Image utilities and GIF encoding
- [Caffeine](https://github.com/ben-manes/caffeine) - Caching
- [Gson](https://github.com/google/gson) - JSON parsing
- [SLF4J](https://www.slf4j.org/) - Logging

## License

See [LICENSE](LICENSE) for details.
