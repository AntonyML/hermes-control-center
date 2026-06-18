package com.hermes.controlcenter.cache;

import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import com.hermes.controlcenter.domain.snapshot.StartupProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Application-wide startup cache facade. Combines a {@link HealthSnapshotCache}
 * and a {@link StackStateBuffer} behind a single API so the bootstrap pipeline
 * only has one place to write into and the UI only has one place to read from.
 */
public class StartupCache {
    private static final Logger log = LoggerFactory.getLogger(StartupCache.class);

    private final HealthSnapshotCache snapshots = new HealthSnapshotCache();
    private final StackStateBuffer buffer = new StackStateBuffer();
    private volatile StartupProgress progress = StartupProgress.initial();
    private volatile boolean warmedUp = false;
    private volatile long lastProbeMillis = 0L;

    public void recordProgress(StartupProgress p) {
        this.progress = p;
        log.info("startup progress: {} ({}%) {}", p.getPhase().getLabel(),
            Math.round(p.getPercent() * 100), p.getMessage());
    }

    public StartupProgress progress() {
        return progress;
    }

    public void recordSnapshots(StackSnapshot stack, DiagnosticSnapshot diag,
                                HermesSessionState session) {
        snapshots.put(stack, diag);
        buffer.put("WSL", stack.getWsl());
        buffer.put("Ubuntu 24.04", stack.getUbuntu());
        buffer.put("tmux", stack.getTmux());
        buffer.put("Hermes", stack.getHermes());
        buffer.put("Engram", stack.getEngram());
        buffer.put("OpenCode", stack.getOpencode());
        if (diag != null) {
            for (var e : diag.getPlugins().entrySet()) {
                buffer.put(e.getKey(), e.getValue());
            }
        }
        if (session != null) {
            buffer.putHermesSession(session);
        }
        warmedUp = true;
        lastProbeMillis = System.currentTimeMillis();
    }

    public Optional<StackSnapshot> latestStack() {
        return snapshots.stackSnapshot();
    }

    public Optional<DiagnosticSnapshot> latestDiagnostic() {
        return snapshots.diagnosticSnapshot();
    }

    public StackStateBuffer buffer() {
        return buffer;
    }

    public boolean isWarmedUp() {
        return warmedUp;
    }

    public long lastProbeMillis() {
        return lastProbeMillis;
    }

    public void invalidate() {
        snapshots.invalidate();
        buffer.clear();
        warmedUp = false;
    }
}
