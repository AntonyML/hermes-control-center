package com.hermes.controlcenter.domain.snapshot;

import java.util.Objects;

public enum StartupPhase {
    BOOT("Boot", "Cargando configuración y contexto de la aplicación"),
    CONFIG("Configuración", "Cargando config.json"),
    CACHE("Cache", "Calentando cache de arranque"),
    WSL("WSL", "Probando WSL2 y Ubuntu 24.04"),
    TMUX("tmux", "Probando tmux server"),
    HERMES("Hermes", "Probando Hermes Agent"),
    ENGRAM("Engram", "Probando Engram HTTP"),
    PLUGINS("Plugins", "Probando plugins del shell"),
    SNAPSHOT("Snapshot", "Construyendo snapshot del stack"),
    READY("Listo", "Stack inicializado");

    private final String label;
    private final String description;

    StartupPhase(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    public boolean isTerminal() {
        return this == READY;
    }

    public double weight() {
        return switch (this) {
            case BOOT -> 0.05;
            case CONFIG -> 0.05;
            case CACHE -> 0.05;
            case WSL -> 0.15;
            case TMUX -> 0.10;
            case HERMES -> 0.20;
            case ENGRAM -> 0.20;
            case PLUGINS -> 0.10;
            case SNAPSHOT -> 0.05;
            case READY -> 0.05;
        };
    }

    public static double totalWeight() {
        double s = 0;
        for (StartupPhase p : values()) s += p.weight();
        return s;
    }

    public static StartupPhase atOrdinal(int i) {
        var vals = values();
        return vals[Math.max(0, Math.min(vals.length - 1, i))];
    }

    public StartupPhase next() {
        var vals = values();
        int next = this.ordinal() + 1;
        return next >= vals.length ? this : vals[next];
    }

    @Override
    public String toString() {
        return label;
    }
}
