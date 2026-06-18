package com.hermes.controlcenter.infrastructure;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.CommandSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized whitelist of every external command the app is allowed to launch.
 * No service may construct a ProcessBuilder directly; everything goes through
 * {@link CommandSpec} and {@link CommandExecutor}. The whitelist is the single
 * source of truth.
 *
 * <p>Two views exist:</p>
 * <ul>
 *     <li><b>Method-based</b>: typed factories for the well-known commands
 *         (stack scripts, plugin probes, etc.).</li>
 *     <li><b>Console verb whitelist</b>: {@link #CONSOLE_VERBS} maps the
 *         human-readable verbs that the in-app console accepts to the
 *         corresponding {@link CommandSpec}. Anything typed by the user that
 *         is not in this map is rejected with a clear message.</li>
 * </ul>
 */
public final class CommandCatalog {
    private CommandCatalog() {}

    // ===== Stack whitelist (real Hermes flow) =====

    /**
     * Records the {@code tmux attach -t hermes} spec for documentation and
     * the console whitelist. This spec is NEVER executed through the
     * executor's "interactive" path: a Swing process has no tty. Use
     * {@link com.hermes.controlcenter.services.TerminalLauncherService} to
     * open a real terminal that runs the attach.
     */
    public static CommandSpec attachHermes(AppConfig cfg) {
        return new CommandSpec(
            "Attach to existing tmux session 'hermes' (launches a real terminal)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tmux", "attach", "-t", "hermes"
            },
            false
        );
    }

    public static CommandSpec listTmuxSessions(AppConfig cfg) {
        return new CommandSpec(
            "List tmux sessions",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tmux", "ls"
            },
            false
        );
    }

    public static CommandSpec createHermesSession(AppConfig cfg) {
        return new CommandSpec(
            "Create tmux 'hermes' session (4-pane workspace)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", cfg.getTmuxScript()
            },
            false
        );
    }

    public static CommandSpec startHermesStack(AppConfig cfg) {
        return new CommandSpec(
            "Start Hermes stack (hermes-start.sh)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", cfg.getHermesStartScript()
            },
            false
        );
    }

    public static CommandSpec startEngram(AppConfig cfg) {
        return new CommandSpec(
            "Start Engram HTTP server (engram-start.sh)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", cfg.getEngramStartScript()
            },
            false
        );
    }

    // ===== Read-only health checks (CHECK must never START anything) =====

    /**
     * Read-only: exit 0 if tmux session 'hermes' exists, non-zero otherwise.
     * This is the PRIMARY signal for "is Hermes online". It never starts,
     * attaches to, or otherwise mutates the session — has-session is the
     * standard tmux existence probe and is more robust than parsing `tmux ls`.
     */
    public static CommandSpec hasHermesSession(AppConfig cfg) {
        return new CommandSpec(
            "Check tmux session 'hermes' exists (read-only)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tmux", "has-session", "-t", "hermes"
            },
            false
        );
    }

    /**
     * Read-only: lists panes of the 'hermes' tmux session. SECONDARY signal,
     * consulted only when {@link #hasHermesSession} is inconclusive.
     */
    public static CommandSpec listHermesPanes(AppConfig cfg) {
        return new CommandSpec(
            "List panes of tmux session 'hermes' (read-only)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tmux", "list-panes", "-t", "hermes"
            },
            false
        );
    }

    /**
     * Read-only: looks for a running Hermes Agent process by the binary path
     * used in hermes-start.sh. TERTIARY signal, covers Hermes running outside
     * the managed tmux session.
     */
    public static CommandSpec pgrepHermesProcess(AppConfig cfg) {
        return new CommandSpec(
            "Check for a running Hermes Agent process (read-only)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "pgrep", "-f", "hermes-agent/hermes"
            },
            false
        );
    }

    /**
     * Read-only: probes Engram's HTTP endpoint; if that doesn't respond,
     * falls back to checking whether its port is already bound. Either case
     * means Engram is ONLINE — a bound port (the same condition that surfaces
     * as "address already in use" if something else tries to bind it again)
     * is proof of life, not failure. Never invokes engram-start.sh.
     */
    public static CommandSpec checkEngramHealth(AppConfig cfg) {
        String port = String.valueOf(cfg.getEngramPort());
        String probe =
            "curl -s --max-time 2 http://127.0.0.1:" + port + "/health >/dev/null 2>&1 && echo __HTTP_OK__ "
            + "|| (ss -ltn 2>/dev/null | grep -q ':" + port + " ' && echo __PORT_BOUND__) "
            + "|| echo __OFFLINE__";
        return new CommandSpec(
            "Check Engram health on port " + port + " (read-only)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", "-lc", probe
            },
            false
        );
    }

    // ===== Plugin probes =====

    public static CommandSpec probeCommand(AppConfig cfg, String cmd) {
        return new CommandSpec(
            "Probe " + cmd + " version",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", "-lc", "command -v " + cmd + " >/dev/null 2>&1 && " + cmd + " --version 2>&1 || echo NOT_FOUND"
            },
            false
        );
    }

    public static CommandSpec wslUname(AppConfig cfg) {
        return new CommandSpec(
            "Ubuntu version (uname)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "uname", "-a"
            },
            false
        );
    }

    public static CommandSpec wslUptime(AppConfig cfg) {
        return new CommandSpec(
            "Ubuntu uptime",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "uptime"
            },
            false
        );
    }

    public static CommandSpec wslCatBashrc(AppConfig cfg) {
        return new CommandSpec(
            "Inspect ~/.bashrc (oh-my-posh / zoxide init lines)",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "bash", "-c", "tail -10 ~/.bashrc"
            },
            false
        );
    }

    public static CommandSpec tailEngramLog(AppConfig cfg) {
        return new CommandSpec(
            "Tail engram log",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tail", "-n", "40",
                cfg.getHermesRoot().replace("E:\\", "/mnt/e/").replace("\\", "/")
                    + "/logs/engram.log"
            },
            false
        );
    }

    public static CommandSpec tmuxVersion(AppConfig cfg) {
        return new CommandSpec(
            "tmux version",
            new String[] {
                "wsl", "-d", cfg.getWslDistro(), "-u", cfg.getLinuxUser(), "--",
                "tmux", "-V"
            },
            false
        );
    }

    // ===== Console verb whitelist =====
    //
    // The console only accepts verbs listed here. Each verb is bound to:
    //   - a description (used by `help`)
    //   - a mode (DIAGNOSTIC or INTERACTIVE_TERMINAL)
    //   - a factory (DIAGNOSTIC) that produces the actual CommandSpec
    //   - a raw command template (INTERACTIVE_TERMINAL) executed in a new
    //     terminal window by TerminalLauncherService
    //
    // Factories are used (not pre-built CommandSpecs) so the runtime
    // AppConfig is always honored.

    public enum Mode { DIAGNOSTIC, INTERACTIVE_TERMINAL }

    public static final class Verb {
        public final String verb;
        public final String description;
        public final Mode mode;
        public final java.util.function.Function<AppConfig, CommandSpec> specFactory;
        public final String rawCommand;

        public Verb(String verb, String description, Mode mode,
                    java.util.function.Function<AppConfig, CommandSpec> factory,
                    String rawCommand) {
            this.verb = verb;
            this.description = description;
            this.mode = mode;
            this.specFactory = factory;
            this.rawCommand = rawCommand;
        }

        public static Verb diagnostic(String verb, String description,
                                      java.util.function.Function<AppConfig, CommandSpec> factory) {
            return new Verb(verb, description, Mode.DIAGNOSTIC, factory, null);
        }

        public static Verb terminal(String verb, String description, String rawCommand) {
            return new Verb(verb, description, Mode.INTERACTIVE_TERMINAL, null, rawCommand);
        }

        public boolean isInteractive() {
            return mode == Mode.INTERACTIVE_TERMINAL;
        }
    }

    private static String wsl(AppConfig cfg, String... rest) {
        StringBuilder sb = new StringBuilder();
        sb.append("wsl -d ").append(cfg.getWslDistro())
          .append(" -u ").append(cfg.getLinuxUser())
          .append(" --");
        for (String r : rest) sb.append(' ').append(r);
        return sb.toString();
    }

    public static final List<Verb> CONSOLE_VERBS = List.of(
        Verb.diagnostic("status", "Muestra el snapshot actual del stack",
            cfg -> listTmuxSessions(cfg)),
        Verb.diagnostic("refresh", "Reprobea todos los servicios",
            cfg -> listTmuxSessions(cfg)),
        Verb.diagnostic("start stack", "Inicia el stack Hermes (hermes-start.sh)",
            cfg -> startHermesStack(cfg)),
        Verb.diagnostic("start engram", "Inicia Engram (engram-start.sh)",
            cfg -> startEngram(cfg)),
        Verb.diagnostic("create session", "Crea la sesión tmux 'hermes'",
            cfg -> createHermesSession(cfg)),
        Verb.terminal("attach hermes", "Adjunta a la sesión tmux 'hermes' en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- tmux attach -t hermes"),
        Verb.terminal("open terminal", "Abre una terminal real con shell WSL",
            "wsl -d {{distro}} -u {{user}} -- bash -l"),
        Verb.terminal("open lazygit", "Abre Lazygit en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- lazygit"),
        Verb.terminal("open btop", "Abre Btop en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- btop"),
        Verb.terminal("open eza", "Abre Eza (tree) en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- eza -lah --git {{hermesRoot}}"),
        Verb.terminal("open zoxide", "Abre Zoxide en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- z {{hermesRoot}}"),
        Verb.terminal("go to hermes", "Salta a /mnt/e/Dev/Hermes en una terminal real",
            "wsl -d {{distro}} -u {{user}} -- bash -lc \"cd {{hermesRoot}} && exec bash -l\""),
        Verb.diagnostic("diagnose", "Ejecuta diagnóstico técnico (uname)",
            cfg -> wslUname(cfg)),
        Verb.diagnostic("tmux ls", "Lista sesiones tmux activas",
            cfg -> listTmuxSessions(cfg)),
        Verb.diagnostic("tmux -V", "Versión de tmux",
            cfg -> tmuxVersion(cfg)),
        Verb.diagnostic("uname -a", "Información de kernel Ubuntu",
            cfg -> wslUname(cfg)),
        Verb.diagnostic("uptime", "Tiempo activo de Ubuntu",
            cfg -> wslUptime(cfg)),
        Verb.diagnostic("tail engram", "Tail del log de Engram",
            cfg -> tailEngramLog(cfg)),
        Verb.diagnostic("help", "Muestra esta ayuda",
            cfg -> null)
    );

    public static final Map<String, Verb> VERB_INDEX;
    static {
        Map<String, Verb> idx = new LinkedHashMap<>();
        for (Verb v : CONSOLE_VERBS) idx.put(v.verb.toLowerCase(), v);
        VERB_INDEX = Map.copyOf(idx);
    }
}
