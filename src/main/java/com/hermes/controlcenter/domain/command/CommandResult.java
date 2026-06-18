package com.hermes.controlcenter.domain.command;

import com.hermes.controlcenter.infrastructure.CommandExecutor.ExecutionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandResult {
    private final CommandRequest request;
    private final boolean allowed;
    private final boolean ok;
    private final int exitCode;
    private final String output;
    private final String errorOutput;
    private final String errorTag;
    private final long durationMs;
    private final List<String> stream;
    private final String rejectReason;

    private CommandResult(CommandRequest request, boolean allowed, boolean ok, int exitCode,
                          String output, String errorOutput, String errorTag,
                          long durationMs, List<String> stream, String rejectReason) {
        this.request = request;
        this.allowed = allowed;
        this.ok = ok;
        this.exitCode = exitCode;
        this.output = output;
        this.errorOutput = errorOutput;
        this.errorTag = errorTag;
        this.durationMs = durationMs;
        this.stream = stream == null ? Collections.emptyList() : List.copyOf(stream);
        this.rejectReason = rejectReason;
    }

    public static CommandResult rejected(CommandRequest req, String reason) {
        return new CommandResult(req, false, false, -1, "", "", null, 0L, List.of(), reason);
    }

    public static CommandResult fromExecution(CommandRequest req, ExecutionResult er,
                                              List<String> stream, long durationMs) {
        return new CommandResult(req, true, er.isOk(), er.getExitCode(),
            er.getOutput(), er.getErrorOutput(), er.getErrorTag(),
            durationMs, stream, null);
    }

    public CommandRequest getRequest() { return request; }
    public boolean isAllowed() { return allowed; }
    public boolean isOk() { return ok; }
    public int getExitCode() { return exitCode; }
    public String getOutput() { return output; }
    public String getErrorOutput() { return errorOutput; }
    public String getErrorTag() { return errorTag; }
    public long getDurationMs() { return durationMs; }
    public List<String> getStream() { return stream; }
    public String getRejectReason() { return rejectReason; }
}
