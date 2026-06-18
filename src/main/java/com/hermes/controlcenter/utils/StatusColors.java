package com.hermes.controlcenter.utils;

import com.hermes.controlcenter.domain.model.ServiceStatus;

import java.awt.Color;

public final class StatusColors {
    private StatusColors() {}

    public static Color of(ServiceStatus status) {
        return switch (status) {
            case ONLINE -> new Color(0x4ADE80);
            case OFFLINE -> new Color(0xF87171);
            case STARTING -> new Color(0xFACC15);
            case UNKNOWN -> new Color(0x94A3B8);
        };
    }

    public static Color background() {
        return new Color(0x0F172A);
    }

    public static Color cardBackground() {
        return new Color(0x1E293B);
    }

    public static Color cardBorder() {
        return new Color(0x334155);
    }

    public static Color textPrimary() {
        return new Color(0xF8FAFC);
    }

    public static Color textSecondary() {
        return new Color(0x94A3B8);
    }

    public static Color accent() {
        return new Color(0x38BDF8);
    }
}
