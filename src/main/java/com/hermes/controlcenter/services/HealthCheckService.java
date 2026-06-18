package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.PrimaryAction;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregates per-service checks into a single StackSnapshot and decides
 * which single primary action should be exposed in the UI.
 */
public class HealthCheckService {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private final WslService wsl;
    private final TmuxService tmux;
    private final HermesService hermes;
    private final EngramService engram;
    private final OpenCodeService opencode;
    private final AppConfig config;

    public HealthCheckService(WslService wsl,
                              TmuxService tmux,
                              HermesService hermes,
                              EngramService engram,
                              OpenCodeService opencode,
                              AppConfig config) {
        this.wsl = wsl;
        this.tmux = tmux;
        this.hermes = hermes;
        this.engram = engram;
        this.opencode = opencode;
        this.config = config;
    }

    public StackSnapshot snapshot() {
        log.info("snapshot: probing all services");
        ServiceHealth wslH = wsl.check();
        ServiceHealth tmuxH = tmux.check();
        ServiceHealth hermesH = hermes.check();
        ServiceHealth engramH = engram.check();
        ServiceHealth opencodeH = opencode.check();

        // Ubuntu status mirrors WSL (if WSL works, the configured distro works).
        ServiceHealth ubuntuH = wslH.getStatus() == ServiceStatus.ONLINE
            ? ServiceHealth.online("Ubuntu", null, config.getWslDistro() + " ready")
            : ServiceHealth.offline("Ubuntu", "wsl not responding");

        boolean sessionExists = tmuxH.getStatus() == ServiceStatus.ONLINE
            && tmux.sessionExists("hermes");

        PrimaryAction action = decideAction(wslH, ubuntuH, tmuxH, hermesH, engramH, sessionExists);
        log.info("snapshot: primary action = {}", action);

        return new StackSnapshot(wslH, ubuntuH, tmuxH, hermesH, engramH, opencodeH, sessionExists, action);
    }

    private PrimaryAction decideAction(ServiceHealth wsl,
                                       ServiceHealth ubuntu,
                                       ServiceHealth tmux,
                                       ServiceHealth hermes,
                                       ServiceHealth engram,
                                       boolean sessionExists) {
        // If core infra is offline, we cannot start anything — keep action neutral.
        if (wsl.getStatus() != ServiceStatus.ONLINE || ubuntu.getStatus() != ServiceStatus.ONLINE) {
            return PrimaryAction.START_STACK;
        }
        if (hermes.getStatus() != ServiceStatus.ONLINE || engram.getStatus() != ServiceStatus.ONLINE) {
            return PrimaryAction.START_STACK;
        }
        if (sessionExists) {
            return PrimaryAction.ENTER_HERMES;
        }
        return PrimaryAction.CREATE_SESSION;
    }

    // Convenience for tests
    public ServiceHealth statusFor(String name) {
        return switch (name) {
            case "WSL" -> wsl.check();
            case "tmux" -> tmux.check();
            case "Hermes" -> hermes.check();
            case "Engram" -> engram.check();
            case "OpenCode" -> opencode.check();
            default -> ServiceHealth.unknown(name);
        };
    }
}
