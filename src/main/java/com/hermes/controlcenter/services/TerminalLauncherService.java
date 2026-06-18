package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import com.hermes.controlcenter.infrastructure.CommandExecutor.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches the real terminal session for tmux attach. This is the ONLY place
 * that opens a tmux attach window.
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>Probe if a session exists.</li>
 *   <li>If not, create it via {@code hermes-tmux.sh} (idempotent).</li>
 *   <li>Open a real terminal that runs:
 *       {@code wsl -d Ubuntu-24.04 -u USER -- tmux attach -t hermes}</li>
 * </ol>
 *
 * <p>The terminal launch prefers Windows Terminal ({@code wt.exe}); if not
 * available, it falls back to {@code cmd.exe /c start "Hermes" wsl ...}
 * which spawns a new console window visible to the user.</p>
 */
public class TerminalLauncherService {
    private static final Logger log = LoggerFactory.getLogger(TerminalLauncherService.class);

    private final CommandExecutor executor;
    private final AppConfig config;
    private final TmuxService tmuxService;

    public TerminalLauncherService(CommandExecutor executor, AppConfig config, TmuxService tmuxService) {
        this.executor = executor;
        this.config = config;
        this.tmuxService = tmuxService;
    }

    public boolean hermesSessionExists() {
        return tmuxService.sessionExists("hermes");
    }

    /**
     * Full flow: ensure session exists, then open a real terminal to attach.
     * The attach is non-blocking: it returns as soon as the terminal window
     * has been launched. Output of the wsl attach command is consumed in the
     * background; the visible window is what the user actually interacts with.
     */
    public LaunchResult openHermesTerminal() {
        log.info("openHermesTerminal requested");
        if (!tmuxService.sessionExists("hermes")) {
            log.info("session missing; creating via hermes-tmux.sh");
            var create = executor.run(CommandCatalog.createHermesSession(config), 30);
            if (create.isFailed()) {
                log.error("create session failed: {}", create.getErrorTag());
                return LaunchResult.failed("create session failed: " + create.getErrorTag());
            }
        }
        return openAttachTerminal();
    }

    /**
     * Open a real terminal that runs the tmux attach. This is the only method
     * in the codebase that opens a new console window the user can see.
     */
    public LaunchResult openAttachTerminal() {
        String wslAttach = String.join(" ",
            "wsl", "-d", quote(config.getWslDistro()),
            "-u", quote(config.getLinuxUser()), "--",
            "tmux", "attach", "-t", "hermes");
        return openRawTerminalInWindow("Hermes — tmux attach", wslAttach);
    }

    /**
     * Open a real terminal that runs an arbitrary command. The command runs
     * inside a new console window (Windows Terminal preferred, cmd fallback).
     * The command itself can be a wsl-prefixed command, a native Windows
     * command, or any other executable.
     */
    public LaunchResult openRawTerminalInWindow(String title, String commandLine) {
        log.info("openRawTerminalInWindow: title='{}' cmd='{}'", title, commandLine);
        if (hasWindowsTerminal()) {
            boolean ok = launchWithWindowsTerminal(title, commandLine);
            if (ok) {
                return LaunchResult.ok("Windows Terminal — " + title);
            }
            log.warn("wt.exe found but launch failed; falling back to cmd /c start");
        }
        boolean ok = launchWithCmdStart(title, commandLine);
        if (!ok) {
            return LaunchResult.failed("could not launch a real terminal: " + title);
        }
        return LaunchResult.ok("Console (cmd) — " + title);
    }

    private boolean hasWindowsTerminal() {
        String configured = config.getTerminalCommand();
        if (configured == null || configured.isBlank() || "wt".equalsIgnoreCase(configured)) {
            return commandOnPath("wt.exe") || commandOnPath("wt");
        }
        return commandOnPath(configured);
    }

    private boolean commandOnPath(String cmd) {
        try {
            Process p = new ProcessBuilder("where.exe", cmd)
                .redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean launchWithWindowsTerminal(String title, String commandLine) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("wt.exe");
            cmd.add("-w");
            cmd.add("0");
            cmd.add("new-tab");
            cmd.add("--title");
            cmd.add(title);
            cmd.add("wsl.exe");
            cmd.add("-d");
            cmd.add(config.getWslDistro());
            cmd.add("-u");
            cmd.add(config.getLinuxUser());
            cmd.add("--");
            cmd.add("tmux");
            cmd.add("attach");
            cmd.add("-t");
            cmd.add("hermes");
            log.info("launch: {}", String.join(" ", cmd));
            new ProcessBuilder(cmd).inheritIO().start();
            return true;
        } catch (IOException e) {
            log.error("wt launch failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean launchWithCmdStart(String title, String commandLine) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("\"" + title + "\"");
            cmd.add(commandLine);
            log.info("launch: {}", String.join(" ", cmd));
            new ProcessBuilder(cmd).inheritIO().start();
            return true;
        } catch (IOException e) {
            log.error("cmd start launch failed: {}", e.getMessage());
            return false;
        }
    }

    private String quote(String s) {
        if (s == null) return "\"\"";
        if (s.contains(" ")) return "\"" + s + "\"";
        return s;
    }

    /**
     * Result of a launch attempt. Surfaces both success and the human-readable
     * detail so the UI / console can show it.
     */
    public static class LaunchResult {
        private final boolean ok;
        private final String detail;

        private LaunchResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }

        public static LaunchResult ok(String d) { return new LaunchResult(true, d); }
        public static LaunchResult failed(String d) { return new LaunchResult(false, d); }

        public boolean isOk() { return ok; }
        public String getDetail() { return detail; }
    }
}
