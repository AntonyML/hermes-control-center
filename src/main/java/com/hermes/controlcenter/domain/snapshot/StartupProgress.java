package com.hermes.controlcenter.domain.snapshot;

import java.time.Instant;

public class StartupProgress {
    private final StartupPhase phase;
    private final double percent;
    private final String message;
    private final Instant updatedAt;

    public StartupProgress(StartupPhase phase, double percent, String message) {
        this.phase = phase;
        this.percent = Math.max(0, Math.min(1, percent));
        this.message = message == null ? "" : message;
        this.updatedAt = Instant.now();
    }

    public static StartupProgress initial() {
        return new StartupProgress(StartupPhase.BOOT, 0.0, "Inicializando…");
    }

    public StartupProgress withPhase(StartupPhase p, String message) {
        return new StartupProgress(p, this.percent, message);
    }

    public StartupProgress withPercent(double pct) {
        return new StartupProgress(this.phase, pct, this.message);
    }

    public StartupProgress withMessage(String msg) {
        return new StartupProgress(this.phase, this.percent, msg);
    }

    public StartupProgress advanced(StartupPhase target, String message) {
        double newPct = computePercentUpTo(target);
        return new StartupProgress(target, newPct, message);
    }

    public static double computePercentUpTo(StartupPhase target) {
        double sum = 0;
        for (StartupPhase p : StartupPhase.values()) {
            if (p.ordinal() <= target.ordinal()) sum += p.weight();
            else break;
        }
        return sum / StartupPhase.totalWeight();
    }

    public StartupPhase getPhase() { return phase; }
    public double getPercent() { return percent; }
    public String getMessage() { return message; }
    public Instant getUpdatedAt() { return updatedAt; }
}
