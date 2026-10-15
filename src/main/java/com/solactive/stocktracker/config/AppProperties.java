package com.solactive.stocktracker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the {@code app.*} configuration tree.
 *
 * @param tradingZone time zone whose calendar date defines a "trading day" (exchange local time)
 * @param folder      folder used by the folder scan
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId tradingZone,
        @NotNull @Valid Folder folder) {

    /**
     * Folder used by the folder scan.
     *
     * @param dir folder holding the {@code *.csv} files; files stay there after processing
     */
    public record Folder(@NotNull Path dir) {
    }
}
