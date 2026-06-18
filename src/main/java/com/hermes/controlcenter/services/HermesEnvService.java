package com.hermes.controlcenter.services;

import com.hermes.controlcenter.domain.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guarantees that environment variables for Hermes agent (NVIDIA API keys,
 * Engram settings) are available in ALL execution contexts.
 *
 * <p>Strategy:</p>
 * <ul>
 *   <li>Reads the canonical key source {@code config/hermes-env.sh} from WSL</li>
 *   <li>Creates or updates {@code ~/.hermes/.env} so {@code hermes -z} works
 *       regardless of how it is launched (Java UI, manual tmux, etc.)</li>
 *   <li>Validates that critical variables are present</li>
 *   <li>Provides an {@link EnvStatus} enum for the UI status indicator</li>
 * </ul>
 */
public class HermesEnvService {
    private static final Logger log = LoggerFactory.getLogger(HermesEnvService.class);

    public enum EnvStatus {
        OK,              // everything loaded
        MISSING_KEYS,    // hermes-env.sh exists but NVIDIA_API_KEY missing
        NOT_LOADED,      // hermes-env.sh does not exist
        CHECK_FAILED     // could not probe (WSL error)
    }

    private static final String WSL_ENV_PATH = "/mnt/e/Dev/Hermes/config/hermes-env.sh";

    private static final String SCRIPT_FETCH =
        "HERMES_ENV=\"" + WSL_ENV_PATH + "\"; "
      + "if [ -f \"$HERMES_ENV\" ]; then "
      + "  source \"$HERMES_ENV\" 2>/dev/null; "
      + "  echo \"__ENV_DUMP__\"; "
      + "  echo \"NVIDIA_API_KEY=${NVIDIA_API_KEY:-}\"; "
      + "  echo \"NVIDIA_GPTOSS_API_KEY=${NVIDIA_GPTOSS_API_KEY:-}\"; "
      + "  echo \"NVIDIA_QWEN_API_KEY=${NVIDIA_QWEN_API_KEY:-}\"; "
      + "  echo \"NVIDIA_DEEPSEEK_API_KEY=${NVIDIA_DEEPSEEK_API_KEY:-}\"; "
      + "  echo \"ENGRAM_PORT=${ENGRAM_PORT:-}\"; "
      + "  echo \"ENGRAM_DATA_DIR=${ENGRAM_DATA_DIR:-}\"; "
      + "  echo \"ENGRAM_TIMEZONE=${ENGRAM_TIMEZONE:-}\"; "
      + "  echo \"__ENV_END__\"; "
      + "else "
      + "  echo \"__ENV_MISSING__\"; "
      + "fi";

    private final AppConfig config;
    private volatile EnvStatus lastStatus = EnvStatus.CHECK_FAILED;
    private volatile String lastError = "";

    public HermesEnvService(AppConfig config) {
        this.config = config;
    }

    public EnvStatus lastStatus() { return lastStatus; }
    public String lastError() { return lastError; }

    /**
     * Full env load: probe, then auto-create {@code ~/.hermes/.env} if needed.
     * Called once during bootstrap.
     */
    public EnvStatus ensureEnvLoaded() {
        Map<String, String> vars = fetchVarsFromEnvSh();
        if (vars.isEmpty()) {
            lastStatus = EnvStatus.NOT_LOADED;
            lastError = "hermes-env.sh not found at " + WSL_ENV_PATH;
            log.warn("HermesEnv: {} — {}", lastStatus, lastError);
            return lastStatus;
        }

        String nvidiaKey = vars.getOrDefault("NVIDIA_API_KEY", "");
        if (nvidiaKey.isBlank()) {
            lastStatus = EnvStatus.MISSING_KEYS;
            lastError = "NVIDIA_API_KEY is empty in hermes-env.sh";
            log.warn("HermesEnv: {} — {}", lastStatus, lastError);
            return lastStatus;
        }

        boolean dotenvOk = createDotEnv(vars);
        if (!dotenvOk) {
            lastStatus = EnvStatus.CHECK_FAILED;
            lastError = "could not write ~/.hermes/.env";
            log.warn("HermesEnv: {} — {}", lastStatus, lastError);
            return lastStatus;
        }

        lastStatus = EnvStatus.OK;
        lastError = "";
        log.info("HermesEnv: OK — NVIDIA_API_KEY loaded, ~/.hermes/.env created/updated");
        return lastStatus;
    }

