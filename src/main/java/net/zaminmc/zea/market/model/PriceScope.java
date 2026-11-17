package net.zaminmc.zea.market.model;

/**
 * What a shop's advertised price is charged or paid for, exactly as the
 * server's price line words it.
 *
 * <p>The scope comes from the price line's shape and is independent of
 * the shop's direction — both scopes are observed on both sides:
 *
 * <ul>
 *   <li>{@code PER_BULK} — the price covers a bulk of the item
 *       ({@code Price per 7x Block of Diamond - 1G}); the bulk size is a
 *       separate reported property, never assumed to be a stack of 64.
 *       Bulk SELLING shops charge per bulk, and BUYING shops that take
 *       whole bulks pay per bulk.</li>
 *   <li>{@code PER_ITEM} — the price covers one individual item
 *       ({@code Price per Netherite Ingot - 17G}). Per-item SELLING
 *       shops charge per item, and BUYING shops pay per item.</li>
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
