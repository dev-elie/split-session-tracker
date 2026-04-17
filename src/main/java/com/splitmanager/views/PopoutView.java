package com.splitmanager.views;

import com.splitmanager.ManagerKnownPlayers;
import com.splitmanager.ManagerSession;
import com.splitmanager.PluginConfig;
import com.splitmanager.controllers.PanelController;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.Formats;
import com.splitmanager.views.graph.SessionGraphData;
import com.splitmanager.views.graph.SessionGraphMode;
import com.splitmanager.views.graph.SessionGraphPanel;
import com.splitmanager.views.graph.SessionGraphSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class PopoutView extends PanelView
{
	private static final int GRAPH_REFRESH_INTERVAL_MS = 60_000;
	private static final Dimension LEFT_PANE_PREFERRED_SIZE = new Dimension(330, 600);
	private static final Dimension LEFT_PANE_MINIMUM_SIZE = new Dimension(180, 0);
	private static final Dimension RIGHT_PANE_MINIMUM_SIZE = new Dimension(260, 0);

	private SessionGraphPanel graphPanel;
	private JComboBox<SessionGraphMode> graphModeDropdown;
	private JCheckBox showSleepingPlayersToggle;
	private JLabel graphDescriptionLabel;
	private JLabel totalLootValue;
	private JLabel gpPerHourValue;
	private JLabel topPlayerValue;
	private final Timer graphRefreshTimer = new Timer(GRAPH_REFRESH_INTERVAL_MS, e -> refreshGraph());

	public PopoutView(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager, PanelController controller)
	{
		super(sessionManager, config, playerManager, controller);
		graphRefreshTimer.setInitialDelay(GRAPH_REFRESH_INTERVAL_MS);
		buildPopoutLayout();
		refreshGraph();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		if (!graphRefreshTimer.isRunning())
		{
			graphRefreshTimer.start();
		}
	}

	@Override
	public void removeNotify()
	{
		graphRefreshTimer.stop();
		super.removeNotify();
	}

	private void buildPopoutLayout()
	{
		Component[] sidebarComponents = getComponents();
		removeAll();
		setLayout(new BorderLayout(8, 0));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JScrollPane controlsScroll = new JScrollPane(wrapSidebar(sidebarComponents));
		controlsScroll.setPreferredSize(LEFT_PANE_PREFERRED_SIZE);
		controlsScroll.setMinimumSize(LEFT_PANE_MINIMUM_SIZE);
		controlsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		controlsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		JComponent graphDashboard = buildGraphDashboard();
		graphDashboard.setMinimumSize(RIGHT_PANE_MINIMUM_SIZE);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsScroll, graphDashboard);
		splitPane.setResizeWeight(0.32d);
		splitPane.setContinuousLayout(true);
		splitPane.setDividerLocation(340);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		add(splitPane, BorderLayout.CENTER);
	}

	private JComponent wrapSidebar(Component[] sidebarComponents)
	{
		JPanel sidebar = new ScrollableSidebarPanel();
		sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
		if (sidebarComponents != null)
		{
			for (Component component : sidebarComponents)
			{
				sidebar.add(component);
			}
		}
		sidebar.add(Box.createVerticalGlue());
		sidebar.setMinimumSize(LEFT_PANE_MINIMUM_SIZE);
		return sidebar;
	}

	private static class ScrollableSidebarPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return LEFT_PANE_PREFERRED_SIZE;
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(16, visibleRect.height - 32);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private JComponent buildGraphDashboard()
	{
		JPanel dashboard = new JPanel(new BorderLayout(0, 8));
		dashboard.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

		JLabel title = new JLabel("Session graph");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2.0f));

		graphModeDropdown = new JComboBox<>(SessionGraphMode.values());
		graphModeDropdown.addActionListener(e -> refreshGraph());
		showSleepingPlayersToggle = new JCheckBox("Show sleeping players", true);
		showSleepingPlayersToggle.addActionListener(e -> refreshGraph());

		JPanel header = new JPanel(new BorderLayout(8, 2));
		header.add(title, BorderLayout.WEST);
		JPanel controls = new JPanel(new GridLayout(1, 2, 8, 0));
		controls.add(showSleepingPlayersToggle);
		controls.add(graphModeDropdown);
		header.add(controls, BorderLayout.EAST);

		graphDescriptionLabel = new JLabel(" ");
		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.add(header, BorderLayout.NORTH);
		top.add(graphDescriptionLabel, BorderLayout.SOUTH);
		dashboard.add(top, BorderLayout.NORTH);

		graphPanel = new SessionGraphPanel();
		dashboard.add(graphPanel, BorderLayout.CENTER);

		JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0));
		totalLootValue = new JLabel("-", SwingConstants.CENTER);
		gpPerHourValue = new JLabel("-", SwingConstants.CENTER);
		topPlayerValue = new JLabel("-", SwingConstants.CENTER);
		stats.add(statPanel("Total loot", totalLootValue));
		stats.add(statPanel("GP/hr", gpPerHourValue));
		stats.add(statPanel("Top earner", topPlayerValue));
		dashboard.add(stats, BorderLayout.SOUTH);

		return dashboard;
	}

	private JPanel statPanel(String title, JLabel valueLabel)
	{
		JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD));

		JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEtchedBorder(),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		panel.add(titleLabel);
		panel.add(valueLabel);
		return panel;
	}

	@Override
	protected void onMetricsRefreshed()
	{
		refreshGraph();
	}

	private void refreshGraph()
	{
		if (graphPanel == null || graphModeDropdown == null)
		{
			return;
		}

		SessionGraphMode mode = (SessionGraphMode) graphModeDropdown.getSelectedItem();
		if (mode == null)
		{
			mode = SessionGraphMode.GP_PER_HOUR;
		}

		Session currentSession = getSessionManager().getCurrentSession().orElse(null);
		List<Kill> kills = currentSession == null ? List.of() : getSessionManager().getAllKills();
		List<PlayerMetrics> metrics = currentSession == null
			? List.of()
			: getSessionManager().computeMetricsFor(currentSession, shouldShowSleepingPlayersInGraph());
		SessionGraphSnapshot snapshot = SessionGraphData.build(
			currentSession,
			getSessionManager().getAllSessionsNewestFirst(),
			kills,
			metrics,
			mode,
			Instant.now());

		graphPanel.setSnapshot(snapshot);
		graphDescriptionLabel.setText(mode.getDescription());
		totalLootValue.setText(formatAmount(snapshot.getTotalLoot()));
		gpPerHourValue.setText(formatAmount(snapshot.getGpPerHour()) + "/h");
		topPlayerValue.setText(snapshot.getTopPlayer().isBlank()
			? "-"
			: snapshot.getTopPlayer() + " (" + formatAmount(snapshot.getTopPlayerTotal()) + ")");
	}

	private String formatAmount(long amount)
	{
		long abs = Math.abs(amount);
		if (abs >= 1_000_000_000L)
		{
			return Formats.OsrsAmountFormatter.toSuffixString(amount, 'b');
		}
		if (abs >= 1_000_000L)
		{
			return Formats.OsrsAmountFormatter.toSuffixString(amount, 'm');
		}
		return Formats.OsrsAmountFormatter.toSuffixString(amount, 'k');
	}

	private boolean shouldShowSleepingPlayersInGraph()
	{
		return showSleepingPlayersToggle == null || showSleepingPlayersToggle.isSelected();
	}
}
