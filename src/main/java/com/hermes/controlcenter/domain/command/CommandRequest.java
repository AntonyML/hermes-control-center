package com.hermes.controlcenter.domain.command;

import com.hermes.controlcenter.domain.model.CommandSpec;

public class CommandRequest {
    private final String rawInput;
    private final String verb;
    private final String description;
    private final CommandSpec spec;
    private final boolean interactive;
    private final long createdAtMillis;

    public CommandRequest(String rawInput, String verb, String description,
                          CommandSpec spec, boolean interactive) {
        this.rawInput = rawInput;
        this.verb = verb;
        this.description = description;
        this.spec = spec;
        this.interactive = interactive;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getRawInput() { return rawInput; }
    public String getVerb() { return verb; }
    public String getDescription() { return description; }
    public CommandSpec getSpec() { return spec; }
    public boolean isInteractive() { return interactive; }
    public long getCreatedAtMillis() { return createdAtMillis; }
}
