package com.hermes.controlcenter.domain.command;

import java.util.ArrayList;
import java.util.List;

public class ConsoleEvent {
    public enum Kind { APPEND, REJECT, COMPLETE, CLEAR }

    private final Kind kind;
    private final String line;
    private final long timestamp;

    private ConsoleEvent(Kind kind, String line) {
        this.kind = kind;
        this.line = line;
        this.timestamp = System.currentTimeMillis();
    }

    public static ConsoleEvent append(String text)   { return new ConsoleEvent(Kind.APPEND, text); }
    public static ConsoleEvent reject(String text)   { return new ConsoleEvent(Kind.REJECT, text); }
    public static ConsoleEvent complete(String text) { return new ConsoleEvent(Kind.COMPLETE, text); }
    public static ConsoleEvent clear()               { return new ConsoleEvent(Kind.CLEAR, ""); }

    public Kind getKind() { return kind; }
    public String getLine() { return line; }
    public long getTimestamp() { return timestamp; }
}
