package net.zaminmc.zea.market.model;

/**
 * How a shop's reported quantity should be read.
 *
 * <p>Observed EarthMC behavior: a SELLING shop reports {@code Stock: N}
 * and a BUYING shop reports {@code Space: N} — available capacity, not
 * current stock. Stackable items may report stock in stacks, unstackable
 * equipment as individual items, and bulk size is a separate property
 * from stack size. The original server-reported value is always preserved
 * verbatim in the observation; this unit records only how the report has
 * been interpreted, and {@code UNKNOWN} whenever the interpretation is
 * not reliable. No screen may present a quantity more confidently than
 * its unit justifies.
 */
public enum StockUnit {
	/** Individual items. */
	ITEMS,
	/** Stacks of the item; the item count is stacks × stack capacity. */
	STACKS,
	/** Free space in a BUYING shop — capacity, not stock. */
	SPACE,
	/** The raw value is preserved but its meaning is not known. */
	UNKNOWN
}
