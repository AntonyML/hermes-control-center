package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines whether Hermes is online. This is a pure health check: it never
 * starts, restarts, or otherwise mutates anything.
 *
 * <p>{@code hermes-start.sh} execs a long-running foreground process (the
 * agent itself). Invoking it here — as a previous version of this class did —
 * meant every health check blocked until the executor's timeout fired, even
 * while Hermes was already running fine inside tmux. That produced the
 * exact symptom reported: Engram and tmux both healthy, Hermes stuck on
 * "timeout".</p>
 *
 * <p>Detection priority (first match wins), all strictly read-only:</p>
 * <ol>
 *   <li>{@code tmux has-session -t hermes}</li>
 *   <li>{@code tmux list-panes -t hermes}</li>
 *   <li>{@code pgrep} for the hermes-agent process</li>
 * </ol>
 */
public class HermesService {
    private static final Logger log = LoggerFactory.getLogger(HermesService.class);
    private final CommandExecutor executor;
    private final AppConfig config;

    public HermesService(CommandExecutor executor, AppConfig config) {
        this.executor = executor;
        this.config = config;
    }

    public ServiceHealth check() {
        var session = executor.run(CommandCatalog.hasHermesSession(config), 5);
        if (session.isOk()) {
            log.info("Hermes ONLINE (tmux session found)");
            return ServiceHealth.online("Hermes", null, "tmux session 'hermes' active");
        }

        var panes = executor.run(CommandCatalog.listHermesPanes(config), 5);
        if (panes.isOk() && panes.getOutput() != null && !panes.getOutput().isBlank()) {
            log.info("Hermes ONLINE (tmux panes found)");
            return ServiceHealth.online("Hermes", null, "tmux panes active");
        }

        var process = executor.run(CommandCatalog.pgrepHermesProcess(config), 5);
        if (process.isOk() && process.getOutput() != null && !process.getOutput().isBlank()) {
            log.info("Hermes ONLINE (process found)");
            return ServiceHealth.online("Hermes", null, "hermes-agent process running");
        }

        log.info("Hermes OFFLINE (session missing)");
        return ServiceHealth.offline("Hermes", "tmux session 'hermes' not found");
    }
}
