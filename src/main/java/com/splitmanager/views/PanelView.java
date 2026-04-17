package com.splitmanager.views;

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
import com.splitmanager.models.Transfer;
import com.splitmanager.models.WaitlistTable;
import com.splitmanager.utils.Formats;
import static com.splitmanager.utils.Formats.OsrsAmountFormatter.toSuffixString;
import com.splitmanager.utils.MarkdownFormatter;
import com.splitmanager.utils.PaymentProcessor;
import com.splitmanager.views.components.DropdownRip;
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
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.DefaultFormatterFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

@Slf4j
@Getter
/**
 * Swing-based view for the Auto Split Manager panel. Renders sections and forwards
 * user interactions to PanelActions.
 */
public class PanelView extends PluginPanel
{
	private final JPanel activePlayersButtonsPanel = new JPanel(new GridLayout(0, 1, 0, 2));
	private final ManagerSession sessionManager;
	private final PluginConfig config;
	private final ManagerKnownPlayers playerManager;
	private final JComboBox<String> knownPlayersDropdown = new JComboBox<>();
	private final JTextField newPlayerField = new JTextField();
	private final JLabel historyLabel = new JLabel("History: OFF");
	private final JFormattedTextField killAmountField = makeOsrsField();
	private final JFormattedTextField activeKillAmountField = makeOsrsField(); // TODO Remove this?
	private final JTable metricsTable = new JTable(new Metrics());
	private final RecentSplitsTable recentSplitsModel;
	private final WaitlistTable waitlistTableModel = new WaitlistTable();
	private final JTable waitlistTable = new JTable(waitlistTableModel);
	private final JButton btnWaitlistAdd = new JButton("Add");
	private final JButton btnWaitlistDelete = new JButton("Del");
	private final JButton btnAddPlayer = new JButton("Add Player");
	private final JLabel knownListLabel = new JLabel("Known:");
	private final JLabel altsLabel = new JLabel("Known alts:");
	private final JLabel altOfLabel = new JLabel("");
	private final JList<String> altsList = new JList<>(new DefaultListModel<>());
	private final JComboBox<String> addAltDropdown = new JComboBox<>();
	private final JButton btnAddAlt = new JButton("Add alt");
	private final JButton btnRemoveAlt = new JButton("Remove alt");
	private final JButton btnRemovePlayer = new JButton("Remove");
	private final JButton btnAddKill = new JButton("Add");
	private final JButton btnStart = new JButton("Start");
	private final JButton btnStop = new JButton("Stop");
	private final JButton btnAddToSession = new JButton("Add");
	private final JButton btnRemoveFromSession = new JButton("Remove");
	private final JComboBox<String> currentSessionPlayerDropdown = new JComboBox<>();
	private final JComboBox<String> notInCurrentSessionPlayerDropdown = new JComboBox<>();
	private final DefaultListModel<Session> historyModel = new DefaultListModel<>();
	private final JList<Session> historyList = new JList<>(historyModel);
	private final Dimension dl = new Dimension(48, 24);
	private final Dimension dm = new Dimension(64, 24);
	private final Dimension bm = new Dimension(59, 24);
	private final Dimension dv = new Dimension(96, 24);
	private final Dimension d = new Dimension(128, 24);
	private final Insets inset = new Insets(3, 3, 3, 3);
	private final Dimension lm = new Dimension(0, 140);
	private final Dimension ll = new Dimension(0, 280);
	private final JTable recentSplitsTable;
	//Icons
	private final String infoIconUniCode = "\uD83D\uDEC8";
	private final JPanel metricsContentWrapper = new JPanel(new BorderLayout());
	private PanelActions actions;
	// Tutorial UI
	private JPanel tutorialPanel;
	private JTextArea tutorialText;
	private JButton btnTourStart;
	private JButton btnTourPrev;
	private JButton btnTourNext;
	private JButton btnTourEnd;
	private boolean tourRunning = false;
	private int tourStep = 0;
	private Timer rainbowTimer;
	private JComponent highlighted;
	private Border originalBorder;
	// References to copy buttons so we can highlight them in the tour
	private JButton btnCopyJson;
	private JButton btnCopyMd;
	private DropdownRip detectedValuesDropdown;

