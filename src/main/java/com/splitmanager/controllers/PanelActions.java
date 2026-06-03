package com.splitmanager.controllers;

import com.splitmanager.models.SettlementConfigSnapshot;

/**
 * Actions that the PanelView can invoke on its controller.
 */
public interface PanelActions
{
	/**
	 * Start a new session if none is active.
	 */
	void startSession();

	/**
	 * Stop the currently active session, if any.
	 */
	void stopSession();

	/**
	 * Add a player to the active session.
	 *
	 * @param player player display name
	 */
	void addPlayerToSession(String player);

	/**
	 * Add a name to the known-players list.
	 *
	 * @param name player name
	 */
	void addKnownPlayer(String name);

	/**
	 * Remove a name from the known-players list.
	 *
	 * @param name player name
	 */
	void removeKnownPlayer(String name);

	/**
	 * Record a loot amount for a player.
	 *
	 * @param player player name
	 * @param amount amount (may be negative if allowed by config)
	 */
	void addLoot(String player, long amount);

	/**
	 * Read current inputs from the view and record a event.
	 */
	void addLootFromInputs();

	/**
	 * Link an alt to a main account.
	 *
	 * @param main main player
	 * @param alt  alt account to link
	 */
	void addAltToMain(String main, String alt);

	/**
	 * Remove an alt link from the selected main.
	 *
	 * @param main          selected main
	 * @param selectedEntry UI entry string to parse
	 */
	void removeSelectedAlt(String main, String selectedEntry);

	/**
	 * Apply the selected pending value to a specific player.
	 *
	 * @param tableRowIndex index in the pending table
	 */
	void applySelectedPendingValue(int tableRowIndex);

	/**
	 * Delete the selected pending value entry.
	 *
	 * @param tableRowIndex index in the pending table
	 */
	void deleteSelectedPendingValue(int tableRowIndex);

	/**
	 * Load a stopped session into history mode.
	 *
	 * @param sessionId selected session id
	 */
	void loadHistory(String sessionId);

	/**
	 * Exit history mode.
	 */
	void unloadHistory();

	/**
	 * Export history JSON for all history or the selected history session.
	 *
	 * @param selectedSessionId selected history session id
	 */
	void exportHistory(String selectedSessionId);

	/**
	 * Import history JSON from the clipboard.
	 */
	void importHistoryFromClipboard();

	/**
	 * Save edited settlement context for the currently loaded history session.
	 *
	 * @param snapshot edited settlement-affecting config
	 */
	void saveHistorySettlementContext(SettlementConfigSnapshot snapshot);

	/**
	 * Recompute the loaded history view with edited settlement context without saving it.
	 *
	 * @param snapshot edited settlement-affecting config
	 */
	void applyHistorySettlementContext(SettlementConfigSnapshot snapshot);

	/**
	 * Discard edits and reload the currently loaded history settlement context.
	 */
	void cancelHistorySettlementContextEdit();

	/**
	 * Handle selection change in known-players list.
	 *
	 * @param selected currently selected name
	 */
	void onKnownPlayerSelectionChanged(String selected);

	/**
	 * Refresh all sections of the view; idempotent and safe after mutations.
	 */
	void refreshAllView(); // idempotent, safe to call after model mutations

	/**
	 * Refresh every open panel that shares the same manager state.
	 */
	void refreshSharedViews();

	/**
	 * Recompute and apply metrics for current session.
	 */
	void recomputeMetrics();

	void recomputeMetricsForSession(String sessionId);

	/**
	 * Copy metrics to clipboard in JSON.
	 */
	void copyMetricsJson();

	/**
	 * Copy metrics to clipboard in Markdown.
	 */
	void copyMetricsMarkdown();

	/**
	 * Open or bring to front the popout window.
	 *
	 * @param editMode true to start in edit mode
	 */
	void togglePopout(boolean editMode);

	// Tutorial controls to keep MVC separation
	void tourStart();

	void tourPrev();

	void tourNext();

	void tourEnd();
}
