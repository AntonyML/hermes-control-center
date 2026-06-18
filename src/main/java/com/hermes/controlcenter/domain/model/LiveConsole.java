package com.hermes.controlcenter.domain.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LiveConsole {
    public enum Level { INFO, OK, WARN, ERROR, COMMAND, INPUT, OUTPUT }

    public record Line(LocalTime time, Level level, String text) {
        public String format() {
            return "[" + time.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] "
                + switch (level) {
                    case OK -> "✓ ";
                    case WARN -> "▲ ";
                    case ERROR -> "✗ ";
                    case COMMAND -> "$ ";
                    case INPUT -> "> ";
                    case OUTPUT -> "  ";
                    case INFO -> "· ";
                } + text;
        }
    }

    public interface Listener {
        void onAppend(Line line);
    }

    private final int capacity;
    private final List<Line> lines;
    private final List<Listener> listeners = new ArrayList<>();

    public LiveConsole(int capacity) {
        this.capacity = capacity;
        this.lines = new ArrayList<>(capacity);
    }

    public synchronized void addListener(Listener l) { listeners.add(l); }

    public synchronized void info(String text)   { append(Level.INFO, text); }
    public synchronized void ok(String text)     { append(Level.OK, text); }
    public synchronized void warn(String text)   { append(Level.WARN, text); }
    public synchronized void error(String text)  { append(Level.ERROR, text); }
    public synchronized void command(String text){ append(Level.COMMAND, text); }
    public synchronized void input(String text)  { append(Level.INPUT, text); }
    public synchronized void output(String text) { append(Level.OUTPUT, text); }

    public synchronized void append(Level level, String text) {
        Line line = new Line(LocalTime.now(), level, text);
        lines.add(line);
        if (lines.size() > capacity) {
            lines.remove(0);
        }
        for (Listener l : listeners) {
            try { l.onAppend(line); } catch (Exception ignored) {}
        }
    }

    public synchronized List<Line> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public synchronized void clear() {
        lines.clear();
    }
}
