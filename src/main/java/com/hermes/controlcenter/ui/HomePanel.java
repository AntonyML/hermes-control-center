package com.hermes.controlcenter.ui;

import com.hermes.controlcenter.controllers.ConsoleController;
import com.hermes.controlcenter.controllers.HomeController;
import com.hermes.controlcenter.controllers.PluginRegistry;
import com.hermes.controlcenter.domain.model.DiagnosticSnapshot;
import com.hermes.controlcenter.domain.model.ServiceHealth;
import com.hermes.controlcenter.domain.model.StackSnapshot;
import com.hermes.controlcenter.domain.session.PluginDescriptor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Home screen — header + 12 plugin cards + primary action button + console.
 * <p>The view is dumb: all snapshot data comes from {@link HomeController},
 * and card actions are dispatched through {@link ConsoleController} so they
 * share the verb whitelist with the typed console.</p>
 */
public class HomePanel extends JPanel {
    private final HomeController home;
    private final ConsoleController console;
    private final Map<String, StatusCard> cards = new LinkedHashMap<>();
    private final JLabel headerSubtitle;
    private final JLabel headerTimestamp;
    private final JButton primaryButton;

    public HomePanel(HomeController homeController, ConsoleController consoleController) {
        this.home = homeController;
        this.console = consoleController;

        setLayout(new BorderLayout(0, 16));
        setBackground(new Color(0x0F172A));
        setBorder(new EmptyBorder(20, 28, 20, 28));

        // ===== HEADER =====
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("HERMES CONTROL CENTER");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        title.setForeground(new Color(0xF8FAFC));
        header.add(title, BorderLayout.NORTH);

        JPanel sub = new JPanel(new BorderLayout());
        sub.setOpaque(false);
        headerSubtitle = new JLabel(home.projectSubtitle());
        headerSubtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        headerSubtitle.setForeground(new Color(0x94A3B8));
        sub.add(headerSubtitle, BorderLayout.WEST);

        headerTimestamp = new JLabel(home.lastUpdatedLabel());
        headerTimestamp.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        headerTimestamp.setForeground(new Color(0x64748B));
        sub.add(headerTimestamp, BorderLayout.EAST);

        header.add(sub, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        // ===== CARDS GRID =====
        JPanel cardsGrid = new JPanel(new GridLayout(3, 4, 12, 12));
        cardsGrid.setOpaque(false);
        for (PluginDescriptor p : PluginRegistry.PLUGINS) {
            StatusCard c = new StatusCard(p.getLabel(), p.getDefaultActionLabel(),
                e -> console.submit(p.getDefaultAction()));
            cards.put(p.getLabel(), c);
            cardsGrid.add(c);
        }

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(cardsGrid, BorderLayout.CENTER);

        // ===== PRIMARY ACTION BUTTON =====
        primaryButton = new JButton(home.currentPrimaryAction().getLabel());
        primaryButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        primaryButton.setBackground(new Color(0x38BDF8));
        primaryButton.setForeground(new Color(0x0F172A));
        primaryButton.setFocusPainted(false);
        primaryButton.setBorder(BorderFactory.createEmptyBorder(12, 28, 12, 28));
        primaryButton.addActionListener(e -> home.runPrimaryAction());
        JPanel btnRow = new JPanel();
        btnRow.setOpaque(false);
        btnRow.add(primaryButton);
        center.add(btnRow, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // ===== CONSOLE =====
        LiveConsolePanel consolePanel = new LiveConsolePanel(console);
        consolePanel.setPreferredSize(new Dimension(0, 280));
        add(consolePanel, BorderLayout.SOUTH);
    }

    public void applyStackSnapshot(StackSnapshot snap) {
        for (ServiceHealth h : snap.getAll()) {
            StatusCard c = cards.get(h.getName());
            if (c != null) c.update(h);
        }
        // Some plugin cards use different names — fall back to the diagnostic list.
        DiagnosticSnapshot diag = home.currentDiagnostic();
        for (ServiceHealth p : diag.getPluginsList()) {
            StatusCard c = cards.get(p.getName());
            if (c != null) c.update(p);
        }
        headerSubtitle.setText(home.projectSubtitle());
        primaryButton.setText(home.currentPrimaryAction().getLabel());
        headerTimestamp.setText(home.lastUpdatedLabel());
        SwingUtilities.invokeLater(this::repaint);
    }
}
