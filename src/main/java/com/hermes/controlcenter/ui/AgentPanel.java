package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.controllers.AgentController;
import com.hermes.controlcenter.domain.model.TaskRecord;
import com.hermes.controlcenter.domain.model.TaskRecord.State;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The Agente tab. Real, working UI for sending tasks to the Hermes agent
 * via {@link com.hermes.controlcenter.services.HermesAgentService}, viewing
 * live streamed output, cancelling a running task, and browsing past tasks.
 *
 * <p>Layout:</p>
 * <pre>
 *   +---------------------------------------------------+
 *   | Header: title + status pill + spinner (when busy) |
 *   +---------------------------+-----------------------+
 *   | Input: multi-line prompt  | History: list of past |
 *   | [Send] [Cancel]           | tasks (click to view) |
 *   +---------------------------+-----------------------+
 *   | Output: streaming stdout/stderr (read-only)      |
 *   +---------------------------------------------------+
 * </pre>
 */
public class AgentPanel extends JPanel implements AgentController.Listener {
    private final AgentController controller;
    private final JTextArea promptInput;
    private final JTextArea outputArea;
    private final JButton sendButton;
    private final JButton cancelButton;
    private final JButton clearButton;
    private final JLabel statusLabel;
    private final JLabel promptHintLabel;
    private final JProgressBar spinner;
    private final JLabel roleLabel;
    private final DefaultListModel<TaskRecord> historyModel;
    private final JList<TaskRecord> historyList;
    private final JScrollPane outputScroll;
    private final Timer refreshTimer;

    private volatile TaskRecord displayedRecord;

    public AgentPanel(AgentController controller) {
        this.controller = controller;
        controller.addListener(this);

        setLayout(new BorderLayout(0, 10));
        setBackground(new Color(0x0F172A));
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // ===== Header =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Agente Hermes");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        title.setForeground(new Color(0xF8FAFC));
        header.add(title, BorderLayout.WEST);

        JPanel statusBox = new JPanel();
        statusBox.setOpaque(false);
        statusBox.setLayout(new BorderLayout(8, 0));
        spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(140, 14));
        spinner.setVisible(false);
        spinner.setForeground(new Color(0x38BDF8));
        spinner.setBackground(new Color(0x1E293B));
        statusBox.add(spinner, BorderLayout.WEST);

