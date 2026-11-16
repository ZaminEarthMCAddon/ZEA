package net.zaminmc.zea.chat.model;

/**
 * What kind of EarthMC message this is. Categories drive the Stage 3 feed
 * model; they are distinct from feeds (views) and from server channels
 * (writable destinations).
 */
public enum MessageCategory {
        GLOBAL,
        LOCAL,
        TRADE,
        NATION,
        TOWN,
        PRIVATE_MESSAGE,
        DISCORD,
        DISCORD_RECRUITMENT,
        VOTE,
        CRATE_WIN,
        CRATE_LUCKY,
        DEATH,
        SYSTEM,
        PROMO_BANNER,
        ITEM_SHOWCASE,
        UNCLASSIFIED
}
