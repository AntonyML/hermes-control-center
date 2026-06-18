package com.hermes.controlcenter.app;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.bootstrap.StartupController;
import com.hermes.controlcenter.domain.snapshot.StartupProgress;
import com.hermes.controlcenter.ui.SplashFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.EventQueue;

/**
 * Application entry point. Intentionally minimal: configures Look &amp; Feel,
 * builds the {@link ApplicationContext}, shows the splash, kicks off the
 * {@link StartupController} and shows the {@link MainFrame} when bootstrap
 * finishes. No business logic lives in this class.
 */
public class HermesControlCenterApp {
    private static final Logger log = LoggerFactory.getLogger(HermesControlCenterApp.class);

    public static void main(String[] args) {
        installLookAndFeel();
        EventQueue.invokeLater(HermesControlCenterApp::startup);
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatArcDarkIJTheme());
        } catch (Exception e) {
            log.warn("FlatLaf not applied; falling back: {}", e.getMessage());
        }
    }

    private static void startup() {
        log.info("Hermes Control Center booting");
        ApplicationContext ctx = new ApplicationContext();
        ctx.console().info("Hermes Control Center · iniciando");

        SplashFrame splash = new SplashFrame();
        StartupController startup = new StartupController(ctx);
        MainFrame main = new MainFrame(ctx);

        startup.onProgress(splash::apply);
        startup.onComplete(() -> {
            splash.onComplete(() -> {
                main.show();
                main.refreshAfterBootstrap();
            });
        });

        splash.showCentered();
        // Initial progress tick so the splash does not look frozen.
        splash.apply(StartupProgress.initial());
        startup.start();
    }
}
