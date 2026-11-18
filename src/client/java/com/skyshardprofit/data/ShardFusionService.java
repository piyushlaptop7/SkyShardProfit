package com.skyshardprofit.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShardFusionService {
    private static final String PROPERTIES_URL =
            "https://raw.githubusercontent.com/Campionnn/SkyShards/master/public/fusion-properties.json";
    private static final String RECIPES_URL =
            "https://raw.githubusercontent.com/Campionnn/SkyShards/master/public/fusion-data.json";
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long RECIPE_CACHE_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private static final long PRICE_REFRESH_MS = TimeUnit.SECONDS.toMillis(90);
    private static final double[] TAX_PRESETS = {0.0125, 0.01125, 0.01};

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SkyShardProfit-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Path dataDirectory = FabricLoader.getInstance().getConfigDir().resolve("skyshardprofit");
    private final Path propertiesFile = dataDirectory.resolve("fusion-properties.json");
    private final Path recipesFile = dataDirectory.resolve("fusion-data.json");
    private final Path bazaarFile = dataDirectory.resolve("bazaar.json");
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private volatile Map<String, ShardMeta> shards = Map.of();
    private volatile List<FusionOpportunity> opportunities = List.of();
    private volatile Map<String, String> productIdsByCode = Map.of();
    private volatile JsonObject bazaarProducts = new JsonObject();
    private volatile String status = "Waiting for recipe data";
    private volatile String lastError = "";
    private volatile long bazaarUpdatedAt = 0L;
    private volatile long lastPriceRefreshAt = 0L;
    private volatile long lastRecipeRefreshAt = 0L;
    private volatile TradeMode tradeMode = TradeMode.BUY_ORDER_TO_SELL_OFFER;
    private volatile int taxPreset = 0;
    private volatile boolean started = false;
    private volatile boolean recipesReady = false;

    public void start() {
        if (started) return;
        started = true;
        worker.execute(() -> {
            try {
                Files.createDirectories(dataDirectory);
                status = "Loading fusion recipes...";
                JsonObject properties = loadJsonWithCache(PROPERTIES_URL, propertiesFile, RECIPE_CACHE_AGE_MS);
                JsonObject recipeRoot = loadJsonWithCache(RECIPES_URL, recipesFile, RECIPE_CACHE_AGE_MS);
                parseShardMetadata(properties);
                List<FusionRecipe> recipes = parseRecipes(recipeRoot);
                recipesReady = true;
                status = "Loading Bazaar prices...";

                JsonObject bazaar = loadBazaarCache();
                if (bazaar != null) {
                    parseBazaar(bazaar);
                    lastPriceRefreshAt = System.currentTimeMillis();
                    rebuildOpportunities(recipes);
                }

                try {
                    JsonObject fresh = downloadJson(BAZAAR_URL);
                    saveJson(bazaarFile, fresh);
                    parseBazaar(fresh);
                    lastPriceRefreshAt = System.currentTimeMillis();
                    rebuildOpportunities(recipes);
                    status = "Live Bazaar prices loaded";
                    lastError = "";
                } catch (Exception networkError) {
                    lastPriceRefreshAt = System.currentTimeMillis();
                    if (bazaar == null) throw networkError;
                    status = "Using cached Bazaar prices (offline)";
                    lastError = networkError.getMessage();
                }
            } catch (Exception exception) {
                lastPriceRefreshAt = System.currentTimeMillis();
                status = "Could not load shard data";
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            }
        });
    }

    public void tick() {
        if (!started || !recipesReady || refreshInProgress.get()) return;
        if (System.currentTimeMillis() - lastPriceRefreshAt >= PRICE_REFRESH_MS) {
            refreshPrices();
        }
    }

    public void refreshPrices() {
        if (!recipesReady || !refreshInProgress.compareAndSet(false, true)) return;
        status = "Refreshing Bazaar prices...";
        worker.execute(() -> {
            try {
                JsonObject fresh = downloadJson(BAZAAR_URL);
                saveJson(bazaarFile, fresh);
                parseBazaar(fresh);
                lastPriceRefreshAt = System.currentTimeMillis();
                rebuildOpportunities(parseRecipes(loadJsonFromFile(recipesFile)));
                status = "Live Bazaar prices loaded";
                lastError = "";
            } catch (Exception exception) {
                status = "Price refresh failed; keeping last snapshot";
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                lastPriceRefreshAt = System.currentTimeMillis();
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    public void cycleTradeMode() {
        tradeMode = tradeMode.next();
        scheduleRebuild();
    }

    public void cycleTaxPreset() {
        taxPreset = (taxPreset + 1) % TAX_PRESETS.length;
        scheduleRebuild();
    }

    private void scheduleRebuild() {
        if (!recipesReady || !refreshInProgress.compareAndSet(false, true)) return;
        status = "Recalculating fusions...";
        worker.execute(() -> {
            try {
                rebuildOpportunities(parseRecipes(loadJsonFromFile(recipesFile)));
                status = "Fusion list updated";
            } catch (Exception exception) {
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    public List<FusionOpportunity> opportunities() {
        return opportunities;
    }

    public String status() {
        return status;
    }

    public String lastError() {
        return lastError;
    }

    public TradeMode tradeMode() {
        return tradeMode;
    }

    public double taxRate() {
        return TAX_PRESETS[taxPreset];
    }

    public int shardCount() {
        return shards.size();
    }

    public int opportunityCount() {
        return opportunities.size();
    }

    public long bazaarUpdatedAt() {
        return bazaarUpdatedAt;
    }

    public boolean isReady() {
        return recipesReady && bazaarUpdatedAt > 0;
    }

    private JsonObject loadJsonWithCache(String url, Path cache, long maxAgeMillis) throws Exception {
        if (Files.exists(cache)) {
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis();
            if (age <= maxAgeMillis) return loadJsonFromFile(cache);
        }
        try {
            JsonObject downloaded = downloadJson(url);
            saveJson(cache, downloaded);
            return downloaded;
        } catch (Exception networkError) {
            if (Files.exists(cache)) return loadJsonFromFile(cache);
            throw networkError;
        }
    }

    private JsonObject loadBazaarCache() {
        try {
            if (Files.exists(bazaarFile)) return loadJsonFromFile(bazaarFile);
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonObject downloadJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "SkyShardProfit/1.0 (Minecraft client mod)")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonObject loadJsonFromFile(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private void saveJson(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void parseShardMetadata(JsonObject root) {
        Map<String, ShardMeta> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject object = entry.getValue().getAsJsonObject();
            String name = stringValue(object, "name", entry.getKey());
            String rarity = stringValue(object, "rarity", "Unknown");
            String category = stringValue(object, "category", "Unknown");
            int fuseAmount = intValue(object, "fuse_amount", 5);
            List<String> families = new ArrayList<>();
            JsonArray familyArray = object.getAsJsonArray("family");
            if (familyArray != null) {
                for (JsonElement family : familyArray) {
                    if (!family.isJsonNull()) families.add(family.getAsString());
                }
            }
            parsed.put(entry.getKey(), new ShardMeta(entry.getKey(), name, rarity, category, families, Math.max(1, fuseAmount)));
        }
        shards = Map.copyOf(parsed);
    }

    private List<FusionRecipe> parseRecipes(JsonObject root) {
        List<FusionRecipe> parsed = new ArrayList<>(140_000);
        JsonObject recipes = root.getAsJsonObject("recipes");
        if (recipes == null) return parsed;

        for (Map.Entry<String, JsonElement> outputEntry : recipes.entrySet()) {
            if (!outputEntry.getValue().isJsonObject()) continue;
            JsonObject outputAmounts = outputEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> amountEntry : outputAmounts.entrySet()) {
                int amount;
                try {
                    amount = Integer.parseInt(amountEntry.getKey());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (!amountEntry.getValue().isJsonArray()) continue;
                for (JsonElement pairElement : amountEntry.getValue().getAsJsonArray()) {
                    if (!pairElement.isJsonArray()) continue;
                    JsonArray pair = pairElement.getAsJsonArray();
                    if (pair.size() < 2) continue;
                    String first = pair.get(0).getAsString();
                    String second = pair.get(1).getAsString();
                    if (shards.containsKey(outputEntry.getKey()) && shards.containsKey(first) && shards.containsKey(second)) {
                        parsed.add(new FusionRecipe(outputEntry.getKey(), amount, first, second));
                    }
                }
            }
        }
        lastRecipeRefreshAt = System.currentTimeMillis();
        return parsed;
    }

    private void parseBazaar(JsonObject root) {
        JsonObject products = root.getAsJsonObject("products");
        if (products == null) throw new IllegalArgumentException("Bazaar response has no products object");
        bazaarProducts = products;
        bazaarUpdatedAt = longValue(root, "lastUpdated", System.currentTimeMillis());
        Map<String, String> marketIds = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            String productId = entry.getKey();
            if (productId.startsWith("SHARD_")) {
                marketIds.put(normalize(productId.substring("SHARD_".length())), productId);
            }
        }
        // A known market-name exception in the community shard catalogue.
        if (products.has("SHARD_SEA_EMPEROR")) {
            marketIds.put(normalize("Loch Emperor"), "SHARD_SEA_EMPEROR");
        }
        Map<String, String> resolved = new HashMap<>();
        for (ShardMeta meta : shards.values()) {
            String id = marketIds.get(normalize(meta.name()));
            if (id != null) resolved.put(meta.code(), id);
        }
        productIdsByCode = Map.copyOf(resolved);
    }

    private void rebuildOpportunities(List<FusionRecipe> recipes) {
        JsonObject products = bazaarProducts;
        Map<String, String> ids = productIdsByCode;
        Map<String, ShardMeta> metadata = shards;
        List<FusionOpportunity> calculated = new ArrayList<>(recipes.size());
        double tax = taxRate();
        TradeMode mode = tradeMode;

        for (FusionRecipe recipe : recipes) {
            ShardMeta output = metadata.get(recipe.outputCode());
            ShardMeta first = metadata.get(recipe.firstInputCode());
            ShardMeta second = metadata.get(recipe.secondInputCode());
            if (output == null || first == null || second == null) continue;
            String outputProduct = ids.get(output.code());
            String firstProduct = ids.get(first.code());
            String secondProduct = ids.get(second.code());
            if (outputProduct == null || firstProduct == null || secondProduct == null) continue;

            JsonObject firstBook = objectValue(products, firstProduct);
            JsonObject secondBook = objectValue(products, secondProduct);
            JsonObject outputBook = objectValue(products, outputProduct);
            if (firstBook == null || secondBook == null || outputBook == null) continue;

            double firstUnit;
            double secondUnit;
            double outputUnit;
            if (mode == TradeMode.BUY_ORDER_TO_SELL_OFFER) {
                firstUnit = bestBid(firstBook);
                secondUnit = bestBid(secondBook);
                outputUnit = bestAsk(outputBook);
            } else {
                firstUnit = walkCost(firstBook.getAsJsonArray("sell_summary"), first.fuseAmount());
                secondUnit = walkCost(secondBook.getAsJsonArray("sell_summary"), second.fuseAmount());
                outputUnit = walkRevenue(outputBook.getAsJsonArray("buy_summary"), recipe.outputAmount());
                if (Double.isNaN(firstUnit) || Double.isNaN(secondUnit) || Double.isNaN(outputUnit)) continue;
                firstUnit /= first.fuseAmount();
                secondUnit /= second.fuseAmount();
                outputUnit /= recipe.outputAmount();
            }
            if (!isPositiveFinite(firstUnit) || !isPositiveFinite(secondUnit) || !isPositiveFinite(outputUnit)) continue;

            double firstCost = firstUnit * first.fuseAmount();
            double secondCost = secondUnit * second.fuseAmount();
            double inputCost = firstCost + secondCost;
            double outputGross = outputUnit * recipe.outputAmount();
            double outputNet = outputGross * (1.0 - tax);
            double profit = outputNet - inputCost;
            double roi = inputCost <= 0 ? 0 : profit / inputCost * 100.0;

            calculated.add(new FusionOpportunity(
                    output.name(), output.rarity(), recipe.outputAmount(),
                    first.name(), second.name(), first.fuseAmount(), second.fuseAmount(),
                    firstUnit, secondUnit, firstCost, secondCost, inputCost,
                    outputGross, outputNet, profit, roi,
                    firstProduct, secondProduct, outputProduct
            ));
        }

        calculated.sort(Comparator.comparingDouble(FusionOpportunity::profit).reversed()
                .thenComparing(Comparator.comparingDouble(FusionOpportunity::roiPercent).reversed()));
        opportunities = List.copyOf(calculated);
    }

    private double bestAsk(JsonObject book) {
        JsonArray orders = book.getAsJsonArray("sell_summary");
        if (orders == null || orders.isEmpty()) return Double.NaN;
        double best = Double.POSITIVE_INFINITY;
        for (JsonElement order : orders) {
            double price = doubleValue(order.getAsJsonObject(), "pricePerUnit", Double.NaN);
            if (price > 0 && price < best) best = price;
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private double bestBid(JsonObject book) {
        JsonArray orders = book.getAsJsonArray("buy_summary");
        if (orders == null || orders.isEmpty()) return Double.NaN;
        double best = 0;
        for (JsonElement order : orders) {
            double price = doubleValue(order.getAsJsonObject(), "pricePerUnit", Double.NaN);
            if (price > best) best = price;
        }
        return best > 0 ? best : Double.NaN;
    }

    private double walkCost(JsonArray asks, int quantity) {
        if (asks == null || asks.isEmpty()) return Double.NaN;
        List<OrderLevel> levels = orderLevels(asks);
        levels.sort(Comparator.comparingDouble(OrderLevel::price));
        return walk(levels, quantity);
    }

    private double walkRevenue(JsonArray bids, int quantity) {
        if (bids == null || bids.isEmpty()) return Double.NaN;
        List<OrderLevel> levels = orderLevels(bids);
        levels.sort(Comparator.comparingDouble(OrderLevel::price).reversed());
        return walk(levels, quantity);
    }

    private List<OrderLevel> orderLevels(JsonArray orders) {
        List<OrderLevel> levels = new ArrayList<>();
        for (JsonElement order : orders) {
            JsonObject object = order.getAsJsonObject();
            double price = doubleValue(object, "pricePerUnit", 0);
            int amount = intValue(object, "amount", 0);
            if (price > 0 && amount > 0) levels.add(new OrderLevel(price, amount));
        }
        return levels;
    }

    private double walk(List<OrderLevel> levels, int quantity) {
        int remaining = quantity;
        double total = 0;
        for (OrderLevel level : levels) {
            int take = Math.min(remaining, level.amount());
            total += take * level.price();
            remaining -= take;
            if (remaining <= 0) return total;
        }
        return Double.NaN;
    }

    private static JsonObject objectValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean isPositiveFinite(double value) {
        return value > 0 && Double.isFinite(value);
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private record OrderLevel(double price, int amount) {
    }
}
