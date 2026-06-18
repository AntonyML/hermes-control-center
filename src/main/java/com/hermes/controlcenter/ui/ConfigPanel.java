package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.controllers.ConfigController;
import com.hermes.controlcenter.controllers.ConfigController.Field;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

/**
 * Config screen. Renders a form for the {@link ConfigController} fields and
 * dispatches save/reset through the controller.
 */
public class ConfigPanel extends JPanel {
    private final ConfigController controller;
    private final Map<String, JTextField> fields = new HashMap<>();
    private final JLabel statusLabel;
    private final JButton saveButton;
    private final JButton resetButton;

    public ConfigPanel(ConfigController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(0, 16));
        setBackground(new Color(0x0F172A));
        setBorder(new EmptyBorder(32, 40, 32, 40));

        JLabel header = new JLabel("Configuración");
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        header.setForeground(new Color(0xF8FAFC));
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 8, 8, 8);
        c.gridx = 0; c.weightx = 0;

        int row = 0;
        for (Field f : controller.fields()) {
            addRow(form, c, row++, f);
        }
        add(form, BorderLayout.CENTER);

        JPanel btnRow = new JPanel();
        btnRow.setOpaque(false);
        saveButton = new JButton("Guardar");
        saveButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        saveButton.setBackground(new Color(0x4ADE80));
        saveButton.setForeground(new Color(0x0F172A));
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        saveButton.addActionListener(e -> onSave());

        resetButton = new JButton("Restablecer");
        resetButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        resetButton.setBackground(new Color(0xF87171));
        resetButton.setForeground(new Color(0x0F172A));
        resetButton.setFocusPainted(false);
        resetButton.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        resetButton.addActionListener(e -> onReset());

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0x94A3B8));

        btnRow.add(saveButton);
        btnRow.add(resetButton);
        btnRow.add(statusLabel);
        add(btnRow, BorderLayout.SOUTH);

        // initial load
        for (Field f : controller.fields()) {
            JTextField tf = fields.get(f.key);
            if (tf != null) tf.setText(f.value);
        }
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, Field f) {
        c.gridy = row;
        JLabel l = new JLabel(f.label);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        l.setForeground(new Color(0xCBD5E1));
        c.gridx = 0; c.weightx = 0;
        form.add(l, c);

        c.gridx = 1; c.weightx = 1;
        JTextField field = new JTextField(f.value);
        field.setBackground(new Color(0x1E293B));
        field.setForeground(new Color(0xF8FAFC));
        field.setCaretColor(new Color(0xF8FAFC));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x334155)),
            new EmptyBorder(6, 8, 6, 8)));
        field.setPreferredSize(new Dimension(420, 30));
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { push(); }
            private void push() { controller.updateField(f.key, field.getText()); }
        });
        form.add(field, c);
        fields.put(f.key, field);
    }

    private void onSave() {
        statusLabel.setText(controller.save());
    }

    private void onReset() {
        controller.resetToDefaults();
        for (Field f : controller.fields()) {
            JTextField tf = fields.get(f.key);
            if (tf != null) tf.setText(f.value);
        }
        statusLabel.setText("Restablecido a defaults");
    }

    public void showStatus(String text) {
        statusLabel.setText(text);
    }
}
