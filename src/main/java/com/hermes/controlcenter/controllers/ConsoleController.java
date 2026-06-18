package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.domain.command.CommandRequest;
import com.hermes.controlcenter.domain.command.CommandResult;
import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.CommandSpec;
import com.hermes.controlcenter.domain.model.LiveConsole;
import com.hermes.controlcenter.infrastructure.CommandCatalog;
import com.hermes.controlcenter.infrastructure.CommandCatalog.Mode;
import com.hermes.controlcenter.infrastructure.CommandCatalog.Verb;
import com.hermes.controlcenter.infrastructure.CommandExecutor;
import com.hermes.controlcenter.infrastructure.CommandExecutor.ExecutionResult;
import com.hermes.controlcenter.services.TerminalLauncherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * The in-app console controller. The view is a dumb text component plus an
 * input field. This controller:
 *
 * <ul>
 *   <li>parses the user-typed text into a verb + args,</li>
 *   <li>validates the verb against {@link CommandCatalog#VERB_INDEX},</li>
 *   <li>builds a {@link CommandRequest} bound to the configured
 *       {@link CommandSpec},</li>
 *   <li>executes the request via {@link CommandExecutor} (or routes to the
 *       terminal launcher for interactive verbs),</li>
 *   <li>streams output into the {@link LiveConsole},</li>
 *   <li>keeps a per-session command history that the view can navigate
 *       with Up/Down arrows.</li>
 * </ul>
 */
public class ConsoleController {
    private static final Logger log = LoggerFactory.getLogger(ConsoleController.class);

    public enum SinkEvent { APPEND, REJECT, COMPLETE, HISTORY_CHANGED, CLEAR }

    public interface SinkListener {
        void onEvent(SinkEvent ev, String detail);
    }

    private final ApplicationContext ctx;
    private final LiveConsole console;
    private final CommandExecutor executor;

    private final LinkedList<String> history = new LinkedList<>();
    private static final int HISTORY_CAP = 200;
    private int historyCursor = -1;
    private String historyDraft = "";

    private final List<SinkListener> sinkListeners = new CopyOnWriteArrayList<>();

    public ConsoleController(ApplicationContext ctx) {
        this.ctx = ctx;
        this.console = ctx.console();
        this.executor = ctx.executor();
    }

    public void addSinkListener(SinkListener l) { sinkListeners.add(l); }

    public LiveConsole liveConsole() { return console; }

    public List<String> suggestionsFor(String partial) {
        String p = partial.toLowerCase(Locale.ROOT).trim();
        if (p.isEmpty()) return List.of();
        return CommandCatalog.VERB_INDEX.keySet().stream()
            .filter(v -> v.startsWith(p))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Submit a line of input from the user. The line is appended to history
     * (without duplicates) and dispatched. The actual execution happens
     * asynchronously; output is streamed into the {@link LiveConsole}.
     */
    public void submit(String rawInput) {
        if (rawInput == null) return;
        String input = rawInput.trim();
        if (input.isEmpty()) {
            console.command("");
            return;
        }
        pushHistory(input);
        console.input(input);

        ParsedLine parsed = ParsedLine.parse(input);
        Verb verb = CommandCatalog.VERB_INDEX.get(parsed.verb);
        if (verb == null) {
            reject(parsed, input);
            return;
        }

        if ("help".equalsIgnoreCase(parsed.verb)) {
            printHelp();
            emit(SinkEvent.COMPLETE, "help");
            return;
        }

        if (verb.mode == Mode.INTERACTIVE_TERMINAL) {
            dispatchInteractive(parsed, verb, input);
            return;
        }

        dispatchDiagnostic(parsed, verb, input);
    }

    public void historyUp(String currentDraft) {
        if (history.isEmpty()) return;
        if (historyCursor == -1) {
            historyDraft = currentDraft;
            historyCursor = history.size() - 1;
        } else if (historyCursor > 0) {
            historyCursor--;
        }
        emit(SinkEvent.HISTORY_CHANGED, history.get(historyCursor));
    }

    public void historyDown(String currentDraft) {
        if (historyCursor == -1) return;
        if (historyCursor < history.size() - 1) {
            historyCursor++;
            emit(SinkEvent.HISTORY_CHANGED, history.get(historyCursor));
        } else {
            historyCursor = -1;
            emit(SinkEvent.HISTORY_CHANGED, historyDraft);
        }
    }

    public void clearConsole() {
        console.clear();
        emit(SinkEvent.CLEAR, "");
    }

    public void printBanner() {
        console.info("Hermes Control Center · consola interactiva");
        console.info("Escribe 'help' para ver comandos. ↑/↓ historial. Enter para ejecutar.");
    }

    // ====================== internals ======================

    private void dispatchDiagnostic(ParsedLine parsed, Verb verb, String raw) {
        CommandSpec spec = verb.specFactory.apply(ctx.config());
        CommandRequest request = new CommandRequest(raw, parsed.verb, verb.description, spec, false);
        ctx.io().submit(() -> {
            long t0 = System.currentTimeMillis();
            List<String> stream = new ArrayList<>();
            console.info("$ " + spec.getDescription());
            ExecutionResult er = executor.run(spec, 30, line -> {
                stream.add(line);
                if (line.startsWith("[error]") || line.toLowerCase().contains("error")) {
                    console.error(line);
                } else if (line.startsWith("[stderr]")) {
                    console.warn(line);
                } else if (line.startsWith("[stdout]") || line.startsWith("[launch]")) {
                    console.info(line);
                } else {
                    console.output(line);
                }
            });
            long dur = System.currentTimeMillis() - t0;
            CommandResult result = CommandResult.fromExecution(request, er, stream, dur);
            logResult(result);
            emit(SinkEvent.COMPLETE, verb.verb);
        });
    }

    private void dispatchInteractive(ParsedLine parsed, Verb verb, String raw) {
        console.info("$ " + verb.description);
        try {
            TerminalLauncherService.LaunchResult r;
            if ("attach hermes".equalsIgnoreCase(parsed.verb)
                || "open terminal".equalsIgnoreCase(parsed.verb)) {
                r = ctx.launcher().openHermesTerminal();
            } else if (verb.rawCommand != null) {
                String cmdLine = resolveRawCommand(verb.rawCommand, ctx.config());
                r = ctx.launcher().openRawTerminalInWindow(parsed.verb, cmdLine);
            } else {
                r = ctx.launcher().openHermesTerminal();
            }
            if (r.isOk()) {
                console.ok("Terminal real abierta: " + r.getDetail());
            } else {
                console.error("No se pudo abrir terminal: " + r.getDetail());
            }
        } catch (Exception e) {
            console.error("terminal launch failed: " + e.getMessage());
        }
        emit(SinkEvent.COMPLETE, verb.verb);
    }

    private String resolveRawCommand(String template, AppConfig cfg) {
        String t = template;
        if (t == null) {
            return String.join(" ", "wsl", "-d", cfg.getWslDistro(),
                "-u", cfg.getLinuxUser(), "--",
                "bash", "-l");
        }
        if (t.contains("{{user}}")) t = t.replace("{{user}}", cfg.getLinuxUser());
        if (t.contains("{{distro}}")) t = t.replace("{{distro}}", cfg.getWslDistro());
        if (t.contains("{{hermesRoot}}")) {
            String wslRoot = cfg.getHermesRoot().replace("E:\\", "/mnt/e/").replace("\\", "/");
            t = t.replace("{{hermesRoot}}", wslRoot);
        }
        return t;
    }

    private void reject(ParsedLine parsed, String raw) {
        console.error("comando no permitido: '" + parsed.verb + "'");
        console.info("comandos sugeridos:");
        List<String> sugg = suggestionsFor(parsed.verb);
        if (sugg.isEmpty()) sugg = List.of("status", "refresh", "start stack", "create session",
            "attach hermes", "open terminal", "diagnose", "help");
        for (String s : sugg) console.info("  · " + s);
        emit(SinkEvent.REJECT, parsed.verb);
    }

    private void logResult(CommandResult result) {
        if (!result.isAllowed()) {
            console.error("rechazado: " + result.getRejectReason());
            return;
        }
        if (result.isOk()) {
            console.ok(String.format("OK exit=%d en %dms", result.getExitCode(), result.getDurationMs()));
        } else {
            console.warn(String.format("FAIL exit=%d tag=%s en %dms",
                result.getExitCode(), result.getErrorTag(), result.getDurationMs()));
            if (!result.getErrorOutput().isEmpty()) {
                for (String l : result.getErrorOutput().split("\\R")) {
                    if (!l.isBlank()) console.error(l);
                }
            }
        }
    }

    private void printHelp() {
        console.info("comandos disponibles:");
        for (Verb v : CommandCatalog.CONSOLE_VERBS) {
            console.info(String.format("  %-18s %s%s",
                v.verb, v.description, v.isInteractive() ? "  [terminal real]" : ""));
        }
    }

    private void pushHistory(String line) {
        if (history.isEmpty() || !history.getLast().equals(line)) {
            history.addLast(line);
            if (history.size() > HISTORY_CAP) history.removeFirst();
        }
        historyCursor = -1;
        historyDraft = "";
    }

    private void emit(SinkEvent ev, String detail) {
        for (SinkListener l : sinkListeners) {
            try { l.onEvent(ev, detail); } catch (Exception ignored) {}
        }
    }

    private record ParsedLine(String verb, List<String> args) {
        static ParsedLine parse(String raw) {
            String[] tokens = raw.trim().split("\\s+");
            String verb = tokens.length == 0 ? "" : tokens[0].toLowerCase(Locale.ROOT);
            return new ParsedLine(verb, Arrays.asList(tokens).subList(1, tokens.length));
        }
    }
}
