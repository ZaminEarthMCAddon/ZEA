package net.zaminmc.zea.map.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The deterministic country-association registry: EarthMC towns map to
 * real-world countries for the global view's aggregated territory and
 * flags.
 *
 * <p>Association is <b>nation-primary</b> — a town whose EarthMC nation
 * names a country associates with it — with exact town-name aliases as
 * the fallback for the towns the spec pins down (London → United
 * Kingdom, Paris → France, Hyderabad → Pakistan). Matching is exact
 * only: never a substring, never a guess. A town name that resolves to
 * two countries is ambiguous and associates with nothing; explicit
 * overrides and exclusions correct the dataset's edges at runtime and
 * at load.
 *
 * <p>Every match records its source, so the renderer can rank overlays
 * and the report can say what was trusted. Pure type, pinned by tests;
 * the dataset ships as a bundled JSON resource under ZEA's namespace
 * (ZEA-REPO may distribute revisions with the same shape later).
 */
public final class CountryRegistry {

        /** How an association was decided. */
        public enum Source {
                /** An explicit runtime override — the highest authority. */
                OVERRIDE,
                /** The town's EarthMC nation names the country exactly. */
                NATION,
                /** The town's name is a listed town alias of the country. */
                TOWN_ALIAS,
                /** The town's name equals the country's name or an alias exactly. */
                NAME,
                /** Nothing matched, or every match was ambiguous or excluded. */
                NONE
        }

        /** One canonical country. */
        public record Country(String id, String name, String iso, List<Integer> flagColors) {
        }

        /** The association decision for one town. */
        public record Match(@Nullable String countryId, Source source) {
                public static final Match NONE = new Match(null, Source.NONE);
        }

        private final Map<String, Country> countries = new HashMap<>();
        /** Lower-cased country name/alias → id. */
        private final Map<String, String> countryAliases = new HashMap<>();
        /** Lower-cased town alias → id (ambiguous names never enter). */
        private final Map<String, String> townAliases = new HashMap<>();
        /** Lower-cased town names that must never associate. */
        private final Set<String> exclusions = new HashSet<>();
        /** Runtime manual corrections: town key → id. */
        private final Map<String, String> overrides = new HashMap<>();

        /**
         * Parses the dataset: {@code countries} (id, name, iso, flag
         * colors, aliases), {@code townAliases} (town name → country id),
         * {@code exclusions}. Town names that resolve to two countries are
         * dropped as ambiguous, not guessed.
         */
        public static CountryRegistry parse(String json) {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                CountryRegistry registry = new CountryRegistry();
                for (JsonElement element : root.getAsJsonArray("countries")) {
                        JsonObject country = element.getAsJsonObject();
                        String id = country.get("id").getAsString();
                        String name = country.get("name").getAsString();
                        String iso = country.has("iso") ? country.get("iso").getAsString() : "";
                        List<Integer> flag = readColors(country.getAsJsonArray("flag"));
                        Country parsed = new Country(id, name, iso, flag);
                        registry.countries.put(id, parsed);
                        addAlias(registry.countryAliases, name, id);
                        if (country.has("aliases")) {
                                for (JsonElement alias : country.getAsJsonArray("aliases")) {
                                        addAlias(registry.countryAliases, alias.getAsString(), id);
                                }
                        }
                }
                Map<String, Set<String>> townCandidates = new HashMap<>();
                if (root.has("townAliases")) {
                        for (Map.Entry<String, JsonElement> entry
                                                        : root.getAsJsonObject("townAliases").entrySet()) {
                                Set<String> ids = new HashSet<>();
                                JsonElement value = entry.getValue();
                                if (value.isJsonArray()) {
                                        // An array names several countries: the
                                        // alias is deliberately ambiguous.
                                        for (JsonElement id : value.getAsJsonArray()) {
                                                ids.add(id.getAsString());
                                        }
                                } else {
                                        ids.add(value.getAsString());
                                }
                                townCandidates.computeIfAbsent(normalize(entry.getKey()), ignored -> new HashSet<>())
                                                                .addAll(ids);
                        }
                }
                // An alias that names two countries is ambiguous: dropped
                // rather than guessed. A country's own name still associates
                // a town named exactly after it.
                for (Map.Entry<String, Set<String>> entry : townCandidates.entrySet()) {
                        if (entry.getValue().size() == 1) {
                                registry.townAliases.put(entry.getKey(), entry.getValue().iterator().next());
                        }
                }
                if (root.has("exclusions")) {
                        for (JsonElement excluded : root.getAsJsonArray("exclusions")) {
                                registry.exclusions.add(normalize(excluded.getAsString()));
                        }
                }
                return registry;
        }

        private static void addAlias(Map<String, String> aliases, String name, String id) {
                aliases.putIfAbsent(normalize(name), id);
        }

        private static List<Integer> readColors(@Nullable JsonArray flag) {
                if (flag == null) {
                        return List.of(0xFF888888);
                }
                return java.util.stream.IntStream.range(0, flag.size())
                                        .mapToObj(i -> flag.get(i).getAsString())
                                        .map(CountryRegistry::parseColor)
                                        .toList();
        }

        /** Parses a {@code #RRGGBB} or {@code #AARRGGBB} hex color. */
        private static int parseColor(String hex) {
                String body = hex.startsWith("#") ? hex.substring(1) : hex;
                long value = Long.parseLong(body, 16);
                return (int) (body.length() <= 6 ? 0xFF000000L | value : value);
        }

        private static String normalize(String name) {
                return name.trim().toLowerCase(Locale.ROOT);
        }

        /**
         * Associates a town. Order: runtime override, exclusion, the
         * town's nation, its town alias, its exact name against the
         * countries. Exact matches only — the registry never substrings.
         */
        public Match associate(String townName, @Nullable String nation, String townKey) {
                String override = overrides.get(townKey);
                if (override != null) {
                        return countries.containsKey(override)
                                                        ? new Match(override, Source.OVERRIDE)
                                                        : Match.NONE;
                }
                if (exclusions.contains(normalize(townName))) {
                        return Match.NONE;
                }
                if (nation != null && !nation.isBlank()) {
                        String id = countryAliases.get(normalize(nation));
                        if (id != null) {
                                return new Match(id, Source.NATION);
                        }
                }
                String key = normalize(townName);
                String aliased = townAliases.get(key);
                if (aliased != null) {
                        return new Match(aliased, Source.TOWN_ALIAS);
                }
                String named = countryAliases.get(key);
                if (named != null) {
                        return new Match(named, Source.NAME);
                }
                return Match.NONE;
        }

        /** Records a manual correction: a country id, or null to clear it. */
        public void setOverride(String townKey, @Nullable String countryId) {
                if (countryId == null) {
                        overrides.remove(townKey);
                } else {
                        overrides.put(townKey, countryId);
                }
        }

        /** The country by id, or null. */
        @Nullable
        public Country country(String id) {
                return countries.get(id);
        }

        /** Every canonical country, in dataset order. */
        public List<Country> countries() {
                return List.copyOf(countries.values());
        }
}
