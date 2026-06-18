package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.CommandSpec;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCodeService {
    private static final Logger log = LoggerFactory.getLogger(OpenCodeService.class);
    private final CommandExecutor executor;

    public OpenCodeService(CommandExecutor executor) {
        this.executor = executor;
    }

    public ServiceHealth check() {
        // No whitelist command for opencode probe. Use raw version call (only place
        // outside the whitelist where we shell out; opencode --version is non-invasive
        // and read-only). Routed through CommandExecutor for logging.
        CommandSpec spec = new CommandSpec(
            "Probe opencode version",
            new String[] { "opencode", "--version" },
            false
        );
        var result = executor.run(spec, 5);
        if (result.isOk()) {
            String v = parseVersion(result.getOutput());
            return ServiceHealth.online("OpenCode", v, "ready");
        }
        if (result.getOutput() != null && result.getOutput().toLowerCase().contains("not found")) {
            return ServiceHealth.offline("OpenCode", "opencode binary not on PATH");
        }
        return ServiceHealth.unknown("OpenCode");
    }

    private String parseVersion(String output) {
        if (output == null) return null;
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.matches("\\d+\\.\\d+\\.\\d+.*")) return trimmed.split("\\s+")[0];
        }
        return null;
    }
}
