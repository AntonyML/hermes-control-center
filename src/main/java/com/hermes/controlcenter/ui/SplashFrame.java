package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.bootstrap.StartupController;
import com.hermes.controlcenter.domain.snapshot.StartupPhase;
import com.hermes.controlcenter.domain.snapshot.StartupProgress;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * Splash screen shown while {@link StartupController} is running. The Home
 * frame is constructed off-screen during this time; once the bootstrap
 * completes, {@link #onComplete(Runnable)} fires the supplied callback which
 * is responsible for closing the splash and showing Home.
 *
 * <p>No business logic lives here: the splash is a passive consumer of
 * {@link StartupProgress} events.</p>
 */
public class SplashFrame extends JFrame {
    private final JLabel statusLabel;
    private final JLabel phaseLabel;
    private final JLabel percentLabel;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JProgressBar progressBar;
    private final JPanel stepsPanel;
    private final StepRow[] rows = new StepRow[StartupPhase.values().length];

    public SplashFrame() {
        setUndecorated(true);
        setAlwaysOnTop(true);
        setBackground(new Color(0x0F172A));
        getRootPane().setBackground(new Color(0x0F172A));
        setSize(new Dimension(620, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(0x0B1220));
        root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x1E293B), 1),
            new EmptyBorder(28, 36, 28, 36)));

        // ===== Header =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        titleLabel = new JLabel("HERMES CONTROL CENTER");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        titleLabel.setForeground(new Color(0xF8FAFC));
        titleLabel.setHorizontalAlignment(JLabel.LEFT);
        header.add(titleLabel, BorderLayout.NORTH);

        subtitleLabel = new JLabel("Inicializando stack…");
        subtitleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(0x94A3B8));
        header.add(subtitleLabel, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0x38BDF8));
        header.add(statusLabel, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);

        // ===== Center: steps =====
        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(20, 0, 20, 0));

        stepsPanel = new JPanel(new GridBagLayout());
        stepsPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 4, 0);
        int idx = 0;
        for (StartupPhase phase : StartupPhase.values()) {
            c.gridy = idx++;
            StepRow row = new StepRow(phase);
            rows[phase.ordinal()] = row;
            stepsPanel.add(row, c);
        }
        center.add(stepsPanel, BorderLayout.CENTER);

        // Progress bar
        JPanel progressPanel = new JPanel(new BorderLayout(8, 0));
        progressPanel.setOpaque(false);
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setIndeterminate(false);
        progressBar.setBackground(new Color(0x1E293B));
        progressBar.setForeground(new Color(0x38BDF8));
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(400, 8));
        phaseLabel = new JLabel("BOOT");
        phaseLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        phaseLabel.setForeground(new Color(0x94A3B8));
        percentLabel = new JLabel("0%");
        percentLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        percentLabel.setForeground(new Color(0x38BDF8));

        progressPanel.add(phaseLabel, BorderLayout.WEST);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(percentLabel, BorderLayout.EAST);
        center.add(progressPanel, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);

        // ===== Footer =====
        JLabel footer = new JLabel("Powered by Hermes · tmux · Engram · OpenCode");
        footer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        footer.setForeground(new Color(0x475569));
        footer.setHorizontalAlignment(JLabel.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    public void apply(StartupProgress p) {
        SwingUtilities.invokeLater(() -> {
            double pct = Math.max(0, Math.min(1, p.getPercent()));
            int iv = (int) Math.round(pct * 100);
            progressBar.setValue(iv);
            percentLabel.setText(iv + "%");
            phaseLabel.setText(p.getPhase().getLabel().toUpperCase());
            statusLabel.setText("· " + p.getMessage());
            subtitleLabel.setText(p.getPhase().getDescription());

            for (int i = 0; i < rows.length; i++) {
                rows[i].setState(i < p.getPhase().ordinal()
                    ? StepState.DONE
                    : (i == p.getPhase().ordinal() ? StepState.ACTIVE : StepState.PENDING));
            }
            for (int i = p.getPhase().ordinal() + 1; i < rows.length; i++) {
                rows[i].setState(StepState.PENDING);
            }
            revalidate();
            repaint();
        });
    }

    public void onComplete(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            percentLabel.setText("100%");
            for (StepRow r : rows) r.setState(StepState.DONE);
            statusLabel.setText("· Stack listo");
            try { Thread.sleep(220); } catch (InterruptedException ignored) {}
            dispose();
            if (action != null) action.run();
        });
    }

    public void showCentered() {
        setLocationRelativeTo(null);
        setVisible(true);
        toFront();
    }

    // ============ internals ============

    private enum StepState { PENDING, ACTIVE, DONE }

    private static class StepRow extends JPanel {
        private final JLabel bullet;
        private final JLabel name;
        private final JLabel state;
        private final Color pendingColor = new Color(0x475569);
        private final Color activeColor = new Color(0x38BDF8);
        private final Color doneColor = new Color(0x4ADE80);

        StepRow(StartupPhase phase) {
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            bullet = new JLabel("○");
            bullet.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            bullet.setForeground(pendingColor);
            name = new JLabel(phase.getLabel());
            name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            name.setForeground(new Color(0xCBD5E1));
            state = new JLabel("—");
            state.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            state.setForeground(pendingColor);
            state.setHorizontalAlignment(JLabel.RIGHT);
            add(bullet, BorderLayout.WEST);
            add(name, BorderLayout.CENTER);
            add(state, BorderLayout.EAST);
            setState(StepState.PENDING);
        }

        void setState(StepState s) {
            switch (s) {
                case PENDING -> {
                    bullet.setText("○");
                    bullet.setForeground(pendingColor);
                    state.setText("pendiente");
                    state.setForeground(pendingColor);
                }
                case ACTIVE -> {
                    bullet.setText("◉");
                    bullet.setForeground(activeColor);
                    state.setText("en curso…");
                    state.setForeground(activeColor);
                }
                case DONE -> {
                    bullet.setText("●");
                    bullet.setForeground(doneColor);
                    state.setText("ok");
                    state.setForeground(doneColor);
                }
            }
        }
    }
}
