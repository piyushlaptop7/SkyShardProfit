# SkyShard Profit

A client-side Fabric mod for Hypixel SkyBlock's Attribute Fusion Machine. It searches shard fusion recipes, prices their inputs and outputs from the Hypixel Bazaar, and sorts opportunities by estimated profit.

## Features

- Search by output shard or either input shard.
- Lists the exact shard required in **Input Slot 1** and **Input Slot 2**, including each shard's fusion quantity.
- Shows output quantity, output rarity, per-input unit cost, total input cost, output value before and after Bazaar tax, estimated profit, and ROI.
- Ranks profitable recipes first and lets you toggle **Profit only** to inspect every priced recipe.
- Switches between **Buy Order → Sell Offer** estimates and depth-aware **Instant Buy → Instant Sell** estimates.
- Cycles Bazaar tax assumptions: 1.25%, 1.125%, and 1.00%.
- Refreshes Bazaar prices automatically about every 90 seconds and keeps local data caches for temporary outages.
- Opens with **O** by default. You can change the key in Options → Controls → Key Binds.

## Target environment

- Minecraft **26.1.2**
- Fabric Loader **0.19.2**
- Fabric API **0.155.3+26.1.2**
- Java **25**

This is a client-only mod. It does not need to be installed on a server.

## Build on Windows

This archive contains the mod source project rather than a precompiled JAR. Build the JAR locally with the steps below.

1. Install a Java 25 JDK and Gradle 9.6 or newer, then restart your terminal.
2. Extract this project folder.
3. Open Command Prompt in the extracted `SkyShardProfit` folder.
4. Run:

   ```bat
   gradle build
   ```

5. When Gradle finishes successfully, the mod JAR will be in `build\libs\skyshardprofit-1.0.0.jar`.

The first build needs an internet connection to download Minecraft, Fabric, and Gradle dependencies.

## Install in Prism Launcher

1. Open your Minecraft **26.1.2 Fabric** instance in Prism Launcher.
2. Make sure that instance has Fabric API **0.155.3+26.1.2** installed.
3. Open the instance's folder, then open `.minecraft` and its `mods` folder. Create `mods` if it does not exist.
4. Copy `skyshardprofit-1.0.0.jar` from `build\libs` into that `mods` folder.
5. Start the instance and press **O** in-game.

## First launch and data

On first launch, the mod downloads the shard metadata and fusion recipe graph from the community SkyShards data project, then downloads the public Hypixel Bazaar response. It caches these files in:

`<instance>\.minecraft\config\skyshardprofit\`

Only shards with a matching Hypixel Bazaar product ID can be priced; recipes involving unlisted shards are omitted. The recipe cache is reused for up to 24 hours. Bazaar prices are refreshed approximately every 90 seconds. An internet connection is required for the first download and for live prices; cached data is used when available during temporary outages.

## Understanding the two price modes

- **Buy Order → Sell Offer:** estimates buying the two input stacks using the best current buy-order prices and selling the output at the best current sell-offer price. It is a limit-order estimate and is not a guaranteed fill.
- **Instant Buy → Instant Sell:** walks the available Bazaar order-book levels for the required input quantities and output quantity. Recipes without enough visible order-book depth are omitted from this mode. The Bazaar API exposes a limited number of top order levels, so actual fills can still differ.

The tax selector applies the chosen rate to the output sale. Pick the rate that matches your current Bazaar fee situation.

## Important gameplay notes

- Put the listed **Input Slot 1** shard in the first slot and **Input Slot 2** shard in the second slot. Fusion order can matter.
- The Fusion Machine may offer multiple result choices. This mod ranks and prices the listed recipe; it does not click a result or perform the fusion for you. Confirm that the desired output appears in the in-game machine before spending shards.
- The fusion recipe graph is community-maintained and may be incomplete or change after game updates. All profits are estimates based on the captured Bazaar snapshot, not a promise of profit. Check current prices and the in-game recipe before committing coins.
- Bazaar prices can move while orders fill. The mod does not include opportunity cost, travel time, or any non-Bazaar acquisition costs.

## Data sources and attribution

- Fusion metadata and recipe graph: [Campionnn/SkyShards](https://github.com/Campionnn/SkyShards) (MIT-licensed community project; see `ThirdPartyNotices.md`).
- Market prices: [Hypixel SkyBlock Bazaar API](https://api.hypixel.net/v2/skyblock/bazaar).
- This is an independent community utility and is not affiliated with or endorsed by Hypixel Studios.

## Troubleshooting

- If the screen says it is loading, allow the first data download to complete and check that Minecraft can access GitHub and `api.hypixel.net`.
- If the list is empty, turn **Profit only** off, try a broader search, refresh prices, and verify that the data cache is not blocked or corrupted.
- Delete `config\skyshardprofit` to force the mod to download fresh data on the next launch.
- Ensure that Minecraft, Fabric Loader, Fabric API, and the mod all target the exact versions listed above.

## Easiest build option (no Java or Gradle installed on your PC)

Use the included [GITHUB_BUILD_GUIDE.md](GITHUB_BUILD_GUIDE.md). It explains how to let GitHub Actions build the mod online and provide the compiled JAR as an artifact. The local `build.bat` route is optional.
