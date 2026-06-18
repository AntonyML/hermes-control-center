package com.hermes.controlcenter.bootstrap;

import com.hermes.controlcenter.cache.StartupCache;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import com.hermes.controlcenter.domain.snapshot.StartupPhase;
import com.hermes.controlcenter.domain.snapshot.StartupProgress;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import com.hermes.controlcenter.services.HealthCheckService;
import com.hermes.controlcenter.services.PluginStatusService;
import com.hermes.controlcenter.services.TmuxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Drives the entire startup sequence: phases, progress, parallelism, caching.
 * The Home frame is shown only when the bootstrap completes (success or
 * graceful degraded completion).
 */
public class StackBootstrapService {
    private static final Logger log = LoggerFactory.getLogger(StackBootstrapService.class);

    private final ApplicationContext ctx;
    private final ExecutorService probes;

    public StackBootstrapService(ApplicationContext ctx) {
        this.ctx = ctx;
        this.probes = ctx.probes();
    }

    /**
     * Run the full bootstrap. {@code onProgress} is invoked on a background
     * thread and is responsible for marshalling updates to the UI.
     */
    public void runFull(Consumer<StartupProgress> onProgress,
                        Runnable onComplete) {
        Thread t = new Thread(() -> {
            try {
                log.info("bootstrap: starting full sequence");
                advance(StartupPhase.BOOT, "Construyendo contexto de aplicación", onProgress);
                delayVisualFloor(120);

                advance(StartupPhase.CONFIG, "Cargando config.json", onProgress);
                delayVisualFloor(150);

                advance(StartupPhase.CACHE, "Calentando cache de arranque", onProgress);
                warmCacheOnly(onProgress);
                delayVisualFloor(150);

                advance(StartupPhase.WSL, "Probando WSL2 y Ubuntu 24.04", onProgress);
                wslProbe();
                delayVisualFloor(150);

                advance(StartupPhase.TMUX, "Probando tmux server", onProgress);
                tmuxProbe();
                delayVisualFloor(150);

                advance(StartupPhase.HERMES, "Probando Hermes Agent", onProgress);
                hermesProbe();
                delayVisualFloor(150);

                advance(StartupPhase.ENGRAM, "Probando Engram HTTP", onProgress);
                engramProbe();
                delayVisualFloor(150);

                advance(StartupPhase.PLUGINS, "Probando plugins del shell", onProgress);
                pluginsProbe();
                delayVisualFloor(150);

                advance(StartupPhase.SNAPSHOT, "Construyendo snapshot del stack", onProgress);
                StackSnapshot stack = ctx.health().snapshot();
                DiagnosticSnapshot diag = buildDiagnosticSnapshot();
                HermesSessionState session = probeHermesSession();
                ctx.cache().recordSnapshots(stack, diag, session);
                ctx.state().setStack(stack);
                ctx.state().setDiagnostic(diag);
                ctx.state().setHermesSession(session);
                ctx.state().setBootstrapped(true);
                delayVisualFloor(150);

                advance(StartupPhase.READY, "Stack inicializado", onProgress);
                ctx.console().ok("Bootstrap completo. Snapshot listo.");
                log.info("bootstrap: done");
            } catch (Throwable t1) {
                log.error("bootstrap failed: {}", t1.getMessage(), t1);
                ctx.console().error("bootstrap failed: " + t1.getMessage());
            } finally {
                if (onComplete != null) onComplete.run();
            }
        }, "hcc-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Re-probe a single phase and update the cache in place. Used by the
     * refresh action and by console commands.
     */
    public void refresh(Consumer<StartupProgress> onProgress,
                        Runnable onComplete) {
        Thread t = new Thread(() -> {
            try {
                advance(StartupPhase.WSL, "Reprobe WSL2", onProgress);
                wslProbe();
                advance(StartupPhase.TMUX, "Reprobe tmux", onProgress);
                tmuxProbe();
                advance(StartupPhase.HERMES, "Reprobe Hermes", onProgress);
                hermesProbe();
                advance(StartupPhase.ENGRAM, "Reprobe Engram", onProgress);
                engramProbe();
                advance(StartupPhase.PLUGINS, "Reprobe plugins", onProgress);
                pluginsProbe();
                advance(StartupPhase.SNAPSHOT, "Actualizando snapshot", onProgress);
                StackSnapshot stack = ctx.health().snapshot();
                DiagnosticSnapshot diag = buildDiagnosticSnapshot();
                HermesSessionState session = probeHermesSession();
                ctx.cache().recordSnapshots(stack, diag, session);
                ctx.state().setStack(stack);
                ctx.state().setDiagnostic(diag);
                ctx.state().setHermesSession(session);
                advance(StartupPhase.READY, "Snapshot actualizado", onProgress);
            } catch (Throwable t1) {
                log.error("refresh failed: {}", t1.getMessage(), t1);
                ctx.console().error("refresh failed: " + t1.getMessage());
            } finally {
                if (onComplete != null) onComplete.run();
            }
        }, "hcc-refresh");
        t.setDaemon(true);
        t.start();
    }

    // ====================== internals ======================

    private void advance(StartupPhase p, String msg, Consumer<StartupProgress> sink) {
        StartupProgress next = StartupProgress.initial().advanced(p, msg);
        ctx.cache().recordProgress(next);
        if (sink != null) sink.accept(next);
        log.info("phase {} -> {}: {}", p.name(), p.getLabel(), msg);
    }

    private void warmCacheOnly(Consumer<StartupProgress> sink) {
        StartupCache cache = ctx.cache();
        for (String n : new String[] {
            "WSL", "Ubuntu 24.04", "tmux", "Hermes", "Engram", "OpenCode",
            "Git", "Bat", "Eza", "Btop", "Lazygit", "Zoxide", "Oh My Posh"
        }) {
            cache.buffer().put(n, ServiceHealth.probing(n));
        }
    }

    private void wslProbe() {
        try {
            ServiceHealth h = ctx.wsl().check();
            ctx.cache().buffer().put(h.getName(), h);
        } catch (Exception e) {
            log.warn("wsl probe failed: {}", e.getMessage());
        }
    }

    private void tmuxProbe() {
        try {
            ServiceHealth h = ctx.tmux().check();
            ctx.cache().buffer().put(h.getName(), h);
        } catch (Exception e) {
            log.warn("tmux probe failed: {}", e.getMessage());
        }
    }

    private void hermesProbe() {
        try {
            ServiceHealth h = ctx.hermes().check();
            ctx.cache().buffer().put(h.getName(), h);
        } catch (Exception e) {
            log.warn("hermes probe failed: {}", e.getMessage());
            ctx.cache().buffer().put("Hermes",
                ServiceHealth.starting("Hermes", "probe failed: " + e.getMessage()));
        }
    }

    private void engramProbe() {
        try {
            ServiceHealth h = ctx.engram().check();
            ctx.cache().buffer().put(h.getName(), h);
        } catch (Exception e) {
            log.warn("engram probe failed: {}", e.getMessage());
            ctx.cache().buffer().put("Engram",
                ServiceHealth.starting("Engram", "probe failed: " + e.getMessage()));
        }
    }

    private void pluginsProbe() {
        PluginStatusService ps = ctx.plugins();
        String[] tools = PluginStatusService.TOOLS;
        String[] labels = PluginStatusService.LABELS;
        Map<String, ServiceHealth> map = new LinkedHashMap<>();
        for (int i = 0; i < tools.length; i++) {
            try {
                ServiceHealth h = ps.check(tools[i], labels[i]);
                map.put(labels[i], h);
            } catch (Exception e) {
                map.put(labels[i], ServiceHealth.starting(labels[i], "probe failed"));
            }
        }
        ctx.cache().buffer().putAll(map);
    }

    private DiagnosticSnapshot buildDiagnosticSnapshot() {
        Map<String, ServiceHealth> plugins = new LinkedHashMap<>();
        for (var e : ctx.cache().buffer().snapshot().entrySet()) {
            String k = e.getKey();
            if (PluginStatusService.LABEL_TO_INDEX.containsKey(k)) {
                plugins.put(k, e.getValue());
            }
        }
        StackSnapshot s = ctx.state().getStack();
        return new DiagnosticSnapshot(
            ctx.cache().buffer().get("Ubuntu 24.04"),
            ctx.cache().buffer().get("WSL"),
            plugins,
            s.getHermes(), s.getEngram(), s.getTmux(),
            s.isTmuxSessionExists(),
            "SIGHA"
        );
    }

    private HermesSessionState probeHermesSession() {
        try {
            TmuxService tmux = ctx.tmux();
            boolean exists = tmux.sessionExists("hermes");
            int panes = exists ? parsePaneCount(tmux) : 0;
            return new HermesSessionState("hermes", exists, panes, ctx.config().getLinuxUser(),
                System.currentTimeMillis());
        } catch (Exception e) {
            return HermesSessionState.absent("hermes");
        }
    }

    private int parsePaneCount(TmuxService tmux) {
        try {
            CommandExecutor exec = ctx.executor();
            var r = exec.run(CommandCatalog.listTmuxSessions(ctx.config()), 5);
            if (r.getOutput() == null) return 0;
            for (String line : r.getOutput().split("\\R")) {
                if (line.startsWith("hermes:") && line.contains("(group")) {
                    String inside = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
                    return inside.split(",").length;
                }
                if (line.startsWith("hermes:")) {
                    return 1;
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Amortiguador visual. NO es la fuente de la duración. Las probes reales
     * son lo que llena el tiempo de carga. Esto solo evita que la splash
     * parpadee si todo fue demasiado rápido.
     */
    private void delayVisualFloor(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
