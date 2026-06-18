package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.LiveConsole;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Orchestrates the multi-step Hermes entry flow with visible progress.
 * Each step writes to the live console; commands stream stdout/stderr into it.
 *
 * <p>The "attach to tmux" step never tries to inheritIO on a non-tty Swing
 * process; it goes through {@link TerminalLauncherService} which opens a real
 * terminal window.</p>
 */
public class StackFlowService {
    private static final Logger log = LoggerFactory.getLogger(StackFlowService.class);

    private final CommandExecutor executor;
    private final AppConfig config;
    private final WslService wslService;
    private final TmuxService tmuxService;
    private final HermesService hermesService;
    private final EngramService engramService;
    private final HealthCheckService healthService;
    private final TerminalLauncherService launcher;
    private final LiveConsole console;

    public StackFlowService(CommandExecutor executor,
                            AppConfig config,
                            WslService wslService,
                            TmuxService tmuxService,
                            HermesService hermesService,
                            EngramService engramService,
                            HealthCheckService healthService,
                            TerminalLauncherService launcher,
                            LiveConsole console) {
        this.executor = executor;
        this.config = config;
        this.wslService = wslService;
        this.tmuxService = tmuxService;
        this.hermesService = hermesService;
        this.engramService = engramService;
        this.healthService = healthService;
        this.launcher = launcher;
        this.console = console;
    }

    public void runEntryFlow() {
        console.command("stack entry flow requested");
        Consumer<String> sink = line -> {
            String l = line;
            if (l.startsWith("[error]") || l.toLowerCase().contains("error")) {
                console.error(l);
            } else if (l.startsWith("[stderr]") && !l.isBlank()) {
                console.warn(l);
            } else if (l.startsWith("[stdout]") || l.startsWith("[launch]")) {
                console.info(l);
            } else {
                console.output(l);
            }
        };

        step("Checking WSL2", () -> {
            var h = wslService.check();
            return h.getStatus() == ServiceStatus.ONLINE
                ? Result.ok(h.getMessage())
                : Result.fail("WSL2: " + h.getMessage());
        });

        step("Checking Ubuntu 24.04", () -> {
            var r = executor.run(CommandCatalog.wslUname(config), 5, sink);
            return r.isOk() ? Result.ok(r.getOutput().split("\\R", 2)[0])
                            : Result.fail("uname failed");
        });

        step("Checking tmux", () -> {
            var h = tmuxService.check();
            return h.getStatus() == ServiceStatus.ONLINE
                ? Result.ok(h.getMessage())
                : Result.fail("tmux: " + h.getMessage());
        });

        step("Checking Engram", () -> {
            var h = engramService.check();
            return h.getStatus() == ServiceStatus.ONLINE
                ? Result.ok(h.getMessage())
                : Result.fail("engram: " + h.getMessage());
        });

        step("Checking Hermes", () -> {
            var h = hermesService.check();
            return h.getStatus() == ServiceStatus.ONLINE
                ? Result.ok(h.getMessage())
                : Result.fail("hermes: " + h.getMessage());
        });

        step("Looking for tmux session 'hermes'", () -> {
            boolean exists = tmuxService.sessionExists("hermes");
            return exists
                ? Result.ok("session 'hermes' exists")
                : Result.fail("session not found; will create");
        });

        boolean sessionExists = tmuxService.sessionExists("hermes");
        if (!sessionExists) {
            step("Creating tmux session via hermes-tmux.sh", () -> {
                console.command("$ bash " + config.getTmuxScript());
                var r = executor.run(CommandCatalog.createHermesSession(config), 30, sink);
                return r.isOk()
                    ? Result.ok("session created (4 panes)")
                    : Result.fail("create failed: " + r.getErrorTag());
            });
        }

        step("Opening terminal for tmux attach", () -> {
            try {
                launcher.openHermesTerminal();
                return Result.ok("terminal launched in new window");
            } catch (Exception e) {
                return Result.fail("terminal launch failed: " + e.getMessage());
            }
        });

        console.ok("Flow complete. Working in tmux now.");
    }

    public StackSnapshot quickSnapshot() {
        return healthService.snapshot();
    }

    public boolean runCreateSession() {
        console.command("$ bash " + config.getTmuxScript());
        var r = executor.run(CommandCatalog.createHermesSession(config), 30, line -> {
            if (line.startsWith("[error]") || line.toLowerCase().contains("error")) {
                console.error(line);
            } else {
                console.info(line);
            }
        });
        if (r.isOk()) {
            console.ok("Sesión tmux 'hermes' creada (4 panes).");
            return true;
        }
        console.warn("No se pudo crear la sesión: " + r.getErrorTag());
        return false;
    }

    /**
     * Ensures the Hermes workspace is up by ensuring the tmux session exists.
     *
     * <p>{@code hermes-start.sh} and {@code engram-start.sh} exec long-running
     * foreground processes meant to run <em>inside</em> tmux panes; invoking
     * them here as blocking, timed subprocess calls is what previously made
     * this action hang until timeout. Per the corrected START/CHECK/ATTACH
     * separation, "start the stack" means "create the tmux workspace if it
     * isn't there yet" — nothing else. If the session already exists, this
     * is a no-op.</p>
     */
    public boolean runStartStack() {
        if (tmuxService.sessionExists("hermes")) {
            console.ok("Hermes ya está iniciado (sesión tmux activa).");
            return true;
        }
        console.command("$ bash " + config.getTmuxScript());
        var r = executor.run(CommandCatalog.createHermesSession(config), 60, line -> {
            if (line.startsWith("[error]") || line.toLowerCase().contains("error")) console.error(line);
            else console.info(line);
        });
        if (r.isOk()) {
            console.ok("Sesión tmux 'hermes' creada (4 panes). Stack iniciado.");
            return true;
        }
        console.warn("No se pudo iniciar el stack: " + r.getErrorTag());
        return false;
    }

    public HermesSessionState probeSession() {
        boolean exists = tmuxService.sessionExists("hermes");
        return new HermesSessionState("hermes", exists, exists ? 1 : 0,
            config.getLinuxUser(), System.currentTimeMillis());
    }

    private void step(String label, StepBody body) {
        console.info(label + "...");
        try {
            Result r = body.run();
            if (r.ok) console.ok(label + " — " + r.detail);
            else console.warn(label + " — " + r.detail);
        } catch (Exception e) {
            log.error("step failed: {}", e.getMessage(), e);
            console.error(label + " — exception: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface StepBody { Result run(); }

    private record Result(boolean ok, String detail) {
        static Result ok(String d) { return new Result(true, d); }
        static Result fail(String d) { return new Result(false, d); }
    }
}