    /**
     * Quick probe that only checks if {@code ~/.hermes/.env} exists with
     * {@code NVIDIA_API_KEY}. Does NOT re-source hermes-env.sh.
     * Used by the UI status indicator (fast, read-only).
     */
    public EnvStatus probeDotEnv() {
        String probe =
            "if [ -f ~/.hermes/.env ] && grep -q 'NVIDIA_API_KEY' ~/.hermes/.env 2>/dev/null; then "
          + "  echo \"__OK__\"; "
          + "else "
          + "  echo \"__NO__\"; "
          + "fi";
        String[] cmd = wslCmd(probe);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.readLine();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (p.isAlive()) p.destroyForcibly();
            boolean ok = out != null && out.contains("__OK__");
            lastStatus = ok ? EnvStatus.OK : EnvStatus.MISSING_KEYS;
            if (!ok) lastError = "~/.hermes/.env missing or has no NVIDIA_API_KEY";
        } catch (Exception e) {
            lastStatus = EnvStatus.CHECK_FAILED;
            lastError = e.getMessage();
        }
        if (lastStatus != EnvStatus.OK) {
            log.warn("HermesEnv probe: {} — {}", lastStatus, lastError);
        }
        return lastStatus;
    }

    // ====================== internals ======================

    private Map<String, String> fetchVarsFromEnvSh() {
        Map<String, String> vars = new LinkedHashMap<>();
        String[] cmd = wslCmd(SCRIPT_FETCH);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean inDump = false;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("__ENV_MISSING__")) return Map.of();
                    if (line.contains("__ENV_DUMP__")) { inDump = true; continue; }
                    if (line.contains("__ENV_END__")) break;
                    if (!inDump) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        vars.put(line.substring(0, eq), line.substring(eq + 1));
                    }
                }
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (p.isAlive()) p.destroyForcibly();
        } catch (Exception e) {
            log.warn("HermesEnv: fetch failed: {}", e.getMessage());
            return Map.of();
        }
        return vars;
    }

    /**
     * Creates or updates {@code ~/.hermes/.env} with the critical variables
     * extracted from hermes-env.sh. Preserves any existing content, appends
     * only missing lines.
     */
    private boolean createDotEnv(Map<String, String> vars) {
        String nvidiaKey = vars.getOrDefault("NVIDIA_API_KEY", "");
        String engramPort = vars.getOrDefault("ENGRAM_PORT", "7437");
        String engramDataDir = vars.getOrDefault("ENGRAM_DATA_DIR", "/mnt/e/Dev/Hermes/memory");
        String engramTz = vars.getOrDefault("ENGRAM_TIMEZONE", "America/Costa_Rica");

        String script =
            "DOTENV=~/.hermes/.env; "
          + "mkdir -p ~/.hermes 2>/dev/null; "
          + "touch \"$DOTENV\"; "
          // NVIDIA_API_KEY — always trust the source (hermes-env.sh)
          + "if grep -q '^NVIDIA_API_KEY=' \"$DOTENV\" 2>/dev/null; then "
          + "  sed -i 's|^NVIDIA_API_KEY=.*|NVIDIA_API_KEY=" + nvidiaKey + "|' \"$DOTENV\"; "
          + "else "
          + "  echo 'NVIDIA_API_KEY=" + nvidiaKey + "' >> \"$DOTENV\"; "
          + "fi; "
          // Engram vars
          + "if ! grep -q '^ENGRAM_PORT=' \"$DOTENV\" 2>/dev/null; then "
          + "  echo 'ENGRAM_PORT=" + engramPort + "' >> \"$DOTENV\"; "
          + "fi; "
          + "if ! grep -q '^ENGRAM_DATA_DIR=' \"$DOTENV\" 2>/dev/null; then "
          + "  echo 'ENGRAM_DATA_DIR=" + engramDataDir + "' >> \"$DOTENV\"; "
          + "fi; "
          + "if ! grep -q '^ENGRAM_TIMEZONE=' \"$DOTENV\" 2>/dev/null; then "
          + "  echo 'ENGRAM_TIMEZONE=" + engramTz + "' >> \"$DOTENV\"; "
          + "fi; "
          + "echo \"__OK__\"";

        String[] cmd = wslCmd(script);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.readLine();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (p.isAlive()) p.destroyForcibly();
            return out != null && out.contains("__OK__");
        } catch (Exception e) {
            log.warn("HermesEnv: createDotEnv failed: {}", e.getMessage());
            return false;
        }
    }

    private String[] wslCmd(String script) {
        return new String[] {
            "wsl", "-d", config.getWslDistro(), "-u", config.getLinuxUser(), "--",
            "bash", "-lc", script
        };
    }
}
