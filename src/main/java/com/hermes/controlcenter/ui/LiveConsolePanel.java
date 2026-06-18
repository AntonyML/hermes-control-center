package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.controllers.ConsoleController;
import com.hermes.controlcenter.domain.model.LiveConsole;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Interactive in-app console. Output area on top, input line at the bottom,
 * a send button, and an autocomplete hint label.
 *
 * <p>All input goes through {@link ConsoleController} which validates the
 * verb against the whitelist and dispatches the execution. The console
 * never invokes processes or services directly.</p>
 */
public class LiveConsolePanel extends JPanel implements LiveConsole.Listener, ConsoleController.SinkListener {
    private final JTextArea outputArea;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton clearButton;
    private final JLabel hintLabel;
    private final LiveConsole console;
    private final ConsoleController controller;
    private final JScrollPane scrollPane;
    private String liveDraft = "";

    public LiveConsolePanel(ConsoleController controller) {
        this.console = controller.liveConsole();
        this.controller = controller;
        this.controller.addSinkListener(this);

        setLayout(new BorderLayout(0, 6));
        setBackground(new Color(0x0F172A));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ===== Header =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Consola interactiva");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        title.setForeground(new Color(0xCBD5E1));
        header.add(title, BorderLayout.WEST);

        clearButton = new JButton("Limpiar");
        clearButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        clearButton.setBackground(new Color(0x334155));
        clearButton.setForeground(new Color(0xF8FAFC));
        clearButton.setFocusPainted(false);
        clearButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        clearButton.addActionListener(e -> controller.clearConsole());
        header.add(clearButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ===== Output area =====
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setBackground(new Color(0x020617));
        outputArea.setForeground(new Color(0xE2E8F0));
        outputArea.setLineWrap(false);
        outputArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(0x1E293B)));
        scrollPane.setPreferredSize(new Dimension(700, 200));
        add(scrollPane, BorderLayout.CENTER);

        // ===== Input row =====
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel prompt = new JLabel("hcc ›");
        prompt.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        prompt.setForeground(new Color(0x38BDF8));
        inputRow.add(prompt, BorderLayout.WEST);

        inputField = new JTextField();
        inputField.setBackground(new Color(0x0B1220));
        inputField.setForeground(new Color(0xF8FAFC));
        inputField.setCaretColor(new Color(0x38BDF8));
        inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x334155)),
            new EmptyBorder(6, 8, 6, 8)));
        installInputKeys();
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateHint(); }
            @Override public void removeUpdate(DocumentEvent e) { updateHint(); }
            @Override public void changedUpdate(DocumentEvent e) { updateHint(); }
        });
        inputRow.add(inputField, BorderLayout.CENTER);

        sendButton = new JButton("Ejecutar");
        sendButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        sendButton.setBackground(new Color(0x38BDF8));
        sendButton.setForeground(new Color(0x0F172A));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        sendButton.addActionListener(e -> submitCurrent());
        inputRow.add(sendButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.setOpaque(false);
        bottom.add(inputRow, BorderLayout.NORTH);
        hintLabel = new JLabel(" ");
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        hintLabel.setForeground(new Color(0x64748B));
        bottom.add(hintLabel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        console.addListener(this);
        for (LiveConsole.Line l : console.snapshot()) {
            appendLine(l);
        }
    }

    private void installInputKeys() {
        inputField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit");
        inputField.getActionMap().put("submit", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { submitCurrent(); }
        });
        inputField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        inputField.getActionMap().put("up", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                liveDraft = inputField.getText();
                controller.historyUp(liveDraft);
            }
        });
        inputField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        inputField.getActionMap().put("down", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                controller.historyDown(inputField.getText());
            }
        });
        inputField.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "complete");
        inputField.getActionMap().put("complete", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { completeFromHint(); }
        });
    }

    private void submitCurrent() {
        String text = inputField.getText();
        inputField.setText("");
        liveDraft = "";
        controller.submit(text);
    }

    private void updateHint() {
        String text = inputField.getText();
        if (text.isBlank()) {
            hintLabel.setText(" ");
            return;
        }
        List<String> sugg = controller.suggestionsFor(text);
        if (sugg.isEmpty()) {
            hintLabel.setText("✗ comando no permitido — escribe 'help'");
            hintLabel.setForeground(new Color(0xF87171));
        } else if (sugg.size() == 1) {
            hintLabel.setText("✓ " + sugg.get(0) + " — Enter para ejecutar");
            hintLabel.setForeground(new Color(0x4ADE80));
        } else {
            hintLabel.setText("sugerencias: " + String.join(", ", sugg.subList(0, Math.min(3, sugg.size()))));
            hintLabel.setForeground(new Color(0x38BDF8));
        }
    }

    private void completeFromHint() {
        List<String> sugg = controller.suggestionsFor(inputField.getText());
        if (sugg.size() == 1) {
            inputField.setText(sugg.get(0));
        } else if (!sugg.isEmpty()) {
            inputField.setText(sugg.get(0));
        }
    }

    @Override
    public void onAppend(LiveConsole.Line line) {
        SwingUtilities.invokeLater(() -> appendLine(line));
    }

    private void appendLine(LiveConsole.Line line) {
        outputArea.append(line.format() + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    @Override
    public void onEvent(ConsoleController.SinkEvent ev, String detail) {
        SwingUtilities.invokeLater(() -> {
            switch (ev) {
                case HISTORY_CHANGED -> inputField.setText(detail);
                case CLEAR -> outputArea.setText("");
                case COMPLETE -> inputField.requestFocusInWindow();
                default -> { /* nothing visual */ }
            }
        });
    }

    public void focusInput() {
        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }
}
