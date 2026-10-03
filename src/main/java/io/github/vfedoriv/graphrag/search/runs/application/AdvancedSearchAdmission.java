package io.github.vfedoriv.graphrag.search.runs.application;

import io.github.vfedoriv.graphrag.search.runs.configuration.AdvancedSearchProperties;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchAdmission {
    private final Semaphore capacity;
    public AdvancedSearchAdmission(AdvancedSearchProperties properties) {
        this.capacity = new Semaphore(properties.concurrency() + properties.queueCapacity(), true);
    }

    public Optional<Reservation> tryReserve() {
        return capacity.tryAcquire() ? Optional.of(new Reservation(capacity)) : Optional.empty();
    }

    public static final class Reservation {
        private final Semaphore capacity;
        private final AtomicBoolean held = new AtomicBoolean(true);
        private Reservation(Semaphore capacity) { this.capacity = capacity; }
        public void release() { if (held.compareAndSet(true, false)) { capacity.release(); } }
    }
}
