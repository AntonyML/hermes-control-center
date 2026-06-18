package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.controllers.DiagnosticController;
import com.hermes.controlcenter.controllers.PluginRegistry;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.session.PluginDescriptor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic screen: 12 status cards in a grid + textual report.
 * All data and actions come from {@link DiagnosticController}.
 */
public class DiagnosticPanel extends JPanel {
    private final DiagnosticController controller;
    private final JTextArea report;
    private final JButton refreshButton;
    private final JLabel headerLabel;
    private final JPanel cardsGrid;
    private final Map<String, StatusCard> cards = new LinkedHashMap<>();
    private final List<PluginDescriptor> descriptors = PluginRegistry.PLUGINS;

    public DiagnosticPanel(DiagnosticController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(0, 12));
        setBackground(new Color(0x0F172A));
        setBorder(new EmptyBorder(24, 32, 24, 32));

        headerLabel = new JLabel("Diagnóstico del stack");
        headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        headerLabel.setForeground(new Color(0xF8FAFC));
        add(headerLabel, BorderLayout.NORTH);

        cardsGrid = new JPanel(new GridLayout(3, 4, 10, 10));
        cardsGrid.setOpaque(false);
        for (PluginDescriptor p : descriptors) {
            StatusCard c = new StatusCard(p.getLabel(), null, null);
            cards.put(p.getLabel(), c);
            cardsGrid.add(c);
        }
        add(cardsGrid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        report = new JTextArea(8, 60);
        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setBackground(new Color(0x1E293B));
        report.setForeground(new Color(0xE2E8F0));
        report.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(report);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x334155)));
        scroll.setPreferredSize(new Dimension(700, 160));
        bottom.add(scroll, BorderLayout.CENTER);

        JPanel btnRow = new JPanel();
        btnRow.setOpaque(false);
        refreshButton = new JButton("Refrescar diagnóstico");
        refreshButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        refreshButton.setBackground(new Color(0x38BDF8));
        refreshButton.setForeground(new Color(0x0F172A));
        refreshButton.setFocusPainted(false);
        refreshButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        refreshButton.addActionListener(e -> controller.refresh());
        btnRow.add(refreshButton);
        bottom.add(btnRow, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        applyCurrent();
    }

    public void applyCurrent() {
        DiagnosticSnapshot snap = controller.snapshot();
        applySnapshot(snap);
    }

    public void applySnapshot(DiagnosticSnapshot snap) {
        for (ServiceHealth h : controller.orderedCards(snap)) {
            StatusCard c = cards.get(h.getName());
            if (c != null) c.update(h);
        }
        report.setText(controller.renderReport(snap));
        SwingUtilities.invokeLater(this::repaint);
    }
}
