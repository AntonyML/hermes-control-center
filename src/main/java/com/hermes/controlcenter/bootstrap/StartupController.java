package com.hermes.controlcenter.bootstrap;

import com.hermes.controlcenter.domain.snapshot.StartupProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bridges the bootstrap pipeline (which runs on background threads) and the
 * Swing EDT. UI components register as listeners and only ever receive
 * updates on the EDT, no matter which thread produced them.
 */
public class StartupController {
    private static final Logger log = LoggerFactory.getLogger(StartupController.class);

    private final ApplicationContext ctx;
    private final List<Consumer<StartupProgress>> listeners = new ArrayList<>();
    private final List<Runnable> completeListeners = new ArrayList<>();
    private boolean running = false;
    private boolean finished = false;

    public StartupController(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public boolean isRunning() { return running; }
    public boolean isFinished() { return finished; }

    public void onProgress(Consumer<StartupProgress> listener) {
        listeners.add(listener);
    }

    public void onComplete(Runnable listener) {
        completeListeners.add(listener);
    }

    public void start() {
        if (running) return;
        running = true;
        log.info("StartupController: start");
        ctx.bootstrap().runFull(this::emit, this::complete);
    }

    public void refresh() {
        if (running) return;
        running = true;
        log.info("StartupController: refresh");
        ctx.bootstrap().refresh(this::emit, this::complete);
    }

    private void emit(StartupProgress p) {
        SwingUtilities.invokeLater(() -> {
            for (var l : listeners) {
                try { l.accept(p); } catch (Exception e) {
                    log.warn("listener failed: {}", e.getMessage());
                }
            }
        });
    }

    private void complete() {
        running = false;
        finished = true;
        SwingUtilities.invokeLater(() -> {
            for (var l : completeListeners) {
                try { l.run(); } catch (Exception e) {
                    log.warn("complete listener failed: {}", e.getMessage());
                }
            }
        });
    }
}
