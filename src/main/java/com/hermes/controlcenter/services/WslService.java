package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WslService {
    private static final Logger log = LoggerFactory.getLogger(WslService.class);
    private final CommandExecutor executor;
    private final AppConfig config;

    public WslService(CommandExecutor executor, AppConfig config) {
        this.executor = executor;
        this.config = config;
    }

    public ServiceHealth check() {
        var result = executor.run(CommandCatalog.listTmuxSessions(config), 10);
        if (result.isOk() || (result.getOutput() != null && result.getOutput().contains("hermes"))) {
            return ServiceHealth.online("WSL", null, "wsl responds");
        }
        if (result.getOutput() != null && result.getOutput().toLowerCase().contains("no server running")) {
            return ServiceHealth.online("WSL", null, "wsl up; tmux server stopped");
        }
        if (result.isFailed()) {
            return ServiceHealth.offline("WSL", result.getErrorTag() != null ? result.getErrorTag() : "wsl unreachable");
        }
        return ServiceHealth.online("WSL", null, "wsl responds");
    }
}
