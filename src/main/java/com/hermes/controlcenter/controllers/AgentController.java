package com.hermes.controlcenter.controllers;

import com.hermes.controlcenter.domain.model.TaskRecord;
import com.hermes.controlcenter.services.HermesAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MVC facade for the agent panel. Wraps {@link HermesAgentService}, keeps
 * an in-memory history of past tasks (capped) and re-broadcasts service
 * events to UI listeners, marshalled onto the EDT.
 */
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final int HISTORY_CAP = 50;

    public interface Listener {
        void onChange();
    }

    private final HermesAgentService service;
    private final List<TaskRecord> history = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private TaskRecord current;

    public AgentController(HermesAgentService service) {
        this.service = service;
        service.addTaskListener(rec -> SwingUtilities.invokeLater(this::onServiceUpdate));
    }

    public void addListener(Listener l) { listeners.add(l); }

    public HermesAgentService service() { return service; }

    public TaskRecord current() { return current; }

    public List<TaskRecord> history() { return Collections.unmodifiableList(history); }

    public boolean isRunning() { return service.isRunning(); }

    /**
     * Submit a new task. If a task is already running it is left alone; the
     * caller is expected to cancel first if it wants preemption.
     */
    public TaskRecord submit(String prompt) {
        return submit(prompt, null, "default");
    }

    public TaskRecord submit(String prompt, String workingDir, String role) {
        if (prompt == null || prompt.isBlank()) return null;
        if (service.isRunning()) {
            log.warn("submit ignored: a task is already running (id={})",
                current == null ? "?" : current.getId());
            return null;
        }
        TaskRecord rec = service.submit(prompt.trim(), workingDir, role);
        current = rec;
        history.add(0, rec);
        if (history.size() > HISTORY_CAP) {
            history.remove(history.size() - 1);
        }
        notifyChange();
        return rec;
    }

    public boolean cancel() {
        boolean ok = service.cancel();
        notifyChange();
        return ok;
    }

    public void clearHistory() {
        if (current != null && !current.isTerminal()) return;
        history.clear();
        current = null;
        notifyChange();
    }

    public void selectHistory(TaskRecord rec) {
        if (rec == null) return;
        // Selecting a history item only changes the *view*; current() stays
        // whatever the service is running. The UI uses this to display a
        // past task's full output in the output area.
        notifyChange();
    }

    private void onServiceUpdate() {
        TaskRecord rec = service.currentTask();
        if (rec != null) current = rec;
        notifyChange();
    }

    private void notifyChange() {
        for (Listener l : listeners) {
            try { l.onChange(); } catch (Exception ignored) {}
        }
    }
}
