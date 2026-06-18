package com.hermes.controlcenter.domain.model;

public class AppConfig {
    private String linuxUser = "antony";
    private String wslDistro = "Ubuntu-24.04";
    private String hermesRoot = "E:\\Dev\\Hermes";
    private String tmuxScript = "/mnt/e/Dev/Hermes/scripts/hermes-tmux.sh";
    private String hermesStartScript = "/mnt/e/Dev/Hermes/scripts/hermes-start.sh";
    private String engramStartScript = "/mnt/e/Dev/Hermes/scripts/engram-start.sh";
    private String terminalCommand = "wt";
    private int engramPort = 7437;

    public String getLinuxUser() { return linuxUser; }
    public void setLinuxUser(String v) { this.linuxUser = v; }

    public String getWslDistro() { return wslDistro; }
    public void setWslDistro(String v) { this.wslDistro = v; }

    public String getHermesRoot() { return hermesRoot; }
    public void setHermesRoot(String v) { this.hermesRoot = v; }

    public String getTmuxScript() { return tmuxScript; }
    public void setTmuxScript(String v) { this.tmuxScript = v; }

    public String getHermesStartScript() { return hermesStartScript; }
    public void setHermesStartScript(String v) { this.hermesStartScript = v; }

    public String getEngramStartScript() { return engramStartScript; }
    public void setEngramStartScript(String v) { this.engramStartScript = v; }

    public String getTerminalCommand() { return terminalCommand; }
    public void setTerminalCommand(String v) {
        this.terminalCommand = (v == null || v.isBlank()) ? "wt" : v;
    }

    public int getEngramPort() { return engramPort; }
    public void setEngramPort(int v) { this.engramPort = v; }
}
