package com.hermes.controlcenter.cache;

import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory cache of the most recent reliable StackSnapshot + DiagnosticSnapshot.
 * The cache is the single source of truth shown to the UI between probes so we
 * never display gray "OFFLINE" cards while the real probes are still in flight.
 *
 * <p>Writes happen exclusively from the bootstrap pipeline. Reads can come from
 * any thread. The cache enforces a TTL: snapshots older than {@code ttl} are
 * considered stale and the UI must wait for the next probe instead of trusting
 * the cached value.</p>
 */
public class HealthSnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(HealthSnapshotCache.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Duration ttl;
    private StackSnapshot stack;
    private DiagnosticSnapshot diagnostic;
    private Instant storedAt;

    public HealthSnapshotCache() {
        this(Duration.ofSeconds(45));
    }

    public HealthSnapshotCache(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(StackSnapshot stack, DiagnosticSnapshot diagnostic) {
        lock.writeLock().lock();
        try {
            this.stack = stack;
            this.diagnostic = diagnostic;
            this.storedAt = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
        log.debug("cache put: stack primary={} reliable={} plugins={}",
            stack.getPrimaryAction(), stack.isReliable(),
            diagnostic == null ? 0 : diagnostic.getPlugins().size());
    }

    public Optional<StackSnapshot> stackSnapshot() {
        lock.readLock().lock();
        try {
            if (stack == null || isStale()) {
                return Optional.empty();
            }
            return Optional.of(stack);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<DiagnosticSnapshot> diagnosticSnapshot() {
        lock.readLock().lock();
        try {
            if (diagnostic == null || isStale()) {
                return Optional.empty();
            }
            return Optional.of(diagnostic);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Instant> storedAt() {
        lock.readLock().lock();
        try {
            return storedAt == null ? Optional.empty() : Optional.of(storedAt);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasFreshData() {
        return stackSnapshot().isPresent();
    }

    public void invalidate() {
        lock.writeLock().lock();
        try {
            this.stack = null;
            this.diagnostic = null;
            this.storedAt = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isStale() {
        if (storedAt == null) return true;
        return Duration.between(storedAt, Instant.now()).compareTo(ttl) > 0;
    }
}
