package net.zaminmc.zea.discovery;

import com.google.gson.annotations.SerializedName;

/**
 * The discovery feature's own persisted state ({@code discovery.json}):
 * whether native discovery is currently on, and the schema version for
 * future migrations. The enabled default is deliberately conservative —
 * a fresh installation does not collect anything until the player turns
 * discovery on with {@code /zeadiscovery on}.
 *
 * <p>This file — not the MoulConfig tree — is the single source of truth
 * for the switch, so the command and any future settings entry can never
 * diverge into two competing toggles.
 */
public final class DiscoveryState {
        public static final int CURRENT_VERSION = 1;

        @SerializedName("schemaVersion")
        public int schemaVersion = CURRENT_VERSION;

        @SerializedName("enabled")
        public boolean enabled;

        @SerializedName("lastServer")
        public String lastServer;
}
