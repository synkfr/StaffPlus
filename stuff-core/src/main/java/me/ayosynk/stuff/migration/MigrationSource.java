package me.ayosynk.stuff.migration;

import me.ayosynk.stuff.StuffPlatform;
import java.util.concurrent.CompletableFuture;

/**
 * Interface representing a punishment migration source (e.g. LiteBans, Vanilla).
 */
public interface MigrationSource {
    /**
     * Gets the unique lowercase name of this migration source.
     */
    String getName();

    /**
     * Gets a user-friendly description of what this source imports.
     */
    String getDescription();

    /**
     * Executes the migration process fully asynchronously.
     * Returns a CompletableFuture containing the number of successfully imported punishments.
     * The 'sendMessage' callback allows platform-specific message delivery.
     */
    CompletableFuture<Integer> migrate(StuffPlatform platform, java.util.function.Consumer<String> sendMessage, String[] args);
}
