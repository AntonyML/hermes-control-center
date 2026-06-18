package com.hermes.controlcenter.domain.model;

import java.time.Instant;

public class ServiceHealth {
    private final String name;
    private final ServiceStatus status;
    private final String version;
    private final String message;
    private final Instant checkedAt;

    public ServiceHealth(String name, ServiceStatus status, String version, String message) {
        this.name = name;
        this.status = status;
        this.version = version;
        this.message = message;
        this.checkedAt = Instant.now();
    }

    public static ServiceHealth online(String name, String version, String message) {
        return new ServiceHealth(name, ServiceStatus.ONLINE, version, message);
    }

    public static ServiceHealth offline(String name, String message) {
        return new ServiceHealth(name, ServiceStatus.OFFLINE, null, message);
    }

    public static ServiceHealth starting(String name, String message) {
        return new ServiceHealth(name, ServiceStatus.STARTING, null, message);
    }

    public static ServiceHealth unknown(String name) {
        return new ServiceHealth(name, ServiceStatus.UNKNOWN, null, "not checked");
    }

    public static ServiceHealth probing(String name) {
        return new ServiceHealth(name, ServiceStatus.STARTING, null, "probing…");
    }

    public String getName() { return name; }
    public ServiceStatus getStatus() { return status; }
    public String getVersion() { return version; }
    public String getMessage() { return message; }
    public Instant getCheckedAt() { return checkedAt; }
}
