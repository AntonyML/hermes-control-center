package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.session.PluginDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the plugin cards shown on Home and Diagnostic.
 * Each descriptor carries a default action verb (which the
 * {@link ConsoleController} knows how to execute) and a label. UI never
 * hardcodes plugin names; it iterates this registry.
 *
 * <p>Plugin action verbs are matched against the console verb whitelist,
 * so anything an end-user can type is exactly the same thing a card click
 * dispatches.</p>
 */
public final class PluginRegistry {
    private PluginRegistry() {}

    public static final List<PluginDescriptor> PLUGINS = List.of(
        new PluginDescriptor(
            "ubuntu", "Ubuntu 24.04", "uname",
            PluginDescriptor.PluginKind.SERVICE,
            "uname -a", "kernel",
            "WSL2 distro used by every Hermes process"
        ),
        new PluginDescriptor(
            "wsl", "WSL2", "wsl.exe",
            PluginDescriptor.PluginKind.SERVICE,
            "status", "status",
            "Hypervisor running the whole Hermes stack"
        ),
        new PluginDescriptor(
            "git", "Git", "git",
            PluginDescriptor.PluginKind.VERSION_MANAGER,
            "open lazygit", "open lazygit",
            "Version control used by the Hermes project"
        ),
        new PluginDescriptor(
            "bat", "Bat", "batcat",
            PluginDescriptor.PluginKind.SHELL_TOOL,
            "tail engram", "tail engram log",
            "Used to inspect logs quickly"
        ),
        new PluginDescriptor(
            "eza", "Eza", "eza",
            PluginDescriptor.PluginKind.SHELL_TOOL,
            "open eza", "tree",
            "Tree listing of /mnt/e/Dev/Hermes"
        ),
        new PluginDescriptor(
            "btop", "Btop", "btop",
            PluginDescriptor.PluginKind.MONITOR,
            "open btop", "monitor",
            "System monitor inside the tmux session"
        ),
        new PluginDescriptor(
            "lazygit", "Lazygit", "lazygit",
            PluginDescriptor.PluginKind.GIT_UI,
            "open lazygit", "open",
            "Git TUI launched from inside the tmux session"
        ),
        new PluginDescriptor(
            "zoxide", "Zoxide", "zoxide",
            PluginDescriptor.PluginKind.SHELL_TOOL,
            "open zoxide", "go to Hermes",
            "Quick directory jump wired into the shell"
        ),
        new PluginDescriptor(
            "oh-my-posh", "Oh My Posh", "oh-my-posh",
            PluginDescriptor.PluginKind.PROMPT,
            "diagnose", "show prompt",
            "Prompt theme loaded in the interactive shell"
        ),
        new PluginDescriptor(
            "engram", "Engram", "engram",
            PluginDescriptor.PluginKind.SERVICE,
            "start engram", "start engram",
            "HTTP memory server for Hermes"
        ),
        new PluginDescriptor(
            "hermes", "Hermes", "hermes",
            PluginDescriptor.PluginKind.SERVICE,
            "start stack", "start stack",
            "Hermes Agent — main service"
        ),
        new PluginDescriptor(
            "tmux", "tmux", "tmux",
            PluginDescriptor.PluginKind.SERVICE,
            "attach hermes", "attach",
            "Tmux session multiplexer hosting the 4-pane workspace"
        )
    );

    public static Map<String, PluginDescriptor> byId() {
        Map<String, PluginDescriptor> m = new LinkedHashMap<>();
        for (PluginDescriptor p : PLUGINS) m.put(p.getId(), p);
        return m;
    }

    public static Map<String, PluginDescriptor> byLabel() {
        Map<String, PluginDescriptor> m = new LinkedHashMap<>();
        for (PluginDescriptor p : PLUGINS) m.put(p.getLabel(), p);
        return m;
    }

    public static List<PluginDescriptor> list() {
        return new ArrayList<>(PLUGINS);
    }

    public static PluginDescriptor getById(String id) {
        return byId().get(id);
    }

    public static PluginDescriptor getByLabel(String label) {
        return byLabel().get(label);
    }

    public static String describeConfig(AppConfig cfg) {
        return "linux=" + cfg.getLinuxUser()
            + " distro=" + cfg.getWslDistro()
            + " hermesRoot=" + cfg.getHermesRoot();
    }
}
