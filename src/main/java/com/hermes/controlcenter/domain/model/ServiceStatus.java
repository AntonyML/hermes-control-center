package com.hermes.controlcenter.domain.model;

public enum ServiceStatus {
    ONLINE,
    OFFLINE,
    STARTING,
    UNKNOWN;

    public boolean isReady() {
        return this == ONLINE;
    }

    public boolean isTerminal() {
        return this == ONLINE || this == OFFLINE;
    }
}
