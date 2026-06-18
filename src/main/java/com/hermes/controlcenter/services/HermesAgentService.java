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
 * <p>Routing:</p>
 * <pre>
 *   wsl -d Ubuntu-24.04 -u antony --
 *     bash -lc "source config/hermes-env.sh &amp;&amp; cd $WD &amp;&amp; .../hermes -z \"$1\""
 *     hermes-agent
 *     &lt;prompt&gt;
 * </pre>
 *
 * <p>The prompt is passed as a positional arg to bash so it is not subject
 * to shell expansion inside the double-quoted invocation. The script sources
 * the API keys from {@code config/hermes-env.sh} and changes to the configured
 * working directory (Hermes project root by default) so the agent resolves
 * AGENTS.md and the project tree correctly.</p>
 */
public class HermesAgentService {
    private static final Logger log = LoggerFactory.getLogger(HermesAgentService.class);

    private final AppConfig config;
    private final List<Consumer<TaskRecord>> taskListeners = new CopyOnWriteArrayList<>();
    private volatile Process currentProcess;
    private volatile TaskRecord currentTask;
    private final Object runLock = new Object();

    public HermesAgentService(AppConfig config) {
        this.config = config;
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
        String[] cmd = buildCommand(prompt, rec.getWorkingDir());
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
        log.info("hermes-agent start: id={} cwd={} role={}",
            rec.getId(), rec.getWorkingDir(), rec.getAssignedRole());
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            currentProcess = p;

            Thread out = drainAsync(p.getInputStream(), rec, false);
            Thread err = drainAsync(p.getErrorStream(), rec, true);
            int exit = p.waitFor();
            // give the drain threads a moment to flush
            out.join(1500);
            err.join(1500);

            rec.setExitCode(exit);
            if (rec.getState() != TaskRecord.State.CANCELLED) {
                if (exit == 0) {
                    rec.setState(TaskRecord.State.DONE);
                } else {
                    rec.setErrorTag("exit=" + exit);
                    rec.setState(TaskRecord.State.FAILED);
                }
            }
            rec.setFinishedAt(System.currentTimeMillis());
            rec.appendOutput("[hcc] finished: exit=" + exit
                + " state=" + rec.getState()
                + " duration_ms=" + rec.durationMs(), false);
            log.info("hermes-agent done: id={} exit={} state={}", rec.getId(), exit, rec.getState());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            rec.setErrorTag(e.getClass().getSimpleName() + ": " + e.getMessage());
            rec.setState(TaskRecord.State.FAILED);
            rec.setFinishedAt(System.currentTimeMillis());
            rec.appendOutput("[hcc] error: " + rec.getErrorTag(), true);
            log.error("hermes-agent failed: id={} err={}", rec.getId(), rec.getErrorTag());
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

    private String[] buildCommand(String prompt, String workingDir) {
        String wslRoot = wslPath(config.getHermesRoot());
        String wd = (workingDir == null || workingDir.isBlank()) ? wslRoot : workingDir;
        String script =
            "set +e; "
          + "source " + wslRoot + "/config/hermes-env.sh 2>/dev/null; "
          + "export ENGRAM_DATA_DIR='" + wslRoot + "/memory'; "
          + "export ENGRAM_PORT=7437; "
          + "export ENGRAM_TIMEZONE=America/Costa_Rica; "
          + "cd '" + wd + "' && "
          + wslRoot + "/hermes-agent/.venv/bin/python "
          + wslRoot + "/hermes-agent/hermes -z \"$1\"";
        return new String[] {
            "wsl", "-d", config.getWslDistro(), "-u", config.getLinuxUser(), "--",
            "bash", "-lc", script,
            "hermes-agent",
            prompt
        };
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
