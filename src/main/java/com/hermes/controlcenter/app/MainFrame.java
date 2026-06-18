package com.hermes.controlcenter.app;

import com.hermes.controlcenter.bootstrap.ApplicationContext;
import com.hermes.controlcenter.bootstrap.StartupController;
import com.hermes.controlcenter.controllers.ConfigController;
import com.hermes.controlcenter.controllers.ConsoleController;
import com.hermes.controlcenter.controllers.DiagnosticController;
import com.hermes.controlcenter.controllers.HomeController;
import com.hermes.controlcenter.ui.ConfigPanel;
import com.hermes.controlcenter.ui.DiagnosticPanel;
import com.hermes.controlcenter.ui.HomePanel;
import com.hermes.controlcenter.ui.SplashFrame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Top-level application shell. Built by {@link HermesControlCenterApp#main}
 * after the splash has finished its job. The frame is dumb: it hosts a
 * CardLayout with the three view panels and a navigation bar at the top.
 *
 * <p>No business logic lives here. The frame is replaced as little as
 * possible: once visible it never re-creates its children. State updates
 * come from the controllers via the EDT.</p>
 */
public class MainFrame {
    private static final String CARD_HOME = "home";
    private static final String CARD_DIAG = "diag";
    private static final String CARD_CONFIG = "config";

    private final JFrame frame;
    private final JPanel cards;
    private final HomePanel homePanel;
    private final DiagnosticPanel diagnosticPanel;
    private final ConfigPanel configPanel;
    private final ApplicationContext ctx;

    public MainFrame(ApplicationContext ctx) {
        this.ctx = ctx;
        HomeController homeCtl = new HomeController(ctx);
        ConsoleController consoleCtl = new ConsoleController(ctx);
        DiagnosticController diagCtl = new DiagnosticController(ctx);
        ConfigController configCtl = new ConfigController(ctx);

        this.homePanel = new HomePanel(homeCtl, consoleCtl);
        this.diagnosticPanel = new DiagnosticPanel(diagCtl);
        this.configPanel = new ConfigPanel(configCtl);

        this.cards = new JPanel(new CardLayout());
        cards.setBackground(new Color(0x0F172A));
        cards.add(homePanel, CARD_HOME);
        cards.add(diagnosticPanel, CARD_DIAG);
        cards.add(configPanel, CARD_CONFIG);

        this.frame = new JFrame("Hermes Control Center");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1180, 820);
        frame.setMinimumSize(new Dimension(1080, 720));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(new Color(0x0F172A));
        frame.setLayout(new BorderLayout());
        frame.add(buildNav(), BorderLayout.NORTH);
        frame.add(cards, BorderLayout.CENTER);
    }

    public void show() {
        frame.setVisible(true);
        // Initial populate of the home panel.
        homePanel.applyStackSnapshot(homeController().currentSnapshot());
    }

    private JPanel buildNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(new Color(0x1E293B));
        nav.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel left = new JPanel();
        left.setOpaque(false);
        JLabel brand = new JLabel("HERMES");
        brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        brand.setForeground(new Color(0xF8FAFC));
        left.add(brand);
        nav.add(left, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        JButton home = navButton("Home");
        JButton diag = navButton("Diagnóstico");
        JButton conf = navButton("Configuración");
        home.addActionListener(e -> showCard(CARD_HOME));
        diag.addActionListener(e -> { showCard(CARD_DIAG); diagnosticPanel.applyCurrent(); });
        conf.addActionListener(e -> showCard(CARD_CONFIG));
        right.add(home);
        right.add(diag);
        right.add(conf);
        nav.add(right, BorderLayout.EAST);
        return nav;
    }

    private JButton navButton(String label) {
        JButton b = new JButton(label);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBackground(new Color(0x334155));
        b.setForeground(new Color(0xF8FAFC));
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }

    private void showCard(String card) {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, card);
    }

    private HomeController homeController() {
        // The HomePanel already holds a controller; we don't have a getter.
        // Recreate a controller-bound refresh by asking the cache.
        return new HomeController(ctx);
    }

    /**
     * Wire a refresh tick: re-apply home + diagnostic snapshots to the view
     * whenever the cache updates. Called from the bootstrap completion path.
     */
    public void refreshAfterBootstrap() {
        SwingUtilities.invokeLater(() -> {
            homePanel.applyStackSnapshot(new HomeController(ctx).currentSnapshot());
            diagnosticPanel.applyCurrent();
        });
    }
}
