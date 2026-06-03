package com.splitmanager.views;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.splitmanager.ManagerKnownPlayers;
import com.splitmanager.ManagerSession;
import com.splitmanager.PluginConfig;
import com.splitmanager.controllers.PanelActions;
import com.splitmanager.controllers.PanelController;
import com.splitmanager.models.Kill;
import com.splitmanager.models.Metrics;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.RecentSplitsTable;
import com.splitmanager.models.Session;
import com.splitmanager.models.SettlementConfigSnapshot;
import com.splitmanager.models.Transfer;
import com.splitmanager.models.WaitlistTable;
import com.splitmanager.utils.Formats;
import static com.splitmanager.utils.Formats.OsrsAmountFormatter.toSuffixString;
import com.splitmanager.utils.MarkdownFormatter;
import com.splitmanager.utils.PaymentProcessor;
import com.splitmanager.views.components.DropdownRip;
import com.splitmanager.views.components.PanelTour;
import com.splitmanager.views.components.table.RemoveButtonEditor;
import com.splitmanager.views.components.table.RemoveButtonRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.DefaultFormatterFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

/**
 * Swing-based view for the Auto Split Manager panel. Renders sections and forwards
 * user interactions to PanelActions.
 */
@Slf4j
@Getter
public class PanelView extends PluginPanel
{
	private static final long serialVersionUID = 1L;

	private static final int HEADER_ICON_SIZE = 16;
	private static final int EDIT_HEADER_ICON_SIZE = 14;
	private static final int HEADER_BUTTON_SIZE = 24;
	private static final int DIRECT_PAYMENT_NAME_MAX_LENGTH = 7;
	private static final String EDIT_ICON_PATH = "/com/splitmanager/icons/edit.svg";
	private static final String GRAPH_ICON_PATH = "/com/splitmanager/icons/graph.svg";

	protected final ManagerSession sessionManager;
	protected final PluginConfig config;
	protected final ManagerKnownPlayers playerManager;
	protected final JButton btnToggleEdit;
	protected final JButton btnPopout;
	private final JComboBox<String> knownPlayersDropdown = new JComboBox<>();
	private final JTextField newPlayerField = new JTextField();
	private final JLabel historyLabel = new JLabel("History: OFF");
	private final JFormattedTextField killAmountField = makeOsrsField();
	private final JTable metricsTable = new JTable(new Metrics());
	private final RecentSplitsTable recentSplitsModel;
	private final WaitlistTable waitlistTableModel = new WaitlistTable();
	private final JTable waitlistTable = new JTable(waitlistTableModel);
	private final JButton btnWaitlistAdd = new JButton("Add");
	private final JButton btnWaitlistDelete = new JButton("Del");
	private final JButton btnAddPlayer = new JButton("Add Player");
	private final JLabel knownListLabel = new JLabel("Known:");
	private final JLabel altsLabel = new JLabel("Known alts:");
	private final JList<String> altsList = new JList<>(new DefaultListModel<>());
	private final JComboBox<String> addAltDropdown = new JComboBox<>();
	private final JButton btnAddAlt = new JButton("Add alt");
	private final JButton btnRemoveAlt = new JButton("Remove alt");
	private final JButton btnRemovePlayer = new JButton("Remove");
	private final JButton btnAddKill = new JButton("Add");
	private final JButton btnStart = new JButton("Start");
	private final JButton btnStop = new JButton("Stop");
	private final JPanel historyContextPanel = new JPanel(new GridBagLayout());
	private final JCheckBox historyGeTaxEnabled = new JCheckBox("Account for GE tax");
	private final JTextField historyGeTaxMinimumValue = new JTextField();
	private final JTextField historyGeTaxPercent = new JTextField();
	private final JTextField historyGeTaxMaxPerLoot = new JTextField();
	private final JButton btnSaveHistoryContext = new JButton("Save");
	private final JButton btnApplyHistoryContext = new JButton("Apply view");
	private final JButton btnCancelHistoryContext = new JButton("Cancel");
	private final JComboBox<HistorySessionItem> historySessionDropdown = new JComboBox<>();
	private final JButton btnViewHistory = new JButton("View");
	private final JButton btnUnloadHistory = new JButton("Close");
	private final JButton btnExportHistory = new JButton("Export");
	private final JButton btnImportHistory = new JButton("Import clipboard");
	private final JButton btnAddToSession = new JButton("Add");
	private final JButton btnRemoveFromSession = new JButton("Remove");
	private final JComboBox<String> currentSessionPlayerDropdown = new JComboBox<>();
	private final JComboBox<String> notInCurrentSessionPlayerDropdown = new JComboBox<>();
	private final Dimension dl = new Dimension(48, 24);
	private final Dimension dm = new Dimension(64, 24);
	private final Dimension dv = new Dimension(96, 24);
	private final Insets inset = new Insets(3, 3, 3, 3);
	private final Dimension lm = new Dimension(0, 140);
	private final Dimension ll = new Dimension(0, 280);
	private final JTable recentSplitsTable;
	private final String infoIconUniCode = "\uD83D\uDEC8";
	private final JPanel metricsContentWrapper = new JPanel(new BorderLayout());
	private final PanelTour tour;
	protected PanelController controller;
	private PanelActions actions;
	private JButton btnCopyJson;
	private JButton btnCopyMd;
	private DropdownRip detectedValuesDropdown;

