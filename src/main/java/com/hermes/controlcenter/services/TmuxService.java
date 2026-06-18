package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TmuxService {
    private static final Logger log = LoggerFactory.getLogger(TmuxService.class);
    private final CommandExecutor executor;
    private final AppConfig config;

    public TmuxService(CommandExecutor executor, AppConfig config) {
        this.executor = executor;
        this.config = config;
    }

    public ServiceHealth check() {
        var result = executor.run(CommandCatalog.listTmuxSessions(config), 10);
        if (result.isFailed()) {
            return ServiceHealth.offline("tmux", result.getErrorTag() != null ? result.getErrorTag() : "tmux not responding");
        }
        return ServiceHealth.online("tmux", null, "tmux server running");
    }

    public boolean sessionExists(String sessionName) {
        if ("hermes".equals(sessionName)) {
            // has-session is the standard, robust tmux existence probe — far
            // more reliable than parsing `tmux ls` output line-by-line.
            return executor.run(CommandCatalog.hasHermesSession(config), 5).isOk();
        }
        var result = executor.run(CommandCatalog.listTmuxSessions(config), 10);
        if (result.getOutput() == null) return false;
        return result.getOutput().lines().anyMatch(line -> line.startsWith(sessionName + ":"));
    }
}
