package com.splitmanager.controllers;

import com.splitmanager.ManagerKnownPlayers;
import com.splitmanager.ManagerPanel;
import com.splitmanager.ManagerSession;
import com.splitmanager.PluginConfig;
import com.splitmanager.models.Metrics;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.Session;
import com.splitmanager.models.WaitlistTable;
import com.splitmanager.utils.Formats;
import com.splitmanager.utils.MarkdownFormatter;
import static com.splitmanager.utils.Utils.toast;
import com.splitmanager.views.PanelView;
import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

//Testing push again

/**
 * MVC Controller: non-UI logic + event handling. The View calls into this via PanelActions.
 * Keeps string/markdown/transfer computations here and pushes UI refreshes through the View.
 */
@Slf4j
public class PanelController implements PanelActions
{
	private final ManagerSession sessionManager;
	private final PluginConfig config;
	private final ManagerKnownPlayers playerManager;
	private final ManagerPanel managerPanel;
	@Setter
	private PanelView view;

	public PanelController(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager, ManagerPanel managerPanel)
	{
		this.sessionManager = sessionManager;
		this.playerManager = playerManager;
		this.config = config;
		this.managerPanel = managerPanel;
	}

	@Override
	public void startSession()
	{
		if (sessionManager.isHistoryLoaded())
		{
			sessionManager.unloadHistory();
		}
		if (sessionManager.hasActiveSession())
		{
			toast(view, "Active session exists.");
			return;
		}
		sessionManager.startSession().ifPresent(s -> toast(view, "Session started."));
		managerPanel.refreshAllView();
		refreshAllView();
	}

