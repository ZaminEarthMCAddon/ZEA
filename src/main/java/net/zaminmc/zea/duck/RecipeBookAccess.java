package net.zaminmc.zea.duck;

/**
 * Read access to a recipe-book screen's component, bridged by
 * {@code RecipeBookScreenAccessor}. Lives in {@code net.zaminmc.zea.duck}
 * (not the mixin package) for the same reason {@code ScreenWidgetAccess}
 * does: Mixin owns every class in its config package outright, so a duck
 * interface there would crash the JVM when the target class resolves its
 * interfaces.
 */
public interface RecipeBookAccess {
        /** Whether the screen's recipe book panel is currently open. */
        boolean zea$recipeBookOpen();
}
