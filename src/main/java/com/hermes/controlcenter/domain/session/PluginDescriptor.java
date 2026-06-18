package com.hermes.controlcenter.domain.session;

public class PluginDescriptor {
    private final String id;
    private final String label;
    private final String binary;
    private final PluginKind kind;
    private final String defaultAction;
    private final String defaultActionLabel;
    private final String hermesRelation;

    public PluginDescriptor(String id, String label, String binary, PluginKind kind,
                            String defaultAction, String defaultActionLabel,
                            String hermesRelation) {
        this.id = id;
        this.label = label;
        this.binary = binary;
        this.kind = kind;
        this.defaultAction = defaultAction;
        this.defaultActionLabel = defaultActionLabel;
        this.hermesRelation = hermesRelation;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getBinary() { return binary; }
    public PluginKind getKind() { return kind; }
    public String getDefaultAction() { return defaultAction; }
    public String getDefaultActionLabel() { return defaultActionLabel; }
    public String getHermesRelation() { return hermesRelation; }

    public enum PluginKind { SHELL_TOOL, VERSION_MANAGER, MONITOR, GIT_UI, PROMPT, SERVICE }
}
