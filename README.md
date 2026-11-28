# Mint & Machine companion mods

Companion mods for the **Create: Mint & Machine** modpack (NeoForge 1.21.1). The first four started as KubeJS scripts in the pack; the last two patch other mods with Mixin.

| Folder | Mod | Mod ID | Settings |
|---|---|---|---|
| `economy` | Create: Mint & Machine - Economy | `mintmachine_economy` | `config/mintmachine_economy/settings.json` |
| `combat` | Create: Mint & Machine - Combat Rebalance | `mintmachine_combat` | `config/mintmachine_combat.json` |
| `accelerators` | Botany Pots: Growth Accelerators | `botanypots_accelerators` | `config/botanypots_accelerators-common.toml` |
| `jetpack` | Create: Stuff 'N Additions Jetpack Curio Compat | `sa_jetpack_curio_compat` | none |
| `opacbridge` | Automated Logistics: OPAC Parties | `aal_opac_bridge` | none |
| `missionpaths` | Missions Paths | `missions_paths` | `config/missions/paths.json`, plus a `"path"` on each sub-mission in `config/missions/missions.json` |

- **Economy:**
  - Villagers and wandering traders trade in Create: Numismatics coins.
  - Every item gets a Shipping Bin sell price, saved to `config/mintmachine_economy/shipping_prices.json`. The same mods and settings give the same prices in single player and on a server.
  - Things that don't stack and are worth less than 1 spur aren't bought, since the bin's smallest payout is a whole spur. Water, powder snow, lava and milk are priced at about the bucket or bottle they're in. Free-to-make items (Create packages, shopping lists, written schematics, half-made sequenced-assembly parts) and experience aren't sold.
  - A server sends its prices to each player who joins, so the bin's tooltips and accepted items match what the server pays.
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
- **Automated Logistics: OPAC Parties:**
  - Create Aeronautics: Automated Logistics asks FTB Teams who is on whose team. Without FTB Teams, this answers from Open Parties and Claims parties instead: station, transponder and logistics terminal permissions, and per-team vehicle limits.
  - Does nothing when FTB Teams is installed.
  - Needs Automated Logistics 0.7.x (it patches that version's `FtbTeamsCompat`) and Open Parties and Claims 0.30 or later.
- **Missions Paths:**
  - Splits the Missions mod's 10 weekly missions between paths, 2 each of Create, Travel, Magic, Farming and Fishing by default, and keeps rerolls in a mission's own path.
  - A path with nothing left is filled from the others, so there are always 10. If anything goes wrong, or it's switched off in `paths.json`, the Missions mod picks as usual.
  - Needs Missions 0.5.2 (it patches that version's `DataStorage`).

## Building

```
./gradlew build
```

The jars land in each mod's `build/libs/`. GitHub Actions builds every push. The latest jars and build log are pushed to the `ci-output` branch, and are also attached to each run as an artifact.

All six mods are MIT-licensed (`LICENSE`). Their plain-Java rules have unit tests (`./gradlew test`, also run by `build`). Checks that need the real mods (applying the mixins with the real Mixin transformer, and running against the pack's `missions.json`) were run against the modpack's jars outside this build.

The other mods' classes come from `stubs/`. These are compile-only stand-ins whose signatures are copied from the exact versions in the modpack, and they are never packaged. CI fails if a stub class ends up in a jar. If a mod these depend on changes its API, update the matching stub.
