package net.zaminmc.zea.discovery;

import net.zaminmc.zea.map.source.MapHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The ZaminMC/ZEA-REPO integration: a local-first consumer of the shared
 * dataset. The repository is a <b>source of observations</b>, never an
 * authority over the local set — {@link ObservationMerge} enforces that
 * older remote data never overwrites newer local observations, and the
 * local dataset works entirely without any successful fetch.
 *
 * <p>The repository today ships curated badge/channel/town/nation
 * datasets with a CI-generated {@code manifest.json}; no shop dataset
 * exists there yet. The client therefore:
 *
 * <ol>
 * <li>fetches {@code manifest.json} (conditional GET, cached);</li>
 * <li>looks for a shop-observations dataset entry — when the community
 *     adds one (a {@code datasets/market/...} file the manifest lists),
 *     it is picked up without a client release, because the manifest is
 *     the index;</li>
 * <li>validates every entry with {@link ObservationMerge#isMergeable} —
 *     malformed records are rejected, never merged;</li>
 * <li>merges through {@link ObservationMerge} into the local set and
 *     snapshots what was fetched into {@code remote-cache.json}.</li>
 * </ol>
 *
 * <p>No credentials are involved: the repository is public and read-only
 * from the mod's perspective, and nothing is ever uploaded automatically
 * — publishing is the player's explicit, out-of-band contribution to the
 * repository's own workflow.
 */
public final class RepoClient {
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/Repo");

        /** The shared dataset repository. */
        public static final String REPOSITORY = "https://github.com/ZaminMC/ZEA-REPO";
        /** Raw content base for the repository's files. */
        public static final String RAW_BASE = "https://raw.githubusercontent.com/ZaminMC/ZEA-REPO/main";
        /** The manifest the repository's CI generates and keeps current. */
        public static final String MANIFEST_URL = RAW_BASE + "/manifest.json";

        private final MapHttp http;
        private final java.util.concurrent.Executor executor;
        private String manifestEtag;
        private String manifestLastModified;

        public RepoClient(MapHttp http, java.util.concurrent.Executor executor) {
                this.http = http;
                this.executor = executor;
        }

        /**
         * Runs one fetch-validate-merge pass. Returns a future completing
         * with the number of remote records the pass saw (0 when the
         * repository has no shop dataset yet — a normal state, not an
         * error). Failures complete the future exceptionally; the caller
         * logs and the local set goes on as if untouched.
         */
        public CompletableFuture<Integer> refresh(DiscoveryStore store) {
                return http.getAsync(MANIFEST_URL, manifestEtag, manifestLastModified, executor)
                                .thenCompose(fetched -> {
                                        manifestEtag = fetched.etag();
                                        manifestLastModified = fetched.lastModified();
                                        if (fetched.notModified()) {
                                                return CompletableFuture.completedFuture(cacheCount(store));
                                        }
                                        String datasetUrl = resolveShopDatasetUrl(new String(fetched.body(), java.nio.charset.StandardCharsets.UTF_8));
                                        if (datasetUrl == null) {
                                                LOGGER.info("Repository has no shop dataset yet; staying local-only");
                                                return CompletableFuture.completedFuture(0);
                                        }
                                        return fetchDataset(store, datasetUrl, MANIFEST_URL);
                                });
        }

        /** Fetches and merges one shop dataset document into the local set. */
        private CompletableFuture<Integer> fetchDataset(DiscoveryStore store, String datasetUrl, String repository) {
                return http.getAsync(datasetUrl, null, null, executor)
                                .thenApply(dataset -> {
                                        Map<String, ShopObservation> remote = parseShops(new String(dataset.body(), java.nio.charset.StandardCharsets.UTF_8));
                                        long now = System.currentTimeMillis();
                                        ObservationMerge.merge(store.localShops(), remote, System::currentTimeMillis);
                                        store.putRemoteShops(remote, now, repository);
                                        store.saveIfDirty();
                                        LOGGER.info("Repository merge complete: {} remote shop records ({} valid)",
                                                        remote.size(), store.remoteShops().size());
                                        return remote.size();
                                });
        }

        private int cacheCount(DiscoveryStore store) {
                return store.remoteShops().size();
        }

        /**
         * The shop dataset URL from the manifest — the manifest is a
         * generated file index, so the mod follows whatever the repository
         * lists rather than hardcoding a dataset path. Recognizes entries
         * mentioning a market/shop dataset (JSON), returning the first
         * match's raw URL, or null when the repository lists none.
         */
        static String resolveShopDatasetUrl(String manifestJson) {
                if (manifestJson == null || manifestJson.isBlank()) {
                        return null;
                }
                try {
                        com.google.gson.JsonObject manifest = com.google.gson.JsonParser
                                        .parseString(manifestJson).getAsJsonObject();
                        // The manifest's exact shape is the repository's; scan
                        // every string field and array element for a JSON path
                        // that names a market/shop dataset.
                        String found = findShopPath(manifest, 0);
                        return found == null ? null : RAW_BASE + "/" + found;
                } catch (Exception e) {
                        LOGGER.warn("Repository manifest could not be read: {}", e.toString());
                        return null;
                }
        }

        private static String findShopPath(com.google.gson.JsonElement element, int depth) {
                if (depth > 6 || element == null || element.isJsonNull()) {
                        return null;
                }
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        String value = element.getAsString();
                        String lower = value.toLowerCase(java.util.Locale.ROOT);
                        if ((lower.contains("market") || lower.contains("shop"))
                                        && lower.endsWith(".json")
                                        && !lower.contains("schema")) {
                                return value.startsWith("datasets/") || value.contains("/")
                                                ? value.replaceFirst("^/", "")
                                                : "datasets/" + value;
                        }
                        return null;
                }
                if (element.isJsonObject()) {
                        for (var entry : element.getAsJsonObject().entrySet()) {
                                String found = findShopPath(entry.getValue(), depth + 1);
                                if (found != null) {
                                        return found;
                                }
                        }
                }
                if (element.isJsonArray()) {
                        for (var item : element.getAsJsonArray()) {
                                String found = findShopPath(item, depth + 1);
                                if (found != null) {
                                        return found;
                                }
                        }
                }
                return null;
        }

        /**
         * Parses a shops dataset: either a bare {@code {id: shop}} map or a
         * wrapped {@code {shops: {id: shop}}} document. Entries that fail
         * validation are dropped, and the parser never throws on shape
         * surprises — a malformed repository must not break the client.
         */
        static Map<String, ShopObservation> parseShops(String json) {
                Map<String, ShopObservation> out = new LinkedHashMap<>();
                if (json == null || json.isBlank()) {
                        return out;
                }
                try {
                        com.google.gson.JsonObject root = com.google.gson.JsonParser
                                        .parseString(json).getAsJsonObject();
                        com.google.gson.JsonObject shops = root.has("shops") && root.get("shops").isJsonObject()
                                        ? root.getAsJsonObject("shops")
                                        : root;
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        for (var entry : shops.entrySet()) {
                                if (!entry.getValue().isJsonObject()) {
                                        continue;
                                }
                                try {
                                        ShopObservation observation = gson.fromJson(entry.getValue(),
                                                        ShopObservation.class);
                                        if (!ObservationMerge.isMergeable(observation)) {
                                                LOGGER.warn("Rejected malformed repository shop entry: {}",
                                                                entry.getKey());
                                                continue;
                                        }
                                        out.put(entry.getKey(), observation);
                                } catch (Exception e) {
                                        LOGGER.warn("Rejected unreadable repository shop entry {}: {}",
                                                        entry.getKey(), e.toString());
                                }
                        }
                } catch (Exception e) {
                        LOGGER.warn("Shop dataset could not be parsed: {}", e.toString());
                }
                return out;
        }
}
