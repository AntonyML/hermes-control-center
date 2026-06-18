package com.hermes.controlcenter.cache;

import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Last-known-good buffer keyed by service/plugin name. Unlike HealthSnapshotCache
 * (which is a complete snapshot), this buffer keeps a single per-key health entry
 * that survives across snapshots. The buffer is what the UI uses to render cards
 * with a coherent "no flicker" experience while new probes arrive.
 *
 * <p>Behavior:</p>
 * <ul>
 *     <li>{@link #put(String, ServiceHealth)} updates or inserts a health entry.</li>
 *     <li>{@link #get(String)} returns the last entry (never null — returns
 *         {@code STARTING} "probing…" if nothing is known yet).</li>
 *     <li>{@link #getOrProbe(String)} returns a "probing" entry if no fresh data exists.</li>
 * </ul>
 */
public class StackStateBuffer {
    private static final Logger log = LoggerFactory.getLogger(StackStateBuffer.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, ServiceHealth> byName = new LinkedHashMap<>();
    private HermesSessionState hermesSession = HermesSessionState.absent("hermes");

    public void put(String name, ServiceHealth h) {
        lock.writeLock().lock();
        try {
            byName.put(name, h);
        } finally {
            lock.writeLock().unlock();
        }
        log.trace("buffer put: {} -> {}", name, h.getStatus());
    }

    public void putAll(Map<String, ServiceHealth> map) {
        if (map == null) return;
        for (var e : map.entrySet()) put(e.getKey(), e.getValue());
    }

    public void putHermesSession(HermesSessionState s) {
        lock.writeLock().lock();
        try {
            this.hermesSession = s;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ServiceHealth get(String name) {
        lock.readLock().lock();
        try {
            ServiceHealth h = byName.get(name);
            return h != null ? h : ServiceHealth.probing(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    public HermesSessionState hermesSession() {
        lock.readLock().lock();
        try {
            return hermesSession;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, ServiceHealth> snapshot() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(byName);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int knownCount() {
        lock.readLock().lock();
        try {
            return byName.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        return knownCount() == 0;
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            byName.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ServiceHealth statusOrProbe(String name) {
        ServiceHealth h = get(name);
        if (h.getStatus() == ServiceStatus.OFFLINE) {
            return ServiceHealth.probing(name);
        }
        return h;
    }
}
