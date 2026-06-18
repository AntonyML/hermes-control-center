package com.hermes.controlcenter.domain.model;

import java.util.List;

public class StackSnapshot {
    private final ServiceHealth wsl;
    private final ServiceHealth ubuntu;
    private final ServiceHealth tmux;
    private final ServiceHealth hermes;
    private final ServiceHealth engram;
    private final ServiceHealth opencode;
    private final boolean tmuxSessionExists;
    private final List<ServiceHealth> all;
    private final PrimaryAction primaryAction;
    private final boolean reliable;

    public StackSnapshot(ServiceHealth wsl,
                         ServiceHealth ubuntu,
                         ServiceHealth tmux,
                         ServiceHealth hermes,
                         ServiceHealth engram,
                         ServiceHealth opencode,
                         boolean tmuxSessionExists,
                         PrimaryAction primaryAction) {
        this(wsl, ubuntu, tmux, hermes, engram, opencode, tmuxSessionExists, primaryAction, false);
    }

    public StackSnapshot(ServiceHealth wsl,
                         ServiceHealth ubuntu,
                         ServiceHealth tmux,
                         ServiceHealth hermes,
                         ServiceHealth engram,
                         ServiceHealth opencode,
                         boolean tmuxSessionExists,
                         PrimaryAction primaryAction,
                         boolean reliable) {
        this.wsl = wsl;
        this.ubuntu = ubuntu;
        this.tmux = tmux;
        this.hermes = hermes;
        this.engram = engram;
        this.opencode = opencode;
        this.tmuxSessionExists = tmuxSessionExists;
        this.primaryAction = primaryAction;
        this.reliable = reliable;
        this.all = List.of(wsl, ubuntu, tmux, hermes, engram, opencode);
    }

    public static StackSnapshot starting() {
        ServiceHealth probing = ServiceHealth.probing("stack");
        return new StackSnapshot(
            probing, probing, probing, probing, probing, probing,
            false, PrimaryAction.START_STACK, false
        );
    }

    public ServiceHealth getWsl() { return wsl; }
    public ServiceHealth getUbuntu() { return ubuntu; }
    public ServiceHealth getTmux() { return tmux; }
    public ServiceHealth getHermes() { return hermes; }
    public ServiceHealth getEngram() { return engram; }
    public ServiceHealth getOpencode() { return opencode; }
    public boolean isTmuxSessionExists() { return tmuxSessionExists; }
    public List<ServiceHealth> getAll() { return all; }
    public PrimaryAction getPrimaryAction() { return primaryAction; }
    public boolean isReliable() { return reliable; }

    public long countOnline() {
        return all.stream().filter(h -> h.getStatus() == ServiceStatus.ONLINE).count();
    }

    public int total() {
        return all.size();
    }
}