        statusLabel = new JLabel("IDLE");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statusLabel.setForeground(new Color(0x4ADE80));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x334155)),
            new EmptyBorder(4, 10, 4, 10)));
        statusBox.add(statusLabel, BorderLayout.CENTER);

        roleLabel = new JLabel("rol: default");
        roleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        roleLabel.setForeground(new Color(0x94A3B8));
        statusBox.add(roleLabel, BorderLayout.EAST);

        header.add(statusBox, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ===== Center split: input+history | output =====
        // Top split: input (left) + history (right)
        JPanel inputPanel = new JPanel(new BorderLayout(0, 6));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        JLabel inputTitle = new JLabel("Tarea");
        inputTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        inputTitle.setForeground(new Color(0xCBD5E1));
        inputPanel.add(inputTitle, BorderLayout.NORTH);

        promptInput = new JTextArea(5, 30);
        promptInput.setBackground(new Color(0x0B1220));
        promptInput.setForeground(new Color(0xF8FAFC));
        promptInput.setCaretColor(new Color(0x38BDF8));
        promptInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        promptInput.setLineWrap(true);
        promptInput.setWrapStyleWord(true);
        promptInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x334155)),
            new EmptyBorder(8, 10, 8, 10)));
        installPromptKeys(promptInput);
        promptInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateHint(); }
            @Override public void removeUpdate(DocumentEvent e) { updateHint(); }
            @Override public void changedUpdate(DocumentEvent e) { updateHint(); }
        });
        JScrollPane inputScroll = new JScrollPane(promptInput);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(0x1E293B)));
        inputScroll.setPreferredSize(new Dimension(600, 130));
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        JPanel inputBottom = new JPanel(new BorderLayout(0, 2));
        inputBottom.setOpaque(false);
        promptHintLabel = new JLabel("Ctrl+Enter para enviar");
        promptHintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        promptHintLabel.setForeground(new Color(0x64748B));
        inputBottom.add(promptHintLabel, BorderLayout.WEST);

        JPanel inputBtnRow = new JPanel(new BorderLayout(6, 0));
        inputBtnRow.setOpaque(false);
        sendButton = primaryButton("Enviar", new Color(0x38BDF8));
        sendButton.addActionListener(e -> onSend());
        cancelButton = dangerButton("Cancelar", new Color(0xF87171));
        cancelButton.addActionListener(e -> onCancel());
        cancelButton.setEnabled(false);
        JPanel rightBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(cancelButton);
        rightBtns.add(sendButton);
        inputBtnRow.add(rightBtns, BorderLayout.EAST);
        inputBottom.add(inputBtnRow, BorderLayout.SOUTH);
        inputPanel.add(inputBottom, BorderLayout.SOUTH);

        JPanel historyPanel = new JPanel(new BorderLayout(0, 6));
        historyPanel.setOpaque(false);
        JLabel histTitle = new JLabel("Historial");
        histTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        histTitle.setForeground(new Color(0xCBD5E1));
        historyPanel.add(histTitle, BorderLayout.NORTH);

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setCellRenderer(new HistoryCellRenderer());
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setBackground(new Color(0x0B1220));
        historyList.setForeground(new Color(0xE2E8F0));
        historyList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        historyList.setBorder(new EmptyBorder(4, 4, 4, 4));
        historyList.addListSelectionListener(e -> {
            TaskRecord sel = historyList.getSelectedValue();
            if (sel != null) {
                displayedRecord = sel;
                renderOutput(sel);
            }
        });
        historyList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (e.isPopupTrigger() && historyList.getSelectedValue() != null) {
                    controller.clearHistory();
                }
            }
        });
        JScrollPane histScroll = new JScrollPane(historyList);
        histScroll.setBorder(BorderFactory.createLineBorder(new Color(0x1E293B)));
        histScroll.setPreferredSize(new Dimension(280, 130));
        historyPanel.add(histScroll, BorderLayout.CENTER);

        clearButton = new JButton("Limpiar historial");
        clearButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        clearButton.setBackground(new Color(0x334155));
        clearButton.setForeground(new Color(0xCBD5E1));
        clearButton.setFocusPainted(false);
        clearButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        clearButton.addActionListener(e -> controller.clearHistory());
        historyPanel.add(clearButton, BorderLayout.SOUTH);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, historyPanel);
        topSplit.setDividerLocation(560);
        topSplit.setResizeWeight(0.7);
        topSplit.setBorder(null);
        topSplit.setOpaque(false);
        topSplit.setBackground(new Color(0x0F172A));

        // Output area
        JPanel outputPanel = new JPanel(new BorderLayout(0, 6));
        outputPanel.setOpaque(false);
        JLabel outTitle = new JLabel("Salida");
        outTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        outTitle.setForeground(new Color(0xCBD5E1));
        outputPanel.add(outTitle, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setBackground(new Color(0x020617));
        outputArea.setForeground(new Color(0xE2E8F0));
        outputArea.setLineWrap(false);
        outputArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createLineBorder(new Color(0x1E293B)));
        outputScroll.setPreferredSize(new Dimension(900, 280));
        outputPanel.add(outputScroll, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, outputPanel);
        mainSplit.setDividerLocation(220);
        mainSplit.setResizeWeight(0.4);
        mainSplit.setBorder(null);
        mainSplit.setOpaque(false);
        add(mainSplit, BorderLayout.CENTER);

        refreshTimer = new Timer(200, e -> pollUpdate());
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        onChange();
    }

    private void installPromptKeys(JTextArea ta) {
        ta.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "send");
        ta.getActionMap().put("send", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onSend(); }
        });
        ta.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        ta.getActionMap().put("cancel", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (controller.isRunning()) onCancel();
                else promptInput.setText("");
            }
        });
    }

    private void onSend() {
        String text = promptInput.getText();
        if (text == null || text.isBlank()) return;
        String role = detectRole(text);
        promptInput.setText("");
        displayedRecord = null;
        controller.submit(text, null, role);
    }

    private void onCancel() {
        controller.cancel();
    }

    private void updateHint() {
        String t = promptInput.getText();
        if (t == null || t.isBlank()) {
            promptHintLabel.setText("Ctrl+Enter para enviar · Esc para limpiar");
            promptHintLabel.setForeground(new Color(0x64748B));
            return;
        }
        String role = detectRole(t);
        if (!"default".equals(role)) {
            promptHintLabel.setText("detectado rol: " + role);
            promptHintLabel.setForeground(new Color(0x38BDF8));
        } else {
            promptHintLabel.setText("Ctrl+Enter para enviar");
            promptHintLabel.setForeground(new Color(0x94A3B8));
        }
    }

    /**
     * Lightweight role routing: detects common role keywords in the prompt
     * and tags the task with the matching role. This is a *hint* for the
     * UI only; the actual agent invocation is the same {@code hermes -z}
     * call regardless of role.
     */
    private String detectRole(String prompt) {
        String p = prompt.toLowerCase();
        if (p.contains("arquitect") || p.contains("architect") || p.contains("diseño") || p.contains("design")) return "architect";
        if (p.contains("backend") || p.contains("api") || p.contains("endpoint") || p.contains("nestjs")) return "backend";
        if (p.contains("frontend") || p.contains("react") || p.contains("ui") || p.contains("componente")) return "frontend";
        if (p.contains("base de datos") || p.contains("database") || p.contains("sql") || p.contains("schema") || p.contains("migración")) return "database";
        if (p.contains("auditor") || p.contains("audit") || p.contains("revisar") || p.contains("review")) return "auditor";
        if (p.contains("integra") || p.contains("integration") || p.contains("conectar")) return "integration";
        return "default";
    }

    private void pollUpdate() {
        if (displayedRecord == null) displayedRecord = controller.current();
        if (displayedRecord != null) renderOutput(displayedRecord);
        renderStatus();
        renderHistory();
    }

    private void renderStatus() {
        TaskRecord cur = controller.current();
        boolean running = controller.isRunning();
        spinner.setVisible(running);
        if (cur == null) {
            statusLabel.setText("IDLE");
            statusLabel.setForeground(new Color(0x4ADE80));
            sendButton.setEnabled(true);
            cancelButton.setEnabled(false);
            roleLabel.setText("rol: -");
        } else {
            switch (cur.getState()) {
                case QUEUED:
                    statusLabel.setText("QUEUED");
                    statusLabel.setForeground(new Color(0xFBBF24));
                    break;
                case RUNNING:
                    statusLabel.setText("RUNNING");
                    statusLabel.setForeground(new Color(0x38BDF8));
                    break;
                case DONE:
                    statusLabel.setText("DONE");
                    statusLabel.setForeground(new Color(0x4ADE80));
                    break;
                case FAILED:
                    statusLabel.setText("FAILED");
                    statusLabel.setForeground(new Color(0xF87171));
                    break;
                case CANCELLED:
                    statusLabel.setText("CANCELLED");
                    statusLabel.setForeground(new Color(0x94A3B8));
                    break;
            }
            sendButton.setEnabled(!running);
            cancelButton.setEnabled(running);
            roleLabel.setText("rol: " + cur.getAssignedRole()
                + " · " + (cur.durationMs() / 1000) + "s"
                + (cur.isTerminal() ? " · exit=" + cur.getExitCode() : ""));
        }
    }

    private void renderOutput(TaskRecord rec) {
        try {
            if (rec == null) {
                outputArea.setText("");
                return;
            }
            List<TaskRecord.Line> lines = rec.outputSnapshot();
            StringBuilder sb = new StringBuilder();
            for (TaskRecord.Line l : lines) {
                try {
                    sb.append(l.format()).append('\n');
                } catch (Exception lineEx) {
                    sb.append("[--:--:--    ] ").append(l == null ? "" :
                        (l.text == null ? "" : l.text)).append('\n');
                }
            }
            String newText = sb.toString();
            String current = outputArea.getText();
            if (!newText.equals(current)) {
                outputArea.setText(newText);
                outputArea.setCaretPosition(Math.max(0, outputArea.getDocument().getLength() - 1));
            }
        } catch (Exception fatal) {
            // Última línea de defensa: NUNCA dejar que un error de formato
            // tumbe el EDT. Limpiamos a un estado seguro y seguimos.
            outputArea.setText("[render-error: " + fatal.getClass().getSimpleName() + "]\n");
        }
    }

    private void renderHistory() {
        List<TaskRecord> h = controller.history();
        boolean different = historyModel.size() != h.size();
        if (!different) {
            for (int i = 0; i < h.size(); i++) {
                if (historyModel.get(i) != h.get(i)) { different = true; break; }
            }
        }
        if (different) {
            historyModel.clear();
            for (TaskRecord r : h) historyModel.addElement(r);
        }
        // Refresh each rendered row so state changes update visually
        historyList.repaint();
    }

    @Override
    public void onChange() {
        SwingUtilities.invokeLater(() -> {
            pollUpdate();
        });
    }

    public void focusInput() {
        SwingUtilities.invokeLater(() -> promptInput.requestFocusInWindow());
    }

    private JButton primaryButton(String label, Color bg) {
        JButton b = new JButton(label);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(new Color(0x0F172A));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }

    private JButton dangerButton(String label, Color bg) {
        JButton b = new JButton(label);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(new Color(0x0F172A));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }

    private static class HistoryCellRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof TaskRecord rec) {
                State s = rec.getState();
                String tag;
                Color color;
                switch (s) {
                    case RUNNING -> { tag = "●"; color = new Color(0x38BDF8); }
                    case DONE -> { tag = "✓"; color = new Color(0x4ADE80); }
                    case FAILED -> { tag = "✗"; color = new Color(0xF87171); }
                    case CANCELLED -> { tag = "⊘"; color = new Color(0x94A3B8); }
                    case QUEUED -> { tag = "○"; color = new Color(0xFBBF24); }
                    default -> { tag = "·"; color = new Color(0x94A3B8); }
                }
                String p = rec.getPrompt();
                String summary = p.length() > 38 ? p.substring(0, 35) + "..." : p;
                summary = summary.replace('\n', ' ');
                l.setText(String.format("%s %s  · %ds  · %s",
                    tag,
                    rec.getAssignedRole(),
                    rec.durationMs() / 1000,
                    summary));
                if (!isSelected) l.setForeground(color);
                l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                l.setBorder(new EmptyBorder(4, 8, 4, 8));
            }
            return l;
        }
    }
}
