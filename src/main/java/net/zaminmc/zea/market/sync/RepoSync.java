package net.zaminmc.zea.market.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The ZaminEarthMCAddon/ZEA-REPO sync: fetches the shared shop dataset, validates
 * it, caches it and hands the accepted records to the market — without
 * ever blocking the game and without ever letting the repository
 * overwrite newer local observations (see {@link DatasetMerger}).
 *
 * <p>The repository publishes a CI-generated {@code manifest.json} that
 * indexes every dataset file with its SHA-256. The sync follows that
 * manifest rather than hardcoding dataset paths, and uses it as the
 * change detector: the manifest is fetched conditionally, and a dataset
 * whose hash matches the last accepted snapshot is not downloaded again.
 * The last accepted snapshot is cached locally, so a restart serves the
 * dataset from disk before the network answers anything.
 *
 * <p>All IO runs on the injected executor; the parse runs there too
 * (bounded by {@link SharedShops#MAX_RECORDS}); only the final merge
 * step hops back onto the client thread through the injected sink. No
 * credentials are involved: the repository is read-only from the mod,
 * and publishing goes through the repository's own contribution
 * workflow, never through this class.
 */
public final class RepoSync {
	public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/RepoSync");

	/** The shared dataset repository. */
	public static final String REPOSITORY = "https://github.com/ZaminEarthMCAddon/ZEA-REPO";
	/** Raw content base for the repository's files. */
	public static final String RAW_BASE = "https://raw.githubusercontent.com/ZaminEarthMCAddon/ZEA-REPO/main";
	/** The manifest the repository's CI generates and keeps current. */
	public static final String MANIFEST_URL = RAW_BASE + "/manifest.json";

	private final DatasetHttp http;
	private final Executor executor;
	private final Path cacheFile;
	private final Path metaFile;
	private final Consumer<List<SharedShops.SharedRecord>> clientThreadSink;
	private String manifestEtag;
	private String manifestLastModified;

	public RepoSync(DatasetHttp http, Executor executor, Path cacheFile,
			Consumer<List<SharedShops.SharedRecord>> clientThreadSink) {
		this.http = http;
		this.executor = executor;
		this.cacheFile = cacheFile;
		this.metaFile = cacheFile.resolveSibling(cacheFile.getFileName() + ".meta");
		this.clientThreadSink = clientThreadSink;
	}

	/** What one refresh pass decided. */
	public record Summary(int recordsAccepted, boolean datasetUnchanged,
			boolean noShopDataset) {
	}

	/**
	 * Runs one fetch-validate-cache-deliver pass. The future completes
	 * on the injected executor; the merge happens on the client thread
	 * through the sink. Failures complete exceptionally — the caller
	 * logs, and the cached dataset keeps the market working offline.
	 */
	public CompletableFuture<Summary> refresh() {
		return http.getAsync(MANIFEST_URL, manifestEtag, manifestLastModified, executor)
				.thenCompose(fetched -> {
					manifestEtag = fetched.etag();
					manifestLastModified = fetched.lastModified();
					if (fetched.notModified()) {
						return CompletableFuture.completedFuture(
								new Summary(cacheCount(), true, false));
					}
					return refreshFromManifest(DatasetHttp.utf8(fetched.body()));
				})
				.exceptionally(failure -> {
					LOGGER.info("Repository sync unavailable ({}); the cached dataset stays active",
							failure.toString());
					return new Summary(cacheCount(), true, false);
				});
	}

	private CompletableFuture<Summary> refreshFromManifest(String manifestJson) {
		Entry entry = resolveShopDataset(manifestJson);
		if (entry == null) {
			LOGGER.info("Repository lists no shop dataset yet; staying local-only");
			return CompletableFuture.completedFuture(new Summary(cacheCount(), true, true));
		}
		Meta meta = loadMeta();
		if (entry.sha256() != null && entry.sha256().equals(meta.sha256)) {
			// The manifest hash says the dataset is unchanged: do not
			// download it again, the cache already holds it.
			return CompletableFuture.completedFuture(new Summary(cacheCount(), true, false));
		}
				return http.getAsync(RAW_BASE + "/" + entry.path(), null, null, executor)
						.thenApply(datasetBody -> {
							List<SharedShops.SharedRecord> records =
									SharedShops.parse(DatasetHttp.utf8(datasetBody.body()), entry.path());
					SharedShops.saveCache(cacheFile, records);
					saveMeta(new Meta(entry.sha256(), System.currentTimeMillis()));
					clientThreadSink.accept(records);
					LOGGER.info("Shared dataset accepted: {} records from {}", records.size(), entry.path());
					return new Summary(records.size(), false, false);
				});
	}

	/** One manifest entry: the dataset path and, when listed, its hash. */
	record Entry(String path, String sha256) {
	}

	/**
	 * The shop dataset entry in the manifest: the manifest is the
	 * repository's index, so any dataset file it lists that names a shop
	 * dataset is picked up without a client release. {@code null} when
	 * the manifest lists none (or cannot be read).
	 */
	static Entry resolveShopDataset(String manifestJson) {
		if (manifestJson == null || manifestJson.isBlank()) {
			return null;
		}
		try {
			JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
			JsonObject datasets = manifest.getAsJsonObject("datasets");
			if (datasets == null) {
				return null;
			}
			for (var entry : datasets.entrySet()) {
				String name = entry.getKey().toLowerCase(java.util.Locale.ROOT);
				if (!name.contains("shop") && !name.contains("market")) {
					continue;
				}
				JsonObject files = entry.getValue().isJsonObject()
						&& entry.getValue().getAsJsonObject().getAsJsonObject("files") != null
						? entry.getValue().getAsJsonObject().getAsJsonObject("files")
						: null;
				if (files == null || files.entrySet().isEmpty()) {
					continue;
				}
				var fileEntry = files.entrySet().iterator().next();
				return new Entry(stripLeadingSlash(fileEntry.getKey()),
						fileEntry.getValue().isJsonPrimitive()
								? fileEntry.getValue().getAsString() : null);
			}
			return null;
		} catch (Exception e) {
			LOGGER.warn("Repository manifest could not be read: {}", e.toString());
			return null;
		}
	}

	private static String stripLeadingSlash(String path) {
		return path.startsWith("/") ? path.substring(1) : path;
	}

	/** The last accepted snapshot's metadata, for hash comparison. */
	private record Meta(String sha256, long fetchedAt) {
	}

	private Meta loadMeta() {
		JsonStore json = new JsonStore();
		return json.load(metaFile, Meta.class, () -> new Meta(null, 0L));
	}

	private void saveMeta(Meta meta) {
		try {
			new JsonStore().save(metaFile, meta);
		} catch (Exception e) {
			LOGGER.warn("Repository sync meta could not be written: {}", e.toString());
		}
	}

	private int cacheCount() {
		return SharedShops.loadCache(cacheFile).size();
	}
}