	public PanelView(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager, PanelController controller)
	{
		this.sessionManager = sessionManager;
		this.config = config;
		this.playerManager = playerManager;
		bindActions(controller);

		recentSplitsModel = new RecentSplitsTable(config);
		recentSplitsModel.setListener(editedKill -> {
			if (actions != null)
			{
				actions.recomputeMetricsForSession(editedKill != null ? editedKill.getSessionId() : null);
			}
			else
			{
				// Fallback: recompute current if no controller available
				refreshMetrics();
			}
		});
		recentSplitsTable = makeRecentSplitsTable(recentSplitsModel);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		top.add(generateTutorialPanel());

		top.add(generateSessionPanel());
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

		add(top, BorderLayout.NORTH);
	}

	private static String shortenName(String name, int maxLen)
	{
		if (name == null)
		{
			return "";
		}
		String n = name.trim();
		return n.length() <= maxLen ? n : n.substring(0, maxLen);
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

		btnAddKill.addActionListener(e -> {
			actions.addKillFromInputs();
		});

		btnWaitlistAdd.addActionListener(e -> actions.applySelectedPendingValue(waitlistTable.getSelectedRow()));
		btnWaitlistDelete.addActionListener(e -> actions.deleteSelectedPendingValue(waitlistTable.getSelectedRow()));
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

	private JFormattedTextField makeOsrsField()
	{
		JFormattedTextField f = new JFormattedTextField(
			new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		f.setColumns(14);
		f.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		f.setToolTipText("Enter amount like 10k, 1.1m, or 1b (K = thousands)");
		return f;
	}

	private JPanel generateTutorialPanel()
	{
		if (tutorialPanel != null)
		{
			return tutorialPanel;
		}

		tutorialPanel = new JPanel(new BorderLayout());
		tutorialPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color.GRAY),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		tutorialText = new JTextArea("Welcome! Click Start tour to begin a quick walkthrough.");
		tutorialText.setLineWrap(true);
		tutorialText.setWrapStyleWord(true);
		tutorialText.setEditable(false);
		tutorialText.setFont(tutorialText.getFont().deriveFont(Font.BOLD));
		tutorialText.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel hint = new JLabel("   Tip: You can disable this tour in settings");
		hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		left.add(tutorialText);
		left.add(Box.createVerticalStrut(3));
		left.add(hint);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		btnTourStart = new JButton("Start tour");
		btnTourPrev = new JButton("Previous");
		btnTourNext = new JButton("Next");
		btnTourEnd = new JButton("End");

		Dimension small = new Dimension(90, 22);
		btnTourStart.setPreferredSize(small);
		btnTourPrev.setPreferredSize(small);
		btnTourNext.setPreferredSize(small);
		btnTourEnd.setPreferredSize(small);

		// Create button rows with proper alignment
		JPanel buttonRow1 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		buttonRow1.add(btnTourStart);

		JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonRow2.add(btnTourPrev);
		buttonRow2.add(btnTourNext);

		right.add(buttonRow1);
		right.add(buttonRow2);

		// Route through controller for MVC
		btnTourStart.addActionListener(e -> {
			if (actions != null)
			{
				actions.tourStart();
			}
			else
			{
				startTour();
			}
		});
		btnTourPrev.addActionListener(e -> {
			if (actions != null)
			{
				actions.tourPrev();
			}
			else
			{
				prevTourStep();
			}
		});
		btnTourNext.addActionListener(e -> {
			if (actions != null)
			{
				actions.tourNext();
			}
			else
			{
				nextTourStep();
			}
		});
		btnTourEnd.addActionListener(e -> {
			if (actions != null)
			{
				actions.tourEnd();
			}
			else
			{
				endTourAndDisable();
			}
		});

		tutorialPanel.add(left, BorderLayout.CENTER);
		tutorialPanel.add(right, BorderLayout.SOUTH);

		updateTutorialUI();
		return tutorialPanel;
	}

	public void startTour()
	{
		tourRunning = true;
		tourStep = 0;
		updateTutorialUI();
		highlightTargetForStep();
	}

	public void endTour()
	{
		// do not auto-disable config here; controller decides
		tourRunning = false;
		gotoStep(0);
		clearHighlight();
		updateTutorialUI();
	}

	public void endTourAndDisable()
	{
		// Delegate disabling to controller for MVC; fallback to local end
		if (actions != null)
		{
			actions.tourEnd();
			return;
		}
		// Fallback when no controller bound
		tourRunning = false;
		gotoStep(0);
		clearHighlight();
		try
		{
			config.enableTour(false);
		}
		catch (Throwable ignored)
		{
		}
		updateTutorialUI();
	}

	public void nextTourStep()
	{
		gotoStep(tourStep + 1);
	}

	public void prevTourStep()
	{
		gotoStep(tourStep - 1);
	}

	public void gotoStep(int step)
	{
		int max = getTourSteps().size() - 1;
		if (step < 0)
		{
			step = 0;
		}
		if (step > max)
		{
			endTourAndDisable();
			return;
		}
		tourStep = step;
		updateTutorialUI();
		highlightTargetForStep();
	}

	private void updateTutorialUI()
	{
		boolean enabled = config.enableTour();
		boolean show = enabled || tourRunning;
		if (tutorialPanel != null)
		{
			tutorialPanel.setVisible(show);
		}
		if (tutorialText != null)
		{
			List<String> steps = getTourSteps();
			int max = steps.size();
			String msg = tourRunning ? ("Step " + (tourStep + 1) + "/" + max + ": " + steps.get(tourStep))
				: "Welcome! Click Start tour to begin a quick walkthrough.";
			tutorialText.setText(msg);
		}
		boolean inTour = tourRunning;
		if (btnTourStart != null)
		{
			btnTourStart.setVisible(!inTour);
		}
		if (btnTourPrev != null)
		{
			btnTourPrev.setVisible(inTour);
		}
		if (btnTourNext != null)
		{
			btnTourNext.setVisible(inTour);
		}
		if (btnTourEnd != null)
		{
			btnTourEnd.setVisible(inTour);
		}
	}

	private List<String> getTourSteps()
	{
		List<String> steps = new ArrayList<>();
		steps.add("Scroll down and add a new player: type a name in the text field and click Add Player.");
		steps.add("Optional: The 'Known alts' dropdown lets you link an alt account to a selected known player.");
		steps.add("Start a session using the Start button.");
		steps.add("Add players to the session: use the 'Not in session' dropdown, click Add. Tip: add 2 players to see splits.");
		steps.add("Record a split: use the 'Player' dropdown, enter an amount, then click Add.");
		steps.add("You can remove a player from settlement by clicking the 'x' button in the Settlement table.");
		steps.add("Share results: use the Copy MD button (great for Discord) or Copy JSON if you need raw data.");
		steps.add("Detected values: expand the 'Detected values' section. '!add' in clan chat will queue amounts here. See Settings > Chat detection to configure.");
		steps.add("Review the Recent Splits table.");
		steps.add("Stop the session when you are done.");
		return steps;
	}

	private void highlightTargetForStep()
	{
		clearHighlight();
		if (!tourRunning)
		{
			return;
		}
		switch (tourStep)
		{
			case 0:
				// Start session
				highlight(newPlayerField);
				break;
			case 1:
				// Add new player via text field
				highlight(addAltDropdown);
				break;
			case 2:
				// Known alts dropdown to link alts
				highlight(btnStart);
				break;
			case 3:
				// Add players to session using 'Not in session' dropdown
				highlight(notInCurrentSessionPlayerDropdown);
				break;
			case 4:
				// Record split: Player dropdown (and amount field exists near)
				highlight(currentSessionPlayerDropdown);
				break;
			case 5:
				// Show settlement table where 'x' removes a player
				highlight(metricsTable);
				break;
			case 6:
				// Copy buttons for sharing results
				highlight(btnCopyMd != null ? btnCopyMd : metricsTable);
				break;
			case 7:
				// Detected values dropdown / table
				highlight(detectedValuesDropdown);
				break;
			case 8:
				// Recent splits table review
				highlight(recentSplitsTable);
				break;
			case 9:
				// Stop session
				highlight(btnStop);
				break;
			default:
				clearHighlight();
		}
	}

	private void highlight(JComponent c)
	{
		if (c == null)
		{
			return;
		}
		clearHighlight();
		highlighted = c;
		originalBorder = c.getBorder();
		final float[] hue = {0f};
		if (rainbowTimer != null)
		{
			rainbowTimer.stop();
		}
		rainbowTimer = new Timer(80, e -> {
			hue[0] += 0.02f;
			if (hue[0] > 1f)
			{
				hue[0] = 0f;
			}
			Color color = Color.getHSBColor(hue[0], 1f, 1f);
			Border rb = BorderFactory.createLineBorder(color, 3);
			Border pad = BorderFactory.createEmptyBorder(2, 2, 2, 2);
			c.setBorder(BorderFactory.createCompoundBorder(rb, pad));
			c.repaint();
		});
		rainbowTimer.start();
	}

	private void clearHighlight()
	{
		if (rainbowTimer != null)
		{
			rainbowTimer.stop();
			rainbowTimer = null;
		}
		if (highlighted != null)
		{
			highlighted.setBorder(originalBorder);
			highlighted.repaint();
			highlighted = null;
			originalBorder = null;
		}
	}

	private JTable makeRecentSplitsTable(RecentSplitsTable model)
	{
		JTable t = new JTable(model);
		// ... existing table setup ...

		// Right align Amount renderer
		javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		t.getColumnModel().getColumn(2).setCellRenderer(right);

		// Row-aware player editor
		final JComboBox<String> playerCombo = new JComboBox<>();
		javax.swing.DefaultCellEditor playerEditor = new javax.swing.DefaultCellEditor(playerCombo)
		{
			@Override
			public java.awt.Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected, int row, int column)
			{
				JComboBox<String> combo = playerCombo;

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

				combo.setModel(new DefaultComboBoxModel<>(choices));
				combo.setSelectedItem(value);
				return combo;
			}
		};
		t.getColumnModel().getColumn(1).setCellEditor(playerEditor);

		// Amount editor (existing)
		JFormattedTextField amtField = new JFormattedTextField(new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		amtField.setBorder(null);
		DefaultCellEditor amtEditor = new DefaultCellEditor(amtField);
		t.getColumnModel().getColumn(2).setCellEditor(amtEditor);

		return t;
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

		g2.gridy = 0;
		sessionPanel.add(buttonsHalfHalf, g2);

		historyLabel.setHorizontalAlignment(SwingConstants.CENTER);
		g2.gridy = 1;
		sessionPanel.add(historyLabel, g2);

		return sessionPanel;
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
		try
		{
			waitlistTable.getColumnModel().getColumn(1).setCellRenderer(right);
		}
		catch (Exception ignored)
		{
		}

		// Editor for Player column (use known players list)
		final JComboBox<String> wlPlayerCombo = new JComboBox<>();
		DefaultCellEditor wlPlayerEditor = new DefaultCellEditor(wlPlayerCombo)
		{
			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
			{
				JComboBox<String> combo = wlPlayerCombo;
				combo.setModel(new DefaultComboBoxModel<>(playerManager.getKnownMains().toArray(new String[0])));
				combo.setSelectedItem(value);
				return combo;
			}
		};
		try
		{
			waitlistTable.getColumnModel().getColumn(2).setCellEditor(wlPlayerEditor);
		}
		catch (Exception ignored)
		{
		}

		// Editor for Value column (OSRS amount)
		JFormattedTextField wlAmtField = new JFormattedTextField(new DefaultFormatterFactory(new Formats.OsrsAmountFormatter()));
		wlAmtField.setBorder(null);
		DefaultCellEditor wlAmtEditor = new DefaultCellEditor(wlAmtField);
		try
		{
			waitlistTable.getColumnModel().getColumn(1).setCellEditor(wlAmtEditor);
		}
		catch (Exception ignored)
		{
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
		return new DropdownRip("Recent splits", scroller, config.enableTour(), tooltip);
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

		return new DropdownRip("Settlement information", wrapper);
	}

	private void refreshMetricsContent()
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
		try
		{
			int playerIdx = metricsTable.getColumnModel().getColumnIndex("Player");
			metricsTable.getColumnModel().getColumn(playerIdx).setCellRenderer(greyingRenderer);
		}
		catch (IllegalArgumentException ignored)
		{
		}
		try
		{
			int totalIdx = metricsTable.getColumnModel().getColumnIndex("Total");
			metricsTable.getColumnModel().getColumn(totalIdx).setCellRenderer(greyingRenderer);
		}
		catch (IllegalArgumentException ignored)
		{
		}

		DefaultTableCellRenderer splitRenderer = new DefaultTableCellRenderer()
		{
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
		try
		{
			int splitIdx = metricsTable.getColumnModel().getColumnIndex("Split");
			metricsTable.getColumnModel().getColumn(splitIdx).setCellRenderer(splitRenderer);
		}
		catch (IllegalArgumentException ignored)
		{
		}

		try
		{
			int actionIdx = metricsTable.getColumnModel().getColumnIndex("X");
			metricsTable.getColumnModel().getColumn(actionIdx)
				.setCellRenderer(new RemoveButtonRenderer());
			metricsTable.getColumnModel().getColumn(actionIdx)
				.setCellEditor(new RemoveButtonEditor(this, sessionManager, metricsTable, actions));
		}
		catch (IllegalArgumentException ignored)
		{
		}
	}

	private JComponent generateDirectPaymentsContent()
	{
		Session currentSession = sessionManager.getCurrentSession().orElse(null);
		List<PlayerMetrics> data = sessionManager.computeMetricsFor(currentSession, true);
		List<Transfer> transfers = PaymentProcessor.computeDirectPaymentsStructured(data);

		if (transfers != null && !transfers.isEmpty())
		{
			javax.swing.table.DefaultTableModel txModel =
				new javax.swing.table.DefaultTableModel(new Object[]{"Suggested direct payments"}, 0)
				{
					@Override
					public boolean isCellEditable(int r, int c)
					{
						return false;
					}
				};

			for (Transfer t : transfers)
			{
				String payerShort = shortenName(t.getFrom(), 7);
				String payeeShort = shortenName(t.getTo(), 7);
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
		Session currentSession = sessionManager.getCurrentSession().orElse(null);
		((Metrics) metricsTable.getModel()).setData(sessionManager.computeMetricsFor(currentSession, true));
		refreshMetricsContent();
	}

	private void copyMetricsJsonToClipboard()
	{
		String payload = MarkdownFormatter.buildMetricsJson(sessionManager);
		StringSelection selection = new StringSelection(payload);
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}

	private void copyMetricsMarkdownToClipboard()
	{
		String payload = MarkdownFormatter.buildMetricsMarkdown(
			sessionManager.computeMetricsFor(
				sessionManager.getCurrentSession().orElse(null), true), config);
		StringSelection selection = new StringSelection(payload);
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}
}
