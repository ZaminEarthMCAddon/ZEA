package net.zaminmc.zea.discovery;

import com.google.gson.annotations.SerializedName;

/**
 * One navigation observation: a {@code /n spawn} or {@code /t spawn}
 * command the player actually executed — through ZEA's clickable links,
 * typed by hand, or sent by any other client feature — with the
 * coordinates before and, when a confirmed arrival followed, after.
 * Kept strictly separate from the public shop dataset: this is the
 * player's private travel history and never leaves the machine.
 */
public final class NavigationObservation {
        public static final int CURRENT_VERSION = 1;

        @SerializedName("schemaVersion")
        public int schemaVersion = CURRENT_VERSION;

        /** The executed command, as written minus the leading slash. */
        @SerializedName("command")
        public String command;

        /** The command's target ({@code Anjou}). */
        @SerializedName("target")
        public String target;

        /** Whether it was {@code n} (nation) or {@code t} (town). */
        @SerializedName("kind")
        public String kind;

        /** The dimension id when issued. */
        @SerializedName("dimension")
        public String dimension;

        /** Coordinates before the command, when the player was in-world. */
        @SerializedName("beforeX")
        public Double beforeX;

        @SerializedName("beforeZ")
        public Double beforeZ;

        /** Coordinates after a confirmed successful arrival, when one followed. */
        @SerializedName("afterX")
        public Double afterX;

        @SerializedName("afterZ")
        public Double afterZ;

        /** When the command was issued (epoch ms). */
        @SerializedName("observedAt")
        public long observedAt;

        /** How the command was issued: "click" or "typed" or "other". */
        @SerializedName("source")
        public String source;
}