	public PanelView(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager, PanelController controller)
	{
		this.sessionManager = sessionManager;
		this.config = config;
		this.playerManager = playerManager;
		this.controller = controller;
		bindActions(controller);
		bindEnterSubmits();

		btnToggleEdit = createHeaderButton(loadSvgIcon(EDIT_ICON_PATH, EDIT_HEADER_ICON_SIZE), "H");
		btnToggleEdit.setToolTipText("Toggle history editor");
		btnToggleEdit.addActionListener(e -> onPencilClicked());
		btnPopout = createHeaderButton(loadSvgIcon(GRAPH_ICON_PATH, HEADER_ICON_SIZE), "P");
		btnPopout.setToolTipText("Pop out");
		btnPopout.addActionListener(e -> onPopoutClicked());

		recentSplitsModel = new RecentSplitsTable(config);
		recentSplitsModel.setListener(editedKill -> {
			if (actions != null)
			{
				SwingUtilities.invokeLater(() -> actions.refreshSharedViews());
			}
			else
			{
				// Fallback: recompute current if no controller available
				refreshMetrics();
			}
		});
		waitlistTableModel.setEditListener(() -> {
			if (actions != null)
			{
				SwingUtilities.invokeLater(() -> actions.refreshSharedViews());
			}
		});
		recentSplitsTable = makeRecentSplitsTable(recentSplitsModel);
		tour = new PanelTour(config, () -> actions, new PanelTour.Targets()
		{
			@Override
			public JComponent newPlayerField()
			{
				return newPlayerField;
			}

			@Override
			public JComponent addAltDropdown()
			{
				return addAltDropdown;
			}

			@Override
			public JComponent startButton()
			{
				return btnStart;
			}

			@Override
			public JComponent notInSessionDropdown()
			{
				return notInCurrentSessionPlayerDropdown;
			}

			@Override
			public JComponent currentSessionDropdown()
			{
				return currentSessionPlayerDropdown;
			}

			@Override
			public JComponent metricsTable()
			{
				return metricsTable;
			}

			@Override
			public JComponent copyMarkdownButton()
			{
				return btnCopyMd;
			}

			@Override
			public JComponent detectedValuesDropdown()
			{
				return detectedValuesDropdown;
			}

			@Override
			public JComponent recentSplitsTable()
			{
				return recentSplitsTable;
			}

			@Override
			public JComponent stopButton()
			{
				return btnStop;
			}
		});

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		top.add(tour.getPanel());

		top.add(generateSessionPanel());
		top.add(Box.createVerticalStrut(3));
		top.add(generateHistoryContextPanel());
		top.add(Box.createVerticalStrut(3));
		top.add(generateSessionPlayerManagement());
		top.add(Box.createVerticalStrut(3));
		top.add(generateAddSplit());
		top.add(Box.createVerticalStrut(3));
		top.add(generateRecentSplitsPanel());
		top.add(Box.createVerticalStrut(3));
		top.add(generateWaitlistPanelCollapsible());
		top.add(Box.createVerticalStrut(3));
		top.add(generateMetrics());
		top.add(Box.createVerticalStrut(3));
		top.add(generateKnownPlayersManagement());
		top.add(Box.createVerticalStrut(3));
		top.add(generateHistoryPanel());
		top.add(Box.createVerticalStrut(3));

		add(top, BorderLayout.NORTH);
	}

