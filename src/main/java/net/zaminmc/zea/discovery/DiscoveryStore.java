package net.zaminmc.zea.discovery;

import com.google.gson.annotations.SerializedName;
import net.zaminmc.zea.persistence.JsonStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The discovery persistence: three separate files with separate
 * audiences, per the config layout's separation rules —
 *
 * <ul>
 * <li>{@code market/local-observations.json} — the player's own physical
 *     shop observations (the publishable set, but only the player
 *     publishes it, explicitly);</li>
 * <li>{@code market/navigation-observations.json} — the private travel
 *     history, never shared;</li>
 * <li>{@code market/remote-cache.json} — what the repository last
 *     supplied, with its fetch time.</li>
 * </ul>
 *
 * <p>Every write is atomic through {@link JsonStore}; a corrupt file
 * falls back to empty (the observation set rebuilds from play, and the
 * corrupt original is preserved beside it).
 */
public final class DiscoveryStore {
        private final JsonStore json = new JsonStore();
        private final Path localFile;
        private final Path navigationFile;
        private final Path remoteCacheFile;

        /** The persisted shape of the local shop set. */
        public static final class LocalObservations {
                @SerializedName("schemaVersion")
                public int schemaVersion = ShopObservation.CURRENT_VERSION;
                @SerializedName("shops")
                public Map<String, ShopObservation> shops = new LinkedHashMap<>();
        }

        /** The persisted shape of the private navigation history. */
        public static final class NavigationHistory {
                @SerializedName("schemaVersion")
                public int schemaVersion = NavigationObservation.CURRENT_VERSION;
                @SerializedName("limit")
                public int limit = 500;
                @SerializedName("entries")
                public List<NavigationObservation> entries = new ArrayList<>();
        }

        /** The persisted shape of the repository cache. */
        public static final class RemoteCache {
                @SerializedName("schemaVersion")
                public int schemaVersion = ShopObservation.CURRENT_VERSION;
                @SerializedName("fetchedAt")
                public long fetchedAt;
                @SerializedName("repository")
                public String repository;
                @SerializedName("shops")
                public Map<String, ShopObservation> shops = new LinkedHashMap<>();
        }

        private LocalObservations local = new LocalObservations();
        private NavigationHistory navigation = new NavigationHistory();
        private RemoteCache remoteCache = new RemoteCache();
        private boolean localDirty;
        private boolean navigationDirty;

        public DiscoveryStore(Path localFile, Path navigationFile, Path remoteCacheFile) {
                this.localFile = localFile;
                this.navigationFile = navigationFile;
                this.remoteCacheFile = remoteCacheFile;
                reload();
        }

        /** Reloads all three files from disk. */
        public void reload() {
                local = json.load(localFile, LocalObservations.class, LocalObservations::new);
                navigation = json.load(navigationFile, NavigationHistory.class, NavigationHistory::new);
                remoteCache = json.load(remoteCacheFile, RemoteCache.class, RemoteCache::new);
                localDirty = false;
                navigationDirty = false;
        }

        /** The local physical-shop set, by shop id. */
        public Map<String, ShopObservation> localShops() {
                return local.shops;
        }

        /** The repository's last-supplied shops, by shop id. */
        public Map<String, ShopObservation> remoteShops() {
                return remoteCache.shops;
        }

        /** When the repository cache was last fetched. */
        public long remoteFetchedAt() {
                return remoteCache.fetchedAt;
        }

        /** The repository URL the cache came from. */
        public String remoteRepository() {
                return remoteCache.repository;
        }

        /** The private navigation history. */
        public List<NavigationObservation> navigationEntries() {
                return navigation.entries;
        }

        /** Inserts or updates one local shop record and marks the set dirty. */
        public void putLocalShop(ShopObservation observation) {
                if (observation == null || observation.shopId == null) {
                        return;
                }
                local.shops.put(observation.shopId, observation);
                localDirty = true;
        }

        /** Appends one navigation entry, bounded by the history limit. */
        public void addNavigation(NavigationObservation entry) {
                if (entry == null) {
                        return;
                }
                navigation.entries.add(entry);
                while (navigation.entries.size() > navigation.limit) {
                        navigation.entries.remove(0);
                }
                navigationDirty = true;
        }

        /** Replaces the remote cache wholesale after a successful fetch+merge. */
        public void putRemoteShops(Map<String, ShopObservation> shops, long fetchedAt, String repository) {
                remoteCache.shops = shops == null ? new LinkedHashMap<>() : new LinkedHashMap<>(shops);
                remoteCache.fetchedAt = fetchedAt;
                remoteCache.repository = repository;
                saveRemote();
        }

        /** Writes the dirty files; safe to call every tick — unchanged files are not rewritten. */
        public void saveIfDirty() {
                if (localDirty) {
                        json.saveUnchecked(localFile, local);
                        localDirty = false;
                }
                if (navigationDirty) {
                        json.saveUnchecked(navigationFile, navigation);
                        navigationDirty = false;
                }
        }

        /** Saves the remote cache file. */
        public void saveRemote() {
                json.saveUnchecked(remoteCacheFile, remoteCache);
        }

        /** Flushes everything on shutdown — the one unconditional write per file. */
        public void flush() {
                json.saveUnchecked(localFile, local);
                json.saveUnchecked(navigationFile, navigation);
                saveRemote();
                localDirty = false;
                navigationDirty = false;
        }
}