	@Override
	public void stopSession()
	{
		if (sessionManager.isHistoryLoaded())
		{
			sessionManager.unloadHistory();
			toast(view, "History closed.");
			managerPanel.refreshAllView();
			refreshAllView();
			return;
		}
		int res = JOptionPane.showConfirmDialog(view,
			"Are you sure you want to stop the session?",
			"Confirm stop",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (res != JOptionPane.YES_OPTION)
		{
			return;
		}
		if (sessionManager.stopSession())
		{
			managerPanel.refreshAllView();
		}
		else
		{
			toast(view, "Failed to stop session.");
		}
		refreshAllView();
	}

	@Override
	public void addPlayerToSession(String player)
	{
		if (player == null)
		{
			toast(view, "Select a player in dropdown.");
			return;
		}
		if (sessionManager.addPlayerToActive(player))
		{
			managerPanel.refreshAllView();
		}
		else
		{
			toast(view, "Failed to add player, player might already be in session.");
		}
		refreshAllView();
	}

	@Override
	public void addKnownPlayer(String name)
	{
		String clean = name == null ? "" : name.trim();
		if (clean.isEmpty())
		{
			toast(view, "Enter a name.");
			return;
		}
		if (!playerManager.addKnownPlayer(clean))
		{
			toast(view, "Player already in list exists.");
			return;
		}
		playerManager.saveToConfig();
		managerPanel.refreshAllView();
		view.getKnownPlayersDropdown().setSelectedItem(clean);
		view.getNewPlayerField().setText("");
		view.getNewPlayerField().requestFocusInWindow();
		refreshAllView();
	}

	@Override
	public void removeKnownPlayer(String name)
	{
		if (name == null)
		{
			toast(view, "Select a Player to remove.");
			return;
		}
		int res = JOptionPane.showConfirmDialog(view,
			"Remove '" + name + "'? This will also unlink any alt relationships.",
			"Confirm removal",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (res != JOptionPane.YES_OPTION)
		{
			return;
		}

		if (!playerManager.removeKnownPlayer(name))
		{
			toast(view, "Not found.");
			return;
		}
		playerManager.saveToConfig();
		managerPanel.refreshAllView();
		refreshAllView();
	}

	@Override
	public void addKill(String player, long amount)
	{
		if (player == null)
		{
			toast(view, "Select a player.");
			return;
		}
		if (sessionManager.addKill(player, amount))
		{
			view.getKillAmountField().setText("");
			managerPanel.refreshAllView();
		}
		else
		{
			toast(view, "Failed to add kill (is player in session?).");
		}
		refreshAllView();
	}

	@Override
	public void addKillFromInputs()
	{
		String player = (String) view.getCurrentSessionPlayerDropdown().getSelectedItem();

		Object rawValue = view.getKillAmountField().getValue();
		if (rawValue == null)
		{
			toast(view, "Please enter a valid amount.");
			return;
		}

		String val = rawValue.toString();
		long amt;
		try
		{
			if (rawValue instanceof Number)
			{
				amt = ((Number) rawValue).longValue();
			}
			else
			{
				amt = Formats.OsrsAmountFormatter.stringAmountToLongAmount(val, config);
			}
			log.debug("Adding kill for {} with amount {}", player, amt);
			addKill(player, amt);
		}
		catch (Exception ex)
		{
			log.warn("Invalid kill amount {}", val, ex);
			toast(view, "Invalid amount.");
		}
	}

	@Override
	public void addAltToMain(String main, String alt)
	{
		if (sessionManager.hasActiveSession())
		{
			toast(view, "Cannot add/remove alts while session is active. Stop session first.");
			return;
		}
		if (main == null || alt == null)
		{
			toast(view, "Select a player and an alt to add.");
			return;
		}
		if (!playerManager.canLinkAltToMain(alt, main))
		{
			toast(view, "Cannot link: either main is an alt, alt already linked, or alt is a main.");
			return;
		}
		if (playerManager.trySetAltMain(alt, main))
		{
			toast(view, String.format("Linked %s → %s", alt, main));
			managerPanel.refreshAllView();
			refreshAllView();
		}
		else
		{
			toast(view, "Failed to link alt.");
		}
	}

	@Override
	public void removeSelectedAlt(String selectedMain, String selectedEntry)
	{
		if (sessionManager.hasActiveSession())
		{
			toast(view, "Cannot add/remove alts while session is active. Stop session first.");
			return;
		}
		if (selectedMain == null || selectedMain.isBlank())
		{
			toast(view, "Select a player in Known list.");
			return;
		}
		if (selectedEntry == null || selectedEntry.isBlank())
		{
			toast(view, "Select an alt in the list to remove.");
			return;
		}

		String alt = playerManager.parseAltFromEntry(selectedEntry);
		String main = playerManager.getMainName(alt);

		if (!playerManager.isAlt(alt))
		{
			toast(view, alt + " is not linked as an alt.");
			return;
		}

		if (main == null || !main.equalsIgnoreCase(selectedMain))
		{
			toast(view, String.format("%s is linked to %s, not %s.", alt, main, selectedMain));
			return;
		}

		int res = JOptionPane.showConfirmDialog(view,
			"Unlink '" + alt + "' from '" + main + "'?",
			"Confirm unlink",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);

		if (res != JOptionPane.YES_OPTION)
		{
			return;
		}

		if (playerManager.unlinkAlt(alt))
		{
			toast(view, "Unlinked alt.");
			managerPanel.refreshAllView();
			playerManager.saveToConfig();
			refreshAllView();
		}
		else
		{
			toast(view, "Failed to unlink alt.");
		}
	}

	@Override
	public void applySelectedPendingValue(int idx)
	{
		if (idx < 0)
		{
			toast(view, "Select a detected value first.");
			return;
		}
		if (!sessionManager.hasActiveSession())
		{
			toast(view, "Start a session first.");
			return;
		}
		WaitlistTable m = view.getWaitlistTableModel();
		PendingValue pv = m.getRow(idx);
		if (pv == null)
		{
			return;
		}
		String target = pv.getSuggestedPlayer();
		if (target == null || target.isBlank())
		{
			toast(view, "Choose a Suggested Player in the table first.");
			return;
		}
		if (sessionManager.applyPendingValueToPlayer(pv.getId(), target))
		{
			managerPanel.refreshAllView();
		}
		else
		{
			toast(view, "Failed to add value. Is the player in the session?");
		}
		refreshAllView();
	}

	@Override
	public void deleteSelectedPendingValue(int idx)
	{
		if (idx < 0)
		{
			toast(view, "Select a detected value first.");
			return;
		}
		WaitlistTable m = view.getWaitlistTableModel();
		PendingValue pv = m.getRow(idx);
		if (pv == null)
		{
			return;
		}
		if (sessionManager.removePendingValueById(pv.getId()))
		{
			managerPanel.refreshAllView();
		}
		refreshAllView();
	}

	@Override
	public void loadHistory(String sessionId)
	{
		if (sessionId == null || sessionId.isBlank())
		{
			toast(view, "Select a session from history.");
			return;
		}
		if (sessionManager.hasActiveSession())
		{
			toast(view, "Stop the current session first.");
			return;
		}
		if (sessionManager.loadHistory(sessionId).isPresent())
		{
			toast(view, "History loaded.");
			managerPanel.refreshAllView();
		}
		else
		{
			toast(view, "Failed to load history.");
		}
		refreshAllView();
	}

	@Override
	public void unloadHistory()
	{
		if (!sessionManager.isHistoryLoaded())
		{
			return;
		}
		sessionManager.unloadHistory();
		toast(view, "History closed.");
		managerPanel.refreshAllView();
		refreshAllView();
	}

	@Override
	public void exportHistory(String selectedSessionId)
	{
		if (sessionManager.getHistorySessionsNewestFirst().isEmpty())
		{
			toast(view, "No history to export.");
			return;
		}

		Object[] options = {"All history", "Selected session", "Cancel"};
		int choice = JOptionPane.showOptionDialog(view,
			"Export all history or the currently selected session?",
			"Export",
			JOptionPane.DEFAULT_OPTION,
			JOptionPane.QUESTION_MESSAGE,
			null,
			options,
			options[0]);

		if (choice == 0)
		{
			copyToClipboard(sessionManager.exportHistorySessionsJson());
			toast(view, "All history JSON copied.");
			return;
		}

		if (choice == 1)
		{
			if (selectedSessionId == null || selectedSessionId.isBlank())
			{
				toast(view, "Select a session from history.");
				return;
			}
			String payload = sessionManager.exportSessionThreadJson(selectedSessionId);
			if (payload.isEmpty())
			{
				toast(view, "Failed to export selected history.");
				return;
			}
			copyToClipboard(payload);
			toast(view, "Selected history JSON copied.");
		}
	}

	@Override
	public void importHistoryFromClipboard()
	{
		String clipboard = readClipboardText();
		if (clipboard == null || clipboard.trim().isEmpty())
		{
			toast(view, "Clipboard does not contain history JSON.");
			return;
		}

		int importedThreads = sessionManager.importHistorySessionsJson(clipboard);
		if (importedThreads <= 0)
		{
			toast(view, "No valid history found in clipboard.");
			return;
		}

		managerPanel.refreshAllView();
		refreshAllView();
		toast(view, importedThreads == 1
			? "Imported 1 history thread from clipboard."
			: "Imported " + importedThreads + " history threads from clipboard.");
	}

	@Override
	public void onKnownPlayerSelectionChanged(String selected)
	{
		refreshAlts();
	}

	@Override
	public void refreshAllView()
	{
		refreshKnownPlayers();
		recomputeMetrics();
		refreshSessionData();
		refreshWaitlist();
		refreshHistory();
		refreshButtonStates();
	}

	@Override
	public void refreshSharedViews()
	{
		managerPanel.refreshAllView();
	}

	@Override
	public void recomputeMetrics()
	{
		Session current = sessionManager.getCurrentSession().orElse(null);
		if (current != null)
		{
			view.refreshMetrics();
			view.getRecentSplitsModel().setFromKills(sessionManager.getAllKills());
		}
		else
		{
			view.getRecentSplitsModel().clear();
		}
	}

	@Override
	public void recomputeMetricsForSession(String sessionId)
	{
		if (sessionId == null)
		{
			recomputeMetrics();
			return;
		}
		// Find that session (either current or one from history)
		Session target = sessionManager.getAllSessionsNewestFirst().stream()
			.filter(s -> sessionId.equals(s.getId()))
			.findFirst().orElse(null);
		if (target != null)
		{
			((Metrics) view.getMetricsTable().getModel()).setData(
				sessionManager.computeMetricsFor(target, true)
			);
			view.refreshMetrics();
		}
		// Keep the recent splits list up-to-date (it shows all kills)
		view.getRecentSplitsModel().setFromKills(sessionManager.getAllKills());
	}

	@Override
	public void copyMetricsJson()
	{
		String payload = MarkdownFormatter.buildMetricsJson(sessionManager);
		copyToClipboard(payload);
	}

	@Override
	public void copyMetricsMarkdown()
	{
		String payload = MarkdownFormatter.buildMetricsMarkdown(
			sessionManager.computeMetricsFor(
				sessionManager.getCurrentSession().orElse(null), true), config);
		copyToClipboard(payload);
	}

	private void copyToClipboard(String payload)
	{
		StringSelection selection = new StringSelection(payload);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}

	private String readClipboardText()
	{
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			return data == null ? null : data.toString();
		}
		catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException e)
		{
			log.warn("Failed to read clipboard text", e);
			return null;
		}
	}

