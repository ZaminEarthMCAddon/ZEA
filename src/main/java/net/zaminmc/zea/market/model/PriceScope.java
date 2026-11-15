package net.zaminmc.zea.market.model;

/**
 * What a shop's advertised price is charged for.
 *
 * <p>EarthMC's two shop directions price differently, and mixing the two
 * units would produce nonsense comparisons:
 *
 * <ul>
 *   <li>{@code PER_BULK} — a SELLING shop charges per bulk
 *       ({@code Price per 7x Block of Diamond - 1G}); the bulk size is a
 *       separate property, never assumed to be a stack of 64.</li>
 *   <li>{@code PER_ITEM} — a BUYING shop pays per individual item
 *       ({@code Price per Netherite Ingot - 17G}).</li>
 * </ul>
 *
 * <p>Price normalization (a per-individual-item unit price) is only
 * possible when the bulk size is reliably known; see the market pricing
 * engine. Comparisons never rank offers across incompatible scopes.
 */
public enum PriceScope {
	PER_BULK,
	PER_ITEM
}
