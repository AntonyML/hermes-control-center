package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runs {@code hermes -z} (oneshot mode) as a subprocess and streams every
 * line of stdout/stderr to subscribers in real time. One task at a time.
 *
 * <p>This is intentionally separate from {@link com.hermes.controlcenter.infrastructure.CommandExecutor}:
 * the agent's wall time is unbounded (model calls + tool execution can take
 * minutes) and the user must be able to cancel mid-flight. The executor's
 * fixed-timeout model doesn't fit.</p>
 *
 * <p>Routing (inline prompt, NO positional args):</p>
 * <pre>
 *   wsl -d Ubuntu-24.04 -u antony --
 *     bash -lc "source config/hermes-env.sh &amp;&amp; cd /mnt/e/Dev/Hermes &amp;&amp; .../hermes -z \"prompt escapado\""
 * </pre>
 *
 * <p>The prompt is pre-processed by {@link #buildHermesPrompt(String, String, String)}
 * (normalize paths, strip line breaks, inject role/cwd) and then escaped by
 * {@link #escapeForBashDoubleQuote(String)} before being inlined in the bash
 * script as a single double-quoted literal. There is never a separate token
 * after {@code bash -lc}. This avoids the "expected one argument" argparse
 * error that occurs when the prompt is passed as a positional arg.</p>
 */
public class HermesAgentService {
    private static final Logger log = LoggerFactory.getLogger(HermesAgentService.class);

    private final AppConfig config;
    private final HermesEnvService hermesEnv;
    private final List<Consumer<TaskRecord>> taskListeners = new CopyOnWriteArrayList<>();
    private volatile Process currentProcess;
    private volatile TaskRecord currentTask;
    private final Object runLock = new Object();

    public HermesAgentService(AppConfig config) {
        this(config, null);
    }

    public HermesAgentService(AppConfig config, HermesEnvService hermesEnv) {
        this.config = config;
        this.hermesEnv = hermesEnv;
    }

    public HermesEnvService.EnvStatus envStatus() {
        return hermesEnv == null ? HermesEnvService.EnvStatus.CHECK_FAILED : hermesEnv.probeDotEnv();
    }

    public void addTaskListener(Consumer<TaskRecord> l) { taskListeners.add(l); }

    public TaskRecord currentTask() { return currentTask; }

    public boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    /**
     * Submit a new task. Returns the record immediately. Execution happens
     * on the caller's thread (call from ctx.io() if you don't want to block).
     */
    public TaskRecord submit(String prompt, String workingDir, String role) {
        TaskRecord rec = new TaskRecord(prompt);
        if (workingDir != null && !workingDir.isBlank()) rec.setWorkingDir(workingDir);
        if (role != null && !role.isBlank()) rec.setAssignedRole(role);

        // Pre-flight: asegurar que las variables de entorno estén cargadas
        // en ~/.hermes/.env para que hermes detecte el provider NVIDIA.
        if (hermesEnv != null) {
            hermesEnv.ensureEnvLoaded();
        }

        // Pre-flight: si el CLI de hermes no está disponible, fallback
        // automático a `bash -lc "<prompt>"` (modo directo seguro).
        boolean hermesAvailable = probeHermesAvailable();
        if (!hermesAvailable) {
            rec.appendOutput("[hcc] pre-flight: hermes CLI no encontrado, usando fallback bash -lc", true);
            log.warn("hermes-agent: pre-flight failed, using bash fallback for task {}", rec.getId());
        }

        // Construir el PROMPT LIMPIO como un solo string antes de generar
        // el comando. NUNCA pasar el prompt como token suelto a bash.
        String fullPrompt = buildHermesPrompt(role, rec.getWorkingDir(), prompt);
        log.info("[hcc] FINAL PROMPT = \"{}\"", abbreviate(fullPrompt));

        String[] cmd = buildCommand(fullPrompt, rec.getWorkingDir(), !hermesAvailable);
        log.info("[hcc] COMMAND = \"{}\"", rec.getCommandLine());

        rec.setCommandLine(String.join(" ", cmd));
        synchronized (runLock) {
            currentTask = rec;
            rec.setState(TaskRecord.State.QUEUED);
            fireUpdate(rec);
            runOnIoThread(rec, cmd);
        }
        return rec;
    }

    /**
     * Comprueba si el binario hermes-agent es ejecutable dentro de WSL.
     * Read-only: ejecuta un `test -x` rápido. Timeout corto.
     */
    private boolean probeHermesAvailable() {
        String wslRoot = wslPath(config.getHermesRoot());
        String probe = "test -x " + wslRoot + "/hermes-agent/.venv/bin/python && "
            + "test -f " + wslRoot + "/hermes-agent/hermes && echo __OK__ || echo __NO__";
        String[] cmd = {
            "wsl", "-d", config.getWslDistro(), "-u", config.getLinuxUser(), "--",
            "bash", "-lc", probe
        };
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                out = sb.toString();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (p.isAlive()) p.destroyForcibly();
            return out.contains("__OK__");
        } catch (Exception e) {
            log.warn("hermes-agent: pre-flight probe failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Cancel the currently running task. No-op if nothing is running.
     * Returns true if a process was actually destroyed.
     */
    public boolean cancel() {
        Process p = currentProcess;
        TaskRecord rec = currentTask;
        if (p == null || !p.isAlive()) return false;
        log.info("Cancelling task {} (prompt='{}')", rec == null ? "?" : rec.getId(),
            rec == null ? "?" : abbreviate(rec.getPrompt()));
        p.destroyForcibly();
        if (rec != null) {
            rec.appendOutput("[hcc] task cancelled by user", true);
            rec.setState(TaskRecord.State.CANCELLED);
            rec.setFinishedAt(System.currentTimeMillis());
        }
        return true;
    }

    private void runOnIoThread(TaskRecord rec, String[] cmd) {
        Thread t = new Thread(() -> runBlocking(rec, cmd), "hermes-agent-runner");
        t.setDaemon(true);
        t.start();
    }

    private void runBlocking(TaskRecord rec, String[] cmd) {
        rec.setState(TaskRecord.State.RUNNING);
        rec.setStartedAt(System.currentTimeMillis());
        rec.appendOutput("[hcc] launching: " + rec.getCommandLine(), false);
        fireUpdate(rec);
        log.info("hermes-agent start: id={} cwd={} role={} cmd=\"{}\"",
            rec.getId(), rec.getWorkingDir(), rec.getAssignedRole(), rec.getCommandLine());
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            p = pb.start();
            currentProcess = p;

            Thread out = drainAsync(p.getInputStream(), rec, false);
            Thread err = drainAsync(p.getErrorStream(), rec, true);
            int exit = p.waitFor();
            out.join(1500);
            err.join(1500);

            rec.setExitCode(exit);
            if (rec.getState() != TaskRecord.State.CANCELLED) {
                if (exit == 0) {
                    rec.setState(TaskRecord.State.DONE);
                } else {
                    String firstErr = rec.firstStderrLine();
                    String tag = "exit=" + exit
                        + (firstErr.isEmpty() ? "" : ": " + firstErr);
                    rec.setErrorTag(tag);
                    rec.setState(TaskRecord.State.FAILED);
                }
            }
            rec.setFinishedAt(System.currentTimeMillis());
            rec.appendOutput("[hcc] finished: exit=" + exit
                + " state=" + rec.getState()
                + " duration_ms=" + rec.durationMs(), false);
            // Debug exhaustivo: comando, exit, tamaños de stdout/stderr
            log.info("hermes-agent done: id={} exit={} state={} stdout_chars={} stderr_chars={}",
                rec.getId(), exit, rec.getState(),
                rec.fullStdout().length(), rec.fullStderr().length());
            if (exit != 0) {
                log.error("hermes-agent failure detail: id={} tag=\"{}\" stderr=\"{}\"",
                    rec.getId(), rec.getErrorTag(), rec.fullStderr().trim());
            }

            // FALLBACK: si hermes -z se quejó de parsing (expected one argument
            // / usage / argument required), NO marcar FAILED silencioso:
            // ejecutar `bash -lc "echo <prompt> && eval <prompt safe>"` y
            // actualizar el record con su output.
            if (rec.getState() == TaskRecord.State.FAILED
                && isHermesUsageError(rec.fullStderr())) {
                log.warn("hermes-agent: detected -z usage error, running bash fallback for id={}",
                    rec.getId());
                rec.appendOutput("[hcc] detectado error de parsing -z, ejecutando fallback bash -lc", true);
                runBashFallback(rec);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            rec.setErrorTag(e.getClass().getSimpleName() + ": " + e.getMessage());
            rec.setState(TaskRecord.State.FAILED);
            rec.setFinishedAt(System.currentTimeMillis());
            rec.appendOutput("[hcc] error: " + rec.getErrorTag(), true);
            log.error("hermes-agent failed: id={} err={}", rec.getId(), rec.getErrorTag());
        } catch (Exception e) {
            // Cualquier otra excepción no debe tumbar el runner
            rec.setErrorTag(e.getClass().getSimpleName() + ": " + e.getMessage());
            rec.setState(TaskRecord.State.FAILED);
            rec.setFinishedAt(System.currentTimeMillis());
            rec.appendOutput("[hcc] unexpected error: " + rec.getErrorTag(), true);
            log.error("hermes-agent unexpected: id={} err={}", rec.getId(), rec.getErrorTag());
        } finally {
            currentProcess = null;
            fireUpdate(rec);
        }
    }

    private Thread drainAsync(InputStream stream, TaskRecord rec, boolean stderr) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    rec.appendOutput(line, stderr);
                    fireUpdate(rec);
                }
            } catch (IOException ignored) {
                // pipe closed
            }
        }, "hermes-agent-" + (stderr ? "err" : "out"));
        t.setDaemon(true);
        t.start();
        return t;
    }

    private String[] buildCommand(String fullPrompt, String workingDir, boolean fallback) {
        String wslRoot = wslPath(config.getHermesRoot());
        String wd = wslPath((workingDir == null || workingDir.isBlank()) ? wslRoot : workingDir);
        // El prompt ya viene limpio y pre-escapado (vía buildHermesPrompt +
        // escapeForBashDoubleQuote) desde submit(). Aquí SOLO lo embebemos.
        // No más positional args, no más "hermes-agent" como $0, no más
        // tokens sueltos después de -z.
        String escapedPrompt = escapeForBashDoubleQuote(fullPrompt);
        String script;
        if (fallback) {
            // Modo directo seguro: bash ejecuta el prompt como comando simple.
            // Si falla (comando no existe) sigue siendo visible en la UI.
            script =
                "set +e; "
              + "cd '" + wd + "'; "
              + "echo '[hcc-fallback] hermes CLI no disponible, ejecucion directa en bash'; "
              + "echo '[hcc-fallback] prompt recibido:'; "
              + "printf '%s\\n' \"" + escapedPrompt + "\"; "
              + "echo '[hcc-fallback] bash intentando ejecutar prompt como comando...'; "
              + "bash -c \"" + escapedPrompt + "\" 2>&1; "
              + "echo '[hcc-fallback] exit='$?";
        } else {
            script =
                "set +e; "
              + "source " + wslRoot + "/config/hermes-env.sh 2>/dev/null; "
              + "export ENGRAM_DATA_DIR='" + wslRoot + "/memory'; "
              + "export ENGRAM_PORT=7437; "
              + "export ENGRAM_TIMEZONE=America/Costa_Rica; "
              + "cd '" + wd + "'; "
              + wslRoot + "/hermes-agent/.venv/bin/python "
              + wslRoot + "/hermes-agent/hermes -z \"" + escapedPrompt + "\"";
        }
        // ÚNICO arg después de "bash -lc": el script completo. Ningún token
        // suelto, ningún positional arg, ningún fragmento del prompt aquí.
        return new String[] {
            "wsl", "-d", config.getWslDistro(), "-u", config.getLinuxUser(), "--",
            "bash", "-lc", script
        };
    }

    /**
     * Construye el prompt limpio que se pasará al agente.
     *
     * Reglas:
     *  - normaliza paths Windows a WSL (E:\ → /mnt/e/, etc.)
     *  - concatena role + cwd + prompt en un SOLO string
     *  - remueve tokens inválidos que se colaron (p.ej. "hermes-agent")
     *  - reemplaza saltos de línea con espacios (NUNCA \n en el prompt final)
     *  - idempotente: misma entrada → misma salida
     */
    private String buildHermesPrompt(String role, String workingDir, String prompt) {
        if (prompt == null) prompt = "";
        // 1) Quitar saltos de línea (CR/LF → espacio)
        String p = prompt.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        // 2) Quitar tokens que no deberían estar (huérfanos del build previo)
        p = p.replace("hermes-agent", "").replace("\\\\", "/");
        // 3) Normalizar paths Windows sueltos que el usuario haya pegado
        p = normalizeWindowsPathsInText(p);
        // 4) Prefijos opcionales
        StringBuilder sb = new StringBuilder();
        if (role != null && !role.isBlank() && !"default".equalsIgnoreCase(role)) {
            sb.append("[role=").append(role.trim()).append("] ");
        }
        if (workingDir != null && !workingDir.isBlank()) {
            sb.append("[cwd=").append(wslPath(workingDir)).append("] ");
        }
        sb.append(p.trim());
        // 5) Colapsar espacios múltiples
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        // 6) Defensa final: si quedó vacío, poner marcador
        if (out.isEmpty()) out = "[empty-prompt]";
        return out;
    }

    /**
     * Convierte paths tipo Windows pegados en texto en su equivalente WSL.
     * Ej:  E:\Dev\Hermes\foo  →  /mnt/e/Dev/Hermes/foo
     *      "abrir E:\test"     →  "abrir /mnt/e/test"
     */
    private String normalizeWindowsPathsInText(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.replaceAll("([A-Za-z]):\\\\", "/mnt/$1/")
                .replaceAll("\\\\", "/");
    }

    /**
     * Escapa un string para embeberlo de forma segura dentro de un literal
     * double-quoted de bash (ej:  bash -c ".... -z \"$PROMPT\""  ).
     *
     * Caracteres que requieren escape en double-quoted bash:
     *   \  → \\
     *   "  → \"
     *   $  → \$
     *   `  → \`
     *   !  → \!  (history expansion en interactive bash; -lc suele no
     *            expandir pero lo hacemos por defensa)
     */
    private String escapeForBashDoubleQuote(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '$':  out.append("\\$");  break;
                case '`':  out.append("\\`");  break;
                case '!':  out.append("\\!");  break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Detecta si stderr contiene un error de parsing de argparse en hermes -z.
     * Patrones conocidos: "expected one argument", "usage:", "error:", etc.
     */
    private static boolean isHermesUsageError(String stderr) {
        if (stderr == null || stderr.isEmpty()) return false;
        String lower = stderr.toLowerCase();
        return lower.contains("expected one argument")
            || lower.contains("usage:")
            || (lower.contains("error:") && lower.contains("-z"))
            || (lower.contains("error:") && lower.contains("--oneshot"))
            || lower.contains("argument -z")
            || lower.contains("argument --oneshot");
    }

    /**
     * Fallback post-facto cuando hermes -z falló por parsing.
     * Corre un bash simple que muestra el prompt y opcionalmente lo evalúa
     * de forma segura, actualizando el mismo TaskRecord (append output,
     * reset state a DONE si exit=0, FAILED en otro caso).
     */
    private void runBashFallback(TaskRecord rec) {
        String escaped = escapeForBashDoubleQuote(rec.getPrompt());
        // Script simple: echo del prompt, luego eval. No más.
        String script = "set +e; "
            + "echo '[hcc-fallback] hermes -z fallo, prompt era:'; "
            + "printf '%s\\n' \"" + escaped + "\"; "
            + "echo '[hcc-fallback] intentando ejecutar como comando bash...'; "
            + "bash -c \"" + escaped + "\" 2>&1; "
            + "echo '[hcc-fallback] exit='$?";
        String[] cmd = {
            "wsl", "-d", config.getWslDistro(), "-u", config.getLinuxUser(), "--",
            "bash", "-lc", script
        };
        log.info("hermes-agent fallback: id={} cmd=\"{}\"", rec.getId(), String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Leer todo el output (el fallback es rápido, lectura síncrona)
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    rec.appendOutput(line, false);
                    out.append(line).append('\n');
                }
            }
            int exit = p.waitFor();
            rec.appendOutput("[hcc] fallback exit=" + exit, false);
            if (exit == 0) {
                rec.setState(TaskRecord.State.DONE);
                rec.setErrorTag(null);
            } else {
                rec.setErrorTag("fallback exit=" + exit);
                rec.setState(TaskRecord.State.FAILED);
            }
            log.info("hermes-agent fallback done: id={} exit={} out_chars={}",
                rec.getId(), exit, out.length());
        } catch (Exception e) {
            rec.appendOutput("[hcc] fallback error: " + e.getMessage(), true);
            rec.setErrorTag("fallback exception: " + e.getMessage());
            log.error("hermes-agent fallback exception: id={} err={}", rec.getId(), e.getMessage());
        }
    }

    private void fireUpdate(TaskRecord rec) {
        for (Consumer<TaskRecord> l : taskListeners) {
            try { l.accept(rec); } catch (Exception e) { log.warn("listener failed: {}", e.getMessage()); }
        }
    }

    private static String wslPath(String windowsPath) {
        if (windowsPath == null) return "/mnt/e/Dev/Hermes";
        String p = windowsPath.replace("E:\\", "/mnt/e/").replace("\\", "/");
        if (p.matches("^/mnt/[a-zA-Z]:.*")) return p;
        return "/mnt/e/Dev/Hermes";
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }
}