	@Override
	public void togglePopout(boolean editMode)
	{
		managerPanel.togglePopOutWindow(editMode);
	}

	// Tutorial control implementations to keep view passive
	@Override
	public void tourStart()
	{
		view.startTour();
	}

	@Override
	public void tourPrev()
	{
		view.prevTourStep();
	}

	@Override
	public void tourNext()
	{
		view.nextTourStep();
	}

	@Override
	public void tourEnd()
	{
		try
		{
			config.enableTour(false);
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to persist tour disabled state", e);
		}
		view.endTour();
	}

	/**
	 * Refreshes the list of known players and updates corresponding UI components.
	 * <p>
	 * This method retrieves the list of known players from the session manager and updates
	 * the dropdown menu for known players in the user interface, as well as the label showing
	 * the count of known players. It ensures synchronization between the backend data and
	 * the visual presentation of known players.
	 */
	private void refreshKnownPlayers()
	{
		String[] players = sessionManager.getKnownPlayers().toArray(new String[0]);
		setModelPreservingSelection(view.getKnownPlayersDropdown(), players);
		view.getKnownListLabel().setText("Known (" + players.length + "):");

		refreshAlts();
	}

	private void setModelPreservingSelection(JComboBox<String> comboBox, String[] values)
	{
		Object selected = comboBox.getSelectedItem();
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(values);
		comboBox.setModel(model);
		if (selected == null)
		{
			return;
		}
		String selectedValue = selected.toString();
		for (String value : values)
		{
			if (selectedValue.equals(value))
			{
				comboBox.setSelectedItem(selectedValue);
				return;
			}
		}
	}

