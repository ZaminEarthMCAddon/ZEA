package net.zaminmc.zea.duck;

/**
 * Exposes {@code AbstractContainerScreen}'s protected layout fields —
 * the GUI rectangle ({@code imageWidth}/{@code imageHeight}) — to
 * non-mixin ZEA code. The button editor previews the container it was
 * opened from through this interface, so the preview uses the exact
 * dimensions the runtime button and search-bar layout run against. Like
 * every duck interface here, it is implemented by a mixin targeting the
 * declaring class.
 */
public interface ContainerScreenAccess {
        /** The container GUI's width ({@code imageWidth}). */
        int zea$imageWidth();

        /** The container GUI's height ({@code imageHeight}). */
        int zea$imageHeight();
}
