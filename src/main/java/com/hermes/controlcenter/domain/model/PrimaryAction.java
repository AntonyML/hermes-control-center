package com.hermes.controlcenter.domain.model;

public enum PrimaryAction {
    ENTER_HERMES("Entrar a Hermes"),
    CREATE_SESSION("Crear sesión Hermes"),
    START_STACK("Iniciar stack");

    private final String label;

    PrimaryAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