	/**
	 * Refreshes and updates user interface components related to the current session data.
	 */
	private void refreshSessionData()
	{
		Session currentSession = sessionManager.getCurrentSession().orElse(null);

		if (currentSession != null && currentSession.isActive())
		{
			String[] sessionPlayers = currentSession.getPlayers().toArray(new String[0]);
			String[] notPlayers = sessionManager.getNonActivePlayers().toArray(new String[0]);

			view.getCurrentSessionPlayerDropdown().setEnabled(true);
			setModelPreservingSelection(view.getCurrentSessionPlayerDropdown(), sessionPlayers);
			setModelPreservingSelection(view.getNotInCurrentSessionPlayerDropdown(), notPlayers);
		}
		else
		{
			setModelPreservingSelection(view.getCurrentSessionPlayerDropdown(), new String[0]);
			setModelPreservingSelection(view.getNotInCurrentSessionPlayerDropdown(), new String[0]);
			view.getCurrentSessionPlayerDropdown().setEnabled(false);
		}

		if (sessionManager.isHistoryLoaded())
		{
			view.getHistoryLabel().setText("HISTORY LOADED");
			view.getHistoryLabel().setOpaque(true);
			view.getHistoryLabel().setBackground(new Color(132, 84, 0));
			view.getHistoryLabel().setForeground(Color.WHITE);
			view.getHistoryLabel().setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
			view.getHistoryLabel().setToolTipText("Start closes history and starts a new session. Stop closes history.");
		}
		else
		{
			view.getHistoryLabel().setText("History: OFF");
			view.getHistoryLabel().setOpaque(false);
			view.getHistoryLabel().setBackground(null);
			view.getHistoryLabel().setForeground(null);
			view.getHistoryLabel().setBorder(null);
			view.getHistoryLabel().setToolTipText(null);
		}

		Session current = sessionManager.getCurrentSession().orElse(null);
		if (current != null)
		{
			view.getRecentSplitsModel().setFromKills(sessionManager.getAllKills());
		}
		else
		{
			view.getRecentSplitsModel().clear();
		}
	}

	/**
	 * Refreshes the data and UI components related to the waitlist table.
	 * <p>
	 * This method fetches the latest pending values from the session manager and updates
	 * the waitlist table model with this data. It also configures the cell editor for the
	 * third column of the waitlist table, populating it with a dropdown of known main players
	 * retrieved from the session manager.
	 * <p>
	 * Ensures that the waitlist table reflects the most up-to-date state of the application.
	 */
	private void refreshWaitlist()
	{
		view.getWaitlistTableModel().setData(sessionManager.getPendingValues());
		view.getWaitlistTable()
			.getColumnModel()
			.getColumn(2)
			.setCellEditor(new DefaultCellEditor(new JComboBox<>(
				playerManager.getKnownMains().toArray(new String[0]
				)
			)));
	}

