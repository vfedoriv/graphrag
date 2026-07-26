package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DiscoveryExecutionPolicyTest {

    @Test
    void capturesOnlyExecutionBudgetsAndCanonicalFingerprint() {
        RuntimeSettingsService.DiscoverySettings settings = new RuntimeSettingsService.DiscoverySettings(
            12, 1024, 2048, 1000, 2000, 500, 10, 3, Duration.ofSeconds(7), Duration.ofSeconds(19));

        DiscoveryExecutionPolicy policy = DiscoveryExecutionPolicy.from(settings, "fingerprint");

        assertThat(policy.maxConcurrency()).isEqualTo(3);
        assertThat(policy.sourceTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(policy.requestTimeout()).isEqualTo(Duration.ofSeconds(19));
        assertThat(policy.settingsFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThatThrownBy(() -> new DiscoveryExecutionPolicy(
            0, Duration.ofSeconds(1), Duration.ofSeconds(1), "fingerprint"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscoveryExecutionPolicy(
            1, Duration.ZERO, Duration.ofSeconds(1), "fingerprint"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscoveryExecutionPolicy(
            1, Duration.ofSeconds(1), Duration.ofSeconds(-1), "fingerprint"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
