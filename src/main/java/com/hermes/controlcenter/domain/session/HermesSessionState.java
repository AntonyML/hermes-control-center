package com.hermes.controlcenter.domain.session;

import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;

public class HermesSessionState {
    private final String sessionName;
    private final boolean exists;
    private final int paneCount;
    private final String attachedUser;
    private final long lastSeenMillis;

    public HermesSessionState(String sessionName, boolean exists, int paneCount,
                              String attachedUser, long lastSeenMillis) {
        this.sessionName = sessionName;
        this.exists = exists;
        this.paneCount = paneCount;
        this.attachedUser = attachedUser;
        this.lastSeenMillis = lastSeenMillis;
    }

    public static HermesSessionState absent(String name) {
        return new HermesSessionState(name, false, 0, null, 0L);
    }

    public String getSessionName() { return sessionName; }
    public boolean isExists() { return exists; }
    public int getPaneCount() { return paneCount; }
    public String getAttachedUser() { return attachedUser; }
    public long getLastSeenMillis() { return lastSeenMillis; }

    public ServiceHealth toHealth() {
        if (!exists) {
            return ServiceHealth.offline("Hermes session",
                "tmux session '" + sessionName + "' not found");
        }
        return ServiceHealth.online("Hermes session",
            paneCount + " panes",
            attachedUser != null ? "attached: " + attachedUser : "ready");
    }
}
