package com.hermes.controlcenter.domain.model;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A single task submitted to the Hermes agent. Mutable while the task is
 * in flight; safe to read concurrently from the UI thread (it dispatches
 * updates via {@link Listener} callbacks that the controller marshals onto
 * the EDT).
 *
 * <p>The record stores every stdout/stderr line emitted by the underlying
 * {@code hermes -z} process so the UI can stream it in real time and
 * reconstruct the full output after completion.</p>
 */
public class TaskRecord {
    public enum State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    public interface Listener {
        void onUpdate(TaskRecord record);
    }

    private final String id;
    private final String prompt;
    private final long submittedAt;
    private final List<Line> output = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    private long startedAt;
    private long finishedAt;
    private volatile State state = State.QUEUED;
    private int exitCode = -1;
    private String errorTag;
    private String workingDir;
    private String assignedRole = "default";
    private String commandLine;

    public TaskRecord(String prompt) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.prompt = prompt == null ? "" : prompt;
        this.submittedAt = System.currentTimeMillis();
    }

    public synchronized void addListener(Listener l) { listeners.add(l); }
    public synchronized void removeListener(Listener l) { listeners.remove(l); }

    public String getId() { return id; }
    public String getPrompt() { return prompt; }
    public long getSubmittedAt() { return submittedAt; }
    public long getStartedAt() { return startedAt; }
    public long getFinishedAt() { return finishedAt; }
    public State getState() { return state; }
    public int getExitCode() { return exitCode; }
    public String getErrorTag() { return errorTag; }
    public String getWorkingDir() { return workingDir; }
    public String getAssignedRole() { return assignedRole; }
    public String getCommandLine() { return commandLine; }

    public void setStartedAt(long t) { this.startedAt = t; }
    public void setFinishedAt(long t) { this.finishedAt = t; }
    public void setErrorTag(String t) { this.errorTag = t; }
    public void setWorkingDir(String d) { this.workingDir = d; }
    public void setAssignedRole(String r) { this.assignedRole = r; }
    public void setCommandLine(String c) { this.commandLine = c; }

    public synchronized void setState(State s) {
        if (this.state == s) return;
        this.state = s;
        fireUpdate();
    }

    public synchronized void setExitCode(int code) {
        this.exitCode = code;
        fireUpdate();
    }

    public synchronized void appendOutput(String line, boolean stderr) {
        output.add(new Line(System.currentTimeMillis(), stderr, line));
        fireUpdate();
    }

    public synchronized List<Line> outputSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(output));
    }

    public synchronized String fullOutput() {
        StringBuilder sb = new StringBuilder();
        for (Line l : output) sb.append(l.text).append('\n');
        return sb.toString();
    }

    public synchronized String fullStdout() {
        StringBuilder sb = new StringBuilder();
        for (Line l : output) if (!l.stderr) sb.append(l.text).append('\n');
        return sb.toString();
    }

    public synchronized String fullStderr() {
        StringBuilder sb = new StringBuilder();
        for (Line l : output) if (l.stderr) sb.append(l.text).append('\n');
        return sb.toString();
    }

    public synchronized String firstStderrLine() {
        for (Line l : output) {
            if (l.stderr) {
                String t = l.text == null ? "" : l.text.trim();
                if (!t.isEmpty()) return t.length() > 200 ? t.substring(0, 200) + "..." : t;
            }
        }
        return "";
    }

    public long durationMs() {
        long start = startedAt == 0 ? submittedAt : startedAt;
        long end = finishedAt == 0 ? System.currentTimeMillis() : finishedAt;
        return Math.max(0, end - start);
    }

    public boolean isTerminal() {
        return state == State.DONE || state == State.FAILED || state == State.CANCELLED;
    }

    private void fireUpdate() {
        for (Listener l : new ArrayList<>(listeners)) {
            try { l.onUpdate(this); } catch (Exception ignored) {}
        }
    }

    public static class Line {
        public final long ts;
        public final boolean stderr;
        public final String text;
        private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

        public Line(long ts, boolean stderr, String text) {
            this.ts = ts; this.stderr = stderr; this.text = text;
        }
        public String format() {
            String prefix;
            try {
                LocalTime t;
                if (ts <= 0L) {
                    t = LocalTime.now();
                } else {
                    t = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime();
                }
                prefix = t.format(HMS);
            } catch (Exception ex) {
                prefix = "--:--:--";
            }
            return "[" + prefix + (stderr ? " err" : "    ") + "] " + (text == null ? "" : text);
        }
    }
}
