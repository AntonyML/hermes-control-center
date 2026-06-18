package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.ServiceStatus;
import com.hermes.controlcenter.utils.StatusColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

/**
 * Status card for a single service / plugin. The card has a primary action
 * button; if the action is null the button is hidden.
 */
public class StatusCard extends JPanel {
    private final JLabel nameLabel;
    private final JLabel statusLabel;
    private final JLabel detailLabel;
    private final JLabel dotLabel;
    private final JButton actionButton;

    public StatusCard(String name, String actionLabel, ActionListener action) {
        setLayout(new BorderLayout(8, 0));
        setBackground(StatusColors.cardBackground());
        setBorder(new EmptyBorder(12, 14, 12, 14));
        setPreferredSize(new Dimension(220, 90));

        dotLabel = new JLabel("●");
        dotLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        dotLabel.setForeground(StatusColors.of(ServiceStatus.UNKNOWN));
        add(dotLabel, BorderLayout.WEST);

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);

        nameLabel = new JLabel(name);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        nameLabel.setForeground(StatusColors.textPrimary());
        text.add(nameLabel);

        statusLabel = new JLabel("—");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusLabel.setForeground(StatusColors.textSecondary());
        text.add(statusLabel);

        detailLabel = new JLabel(" ");
        detailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        detailLabel.setForeground(StatusColors.textSecondary());
        text.add(detailLabel);

        add(text, BorderLayout.CENTER);

        actionButton = new JButton();
        if (actionLabel == null) {
            actionButton.setVisible(false);
        } else {
            actionButton.setText(actionLabel);
            actionButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            actionButton.setBackground(new Color(0x334155));
            actionButton.setForeground(new Color(0xF8FAFC));
            actionButton.setFocusPainted(false);
            actionButton.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            if (action != null) actionButton.addActionListener(action);
        }
        add(actionButton, BorderLayout.EAST);
    }

    public void update(ServiceHealth health) {
        ServiceStatus status = health.getStatus();
        dotLabel.setForeground(StatusColors.of(status));
        statusLabel.setText(status.name() + (health.getVersion() != null ? " · " + health.getVersion() : ""));
        detailLabel.setText(health.getMessage() != null ? health.getMessage() : " ");
        revalidate();
        repaint();
    }

    public void setActionLabel(String label) {
        actionButton.setText(label);
        actionButton.setVisible(label != null);
    }
}
