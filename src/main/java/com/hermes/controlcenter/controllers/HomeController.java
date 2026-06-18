package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.cache.StartupCache;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.LiveConsole;
import com.hermes.controlcenter.domain.model.PrimaryAction;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import com.hermes.controlcenter.domain.session.PluginDescriptor;
import com.hermes.controlcenter.services.StackFlowService;
import com.hermes.controlcenter.services.TerminalLauncherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controller for the Home panel. The view is dumb: it asks the controller
 * for snapshots, runs actions and never touches services or commands
 * directly. The controller is the only object that turns UI intents into
 * service calls.
 */
public class HomeController {
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ApplicationContext ctx;
    private final StartupCache cache;
    private final StackFlowService flow;
    private final TerminalLauncherService launcher;
    private final LiveConsole console;

    public HomeController(ApplicationContext ctx) {
        this.ctx = ctx;
        this.cache = ctx.cache();
        this.flow = ctx.flow();
        this.launcher = ctx.launcher();
        this.console = ctx.console();
    }

    public StackSnapshot currentSnapshot() {
        return cache.latestStack().orElseGet(StackSnapshot::starting);
    }

    public DiagnosticSnapshot currentDiagnostic() {
        return cache.latestDiagnostic().orElseGet(() -> DiagnosticSnapshot.empty("SIGHA"));
    }

    public List<ServiceHealth> cardHealth() {
        return cache.buffer().snapshot().values().stream()
            .filter(h -> h.getStatus() != ServiceStatus.UNKNOWN)
            .toList();
    }

    public boolean isBootstrapped() {
        return cache.isWarmedUp();
    }

    public PrimaryAction currentPrimaryAction() {
        return currentSnapshot().getPrimaryAction();
    }

    public String projectSubtitle() {
        StackSnapshot s = currentSnapshot();
        return "Proyecto activo: SIGHA · " + s.countOnline() + " / " + s.total() + " servicios en línea";
    }

    public String lastUpdatedLabel() {
        if (cache.lastProbeMillis() == 0L) {
            return "última actualización: —";
        }
        long secs = Math.max(0, (System.currentTimeMillis() - cache.lastProbeMillis()) / 1000);
        if (secs < 5) return "última actualización: ahora";
        if (secs < 60) return "última actualización: hace " + secs + "s";
        long mins = secs / 60;
        return "última actualización: hace " + mins + "m";
    }

    /**
     * Execute the user-requested primary action. The button text already
     * reflects the contextual action; we route based on it.
     */
    public void runPrimaryAction() {
        PrimaryAction action = currentPrimaryAction();
        log.info("primary action: {}", action);
        ctx.io().submit(() -> {
            try {
                switch (action) {
                    case ENTER_HERMES -> {
                        TerminalLauncherService.LaunchResult r = launcher.openHermesTerminal();
                        if (r.isOk()) console.ok("Terminal lanzada: " + r.getDetail());
                        else console.error("No se pudo abrir terminal: " + r.getDetail());
                    }
                    case CREATE_SESSION -> {
                        boolean created = flow.runCreateSession();
                        if (created) {
                            TerminalLauncherService.LaunchResult r = launcher.openAttachTerminal();
                            console.ok("Sesión creada y terminal abierta: " + r.getDetail());
                        } else {
                            console.warn("No se pudo crear la sesión.");
                        }
                    }
                    case START_STACK -> {
                        boolean started = flow.runStartStack();
                        if (started) console.ok("Stack Hermes + Engram iniciados.");
                        else console.warn("No se pudo iniciar el stack completo.");
                    }
                }
            } catch (Exception e) {
                log.error("primary action failed: {}", e.getMessage(), e);
                console.error("Acción principal falló: " + e.getMessage());
            }
        });
    }

    public void refresh() {
        ctx.bootstrap().refresh(p -> {}, null);
    }

    public String pluginActionLabel(String pluginLabel) {
        return PluginRegistry.byLabel().get(pluginLabel) == null
            ? null
            : PluginRegistry.byLabel().get(pluginLabel).getDefaultActionLabel();
    }

    public String pluginActionCommand(String pluginLabel) {
        PluginDescriptor d = PluginRegistry.byLabel().get(pluginLabel);
        return d == null ? null : d.getDefaultAction();
    }

    public HermesSessionState hermesSession() {
        return cache.buffer().hermesSession();
    }
}
