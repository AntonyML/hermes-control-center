package com.hermes.controlcenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hermes.controlcenter.domain.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class ConfigStore {
    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final String CONFIG_PATH = "config.json";
    private final ObjectMapper mapper;

    public ConfigStore() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AppConfig load() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            log.info("No config.json found; using defaults");
            return new AppConfig();
        }
        try {
            return mapper.readValue(file, AppConfig.class);
        } catch (IOException e) {
            log.error("Failed to load config; using defaults: {}", e.getMessage());
            return new AppConfig();
        }
    }

    public void save(AppConfig config) {
        try {
            mapper.writeValue(new File(CONFIG_PATH), config);
            log.info("Config saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage());
        }
    }
}
