package com.splitmanager.views;

import com.splitmanager.ManagerKnownPlayers;
import com.splitmanager.ManagerSession;
import com.splitmanager.PluginConfig;
import com.splitmanager.controllers.PanelController;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.Formats;
import static com.splitmanager.utils.Utils.toast;
import com.splitmanager.views.graph.SessionGraphData;
import com.splitmanager.views.graph.SessionGraphMode;
import com.splitmanager.views.graph.SessionGraphPanel;
import com.splitmanager.views.graph.SessionGraphSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class PopoutView extends PanelView
{
	private static final int GRAPH_REFRESH_INTERVAL_MS = 60_000;
	private static final Dimension LEFT_PANE_PREFERRED_SIZE = new Dimension(330, 600);
	private static final Dimension LEFT_PANE_MINIMUM_SIZE = new Dimension(180, 0);
	private static final Dimension RIGHT_PANE_MINIMUM_SIZE = new Dimension(260, 0);
	private static final ImageIcon DELETE_ICON = loadDeleteIcon();

	private SessionGraphPanel graphPanel;
	private JComboBox<SessionGraphMode> graphModeDropdown;
	private JCheckBox showSleepingPlayersToggle;
	private JLabel graphDescriptionLabel;
	private JLabel totalLootValue;
	private JLabel gpPerHourValue;
	private JLabel topPlayerValue;
	private final Timer graphRefreshTimer = new Timer(GRAPH_REFRESH_INTERVAL_MS, e -> refreshGraph());
	private boolean editMode = false;
	private JComponent editContainer;

	private JPanel dashboardContainer;

	public PopoutView(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager, PanelController controller)
	{
		super(sessionManager, config, playerManager, controller);
		graphRefreshTimer.setInitialDelay(GRAPH_REFRESH_INTERVAL_MS);

		buildPopoutLayout();
		refreshGraph();
	}

	public JButton getToggleEditButton()
	{
		return btnToggleEdit;
	}

	public void setEditMode(boolean editMode)
	{
		this.editMode = editMode;
		updateDashboardContent();
	}

	@Override
	protected void onPencilClicked()
	{
		setEditMode(true);
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

		JComponent sidebar = wrapSidebar(sidebarComponents);
		JScrollPane controlsScroll = new JScrollPane(sidebar);
		controlsScroll.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		sidebar.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		controlsScroll.setPreferredSize(LEFT_PANE_PREFERRED_SIZE);
		controlsScroll.setMinimumSize(LEFT_PANE_MINIMUM_SIZE);
		controlsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		controlsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		dashboardContainer = new JPanel(new BorderLayout());
		dashboardContainer.setMinimumSize(RIGHT_PANE_MINIMUM_SIZE);
		updateDashboardContent();

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsScroll, dashboardContainer);
		splitPane.setResizeWeight(0.32d);
		splitPane.setContinuousLayout(true);
		splitPane.setDividerLocation(340);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		add(splitPane, BorderLayout.CENTER);
	}

	private void updateDashboardContent()
	{
		dashboardContainer.removeAll();
		if (editMode)
		{
			if (editContainer == null)
			{
				editContainer = buildEditDashboard();
			}
			dashboardContainer.add(editContainer, BorderLayout.CENTER);
		}
		else
		{
			dashboardContainer.add(buildGraphDashboard(), BorderLayout.CENTER);
			refreshGraph();
		}
		dashboardContainer.revalidate();
		dashboardContainer.repaint();
	}

	private JComponent buildEditDashboard()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

		JPanel header = new JPanel(new BorderLayout());
		JLabel title = new JLabel("Edit Session History");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2.0f));
		header.add(title, BorderLayout.WEST);

		HistoryTableModel model = new HistoryTableModel();
		JTable table = new JTable(model);
		table.setRowHeight(24);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setDragEnabled(true);
		table.setDropMode(javax.swing.DropMode.INSERT_ROWS);
		table.setTransferHandler(new HistoryTransferHandler(model));
		table.getColumnModel().getColumn(HistoryTableModel.DELETE_COLUMN).setMinWidth(34);
		table.getColumnModel().getColumn(HistoryTableModel.DELETE_COLUMN).setMaxWidth(42);
		table.getColumnModel().getColumn(HistoryTableModel.DELETE_COLUMN).setCellRenderer(new DeleteButtonRenderer(model));
		table.getColumnModel().getColumn(HistoryTableModel.DELETE_COLUMN).setCellEditor(new DeleteButtonEditor(model));

		panel.add(header, BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);

		JLabel hint = new JLabel("Click trash to delete. Linked joins ask for a second click. Drag to reorder.", SwingConstants.CENTER);
		hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() + 1.0f));
		panel.add(hint, BorderLayout.SOUTH);

		return panel;
	}

	private static ImageIcon loadDeleteIcon()
	{
		BufferedImage removeImg = ImageUtil.loadImageResource(PopoutView.class, "/com/splitmanager/icons/trash-solid-full.png");
		return new ImageIcon(ImageUtil.resizeImage(removeImg, 16, 16));
	}

	private static JButton createDeleteButton()
	{
		JButton button = new JButton(DELETE_ICON);
		button.setToolTipText("Remove entry");
		button.setOpaque(true);
		button.setRolloverEnabled(true);
		button.setFocusable(false);
		SwingUtil.removeButtonDecorations(button);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		return button;
	}

	private class HistoryTableModel extends AbstractTableModel
	{
		private static final int DELETE_COLUMN = 4;
		private List<Kill> kills = new ArrayList<>();
		private final String[] columns = {"Time", "Player", "Amount", "Type", "X"};
		private int pendingRosterDeleteRow = -1;
		private int pendingRosterDeleteLinkedRow = -1;

		HistoryTableModel()
		{
			refresh();
		}

		void refresh()
		{
			kills = new ArrayList<>(sessionManager.getAllKills());
			clearPendingRosterDelete();
			fireTableDataChanged();
		}

		boolean requiresRosterDeleteConfirmation(int rowIndex)
		{
			Kill selected = getKillAt(rowIndex).orElse(null);
			return selected != null
				&& Kill.TYPE_JOINED.equalsIgnoreCase(selected.getType())
				&& findLinkedRosterEventRow(rowIndex) >= 0;
		}

		boolean isPendingRosterDelete(int rowIndex)
		{
			return rowIndex >= 0
				&& (rowIndex == pendingRosterDeleteRow || rowIndex == pendingRosterDeleteLinkedRow);
		}

		void markPendingRosterDelete(int rowIndex, int linkedRowIndex)
		{
			pendingRosterDeleteRow = rowIndex;
			pendingRosterDeleteLinkedRow = linkedRowIndex;
			fireTableRowsUpdated(Math.min(rowIndex, linkedRowIndex), Math.max(rowIndex, linkedRowIndex));
		}

		int getPendingRosterDeleteRow()
		{
			return pendingRosterDeleteRow;
		}

		int getPendingRosterDeleteLinkedRow()
		{
			return pendingRosterDeleteLinkedRow;
		}

		void clearPendingRosterDelete()
		{
			pendingRosterDeleteRow = -1;
			pendingRosterDeleteLinkedRow = -1;
		}

		int findLinkedRosterEventRow(int rowIndex)
		{
			Kill selected = getKillAt(rowIndex).orElse(null);
			if (selected == null || !selected.isRosterEvent())
			{
				return -1;
			}
			if (Kill.TYPE_JOINED.equalsIgnoreCase(selected.getType()))
			{
				return findNextRosterEvent(rowIndex, selected.getPlayer(), Kill.TYPE_LEFT);
			}
			return -1;
		}

		boolean hasAssignedSplitsInRosterPeriod(int rowIndex)
		{
			Kill selected = getKillAt(rowIndex).orElse(null);
			if (selected == null || !Kill.TYPE_JOINED.equalsIgnoreCase(selected.getType()))
			{
				return false;
			}
			String player = selected.getPlayer();
			for (int i = rowIndex + 1; i < kills.size(); i++)
			{
				Kill kill = kills.get(i);
				if (kill != null && kill.isRosterEvent() && samePlayer(player, kill.getPlayer()))
				{
					return false;
				}
				if (kill != null && kill.isLoot() && samePlayer(player, kill.getPlayer()))
				{
					return true;
				}
			}
			return false;
		}

		private int findNextRosterEvent(int rowIndex, String player, String targetType)
		{
			for (int i = rowIndex + 1; i < kills.size(); i++)
			{
				Kill candidate = kills.get(i);
				if (samePlayer(player, candidate.getPlayer()) && candidate.isRosterEvent())
				{
					return targetType.equalsIgnoreCase(candidate.getType()) ? i : -1;
				}
			}
			return -1;
		}

		private boolean samePlayer(String first, String second)
		{
			return first != null && second != null && first.equalsIgnoreCase(second);
		}

		java.util.Optional<Kill> getKillAt(int rowIndex)
		{
			return rowIndex >= 0 && rowIndex < kills.size()
				? java.util.Optional.of(kills.get(rowIndex))
				: java.util.Optional.empty();
		}

		@Override
		public int getRowCount()
		{
			return kills.size();
		}

		@Override
		public int getColumnCount()
		{
			return columns.length;
		}

		@Override
		public String getColumnName(int column)
		{
			return columns[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			Kill k = kills.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return Formats.getLocalTime().format(java.time.ZonedDateTime.ofInstant(k.getAt(), java.time.ZoneId.systemDefault()));
				case 1:
					return k.getPlayer();
				case 2:
					return k.getAmount();
				case 3:
					return k.getType() == null ? "LOOT" : k.getType();
				case DELETE_COLUMN:
					return DELETE_ICON;
			}
			return null;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return columnIndex == 1 || columnIndex == 2 || columnIndex == DELETE_COLUMN;
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			if (!sessionManager.prepareHistoryMutation())
			{
				return;
			}
			Kill k = kills.get(rowIndex);
			if (columnIndex == 1)
			{
				k.setPlayer(aValue.toString());
			}
			else if (columnIndex == 2)
			{
				try
				{
					k.setAmount(Long.parseLong(aValue.toString()));
				}
				catch (NumberFormatException ignored) {}
			}
			sessionManager.markHistoryMutation();
			controller.refreshAllView();
			fireTableCellUpdated(rowIndex, columnIndex);
		}
	}

	private class DeleteButtonRenderer implements TableCellRenderer
	{
		private final JButton button = createDeleteButton();
		private final HistoryTableModel model;

		DeleteButtonRenderer(HistoryTableModel model)
		{
			this.model = model;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			int modelRow = table.convertRowIndexToModel(row);
			button.setToolTipText(model.isPendingRosterDelete(modelRow)
				? "Click again to delete both linked roster events"
				: "Remove entry");
			return button;
		}
	}

	private class DeleteButtonEditor extends AbstractCellEditor implements TableCellEditor
	{
		private final JButton button = createDeleteButton();
		private final HistoryTableModel model;
		private int row = -1;
		private JTable table;

		DeleteButtonEditor(HistoryTableModel model)
		{
			this.model = model;
			button.addActionListener(e -> {
				int rowToDelete = row;
				JTable currentTable = table;
				fireEditingStopped();
				row = -1;
				if (rowToDelete >= 0 && rowToDelete < model.getRowCount())
				{
					if (model.isPendingRosterDelete(rowToDelete))
					{
						removeRows(model.getPendingRosterDeleteRow(), model.getPendingRosterDeleteLinkedRow());
					}
					else if (model.getKillAt(rowToDelete).map(Kill::isRosterEvent).orElse(false))
					{
						Kill selected = model.getKillAt(rowToDelete).orElse(null);
						if (selected != null && Kill.TYPE_JOINED.equalsIgnoreCase(selected.getType()))
						{
							if (model.hasAssignedSplitsInRosterPeriod(rowToDelete))
							{
								model.clearPendingRosterDelete();
								toast(PopoutView.this, "Cannot remove a join period with assigned splits.");
								return;
							}
							int linkedRow = model.findLinkedRosterEventRow(rowToDelete);
							if (linkedRow >= 0)
							{
								model.markPendingRosterDelete(rowToDelete, linkedRow);
								highlightLinkedRosterRows(currentTable, rowToDelete, linkedRow);
								return;
							}
						}
						model.clearPendingRosterDelete();
						sessionManager.removeKillAt(rowToDelete);
					}
					else
					{
						model.clearPendingRosterDelete();
						sessionManager.removeKillAt(rowToDelete);
					}
					model.refresh();
					controller.refreshAllView();
				}
			});
		}

		@Override
		public Object getCellEditorValue()
		{
			return DELETE_ICON;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			this.table = table;
			this.row = table.convertRowIndexToModel(row);
			button.setToolTipText(model.isPendingRosterDelete(this.row)
				? "Click again to delete both linked roster events"
				: "Remove entry");
			return button;
		}

		private void removeRows(int firstRow, int secondRow)
		{
			sessionManager.removeKillsAt(java.util.Arrays.asList(firstRow, secondRow));
		}

		private void highlightLinkedRosterRows(JTable table, int rowToDelete, int linkedRow)
		{
			if (table == null)
			{
				return;
			}
			int viewRow = table.convertRowIndexToView(rowToDelete);
			int linkedViewRow = table.convertRowIndexToView(linkedRow);
			table.clearSelection();
			if (viewRow >= 0)
			{
				table.addRowSelectionInterval(viewRow, viewRow);
			}
			if (linkedViewRow >= 0)
			{
				table.addRowSelectionInterval(linkedViewRow, linkedViewRow);
				table.scrollRectToVisible(table.getCellRect(linkedViewRow, 0, true));
			}
			table.repaint();
		}
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
	public void onMetricsRefreshed()
	{
		refreshGraph();
		refreshHistoryEditor();
	}

	private void refreshHistoryEditor()
	{
		if (editContainer == null) return;
		if (editContainer instanceof JPanel)
		{
			for (Component c : ((JPanel) editContainer).getComponents())
			{
				if (c instanceof JScrollPane)
				{
					JScrollPane scroll = (JScrollPane) c;
					if (scroll.getViewport().getView() instanceof JTable)
					{
						JTable table = (JTable) scroll.getViewport().getView();
						if (table.getModel() instanceof HistoryTableModel)
						{
							((HistoryTableModel) table.getModel()).refresh();
						}
					}
				}
			}
		}
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

	private class HistoryTransferHandler extends TransferHandler
	{
		private final HistoryTableModel model;

		HistoryTransferHandler(HistoryTableModel model)
		{
			this.model = model;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			JTable table = (JTable) c;
			int selectedRow = table.getSelectedRow();
			int index = selectedRow < 0 ? -1 : table.convertRowIndexToModel(selectedRow);
			return new Transferable()
			{
				@Override
				public DataFlavor[] getTransferDataFlavors()
				{
					return new DataFlavor[]{DataFlavor.stringFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor)
				{
					return DataFlavor.stringFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
				{
					return String.valueOf(index);
				}
			};
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			return support.isDataFlavorSupported(DataFlavor.stringFlavor);
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support))
			{
				return false;
			}
			try
			{
				String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
				int fromIndex = Integer.parseInt(data);
				if (fromIndex < 0)
				{
					return false;
				}
				JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
				JTable table = (JTable) support.getComponent();
				int dropRow = dl.getRow();
				int toIndex = dropRow >= table.getRowCount()
					? model.getRowCount()
					: table.convertRowIndexToModel(dropRow);

				if (fromIndex != toIndex)
				{
					if (!sessionManager.moveKill(fromIndex, toIndex))
					{
						toast(PopoutView.this, "Cannot move a left event before that player joined.");
						return false;
					}
					model.refresh();
					controller.refreshAllView();
				}
				return true;
			}
			catch (Exception e)
			{
				log.warn("Failed to move history row", e);
				return false;
			}
		}
	}
}
