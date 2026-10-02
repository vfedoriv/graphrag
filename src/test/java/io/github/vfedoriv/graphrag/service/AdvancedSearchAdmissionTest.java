package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.search.runs.application.AdvancedSearchAdmission;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.config.AdvancedSearchProperties;
import org.junit.jupiter.api.Test;

class AdvancedSearchAdmissionTest {
    @Test
    void reservesBeforeWorkAndReleasesCapacityExactlyOnce() {
        AdvancedSearchAdmission admission = new AdvancedSearchAdmission(
            new AdvancedSearchProperties(1, 0, 1, "standard"));
        AdvancedSearchAdmission.Reservation reservation = admission.tryReserve().orElseThrow();
        assertThat(admission.tryReserve()).isEmpty();
        reservation.release();
        reservation.release();
        AdvancedSearchAdmission.Reservation next = admission.tryReserve().orElseThrow();
        assertThat(admission.tryReserve()).isEmpty();
        next.release();
    }
}
