package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines whether Engram is online. This is a pure health check: it never
 * starts, restarts, or otherwise mutates anything — {@code engram-start.sh}
 * is never invoked from here.
 *
 * <p>A bound port is proof of life, not failure: if something is already
 * listening on Engram's port, that IS Engram running. "address already in
 * use" — the exact message a second start attempt would produce — is
 * therefore interpreted as ONLINE, never as an error.</p>
 */
public class EngramService {
    private static final Logger log = LoggerFactory.getLogger(EngramService.class);
    private final CommandExecutor executor;
    private final AppConfig config;

    public EngramService(CommandExecutor executor, AppConfig config) {
        this.executor = executor;
        this.config = config;
    }

    public ServiceHealth check() {
        var result = executor.run(CommandCatalog.checkEngramHealth(config), 5);
        String out = result.getOutput() == null ? "" : result.getOutput();
        String err = result.getErrorOutput() == null ? "" : result.getErrorOutput();
        String combined = (out + " " + err).toLowerCase();
        String endpoint = "http://127.0.0.1:" + config.getEngramPort();

        if (combined.contains("address already in use") || out.contains("__PORT_BOUND__")) {
            log.info("Engram ONLINE (port already bound)");
            return ServiceHealth.online("Engram", null, "port " + config.getEngramPort() + " already bound");
        }
        if (out.contains("__HTTP_OK__")) {
            log.info("Engram ONLINE (port responding)");
            return ServiceHealth.online("Engram", null, endpoint + " responding");
        }
        log.info("Engram OFFLINE");
        return ServiceHealth.offline("Engram", "port " + config.getEngramPort() + " not bound");
    }
}
