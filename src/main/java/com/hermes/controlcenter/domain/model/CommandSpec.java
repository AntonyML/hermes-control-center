package com.hermes.controlcenter.domain.model;

public class CommandSpec {
    private final String description;
    private final String[] command;
    private final boolean interactive;

    public CommandSpec(String description, String[] command, boolean interactive) {
        this.description = description;
        this.command = command;
        this.interactive = interactive;
    }

    public String getDescription() { return description; }
    public String[] getCommand() { return command; }
    public boolean isInteractive() { return interactive; }
}
