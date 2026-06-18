package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.domain.model.AppConfig;
import com.hermes.controlcenter.domain.model.LiveConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Config panel. Holds the editable form fields, applies
 * user changes, persists them and exposes a status message.
 */
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    public static class Field {
        public final String key;
        public final String label;
        public final String value;

        public Field(String key, String label, String value) {
            this.key = key;
            this.label = label;
            this.value = value;
        }
    }

    private final ApplicationContext ctx;
    private AppConfig working;

    public ConfigController(ApplicationContext ctx) {
        this.ctx = ctx;
        this.working = clone(ctx.config());
    }

    public List<Field> fields() {
        AppConfig c = working;
        List<Field> out = new ArrayList<>();
        out.add(new Field("linuxUser", "Usuario Linux", c.getLinuxUser()));
        out.add(new Field("wslDistro", "Distro WSL", c.getWslDistro()));
        out.add(new Field("hermesRoot", "Ruta Hermes (Windows)", c.getHermesRoot()));
        out.add(new Field("tmuxScript", "Script tmux (WSL)", c.getTmuxScript()));
        out.add(new Field("hermesStartScript", "Script hermes-start (WSL)", c.getHermesStartScript()));
        out.add(new Field("engramStartScript", "Script engram-start (WSL)", c.getEngramStartScript()));
        out.add(new Field("terminalCommand", "Comando de terminal (wt / cmd)", c.getTerminalCommand()));
        return out;
    }

    public void updateField(String key, String value) {
        switch (key) {
            case "linuxUser" -> working.setLinuxUser(value);
            case "wslDistro" -> working.setWslDistro(value);
            case "hermesRoot" -> working.setHermesRoot(value);
            case "tmuxScript" -> working.setTmuxScript(value);
            case "hermesStartScript" -> working.setHermesStartScript(value);
            case "engramStartScript" -> working.setEngramStartScript(value);
            case "terminalCommand" -> working.setTerminalCommand(value);
            default -> log.warn("unknown config field: {}", key);
        }
    }

    public String save() {
        AppConfig saved = clone(working);
        ctx.configStore().save(saved);
        ctx.replaceConfig(saved);
        ctx.console().info("configuración guardada en config.json");
        return "Guardado en config.json";
    }

    public String resetToDefaults() {
        AppConfig defaults = new AppConfig();
        this.working = defaults;
        ctx.configStore().save(defaults);
        ctx.replaceConfig(defaults);
        ctx.console().info("configuración restablecida a valores por defecto");
        return "Restablecido a defaults";
    }

    public void log(String text) {
        ctx.console().info(text);
    }

    private static AppConfig clone(AppConfig src) {
        AppConfig c = new AppConfig();
        c.setLinuxUser(src.getLinuxUser());
        c.setWslDistro(src.getWslDistro());
        c.setHermesRoot(src.getHermesRoot());
        c.setTmuxScript(src.getTmuxScript());
        c.setHermesStartScript(src.getHermesStartScript());
        c.setEngramStartScript(src.getEngramStartScript());
        c.setTerminalCommand(src.getTerminalCommand());
        return c;
    }
}