	private static JButton createHeaderButton(Icon icon, String fallbackText)
	{
		JButton button = icon == null ? new JButton(fallbackText) : new JButton(icon);
		button.setForeground(Color.WHITE);
		Dimension size = new Dimension(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
		button.setPreferredSize(size);
		button.setMinimumSize(size);
		button.setMaximumSize(size);
		button.setBorder(new EmptyBorder(0, 0, 0, 0));
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setIconTextGap(0);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		SwingUtil.removeButtonDecorations(button);
		return button;
	}

	private static Icon loadSvgIcon(String iconPath, int iconSize)
	{
		java.net.URL resource = PanelView.class.getResource(iconPath);
		if (resource == null)
		{
			log.warn("Missing SVG icon resource {}", iconPath);
			return null;
		}

		return new FlatSVGIcon(resource).derive(iconSize, iconSize);
	}

	private static String shortenDirectPaymentName(String name)
	{
		if (name == null)
		{
			return "";
		}
		String n = name.trim();
		return n.length() <= DIRECT_PAYMENT_NAME_MAX_LENGTH ? n : n.substring(0, DIRECT_PAYMENT_NAME_MAX_LENGTH);
	}

	public void bindActions(PanelActions actions)
	{
		this.actions = actions;

		btnStart.addActionListener(e -> actions.startSession());
		btnStop.addActionListener(e -> actions.stopSession());

		btnAddToSession.addActionListener(e ->
			actions.addPlayerToSession((String) notInCurrentSessionPlayerDropdown.getSelectedItem()));

		btnAddPlayer.addActionListener(e ->
			actions.addKnownPlayer(newPlayerField.getText()));
		btnRemovePlayer.addActionListener(e ->
			actions.removeKnownPlayer((String) knownPlayersDropdown.getSelectedItem()));
		knownPlayersDropdown.addItemListener(e -> {
			if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED)
			{
				actions.onKnownPlayerSelectionChanged((String) e.getItem());
			}
		});

		btnAddAlt.addActionListener(e ->
			actions.addAltToMain((String) knownPlayersDropdown.getSelectedItem(),
				(String) addAltDropdown.getSelectedItem()));
		btnRemoveAlt.addActionListener(e ->
			actions.removeSelectedAlt((String) knownPlayersDropdown.getSelectedItem(),
				altsList.getSelectedValue()));

		btnAddKill.addActionListener(e -> actions.addKillFromInputs());

		btnWaitlistAdd.addActionListener(e -> actions.applySelectedPendingValue(waitlistTable.getSelectedRow()));
		btnWaitlistDelete.addActionListener(e -> actions.deleteSelectedPendingValue(waitlistTable.getSelectedRow()));
		btnViewHistory.addActionListener(e -> actions.loadHistory(getSelectedHistorySessionId()));
		btnUnloadHistory.addActionListener(e -> actions.unloadHistory());
		btnExportHistory.addActionListener(e -> actions.exportHistory(getSelectedHistorySessionId()));
		btnImportHistory.addActionListener(e -> actions.importHistoryFromClipboard());
		btnSaveHistoryContext.addActionListener(e ->
			actions.saveHistorySettlementContext(getHistoryContextSnapshotFromInputs()));
		btnApplyHistoryContext.addActionListener(e ->
			actions.applyHistorySettlementContext(getHistoryContextSnapshotFromInputs()));
		btnCancelHistoryContext.addActionListener(e -> actions.cancelHistorySettlementContextEdit());
		waitlistTable.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (e.getClickCount() == 2 && waitlistTable.getSelectedRow() != -1)
				{
					if (e.isControlDown() && e.isShiftDown())
					{
						actions.deleteSelectedPendingValue(waitlistTable.getSelectedRow());
					}
					else if (e.isControlDown())
					{
						actions.applySelectedPendingValue(waitlistTable.getSelectedRow());
					}
				}
			}
		});
	}

	private void bindEnterSubmits()
	{
		newPlayerField.addActionListener(e -> clickIfEnabled(btnAddPlayer));
		killAmountField.addActionListener(e -> {
			if (commitField(killAmountField))
			{
				clickIfEnabled(btnAddKill);
			}
		});

		bindEnterToButton(notInCurrentSessionPlayerDropdown, btnAddToSession);
		bindEnterToButton(currentSessionPlayerDropdown, btnAddKill);
		bindEnterToButton(addAltDropdown, btnAddAlt);
	}

	private void bindEnterToButton(JComponent component, JButton button)
	{
		component.getInputMap(JComponent.WHEN_FOCUSED)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit-form");
		component.getActionMap().put("submit-form", new AbstractAction()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				clickIfEnabled(button);
			}
		});
	}

	private void clickIfEnabled(JButton button)
	{
		if (button != null && button.isEnabled())
		{
			button.doClick();
		}
	}

	private boolean commitField(JFormattedTextField field)
	{
		try
		{
			field.commitEdit();
			return true;
		}
		catch (ParseException e)
		{
			log.warn("Invalid field value {}", field.getText(), e);
			return false;
		}
	}

	private JFormattedTextField makeOsrsField()
	{
		JFormattedTextField f = new JFormattedTextField(
			new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		f.setColumns(14);
		f.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		f.setToolTipText("Enter amount like 10k, 1.1m, or 1b (K = thousands)");
		return f;
	}

	public void startTour()
	{
		tour.startTour();
	}

	public void endTour()
	{
		tour.endTour();
	}

	public void nextTourStep()
	{
		tour.nextStep();
	}

	public void prevTourStep()
	{
		tour.previousStep();
	}

	public void setHistorySessions(List<Session> sessions, String selectedSessionId)
	{
		String selected = selectedSessionId;
		if (selected == null)
		{
			selected = getSelectedHistorySessionId();
		}

		DefaultComboBoxModel<HistorySessionItem> model = new DefaultComboBoxModel<>();
		if (sessions != null)
		{
			for (Session session : sessions)
			{
				model.addElement(HistorySessionItem.from(session));
			}
		}
		historySessionDropdown.setModel(model);

		if (selected == null)
		{
			return;
		}
		for (int i = 0; i < model.getSize(); i++)
		{
			HistorySessionItem item = model.getElementAt(i);
			if (selected.equals(item.getSessionId()))
			{
				historySessionDropdown.setSelectedIndex(i);
				return;
			}
		}
	}

	public String getSelectedHistorySessionId()
	{
		Object selected = historySessionDropdown.getSelectedItem();
		if (selected instanceof HistorySessionItem)
		{
			return ((HistorySessionItem) selected).getSessionId();
		}
		return null;
	}

	private JTable makeRecentSplitsTable(RecentSplitsTable model)
	{
		JTable t = new JTable(model);
		// ... existing table setup ...

		// Right align Amount renderer
		javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		t.getColumnModel().getColumn(2).setCellRenderer(right);
		javax.swing.table.DefaultTableCellRenderer center = new javax.swing.table.DefaultTableCellRenderer();
		center.setHorizontalAlignment(SwingConstants.CENTER);
		t.getColumnModel().getColumn(3).setCellRenderer(center);
		t.getColumnModel().getColumn(3).setMaxWidth(36);

		// Row-aware player editor
		DefaultCellEditor playerEditor = getDefaultCellEditor();
		t.getColumnModel().getColumn(1).setCellEditor(playerEditor);

		// Amount editor (existing)
		JFormattedTextField amtField = new JFormattedTextField(new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		amtField.setBorder(null);
		DefaultCellEditor amtEditor = new DefaultCellEditor(amtField);
		t.getColumnModel().getColumn(2).setCellEditor(amtEditor);

		return t;
	}

	@Nonnull
	private DefaultCellEditor getDefaultCellEditor()
	{
		final JComboBox<String> playerCombo = new JComboBox<>();
		return new DefaultCellEditor(playerCombo)
		{

			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected, int row, int column)
			{
				// Determine players for this row's session
				String[] choices;
				Kill k = ((RecentSplitsTable) table.getModel()).getKillAt(row);
				java.util.Set<String> playersForRow = new java.util.LinkedHashSet<>();
				if (k != null && k.getSessionId() != null)
				{
					Session s = sessionManager.getAllSessionsNewestFirst().stream()
						.filter(ss -> k.getSessionId().equals(ss.getId()))
						.findFirst().orElse(null);
					if (s != null && s.getPlayers() != null && !s.getPlayers().isEmpty())
					{
						playersForRow.addAll(s.getPlayers());
					}
				}
				if (playersForRow.isEmpty())
				{
					playersForRow.addAll(playerManager.getKnownMains());
				}
				choices = playersForRow.toArray(new String[0]);

				playerCombo.setModel(new DefaultComboBoxModel<>(choices));
				playerCombo.setSelectedItem(value);
				return playerCombo;
			}
		};
	}

	private JPanel generateAddSplit()
	{
		JPanel killPanel = new JPanel();
		killPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("Add split to session:"),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		killPanel.setLayout(new GridBagLayout());

		GridBagLayout gbl1 = (GridBagLayout) killPanel.getLayout();
		gbl1.columnWidths = new int[]{dm.width, 0};     // col0 fixed, col1 auto
		gbl1.columnWeights = new double[]{0.0, 1.0};    // col0 no grow, col1 grows

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = inset;

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		gbc.anchor = GridBagConstraints.EAST;

		JLabel apLabel = new JLabel("Player:");
		apLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		killPanel.add(apLabel, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		killPanel.add(currentSessionPlayerDropdown, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0;

		JLabel amountLabel = new JLabel("Amount:");
		amountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		killPanel.add(amountLabel, gbc);

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		killPanel.add(killAmountField, gbc);

		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.NONE;
		btnAddKill.setPreferredSize(dv);
		btnAddKill.setMinimumSize(dv);
		killPanel.add(btnAddKill, gbc);

		return killPanel;
	}

	private JPanel generateSessionPlayerManagement()
	{
		JPanel rosterPanel = new JPanel();
		rosterPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("Add players to session:"),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		rosterPanel.setLayout(new GridBagLayout());

		GridBagLayout gbl1 = (GridBagLayout) rosterPanel.getLayout();
		gbl1.columnWidths = new int[]{dm.width, 0};     // col0 fixed, col1 auto
		gbl1.columnWeights = new double[]{0.0, 1.0};

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = inset;

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		String[] Players = sessionManager.getNonActivePlayers().toArray(new String[0]);
		notInCurrentSessionPlayerDropdown.setModel(new DefaultComboBoxModel<>(Players));
		// Add dropdown with info (i) icon and tooltip about right-clicking names in chat/clan
		JPanel addToSessionRow = new JPanel(new BorderLayout(6, 0));
		addToSessionRow.add(notInCurrentSessionPlayerDropdown, BorderLayout.CENTER);
		JLabel addToSessionInfo = new JLabel(infoIconUniCode); // info symbol
		addToSessionInfo.setToolTipText("Tip: You can also right-click a player's name in Chat or Clan Chat to add/remove them to the session.");
		addToSessionInfo.setForeground(Color.GRAY);
		addToSessionRow.add(addToSessionInfo, BorderLayout.EAST);
		rosterPanel.add(addToSessionRow, gbc);

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;


		JLabel apLabel = new JLabel("Player:");
		apLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rosterPanel.add(apLabel, gbc);

		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.NONE;
		btnAddToSession.setPreferredSize(dv);
		rosterPanel.add(btnAddToSession, gbc);

		return rosterPanel;
	}

	private JPanel generateKnownPlayersManagement()
	{
		JPanel PlayersPanel = new JPanel();
		PlayersPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = inset;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		JLabel title = new JLabel("Edit the known players list:");
		title.setFont(title.getFont().deriveFont(Font.BOLD));

		newPlayerField.setColumns(14);
		newPlayerField.setPreferredSize(dv);
		knownPlayersDropdown.setPreferredSize(dv);
		addAltDropdown.setPreferredSize(dv);

		int row = 0;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		PlayersPanel.add(title, gbc);
		row++;

		JLabel nameLabel = new JLabel("Name (Case sensitive):");
		nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		gbc.gridwidth = 1;
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.EAST;
		PlayersPanel.add(nameLabel, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 1.0;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		newPlayerField.setToolTipText("Note: OSRS player names are case-sensitive. Add with exact casing.");
		JPanel addKnownPlayerRow = new JPanel(new BorderLayout(6, 0));
		addKnownPlayerRow.add(newPlayerField, BorderLayout.CENTER);
		JLabel addKnownPlayerInfo = new JLabel(infoIconUniCode); // info symbol
		addKnownPlayerInfo.setToolTipText("Tip: You can also right-click a player's name in Chat or Clan Chat to add/remove them to Known Players.");
		addKnownPlayerInfo.setForeground(Color.GRAY);
		addKnownPlayerRow.add(addKnownPlayerInfo, BorderLayout.EAST);
		PlayersPanel.add(addKnownPlayerRow, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.NONE;
		btnAddPlayer.setPreferredSize(dv);
		btnAddPlayer.setMinimumSize(dv);
		PlayersPanel.add(btnAddPlayer, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.CENTER;
		JSeparator sep0 = new JSeparator(SwingConstants.HORIZONTAL);
		sep0.setMinimumSize(new Dimension(0, 5));
		PlayersPanel.add(sep0, gbc);
		row++;

		JLabel alterLbl = new JLabel("Alter player info:");
		alterLbl.setHorizontalAlignment(SwingConstants.LEFT);
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		PlayersPanel.add(alterLbl, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		PlayersPanel.add(knownPlayersDropdown, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.NONE;
		btnRemovePlayer.setPreferredSize(dv);
		btnRemovePlayer.setMinimumSize(dv);
		PlayersPanel.add(btnRemovePlayer, gbc);
		row++;

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.CENTER;
		JSeparator sep1 = new JSeparator(SwingConstants.HORIZONTAL);
		sep1.setMinimumSize(new Dimension(0, 5));
		PlayersPanel.add(sep1, gbc);
		row++;

		altsLabel.setHorizontalAlignment(SwingConstants.LEFT);
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		PlayersPanel.add(altsLabel, gbc);
		row++;

		JScrollPane altsScroll = new JScrollPane(altsList);
		altsScroll.setPreferredSize(lm);
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.anchor = GridBagConstraints.CENTER;
		PlayersPanel.add(altsScroll, gbc);
		row++;

		JLabel addAltLbl = new JLabel("Add alt:");
		addAltLbl.setPreferredSize(dl);
		addAltLbl.setHorizontalAlignment(SwingConstants.RIGHT);

		gbc.gridwidth = 1;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.EAST;
		PlayersPanel.add(addAltLbl, gbc);

		gbc.gridx = 1;
		gbc.gridy = row;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		PlayersPanel.add(addAltDropdown, gbc);
		row++;

		JPanel altButtonsRow = new JPanel(new GridLayout(1, 2, 6, 0));
		altButtonsRow.add(btnAddAlt);
		altButtonsRow.add(btnRemoveAlt);

		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.CENTER;
		PlayersPanel.add(altButtonsRow, gbc);

		return new DropdownRip("Known player info", PlayersPanel, config.enableTour());
	}

	private JPanel generateSessionPanel()
	{
		JPanel sessionPanel = new JPanel();
		sessionPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("Session"),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		sessionPanel.setLayout(new GridBagLayout());
		GridBagConstraints g2 = new GridBagConstraints();
		g2.gridx = 0;
		g2.insets = new Insets(3, 3, 3, 3);
		g2.weightx = 1.0;
		g2.fill = GridBagConstraints.HORIZONTAL;
		g2.anchor = GridBagConstraints.CENTER;

		JPanel buttonsHalfHalf = new JPanel(new GridLayout(1, 2, 6, 0));
		buttonsHalfHalf.add(btnStart);
		buttonsHalfHalf.add(btnStop);

		historyLabel.setHorizontalAlignment(SwingConstants.CENTER);
		g2.gridy = 0;
		sessionPanel.add(historyLabel, g2);

		g2.gridy = 1;
		sessionPanel.add(buttonsHalfHalf, g2);

		return sessionPanel;
	}

	private JPanel generateHistoryContextPanel()
	{
		JPanel header = new JPanel(new BorderLayout(4, 2));
		header.setOpaque(false);
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		titleRow.setOpaque(false);
		JLabel title = new JLabel("History context");
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		titleRow.add(title);
		header.add(titleRow, BorderLayout.NORTH);

		JTextArea infoText = new JTextArea("This box stores the context saved with the loaded history. "
			+ "Use Apply to refresh the live view with the current fields, Save to overwrite the saved history context, "
			+ "and Cancel to reload the stored values back into the fields.");
		infoText.setEditable(false);
		infoText.setOpaque(false);
		infoText.setLineWrap(true);
		infoText.setWrapStyleWord(true);
		infoText.setFocusable(false);
		infoText.setBorder(null);
		header.add(infoText, BorderLayout.CENTER);

		historyContextPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("History context"),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 3, 2, 3);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 2;
		historyContextPanel.add(header, gbc);

		JPanel taxSection = new JPanel(new GridBagLayout());
		taxSection.setOpaque(false);
		taxSection.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("Tax"),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));

		GridBagConstraints taxGbc = new GridBagConstraints();
		taxGbc.insets = new Insets(2, 3, 2, 3);
		taxGbc.fill = GridBagConstraints.HORIZONTAL;
		taxGbc.weightx = 1.0;
		taxGbc.gridx = 0;
		taxGbc.gridy = 0;
		taxGbc.gridwidth = 2;
		taxSection.add(historyGeTaxEnabled, taxGbc);

		taxGbc.gridwidth = 1;
		taxGbc.gridy++;
		taxGbc.gridx = 0;
		taxGbc.weightx = 0.0;
		taxSection.add(new JLabel("Min"), taxGbc);
		taxGbc.gridx = 1;
		taxGbc.weightx = 1.0;
		taxSection.add(historyGeTaxMinimumValue, taxGbc);

		taxGbc.gridy++;
		taxGbc.gridx = 0;
		taxGbc.weightx = 0.0;
		taxSection.add(new JLabel("Percent"), taxGbc);
		taxGbc.gridx = 1;
		taxGbc.weightx = 1.0;
		taxSection.add(historyGeTaxPercent, taxGbc);

		taxGbc.gridy++;
		taxGbc.gridx = 0;
		taxGbc.weightx = 0.0;
		taxSection.add(new JLabel("Cap"), taxGbc);
		taxGbc.gridx = 1;
		taxGbc.weightx = 1.0;
		taxSection.add(historyGeTaxMaxPerLoot, taxGbc);

		gbc.gridy++;
		historyContextPanel.add(taxSection, gbc);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.add(btnCancelHistoryContext);
		buttons.add(btnApplyHistoryContext);
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		historyContextPanel.add(buttons, gbc);

		gbc.gridy++;
		historyContextPanel.add(btnSaveHistoryContext, gbc);

		historyContextPanel.setVisible(false);
		return historyContextPanel;
	}

	public void setHistoryContextVisible(boolean visible)
	{
		historyContextPanel.setVisible(visible);
		historyContextPanel.revalidate();
		historyContextPanel.repaint();
	}

	public void setHistoryContextSnapshot(SettlementConfigSnapshot snapshot)
	{
		if (snapshot == null)
		{
			snapshot = new SettlementConfigSnapshot(false,
				PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE,
				PluginConfig.DEFAULT_GE_TAX_PERCENT,
				PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE);
		}
		historyGeTaxEnabled.setSelected(snapshot.isAccountForGeTax());
		historyGeTaxMinimumValue.setText(snapshot.getGeTaxMinimumValue());
		historyGeTaxPercent.setText(Double.toString(snapshot.getGeTaxPercent()));
		historyGeTaxMaxPerLoot.setText(snapshot.getGeTaxMaxPerLoot());
	}

	private SettlementConfigSnapshot getHistoryContextSnapshotFromInputs()
	{
		double percent;
		try
		{
			percent = Double.parseDouble(historyGeTaxPercent.getText().trim());
		}
		catch (NumberFormatException e)
		{
			percent = Double.NaN;
		}
		return new SettlementConfigSnapshot(
			historyGeTaxEnabled.isSelected(),
			historyGeTaxMinimumValue.getText().trim(),
			percent,
			historyGeTaxMaxPerLoot.getText().trim());
	}

	private JPanel generateWaitlistPanel()
	{
		JPanel p = new JPanel();
		p.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		waitlistTable.setFillsViewportHeight(true);
		waitlistTable.setRowHeight(22);
		waitlistTable.setToolTipText("Ctrl + Double-click a row to accept the pending value");
		waitlistTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Align Value column to the right
		javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		if (waitlistTable.getColumnModel().getColumnCount() > 1)
		{
			waitlistTable.getColumnModel().getColumn(1).setCellRenderer(right);
		}

		// Editor for Player column (use known players list)
		DefaultCellEditor wlPlayerEditor = getCellEditor();
		if (waitlistTable.getColumnModel().getColumnCount() > 2)
		{
			waitlistTable.getColumnModel().getColumn(2).setCellEditor(wlPlayerEditor);
		}

		// Editor for Value column (OSRS amount)
		JFormattedTextField wlAmtField = new JFormattedTextField(new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		wlAmtField.setBorder(null);
		DefaultCellEditor wlAmtEditor = new DefaultCellEditor(wlAmtField);
		if (waitlistTable.getColumnModel().getColumnCount() > 1)
		{
			waitlistTable.getColumnModel().getColumn(1).setCellEditor(wlAmtEditor);
		}

		JScrollPane sc = new JScrollPane(waitlistTable);
		sc.setPreferredSize(lm);
		p.add(sc, gbc);

		JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
		btns.add(btnWaitlistAdd);
		btns.add(btnWaitlistDelete);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(3, 0, 0, 0);
		p.add(btns, gbc);

		waitlistTableModel.addTableModelListener(e -> {
			if (waitlistTable.getRowCount() > 0)
			{
				waitlistTable.getSelectionModel().setSelectionInterval(0, 0);
			}
			else
			{
				waitlistTable.clearSelection();
			}
		});
		if (waitlistTable.getRowCount() > 0)
		{
			waitlistTable.getSelectionModel().setSelectionInterval(0, 0);
		}
		return p;
	}

	@Nonnull
	private DefaultCellEditor getCellEditor()
	{
		final JComboBox<String> wlPlayerCombo = new JComboBox<>();
		return new DefaultCellEditor(wlPlayerCombo)
		{

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
			{
				wlPlayerCombo.setModel(new DefaultComboBoxModel<>(playerManager.getKnownMains().toArray(new String[0])));
				wlPlayerCombo.setSelectedItem(value);
				return wlPlayerCombo;
			}
		};
	}

	private JComponent generateWaitlistPanelCollapsible()
	{
		JPanel content = new JPanel(new BorderLayout());
		content.add(generateWaitlistPanel(), BorderLayout.CENTER);
		String tooltip = "'!add' in chat queues here. \n" +
			" - Edit player and amount by double clicking the respective field. \n" +
			" - Ctrl+Double-click a row/field to add. \n" +
			" - Ctrl+Shift+Double-click a row/field to remove.";
		detectedValuesDropdown = new DropdownRip("Detected values", content, config.enableTour(), tooltip);
		return detectedValuesDropdown;
	}

	private JComponent generateRecentSplitsPanel()
	{
		JScrollPane scroller = new JScrollPane(recentSplitsTable);
		scroller.setPreferredSize(new Dimension(0, 140));
		String tooltip = " Tip: You can edit 'Player'* and 'Amount' by double clicking the respective field.\n" +
			" *Do to limitations you can only change players to the already participating players.";

		JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		extraButtons.setOpaque(false);
		extraButtons.add(btnToggleEdit);

		return new DropdownRip("Recent splits", scroller, config.enableTour(), tooltip, extraButtons);
	}

	protected void onPencilClicked()
	{
		if (actions != null)
		{
			actions.togglePopout(true);
		}
	}

	protected void onPopoutClicked()
	{
		if (actions != null)
		{
			actions.togglePopout(false);
		}
	}

	private JComponent generateHistoryPanel()
	{
		JPanel historyPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = inset;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		JLabel sessionLabel = new JLabel("Session:");
		sessionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		historyPanel.add(sessionLabel, gbc);

		historySessionDropdown.setPrototypeDisplayValue(new HistorySessionItem("", "14-Jan 15:00 - 8.5h long"));
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		historyPanel.add(historySessionDropdown, gbc);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 6));
		buttons.add(btnViewHistory);
		buttons.add(btnUnloadHistory);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		historyPanel.add(buttons, gbc);

		gbc.gridy = 2;
		historyPanel.add(btnExportHistory, gbc);

		gbc.gridy = 3;
		historyPanel.add(btnImportHistory, gbc);

		return new DropdownRip("View history", historyPanel, false,
			"Load a stopped session. Close history to start a new session.");
	}

	private JComponent generateMetrics()
	{
		JPanel wrapper = new JPanel(new BorderLayout(0, 6));
		wrapper.add(generateMetricsHeader(), BorderLayout.NORTH);

		configureMetricsTable();
		refreshMetricsContent();

		wrapper.add(metricsContentWrapper, BorderLayout.CENTER);

		JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
		btns.add(btnCopyJson);
		btns.add(btnCopyMd);
		wrapper.add(btns, BorderLayout.SOUTH);

		JPanel extraButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		extraButtons.setOpaque(false);
		extraButtons.add(btnPopout);

		return new DropdownRip("Settlement information", wrapper, true, null, extraButtons);
	}

	public void refreshMetricsContent()
	{
		metricsContentWrapper.removeAll();

		JComponent centerContent = config.directPayments()
			? generateDirectPaymentsContent()
			: new JScrollPane(metricsTable);

		if (centerContent instanceof JScrollPane)
		{
			centerContent.setPreferredSize(ll);
		}

		metricsContentWrapper.add(centerContent, BorderLayout.CENTER);
		metricsContentWrapper.revalidate();
		metricsContentWrapper.repaint();
	}

	private JPanel generateMetricsHeader()
	{
		JLabel title = new JLabel("Settlement");
		title.setFont(title.getFont().deriveFont(Font.BOLD));

		boolean direct = config.directPayments();
		String explanation = direct
			? "Direct payments mode: negatives pay positives directly. We'll suggest who pays whom below."
			: (config.flipSettlementSign()
			   ? "Middleman mode (flipped): positive Split means you pay the bank; negative means the bank pays you."
			   : "Middleman mode: negative Split means you pay the bank; positive means the bank pays you.");
		JTextArea desc = new JTextArea(explanation);
		desc.setEditable(false);
		desc.setLineWrap(true);
		desc.setWrapStyleWord(true);

		JPanel header = new JPanel(new BorderLayout());
		header.add(title, BorderLayout.NORTH);
		header.add(desc, BorderLayout.CENTER);

		btnCopyJson = new JButton("Copy JSON");
		btnCopyJson.addActionListener(e -> {
			if (actions != null)
			{
				actions.copyMetricsJson();
			}
			else
			{
				copyMetricsJsonToClipboard();
			}
		});
		btnCopyMd = new JButton("Copy MD");
		btnCopyMd.addActionListener(e -> {
			if (actions != null)
			{
				actions.copyMetricsMarkdown();
			}
			else
			{
				copyMetricsMarkdownToClipboard();
			}
		});

		return header;
	}

	private void configureMetricsTable()
	{
		metricsTable.setFillsViewportHeight(true);
		metricsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

		((Metrics) metricsTable.getModel()).setHideTotalColumn(true);
		refreshMetrics();

		int colCount = metricsTable.getColumnModel().getColumnCount();
		if (colCount > 0)
		{
			int actionColViewIndex = colCount - 1;
			int nonActionCols = Math.max(1, colCount - 1);
			int tableWidth = Math.max(metricsTable.getWidth(), 1);
			int equalWidth = (int) (tableWidth * (1.0 / nonActionCols));
			for (int i = 0; i < colCount; i++)
			{
				if (i == actionColViewIndex)
				{
					continue;
				}
				metricsTable.getColumnModel().getColumn(i).setPreferredWidth(equalWidth);
			}
			metricsTable.getColumnModel().getColumn(actionColViewIndex).setMaxWidth(40);
			metricsTable.getColumnModel().getColumn(actionColViewIndex).setMinWidth(40);
			metricsTable.getColumnModel().getColumn(actionColViewIndex).setPreferredWidth(40);
		}

		DefaultTableCellRenderer greyingRenderer = new DefaultTableCellRenderer()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
			                                                        boolean isSelected, boolean hasFocus,
			                                                        int row, int column)
			{
				java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				Metrics model = (Metrics) table.getModel();
				boolean active = model.isRowActive(row);
				if (!isSelected)
				{
					c.setForeground(active ? table.getForeground() : java.awt.Color.GRAY);
				}
				return c;
			}
		};
		int playerIdx = findMetricsColumnIndex("Player");
		if (playerIdx >= 0)
		{
			metricsTable.getColumnModel().getColumn(playerIdx).setCellRenderer(greyingRenderer);
		}
		int totalIdx = findMetricsColumnIndex("Total");
		if (totalIdx >= 0)
		{
			metricsTable.getColumnModel().getColumn(totalIdx).setCellRenderer(greyingRenderer);
		}

		DefaultTableCellRenderer splitRenderer = new DefaultTableCellRenderer()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
			                                                        boolean isSelected, boolean hasFocus,
			                                                        int row, int column)
			{
				Metrics model = (Metrics) table.getModel();
				boolean active = model.isRowActive(row);
				long raw = model.getRawSplitAt(row);
				long disp = raw;
				if (!config.directPayments() && config.flipSettlementSign())
				{
					disp = -raw;
				}
				java.awt.Component c = super.getTableCellRendererComponent(table,
					Formats.OsrsAmountFormatter.toSuffixString(disp, 'k'), isSelected, hasFocus, row, column);
				if (!isSelected)
				{
					c.setForeground(active ? table.getForeground() : java.awt.Color.GRAY);
				}
				setHorizontalAlignment(SwingConstants.RIGHT);
				return c;
			}
		};
		int splitIdx = findMetricsColumnIndex("Split");
		if (splitIdx >= 0)
		{
			metricsTable.getColumnModel().getColumn(splitIdx).setCellRenderer(splitRenderer);
		}

		int actionIdx = findMetricsColumnIndex("X");
		if (actionIdx >= 0)
		{
			metricsTable.getColumnModel().getColumn(actionIdx)
				.setCellRenderer(new RemoveButtonRenderer());
			metricsTable.getColumnModel().getColumn(actionIdx)
				.setCellEditor(new RemoveButtonEditor(this, sessionManager, metricsTable, actions));
		}
	}

	private int findMetricsColumnIndex(String name)
	{
		for (int i = 0; i < metricsTable.getColumnModel().getColumnCount(); i++)
		{
			Object header = metricsTable.getColumnModel().getColumn(i).getHeaderValue();
			if (name.equals(header))
			{
				return i;
			}
		}
		return -1;
	}

	private JComponent generateDirectPaymentsContent()
	{
		Session currentSession = getMetricsSession();
		List<PlayerMetrics> data = sessionManager.computeMetricsFor(currentSession, true);
		List<Transfer> transfers = PaymentProcessor.computeDirectPaymentsStructured(data);

		if (!transfers.isEmpty())
		{
			javax.swing.table.DefaultTableModel txModel =
				new javax.swing.table.DefaultTableModel(new Object[]{"Suggested direct payments"}, 0)
				{
					private static final long serialVersionUID = 1L;

					@Override
					public boolean isCellEditable(int r, int c)
					{
						return false;
					}
				};

			for (Transfer t : transfers)
			{
				String payerShort = shortenDirectPaymentName(t.getFrom());
				String payeeShort = shortenDirectPaymentName(t.getTo());
				String amountStr = toSuffixString(Math.abs(t.getAmount()), config.defaultValueMultiplier().getValue());
				String display = payerShort + " -> " + payeeShort + ": " + amountStr;
				txModel.addRow(new Object[]{display});
			}

			JTable txTable = new JTable(txModel);
			txTable.setFillsViewportHeight(true);
			txTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
			txTable.setRowSelectionAllowed(false);
			txTable.setShowGrid(false);

			return new JScrollPane(txTable);
		}

		return new JScrollPane(metricsTable);
	}

	public void refreshMetrics()
	{
		Session currentSession = getMetricsSession();
		((Metrics) metricsTable.getModel()).setData(sessionManager.computeMetricsFor(currentSession, true));
		refreshMetricsContent();
		onMetricsRefreshed();
	}

	protected void onMetricsRefreshed()
	{
	}

	private void copyMetricsJsonToClipboard()
	{
		String payload = MarkdownFormatter.buildMetricsJson(sessionManager);
		StringSelection selection = new StringSelection(payload);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}

	private void copyMetricsMarkdownToClipboard()
	{
		String payload = MarkdownFormatter.buildMetricsMarkdown(
			sessionManager.computeMetricsFor(getMetricsSession(), true), config);
		StringSelection selection = new StringSelection(payload);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}

	private Session getMetricsSession()
	{
		if (sessionManager.isHistoryLoaded())
		{
			return sessionManager.getCurrentEditableSession().orElse(sessionManager.getCurrentSession().orElse(null));
		}
		return sessionManager.getCurrentSession().orElse(null);
	}

	private static final class HistorySessionItem
	{
		private final String sessionId;
		private final String label;

		private HistorySessionItem(String sessionId, String label)
		{
			this.sessionId = sessionId;
			this.label = label;
		}

		private static HistorySessionItem from(Session session)
		{
			if (session == null)
			{
				return new HistorySessionItem("", "");
			}
			Instant start = session.getStart();
			String label = start == null
				? "Unknown start"
				: Formats.getLocalDate().format(start) + " " + Formats.getLocalTime().format(start);
			if (session.getEnd() != null)
			{
				label += " - " + formatDuration(start, session.getEnd()) + " long";
			}
			return new HistorySessionItem(session.getId(), label);
		}

		private static String formatDuration(Instant start, Instant end)
		{
			if (start == null || end == null)
			{
				return "unknown";
			}
			long seconds = Math.max(0L, Duration.between(start, end).getSeconds());
			double hours = seconds / 3600.0d;
			String value = String.format(Locale.ENGLISH, "%.1f", hours);
			if (value.endsWith(".0"))
			{
				value = value.substring(0, value.length() - 2);
			}
			return value + "h";
		}

		private String getSessionId()
		{
			return sessionId;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