	private void refreshHistory()
	{
		String selectedSessionId = sessionManager.isHistoryLoaded()
			? sessionManager.getCurrentSession().map(Session::getId).orElse(null)
			: view.getSelectedHistorySessionId();
		view.setHistorySessions(sessionManager.getHistorySessionsNewestFirst(), selectedSessionId);
	}

	/**
	 * Updates the enabled or disabled state of various buttons and fields in the user interface.
	 * The button states are set based on the current session status, player selections, and
	 * whether a completed history session is currently loaded.
	 */
	private void refreshButtonStates()
	{
		boolean historyMode = sessionManager.isHistoryLoaded();
		boolean hasActiveSession = sessionManager.hasActiveSession();

		view.getBtnStart().setEnabled(historyMode || !hasActiveSession);
		view.getBtnStop().setEnabled(historyMode || hasActiveSession);
		view.getBtnAddToSession().setEnabled(!historyMode && hasActiveSession);
		view.getNotInCurrentSessionPlayerDropdown().setEnabled(!historyMode && hasActiveSession);
		view.getBtnRemoveFromSession().setEnabled(!historyMode && hasActiveSession);

		boolean canAddKill = !historyMode && hasActiveSession;
		boolean hasSessionPlayers = view.getCurrentSessionPlayerDropdown().getItemCount() > 0;

		view.getBtnAddKill().setEnabled(canAddKill && hasSessionPlayers);
		view.getKillAmountField().setEnabled(canAddKill && hasSessionPlayers);

		int waitlistRows = view.getWaitlistTableModel().getRowCount();

		view.getBtnWaitlistAdd().setEnabled(!historyMode && hasActiveSession && waitlistRows > 0);
		view.getBtnWaitlistDelete().setEnabled(waitlistRows > 0);

		boolean hasHistory = view.getHistorySessionDropdown().getItemCount() > 0;
		view.getHistorySessionDropdown().setEnabled(!hasActiveSession && hasHistory);
		view.getBtnViewHistory().setEnabled(!hasActiveSession && hasHistory);
		view.getBtnUnloadHistory().setEnabled(historyMode);
		view.getBtnExportHistory().setEnabled(hasHistory);
		view.getBtnImportHistory().setEnabled(true);
		view.getRecentSplitsTable().setEnabled(!historyMode);
	}

	/**
	 * Refreshes the dropdown lists and UI components related to alternate accounts.
	 * This method ensures that the UI reflects the currently selected main player
	 * and dynamically updates the list of eligible alternate accounts that can be linked to the selected player.
	 */
	private void refreshAlts()
	{
		String[] players = sessionManager.getKnownPlayers().toArray(new String[0]);

		String selectedMain = (String) view.getKnownPlayersDropdown().getSelectedItem();
		if (selectedMain == null && players.length > 0)
		{
			selectedMain = players[0];
			view.getKnownPlayersDropdown().setSelectedIndex(0);
		}

		refreshAltList(selectedMain);

		List<String> eligiblePlayers = new java.util.ArrayList<>();

		if (selectedMain != null)
		{
			for (String p : sessionManager.getKnownPlayers())
			{
				if (playerManager.canLinkAltToMain(p, selectedMain))
				{
					eligiblePlayers.add(p);
				}
			}
		}

		setModelPreservingSelection(view.getAddAltDropdown(), eligiblePlayers.toArray(new String[0]));
	}

	/**
	 * Refreshes the list of alternate accounts associated with the given main player.
	 * Updates the alt label and list in the view with relevant information about the player's alt accounts.
	 * If the player is identified as an alt, displays the corresponding main account name.
	 *
	 * @param mainPlayer the name of the main player whose alternate accounts are to be refreshed;
	 *                   if null, the method exits without performing any action.
	 */
	public void refreshAltList(String mainPlayer)
	{
		if (mainPlayer == null)
		{
			return;
		}

		String altsText = !mainPlayer.isBlank()
			? (mainPlayer + " known alts:")
			: "Known alts:";
		view.getAltsLabel().setText(altsText);

		DefaultListModel<String> altsModel = (DefaultListModel<String>) view.getAltsList().getModel();
		altsModel.clear();

		if (playerManager.isAlt(mainPlayer))
		{
			String mainName = playerManager.getMainName(mainPlayer);
			if (mainName != null && !mainName.equalsIgnoreCase(mainPlayer))
			{
				altsModel.addElement(mainPlayer + " is an alt of " + mainName);
			}
		}

		for (String alt : playerManager.getAltsOf(mainPlayer))
		{
			altsModel.addElement(alt);
		}
	}
}
