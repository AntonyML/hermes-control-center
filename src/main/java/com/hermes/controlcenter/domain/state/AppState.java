package com.hermes.controlcenter.domain.state;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.domain.session.HermesSessionState;
import com.hermes.controlcenter.domain.snapshot.StartupProgress;

public class AppState {
    private final long startedAtMillis;
    private StartupProgress progress;
    private StackSnapshot stack;
    private DiagnosticSnapshot diagnostic;
    private HermesSessionState hermesSession;
    private AppConfig config;
    private boolean bootstrapped;

    public AppState(AppConfig config) {
        this.startedAtMillis = System.currentTimeMillis();
        this.config = config;
        this.progress = StartupProgress.initial();
        this.stack = StackSnapshot.starting();
        this.diagnostic = DiagnosticSnapshot.empty("SIGHA");
        this.hermesSession = HermesSessionState.absent("hermes");
        this.bootstrapped = false;
    }

    public long getStartedAtMillis() { return startedAtMillis; }
    public StartupProgress getProgress() { return progress; }
    public void setProgress(StartupProgress p) { this.progress = p; }
    public StackSnapshot getStack() { return stack; }
    public void setStack(StackSnapshot s) { this.stack = s; }
    public DiagnosticSnapshot getDiagnostic() { return diagnostic; }
    public void setDiagnostic(DiagnosticSnapshot d) { this.diagnostic = d; }
    public HermesSessionState getHermesSession() { return hermesSession; }
    public void setHermesSession(HermesSessionState s) { this.hermesSession = s; }
    public AppConfig getConfig() { return config; }
    public void setConfig(AppConfig c) { this.config = c; }
    public boolean isBootstrapped() { return bootstrapped; }
    public void setBootstrapped(boolean b) { this.bootstrapped = b; }
}
