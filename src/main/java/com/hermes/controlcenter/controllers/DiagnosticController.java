package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Diagnostic screen. Aggregates a textual report from the
 * cache and triggers re-probes.
 */
public class DiagnosticController {
    private static final Logger log = LoggerFactory.getLogger(DiagnosticController.class);
    private static final DateTimeFormatter TS = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ApplicationContext ctx;

    public DiagnosticController(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public DiagnosticSnapshot snapshot() {
        return ctx.cache().latestDiagnostic().orElseGet(() -> DiagnosticSnapshot.empty("SIGHA"));
    }

    public void refresh() {
        log.info("diagnostic refresh requested");
        ctx.bootstrap().refresh(p -> {}, null);
    }

    public String renderReport(DiagnosticSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append("Proyecto: ").append(snap.getProjectName()).append('\n');
        sb.append("Sesión tmux 'hermes': ")
            .append(snap.isSessionExists() ? "ACTIVA" : "no existe").append('\n');
        sb.append("Capturado: ").append(TS.format(snap.getCapturedAt())).append("\n\n");

        appendHealth(sb, snap.getUbuntu());
        appendHealth(sb, snap.getWsl());
        appendHealth(sb, snap.getHermes());
        appendHealth(sb, snap.getEngram());
        appendHealth(sb, snap.getTmux());

        Map<String, ServiceHealth> plugins = snap.getPlugins();
        if (plugins != null) {
            for (var e : plugins.entrySet()) {
                appendHealth(sb, e.getValue());
            }
        }
        return sb.toString();
    }

    public List<ServiceHealth> orderedCards(DiagnosticSnapshot snap) {
        List<ServiceHealth> list = new ArrayList<>();
        list.add(snap.getUbuntu());
        list.add(snap.getWsl());
        list.add(snap.getHermes());
        list.add(snap.getEngram());
        list.add(snap.getTmux());
        if (snap.getPlugins() != null) list.addAll(snap.getPlugins().values());
        return list;
    }

    private void appendHealth(StringBuilder sb, ServiceHealth h) {
        if (h == null) return;
        String version = h.getVersion() == null ? "" : " " + h.getVersion();
        String msg = h.getMessage() == null ? "" : "  —  " + h.getMessage();
        sb.append(String.format("%-14s %-9s%s%n", h.getName(),
            h.getStatus().name() + version, msg));
    }
}
