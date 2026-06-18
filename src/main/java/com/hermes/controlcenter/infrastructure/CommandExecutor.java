package com.hermes.controlcenter.infrastructure;

import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.CommandSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CommandExecutor is the ONLY place in the app where external processes are launched.
 * All services route through this class. The UI never invokes ProcessBuilder directly.
 *
 * Streaming: callers may pass a Consumer<String> sink. Both stdout and stderr are
 * captured line-by-line and forwarded to the sink in real time, then the final
 * ExecutionResult is returned to the caller.
 */
public class CommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    public ExecutionResult run(CommandSpec spec) {
        return run(spec, 10, null);
    }

    public ExecutionResult run(CommandSpec spec, int timeoutSeconds) {
        return run(spec, timeoutSeconds, null);
    }

    public ExecutionResult run(CommandSpec spec, int timeoutSeconds, Consumer<String> sink) {
        log.info("exec: {}", spec.getDescription());
        log.debug("  cmd: {}", String.join(" ", spec.getCommand()));
        if (spec.isInteractive()) {
            return runInteractive(spec, sink);
        }
        ProcessBuilder pb = new ProcessBuilder(spec.getCommand());
        pb.redirectErrorStream(false);
        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            StringBuilder errorBuf = new StringBuilder();

            Thread outThread = drain(process.getInputStream(), "stdout", output, sink);
            Thread errThread = drain(process.getErrorStream(), "stderr", errorBuf, sink);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outThread.join(500);
                errThread.join(500);
                return new ExecutionResult(-1, output.toString(), errorBuf.toString(), true, "timeout");
            }
            outThread.join(500);
            errThread.join(500);
            int exit = process.exitValue();
            log.info("  exit: {} (stdout={} stderr={})", exit, output.length(), errorBuf.length());
            return new ExecutionResult(exit, output.toString(), errorBuf.toString(), false, null);
        } catch (IOException | InterruptedException e) {
            log.error("  failed: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ExecutionResult(-1, "", e.getMessage(), true, e.getClass().getSimpleName());
        }
    }

    private Thread drain(InputStream stream, String tag, StringBuilder target, Consumer<String> sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (target) {
                        target.append(line).append(System.lineSeparator());
                    }
                    if (sink != null) {
                        sink.accept("[" + tag + "] " + line);
                    }
                }
            } catch (IOException ignored) {
                // stream closed
            }
        }, "hcc-drain-" + tag);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private ExecutionResult runInteractive(CommandSpec spec, Consumer<String> sink) {
        if (sink != null) {
            sink.accept("[launch] " + spec.getDescription());
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(spec.getCommand());
            pb.inheritIO();
            Process process = pb.start();
            return new ExecutionResult(0, "interactive launched", "", false, null);
        } catch (IOException e) {
            log.error("  failed: {}", e.getMessage());
            if (sink != null) sink.accept("[error] " + e.getMessage());
            return new ExecutionResult(-1, "", e.getMessage(), true, e.getClass().getSimpleName());
        }
    }

    public static class ExecutionResult {
        private final int exitCode;
        private final String output;
        private final String errorOutput;
        private final boolean failed;
        private final String errorTag;

        public ExecutionResult(int exitCode, String output, String errorOutput, boolean failed, String errorTag) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.errorOutput = errorOutput == null ? "" : errorOutput;
            this.failed = failed;
            this.errorTag = errorTag;
        }

        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getErrorOutput() { return errorOutput; }
        public boolean isFailed() { return failed; }
        public String getErrorTag() { return errorTag; }
        public boolean isOk() { return exitCode == 0 && !failed; }
    }
}
