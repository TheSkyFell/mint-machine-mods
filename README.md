# Mint & Machine companion mods

Companion mods for the **Create: Mint & Machine** modpack (NeoForge 1.21.1). Each started as one or more of the pack's KubeJS scripts.

| Folder | Mod | Mod ID | Settings |
|---|---|---|---|
| `economy` | Create: Mint & Machine - Economy | `mintmachine_economy` | `config/mintmachine_economy/settings.json` |
| `combat` | Create: Mint & Machine - Combat Rebalance | `mintmachine_combat` | `config/mintmachine_combat.json` |
| `accelerators` | Botany Pots: Growth Accelerators | `botanypots_accelerators` | `config/botanypots_accelerators-common.toml` |
| `jetpack` | Create: Stuff 'N Additions Jetpack Curio Compat | `sa_jetpack_curio_compat` | none |

- **Economy:**
  - Villagers and wandering traders trade in Create: Numismatics coins.
  - Every item gets a Shipping Bin sell price, saved to `config/mintmachine_economy/shipping_prices.json`.
  - Change is made for coins at villagers and shops, and a Bank Card in the offhand pays at villagers.
  - Golden and big Starcatcher fish sell for more.
  - Needs Numismatics and Shipping Bin. Create and Starcatcher are optional.
- **Combat Rebalance:** Netherite-benchmarked damage, armor, toughness and durability for Mystical Agriculture, Forbidden & Arcanus, Ars Elemental, Ars Technica, Create: Ironworks, Protection Pixel and Create Stuff 'N Additions / Netherite Additions gear.
- **Growth Accelerators:**
  - Mystical Agriculture Growth Accelerators and Botania Agricarnations speed up Botany Pots.
  - Ars Nouveau Agronomic Sourcelinks get Source from pots.
- **Jetpack Curio Compat:**
  - Adds a Curios Tank slot.
  - Netherite Jetpack and Exoskeleton refuel from Stuff 'N Additions tanks.
  - The Netherite Jetpack flies from the Curios Body slot.

## Building

```
./gradlew build
```

The jars land in each mod's `build/libs/`. GitHub Actions builds every push. The latest jars and build log are pushed to the `ci-output` branch, and are also attached to each run as an artifact.

The other mods' classes come from `stubs/`. These are compile-only stand-ins whose signatures are copied from the exact versions in the modpack, and they are never packaged. CI fails if a stub class ends up in a jar. If a mod these depend on changes its API, update the matching stub.
