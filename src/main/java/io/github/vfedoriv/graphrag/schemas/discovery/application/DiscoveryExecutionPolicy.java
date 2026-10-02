package io.github.vfedoriv.graphrag.schemas.discovery.application;

import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;

import java.time.Duration;
import java.util.Objects;

public record DiscoveryExecutionPolicy(
    int maxConcurrency,
    Duration sourceTimeout,
    Duration requestTimeout,
    String settingsFingerprint
) {
    public DiscoveryExecutionPolicy {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("Discovery source concurrency must be positive");
        }
        if (sourceTimeout == null || sourceTimeout.isZero() || sourceTimeout.isNegative()) {
            throw new IllegalArgumentException("Discovery source timeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Discovery request timeout must be positive");
        }
        settingsFingerprint = Objects.requireNonNull(settingsFingerprint, "settingsFingerprint");
    }

    public static DiscoveryExecutionPolicy from(
        RuntimeSettingsService.DiscoverySettings settings, String settingsFingerprint
    ) {
        Objects.requireNonNull(settings, "settings");
        return new DiscoveryExecutionPolicy(
            settings.maxConcurrency(), settings.sourceTimeout(), settings.requestTimeout(), settingsFingerprint);
    }
}
