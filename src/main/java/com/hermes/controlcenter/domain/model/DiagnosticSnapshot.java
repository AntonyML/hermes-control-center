package com.hermes.controlcenter.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiagnosticSnapshot {
    private final ServiceHealth ubuntu;
    private final ServiceHealth wsl;
    private final Map<String, ServiceHealth> plugins;
    private final ServiceHealth hermes;
    private final ServiceHealth engram;
    private final ServiceHealth tmux;
    private final boolean sessionExists;
    private final String projectName;
    private final java.time.Instant capturedAt;

    public DiagnosticSnapshot(ServiceHealth ubuntu,
                              ServiceHealth wsl,
                              Map<String, ServiceHealth> plugins,
                              ServiceHealth hermes,
                              ServiceHealth engram,
                              ServiceHealth tmux,
                              boolean sessionExists,
                              String projectName) {
        this.ubuntu = ubuntu;
        this.wsl = wsl;
        this.plugins = plugins == null ? new LinkedHashMap<>() : new LinkedHashMap<>(plugins);
        this.hermes = hermes;
        this.engram = engram;
        this.tmux = tmux;
        this.sessionExists = sessionExists;
        this.projectName = projectName;
        this.capturedAt = java.time.Instant.now();
    }

    public static DiagnosticSnapshot empty(String projectName) {
        ServiceHealth probing = ServiceHealth.probing("stack");
        return new DiagnosticSnapshot(probing, probing, new LinkedHashMap<>(),
            probing, probing, probing, false, projectName);
    }

    public ServiceHealth getUbuntu() { return ubuntu; }
    public ServiceHealth getWsl() { return wsl; }
    public Map<String, ServiceHealth> getPlugins() { return plugins; }
    public List<ServiceHealth> getPluginsList() { return List.copyOf(plugins.values()); }
    public ServiceHealth getHermes() { return hermes; }
    public ServiceHealth getEngram() { return engram; }
    public ServiceHealth getTmux() { return tmux; }
    public boolean isSessionExists() { return sessionExists; }
    public String getProjectName() { return projectName; }
    public java.time.Instant getCapturedAt() { return capturedAt; }
}
