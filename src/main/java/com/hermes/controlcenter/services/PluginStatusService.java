package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Probes the shell-only tools (git, bat, eza, btop, lazygit, zoxide, oh-my-posh)
 * by running `command -v` + `--version` in the configured WSL distro.
 * Each tool is reported independently so the UI can show it as a card.
 */
public class PluginStatusService {
    private static final Logger log = LoggerFactory.getLogger(PluginStatusService.class);

    public static final String[] TOOLS = {
        "git", "bat", "batcat", "eza", "btop", "lazygit", "zoxide", "oh-my-posh"
    };

    public static final String[] LABELS = {
        "Git", "Bat", "Bat (batcat)", "Eza", "Btop", "Lazygit", "Zoxide", "Oh My Posh"
    };

    public static final Map<String, Integer> LABEL_TO_INDEX = new HashMap<>();
    static {
        for (int i = 0; i < LABELS.length; i++) LABEL_TO_INDEX.put(LABELS[i], i);
    }

    private final CommandExecutor executor;
    private final AppConfig config;

    public PluginStatusService(CommandExecutor executor, AppConfig config) {
        this.executor = executor;
        this.config = config;
    }

    public ServiceHealth check(String tool, String label) {
        var result = executor.run(CommandCatalog.probeCommand(config, tool), 5);
        if (!result.isOk() && result.getErrorTag() != null) {
            return ServiceHealth.offline(label, "exec failed: " + result.getErrorTag());
        }
        String out = result.getOutput();
        if (out == null || out.toUpperCase().contains("NOT_FOUND")) {
            return ServiceHealth.offline(label, "binary not found on PATH");
        }
        String version = parseVersion(out);
        return ServiceHealth.online(label, version, tool + " --version OK");
    }

    public ServiceHealth checkUbuntu() {
        var result = executor.run(CommandCatalog.wslUname(config), 5);
        if (result.isOk() && result.getOutput() != null) {
            String first = result.getOutput().split("\\R", 2)[0].trim();
            String kernel = parseKernelVersion(first);
            return ServiceHealth.online("Ubuntu 24.04", kernel, "uname OK");
        }
        return ServiceHealth.offline("Ubuntu 24.04",
            result.getErrorTag() != null ? result.getErrorTag() : "uname failed");
    }

    public ServiceHealth checkWsl() {
        // WSL is implicitly OK if any wsl -d command returned 0 in the last second.
        // Use a lightweight probe: list tmux sessions (already in catalog) — exit 0 OR
        // exit 1 with "no server running" both mean WSL responded.
        var result = executor.run(CommandCatalog.listTmuxSessions(config), 5);
        if (result.isFailed() && result.getErrorTag() != null) {
            return ServiceHealth.offline("WSL2", result.getErrorTag());
        }
        return ServiceHealth.online("WSL2", null, config.getWslDistro() + " responsive");
    }

    private String parseVersion(String out) {
        if (out == null) return null;
        for (String raw : out.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("not_found")) continue;
            // Take first token that looks like a version
            String[] tokens = line.split("\\s+");
            for (String t : tokens) {
                if (t.matches("\\d[\\d.a-zA-Z_-]*")) return t;
            }
            // Fallback: first non-empty line trimmed
            return line.length() > 60 ? line.substring(0, 60) + "…" : line;
        }
        return null;
    }

    private String parseKernelVersion(String unameLine) {
        if (unameLine == null) return null;
        String[] parts = unameLine.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+\\.\\d+\\.\\d+.*")) return parts[i];
        }
        return null;
    }
}
