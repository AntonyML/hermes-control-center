package com.hermes.controlcenter.bootstrap;

import com.hermes.controlcenter.cache.StartupCache;
import com.hermes.controlcenter.config.ConfigStore;
import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.LiveConsole;
import com.hermes.controlcenter.domain.state.AppState;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import com.hermes.controlcenter.services.EngramService;
import com.hermes.controlcenter.services.HealthCheckService;
import com.hermes.controlcenter.services.HermesService;
import com.hermes.controlcenter.services.OpenCodeService;
import com.hermes.controlcenter.services.PluginStatusService;
import com.hermes.controlcenter.services.StackFlowService;
import com.hermes.controlcenter.services.TerminalLauncherService;
import com.hermes.controlcenter.services.TmuxService;
import com.hermes.controlcenter.services.WslService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application context: the single object that holds every long-lived service,
 * executor, config, console, and cache. Built once at startup and shared by
 * controllers and UI panels. Construction is cheap and synchronous; the heavy
 * lifting (probes, snapshots) is performed by {@link StackBootstrapService}.
 */
public class ApplicationContext {
    private static final Logger log = LoggerFactory.getLogger(ApplicationContext.class);

    private final ConfigStore configStore;
    private final AppConfig config;
    private final LiveConsole console;
    private final CommandExecutor executor;

    private final WslService wsl;
    private final TmuxService tmux;
    private final HermesService hermes;
    private final EngramService engram;
    private final OpenCodeService opencode;
    private final PluginStatusService plugins;
    private final HealthCheckService health;
    private final TerminalLauncherService launcher;
    private final StackFlowService flow;
    private final StackBootstrapService bootstrap;
    private final StartupCache cache;
    private final AppState state;
    private final ExecutorService io;
    private final ExecutorService probes;

    public ApplicationContext() {
        this.console = new LiveConsole(800);
        this.configStore = new ConfigStore();
        this.config = configStore.load();
        this.executor = new CommandExecutor();
        this.cache = new StartupCache();
        this.state = new AppState(config);

        this.wsl = new WslService(executor, config);
        this.tmux = new TmuxService(executor, config);
        this.hermes = new HermesService(executor, config);
        this.engram = new EngramService(executor, config);
        this.opencode = new OpenCodeService(executor);
        this.plugins = new PluginStatusService(executor, config);
        this.health = new HealthCheckService(wsl, tmux, hermes, engram, opencode, config);
        this.launcher = new TerminalLauncherService(executor, config, tmux);
        this.flow = new StackFlowService(executor, config, wsl, tmux, hermes, engram,
            health, launcher, console);
        this.bootstrap = new StackBootstrapService(this);

        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hcc-io");
            t.setDaemon(true);
            return t;
        });
        this.probes = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "hcc-probe");
            t.setDaemon(true);
            return t;
        });
        log.info("ApplicationContext built ({} cached services)", countServices());
    }

    public ConfigStore configStore() { return configStore; }
    public AppConfig config() { return config; }
    public void replaceConfig(AppConfig newConfig) {
        this.state.setConfig(newConfig);
        // services keep their reference; consumers should re-read context.config()
    }
    public LiveConsole console() { return console; }
    public CommandExecutor executor() { return executor; }
    public WslService wsl() { return wsl; }
    public TmuxService tmux() { return tmux; }
    public HermesService hermes() { return hermes; }
    public EngramService engram() { return engram; }
    public OpenCodeService opencode() { return opencode; }
    public PluginStatusService plugins() { return plugins; }
    public HealthCheckService health() { return health; }
    public TerminalLauncherService launcher() { return launcher; }
    public StackFlowService flow() { return flow; }
    public StackBootstrapService bootstrap() { return bootstrap; }
    public StartupCache cache() { return cache; }
    public AppState state() { return state; }
    public ExecutorService io() { return io; }
    public ExecutorService probes() { return probes; }

    private int countServices() {
        return 9;
    }

    public void shutdown() {
        io.shutdownNow();
        probes.shutdownNow();
    }
}
