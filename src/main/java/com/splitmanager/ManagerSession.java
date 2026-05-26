package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.sessions.SplitCalculator;
import com.splitmanager.utils.Formats;
import com.splitmanager.utils.InstantTypeAdapter;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages sessions, persistence, and all the logic for roster changes,
 * child sessions, and live split calculations.
 */
@Singleton
@Slf4j
public class ManagerSession
{
	private final Gson gson;
	private final Map<String, Session> sessions = new LinkedHashMap<>();
	private final List<PendingValue> pendingValues = new ArrayList<>();
	private final ManagerKnownPlayers playerManager;
	private final PluginConfig config;
	private final ManagerPlugin pluginManager;
	private final SplitCalculator splitCalculator;
	// Cache of all kills grouped by mother session id to avoid recomputing on every UI refresh
	private final Map<String, List<Kill>> motherKillsCache = new LinkedHashMap<>();

	/**
	 * Force a full rebuild of the kills cache for the current session's thread.
	 */
	public void invalidateKillsCache()
	{
		getCurrentSession().ifPresent(curr -> {
			String motherId = (curr.getMotherId() == null) ? curr.getId() : curr.getMotherId();
			motherKillsCache.remove(motherId);
		});
	}

	public void insertKillAt(int index, String player, Long amount)
	{
		getCurrentSession().ifPresent(curr -> {
			String mainPlayer = resolveMainName(player);
			if (mainPlayer == null)
			{
				return;
			}
			Kill kill = new Kill(curr.getId(), mainPlayer, amount, Instant.now());
			kill.setType(Kill.TYPE_LOOT);
			curr.getKills().add(kill);
			invalidateKillsCache();
			saveToConfig();
		});
	}

	public void removeKillAt(int index)
	{
		removeKillsAt(Collections.singletonList(index));
	}

	public void removeKillsAt(List<Integer> indices)
	{
		List<Kill> allKills = getAllKills();
		if (indices == null || indices.isEmpty())
		{
			return;
		}
		List<Kill> killsToRemove = new ArrayList<>();
		for (Integer index : indices)
		{
			if (index != null && index >= 0 && index < allKills.size())
			{
				Kill kill = allKills.get(index);
				if (!killsToRemove.contains(kill))
				{
					killsToRemove.add(kill);
				}
			}
		}
		if (killsToRemove.isEmpty())
		{
			return;
		}

		repairRostersBeforeRemoving(killsToRemove);
		for (Session s : sessions.values())
		{
			s.getKills().removeAll(killsToRemove);
		}
		invalidateKillsCache();
		saveToConfig();
	}

	private void repairRostersBeforeRemoving(List<Kill> killsToRemove)
	{
		for (Kill kill : killsToRemove)
		{
			if (kill == null || !kill.isRosterEvent())
			{
				continue;
			}
			if (Kill.TYPE_LEFT.equalsIgnoreCase(kill.getType()))
			{
				addPlayerFromSessionUntilNextEvent(kill.getSessionId(), kill.getPlayer(), killsToRemove);
			}
			else if (Kill.TYPE_JOINED.equalsIgnoreCase(kill.getType()))
			{
				removePlayerFromSessionUntilLinkedLeft(kill.getSessionId(), kill.getPlayer(), killsToRemove);
			}
		}
	}

	private void addPlayerFromSessionUntilNextEvent(String sessionId, String player, List<Kill> ignoredEvents)
	{
		if (player == null)
		{
			return;
		}
		for (Session session : sessionsFrom(sessionId))
		{
			addPlayerIfMissing(session, player);
			if (hasRosterEventForPlayer(session, player, ignoredEvents))
			{
				return;
			}
		}
	}

	private void removePlayerFromSessionUntilLinkedLeft(String sessionId, String player, List<Kill> ignoredEvents)
	{
		if (player == null)
		{
			return;
		}
		boolean first = true;
		for (Session session : sessionsFrom(sessionId))
		{
			if (!first && hasRosterEventForPlayer(session, player, ignoredEvents))
			{
				return;
			}
			session.getPlayers().removeIf(existing -> existing.equalsIgnoreCase(player));
			if (hasIgnoredRosterEventForPlayer(session, player, ignoredEvents, Kill.TYPE_LEFT))
			{
				return;
			}
			first = false;
		}
	}

	private List<Session> sessionsFrom(String sessionId)
	{
		Session start = sessions.get(sessionId);
		if (start == null)
		{
			return Collections.emptyList();
		}
		List<Session> thread = getThreadSessions(start);
		int startIndex = thread.indexOf(start);
		if (startIndex < 0)
		{
			return Collections.emptyList();
		}
		return thread.subList(startIndex, thread.size());
	}

	private boolean hasRosterEventForPlayer(Session session, String player, List<Kill> ignoredEvents)
	{
		for (Kill kill : session.getKills())
		{
			if (kill.isRosterEvent()
				&& !ignoredEvents.contains(kill)
				&& player.equalsIgnoreCase(kill.getPlayer()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasIgnoredRosterEventForPlayer(Session session, String player, List<Kill> ignoredEvents, String type)
	{
		for (Kill kill : session.getKills())
		{
			if (ignoredEvents.contains(kill)
				&& type.equalsIgnoreCase(kill.getType())
				&& player.equalsIgnoreCase(kill.getPlayer()))
			{
				return true;
			}
		}
		return false;
	}

	private void addPlayerIfMissing(Session session, String player)
	{
		for (String existing : session.getPlayers())
		{
			if (existing.equalsIgnoreCase(player))
			{
				return;
			}
		}
		session.getPlayers().add(player);
	}

	public void moveKill(int fromIndex, int toIndex)
	{
		List<Kill> allKills = getAllKills();
		if (fromIndex < 0 || fromIndex >= allKills.size() || toIndex < 0 || toIndex > allKills.size())
		{
			return;
		}
		if (toIndex == fromIndex || toIndex == fromIndex + 1)
		{
			return;
		}

		Kill kill = allKills.get(fromIndex);
		List<Kill> remainingKills = new ArrayList<>(allKills);
		remainingKills.remove(fromIndex);

		int insertionIndex = toIndex;
		if (fromIndex < insertionIndex)
		{
			insertionIndex--;
		}
		insertionIndex = Math.max(0, Math.min(insertionIndex, remainingKills.size()));

		String targetSessionId = currentSessionId;
		if (!remainingKills.isEmpty())
		{
			int targetIndex = insertionIndex < remainingKills.size() ? insertionIndex : remainingKills.size() - 1;
			targetSessionId = remainingKills.get(targetIndex).getSessionId();
		}

		removeKillFromSession(kill);

		Session targetSession = sessions.get(targetSessionId);
		if (targetSession == null)
		{
			targetSession = getCurrentSession().orElse(null);
		}
		if (targetSession == null)
		{
			invalidateKillsCache();
			saveToConfig();
			return;
		}

		Kill movedKill = kill;
		if (!targetSession.getId().equals(kill.getSessionId()))
		{
			movedKill = copyKillForSession(kill, targetSession.getId());
		}

		int localTargetIndex = 0;
		for (int i = 0; i < insertionIndex; i++)
		{
			if (targetSession.getId().equals(remainingKills.get(i).getSessionId()))
			{
				localTargetIndex++;
			}
		}
		targetSession.getKills().add(localTargetIndex, movedKill);

		invalidateKillsCache();
		saveToConfig();
	}

	private void removeKillFromSession(Kill kill)
	{
		for (Session s : sessions.values())
		{
			if (s.getKills().remove(kill))
			{
				return;
			}
		}
	}

	private Kill copyKillForSession(Kill kill, String sessionId)
	{
		Kill copy = new Kill(sessionId, kill.getPlayer(), kill.getAmount(), kill.getAt());
		copy.setType(kill.getType());
		return copy;
	}
	private String currentSessionId;
	@Getter
	private boolean historyLoaded;

	/**
	 * Construct a new ManagerSession bound to the given PluginConfig.
	 * This instance owns all in-memory session state and persists it via the config.
	 *
	 * @param config backing configuration/store used to load and save state
	 */
	@Inject
	public ManagerSession(PluginConfig config, ManagerKnownPlayers playerManager, ManagerPlugin pluginManager, Gson gson)
	{
		this.config = config;
		this.playerManager = playerManager;
		// Use injected client's Gson, customize via newBuilder per guidelines
		this.gson = gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
		this.pluginManager = pluginManager;
		this.splitCalculator = new SplitCalculator();
	}

	/**
	 * Utility: convert null to empty string for config storage.
	 */
	private static String nullToEmpty(String s)
	{
		return s == null ? "" : s;
	}

	/**
	 * Utility: convert empty string to null when reading config values.
	 */
	private static String emptyToNull(String s)
	{
		return s == null || s.isEmpty() ? null : s;
	}

	private String resolveMainName(String player)
	{
		if (player == null)
		{
			return null;
		}
		String trimmed = player.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		return playerManager.getMainName(trimmed);
	}

	/**
	 * Loads configuration data into the application's runtime structures.
	 * <p>
	 * This method performs the following operations:
	 * <p>
	 * 1. Clears the session map and populates it with sessions retrieved from a
	 * JSON array in the configuration. Each session is parsed and added to
	 * the map by its ID.
	 * <p>
	 * 2. Updates the current session ID and clears it when the persisted id
	 * does not point to a loaded session.
	 */
	public void loadFromConfig()
	{
		sessions.clear();
		String json = config.sessionsJson();
		if (json != null && !json.isEmpty())
		{
			try
			{
				Session[] arr = gson.fromJson(json, Session[].class);
				if (arr != null)
				{
					for (Session s : arr)
					{
						if (s != null && s.getId() != null)
						{
							sessions.put(s.getId(), s);
						}
					}
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load sessions from config JSON", e);
			}
		}

		// Invalidate any cached mother->kills when loading fresh data
		motherKillsCache.clear();

		currentSessionId = emptyToNull(config.currentSessionId());
		if (currentSessionId != null && !sessions.containsKey(currentSessionId))
		{
			log.warn("Configured current session id {} was not found in persisted sessions", currentSessionId);
			currentSessionId = null;
		}
		Session current = getCurrentSession().orElse(null);
		historyLoaded = current != null && !current.isActive() && config.historyLoaded();
	}

	/**
	 * Persist sessions, current state, known players, and alt mappings to PluginConfig.
	 */
	public void saveToConfig()
	{
		Session[] arr = sessions.values().toArray(new Session[0]);
		try
		{
			config.sessionsJson(gson.toJson(arr));
			config.currentSessionId(nullToEmpty(currentSessionId));
			config.historyLoaded(historyLoaded);
		}
		catch (Exception e)
		{
			log.warn("Failed to save sessions to config", e);
		}
	}

	/**
	 * Export all sessions as JSON for sharing or backups.
	 */
	public String exportAllSessionsJson()
	{
		return gson.toJson(sessions.values());
	}

	/**
	 * Export all completed history threads as JSON.
	 */
	public String exportHistorySessionsJson()
	{
		Set<String> historyRootIds = getHistorySessionsNewestFirst().stream()
			.map(Session::getId)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		List<Session> historySessions = sessions.values().stream()
			.filter(session -> historyRootIds.contains(getRootSessionId(session)))
			.collect(Collectors.toList());
		return gson.toJson(historySessions);
	}

	/**
	 * Export a single session by id as JSON.
	 *
	 * @return JSON for the specified session, or empty string when not found
	 */
	public String exportSessionJson(String sessionId)
	{
		Session session = sessions.get(sessionId);
		return session == null ? "" : gson.toJson(session);
	}

	/**
	 * Export the selected history thread as JSON, including root and child segments.
	 *
	 * @return JSON for the selected session thread, or empty string when not found
	 */
	public String exportSessionThreadJson(String sessionId)
	{
		Session selected = sessions.get(sessionId);
		if (selected == null)
		{
			return "";
		}
		String rootId = getRootSessionId(selected);
		List<Session> threadSessions = sessions.values().stream()
			.filter(session -> rootId.equals(getRootSessionId(session)))
			.collect(Collectors.toList());
		return gson.toJson(threadSessions);
	}

	private String getRootSessionId(Session session)
	{
		return session.getMotherId() == null ? session.getId() : session.getMotherId();
	}

	/**
	 * @return unmodifiable set of all known player names (mains and alts).
	 */
	public Set<String> getKnownPlayers()
	{
		Set<String> mains = playerManager.getKnownMains();
		if (mains == null)
		{
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(mains);
	}

	/**
	 * Compute known main players not currently active in the session roster.
	 *
	 * @return set of eligible names to add to the current session
	 */
	public Set<String> getNonActivePlayers()
	{
		Session curr = getCurrentSession().orElse(null);
		Set<String> mains = playerManager.getKnownMains();
		if (mains == null)
		{
			return Collections.emptySet();
		}

		if (curr == null || !curr.isActive())
		{
			return Collections.unmodifiableSet(mains);
		}

		Set<String> nonActivePlayers = mains.stream()
			.filter(p -> curr.getPlayers().stream().noneMatch(active -> active.equalsIgnoreCase(p)))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return Collections.unmodifiableSet(nonActivePlayers);
	}

	/**
	 * @return Optional of the currently active child session, if any. Empty if no session is active.
	 */
	public Optional<Session> getCurrentSession()
	{
		return Optional.ofNullable(currentSessionId).map(sessions::get);
	}

	/**
	 * @return all sessions (mother and children) sorted by start time descending (newest first).
	 */
	public List<Session> getAllSessionsNewestFirst()
	{
		return sessions.values().stream()
			.sorted(Comparator.comparing(Session::getStart).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * @return completed root sessions sorted by start time descending for the history picker.
	 */
	public List<Session> getHistorySessionsNewestFirst()
	{
		return sessions.values().stream()
			.filter(session -> session.getMotherId() == null)
			.filter(session -> !session.isActive())
			.sorted(Comparator.comparing(Session::getStart).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * Exit read-only history mode and return to live mode.
	 * Persists the flag immediately.
	 */
	public void unloadHistory()
	{
		if (historyLoaded)
		{
			currentSessionId = null;
		}
		historyLoaded = false;
		saveToConfig();
	}

	/**
	 * Enter read-only history mode by selecting a session to view.
	 * Requires that no active session is running. Persists the flag immediately.
	 *
	 * @param sessionId id of the session (mother or child) to load
	 * @return the loaded session if found and preconditions met; empty otherwise
	 */
	public Optional<Session> loadHistory(String sessionId)
	{
		if (hasActiveSession())
		{
			return Optional.empty(); // must stop active first
		}
		Session s = sessions.get(sessionId);
		if (s == null || s.isActive())
		{
			return Optional.empty();
		}
		currentSessionId = s.getId();
		historyLoaded = true;
		saveToConfig();
		return Optional.of(s);
	}

	/**
	 * @return true if there is a current child session and its end time is null (active).
	 */
	public boolean hasActiveSession()
	{
		return getCurrentSession().map(Session::isActive).orElse(false);
	}

	/**
	 * Start a new session thread consisting of a mother session and an initial active child.
	 * Fails if history mode is on or another session is currently active.
	 *
	 * @return the newly created active child session, if started
	 */
	public Optional<Session> startSession()
	{
		if (historyLoaded)
		{
			return Optional.empty();
		}
		if (hasActiveSession())
		{
			return Optional.empty();
		}

		// Create mother and an initial child immediately (to mirror sheet)
		Session mother = new Session(newId(), Instant.now(), null);
		sessions.put(mother.getId(), mother);
		// initialize empty cache list for this mother thread
		motherKillsCache.put(mother.getId(), new ArrayList<>());

		Session child = new Session(newId(), Instant.now(), mother.getId());
		sessions.put(child.getId(), child);

		currentSessionId = child.getId();
		saveToConfig();
		if (pluginManager != null)
		{
			pluginManager.updateChatWarningStatus();
		}
		return Optional.of(child);
	}

	/**
	 * Stop the currently active child session. If its mother session is still active,
	 * it will be ended as well. No-op in history mode.
	 *
	 * @return true if an active session was stopped
	 */
	public boolean stopSession()
	{
		if (historyLoaded)
		{
			return false;
		}

		Session curr = getCurrentSession().orElse(null);
		if (curr == null || !curr.isActive())
		{
			return false;
		}

		curr.setEnd(Instant.now());

		// If child has a mother which is active, end mother too.
		if (curr.getMotherId() != null)
		{
			Session mother = sessions.get(curr.getMotherId());
			if (mother != null && mother.isActive())
			{
				mother.setEnd(Instant.now());
			}
		}

		currentSessionId = null;
		saveToConfig();
		if (pluginManager != null)
		{
			pluginManager.updateChatWarningStatus();
		}
		return true;
	}

	/**
	 * Add a player to the currently active child session. If the child already has kills recorded,
	 * a new child session is forked (same mother), roster is copied, the player is added, and the
	 * previous child is ended to preserve historical rosters per split segment.
	 * Alt names are resolved to main before checks. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @return true if the roster changed (player added)
	 */
	public boolean addPlayerToActive(String player)
	{
		if (historyLoaded)
		{
			return false;
		}
		Session curr = getCurrentSession().orElse(null);
		if (curr == null || !curr.isActive())
		{
			return false;
		}

		String mainPlayer = resolveMainName(player);
		if (mainPlayer == null || mainPlayer.isBlank())
		{
			return false;
		}
		final String fMain = mainPlayer;
		if (curr.getPlayers().stream().anyMatch(p -> p.equalsIgnoreCase(fMain)))
		{
			// Player (main) already in session
			return false;
		}

		if (curr.hasKills())
		{
			// Create a new child session, copy players, add this player, end current child
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			Session newChild = new Session(newId(), Instant.now(), motherId);
			// copy players
			newChild.getPlayers().addAll(curr.getPlayers());
			// add the new player (main)
			newChild.getPlayers().add(fMain);

			// End current child (but keep kills)
			curr.setEnd(Instant.now());

			// Record a JOINED event kill in the new child
			Kill joinEvent = new Kill(newChild.getId(), fMain, 0L, Instant.now());
			joinEvent.setType(Kill.TYPE_JOINED);
			newChild.getKills().add(joinEvent);

			// Update mother cache incrementally
			motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(joinEvent);

			// Activate new child
			sessions.put(newChild.getId(), newChild);
			currentSessionId = newChild.getId();
		}
		else
		{
			curr.getPlayers().add(fMain);
			// Record a JOINED event kill in the current child (no kills yet)
			Kill joinEvent = new Kill(curr.getId(), fMain, 0L, Instant.now());
			joinEvent.setType(Kill.TYPE_JOINED);
			curr.getKills().add(joinEvent);

			// Update mother cache incrementally
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(joinEvent);
		}
		saveToConfig();
		return true;
	}

	/**
	 * Remove a player from the active child session. If the current child already has kills,
	 * a new child is created (same mother) without this player, and the current child is ended
	 * to keep per-segment rosters intact. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @return true if the roster changed (player removed)
	 */
	public boolean removePlayerFromSession(String player)
	{
		if (historyLoaded)
		{
			return false;
		}
		Session curr = getCurrentSession().orElse(null);
		if (curr == null || !curr.isActive())
		{
			return false;
		}

		if (player == null)
		{
			return false;
		}
		player = resolveMainName(player);
		if (player == null || player.isBlank())
		{
			return false;
		}
		String resolvedPlayer = player;
		if (curr.getPlayers().stream().noneMatch(p -> p.equalsIgnoreCase(resolvedPlayer)))
		{
			return false;
		}

		if (curr.hasKills())
		{
			// Create a new child without this player, end current child
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			Session newChild = new Session(newId(), Instant.now(), motherId);
			String finalPlayer = player;
			newChild.getPlayers().addAll(
				curr.getPlayers().stream().filter(p -> !p.equalsIgnoreCase(finalPlayer)).collect(Collectors.toList())
			);

			// End the current child
			curr.setEnd(Instant.now());

			// Record a LEFT event kill in the new child
			Kill leaveEvent = new Kill(newChild.getId(), finalPlayer, 0L, Instant.now());
			leaveEvent.setType(Kill.TYPE_LEFT);
			newChild.getKills().add(leaveEvent);

			// Update mother cache incrementally
			motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);

			sessions.put(newChild.getId(), newChild);
			currentSessionId = newChild.getId();
		}
		else
		{
			String finalPlayer = player;
			curr.getPlayers().removeIf(p -> p.equalsIgnoreCase(finalPlayer));
			// Record a LEFT event kill in the current child (no kills yet)
			Kill leaveEvent = new Kill(curr.getId(), player, 0L, Instant.now());
			leaveEvent.setType(Kill.TYPE_LEFT);
			curr.getKills().add(leaveEvent);

			// Update mother cache incrementally
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);
		}
		saveToConfig();
		return true;
	}

	/**
	 * Record a kill value for a player in the active session. The player is resolved to its main
	 * and must be on the active roster. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @param amount value in coins (may be negative if allowed by config)
	 * @return true if recorded
	 */
	public boolean addKill(String player, Long amount)
	{
		if (historyLoaded)
		{
			return false;
		}

		Session currentSession = getCurrentSession().orElse(null);
		if (currentSession == null || !currentSession.isActive())
		{
			return false;
		}
		if (amount == null)
		{
			return false;
		}

		String mainPlayer = resolveMainName(player);
		if (mainPlayer == null || mainPlayer.isBlank())
		{
			return false;
		}
		if (currentSession.getPlayers().stream().noneMatch(p -> p.equalsIgnoreCase(mainPlayer)))
		{
			return false;
		}

		Kill newKill = new Kill(currentSession.getId(), mainPlayer, amount, Instant.now());
		currentSession.getKills().add(newKill);

		// Update mother cache incrementally
		String motherId = currentSession.getMotherId() == null ? currentSession.getId() : currentSession.getMotherId();
		motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(newKill);

		saveToConfig();
		return true;
	}

	/**
	 * Read-only view of the queued pending values detected from chat.
	 */
	public List<PendingValue> getPendingValues()
	{
		return Collections.unmodifiableList(pendingValues);
	}

	/**
	 * Queue a new pending value. The suggested player is normalized to its main. If configured,
	 * the value may be auto-applied (when the player is currently in session), in which case this
	 * method records a kill and does not queue. A small cap prevents unbounded growth.
	 *
	 * @param pendingValue pending value payload; null is ignored
	 */
	public void addPendingValue(PendingValue pendingValue)
	{
		if (pendingValue == null || pendingValue.getValue() == null)
		{
			return;
		}

		// Normalize suggestedPlayer player to main for all downstream uses
		String suggestedPlayer = pendingValue.getSuggestedPlayer();
		String resolvedPlayer = resolveMainName(suggestedPlayer);

		pendingValue.setSuggestedPlayer(resolvedPlayer);

		if (resolvedPlayer != null && !resolvedPlayer.isBlank() && !playerManager.isKnownPlayer(resolvedPlayer))
		{
			playerManager.addKnownPlayer(resolvedPlayer);
		}

		// Auto-apply if configured and player already in session
		if (resolvedPlayer != null && config.autoApplyWhenInSession() && hasActiveSession())
		{
			Session currentSession = getCurrentSession().orElse(null);
			if (currentSession != null && currentSession.getPlayers().stream().anyMatch(p -> p.equalsIgnoreCase(resolvedPlayer)))
			{
				addKill(resolvedPlayer, pendingValue.getValue());
				return; // do not queue
			}
		}

		// Limit size to avoid unbounded growth
		if (pendingValues.size() >= 100)
		{
			pendingValues.remove(0);
		}
		pendingValues.add(pendingValue);
	}

	/**
	 * Remove a pending value by its id.
	 *
	 * @param id unique pending id
	 * @return true if removed
	 */
	public boolean removePendingValueById(String id)
	{
		return pendingValues.removeIf(p -> p.getId().equals(id));
	}

	/**
	 * Apply a pending value to a specific player and remove it from the queue.
	 * The player is resolved to its main; the underlying addKill() enforces roster rules.
	 *
	 * @param id     pending id
	 * @param player target player (main or alt)
	 * @return true if applied
	 */
	public boolean applyPendingValueToPlayer(String id, String player)
	{
		PendingValue pv = pendingValues.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
		if (pv == null)
		{
			return false;
		}
		String target = resolveMainName(player);
		boolean ok = addKill(target, pv.getValue());
		if (ok)
		{
			pendingValues.remove(pv);
		}
		return ok;
	}

	/**
	 * Returns true if the given player (main or alt) is present in the roster of the current session.
	 * Alts are resolved to their main before the check.
	 */
	public boolean currentSessionHasPlayer(String player)
	{
		return sessionHasPlayer(player, getCurrentSession().orElse(null));
	}

	/**
	 * Returns true if the given player (main or alt) is present in the roster of the provided session.
	 * Alts are resolved to their main before the check.
	 */
	public boolean sessionHasPlayer(String player, Session session)
	{
		if (session == null)
		{
			return false;
		}

		String mainPlayer = resolveMainName(player);
		if (mainPlayer == null)
		{
			return false;
		}

		return session.getPlayers().stream().anyMatch(e ->
			e.equalsIgnoreCase(mainPlayer));
	}

	public void init()
	{
		loadFromConfig();
	}

	/**
	 * Compute metrics for the given session's thread (mother + children) including only currently active players.
	 *
	 * @param s a session within the thread to compute against
	 * @return list of PlayerMetrics rows (non-zero totals only)
	 */
	public List<PlayerMetrics> computeMetricsFor(Session s)
	{
		return computeMetricsFor(s, false);
	}

	/**
	 * Compute metrics for the given session's thread (mother + children).
	 * When includeNonActivePlayers is true, any player appearing in the thread or known list may be included.
	 * Otherwise, only players on the provided session's current roster are considered for output.
	 * Players with zero total and zero split are omitted.
	 *
	 * @param s                       a session within the thread to compute against
	 * @param includeNonActivePlayers whether to include players outside the current roster
	 * @return list of PlayerMetrics rows
	 */
	public List<PlayerMetrics> computeMetricsFor(Session s, boolean includeNonActivePlayers)
	{
		if (s == null)
		{
			return List.of();
		}

		return splitCalculator.compute(
			s,
			getThreadSessions(s),
			playerManager.getKnownPlayers(),
			includeNonActivePlayers,
			buildGeTaxSettings());
	}

	private SplitCalculator.GeTaxSettings buildGeTaxSettings()
	{
		if (config == null || !config.accountForGeTax())
		{
			return SplitCalculator.GeTaxSettings.disabled();
		}

		return new SplitCalculator.GeTaxSettings(
			true,
			parseGeTaxMinimumValue(config.geTaxMinimumValue()),
			sanitizeGeTaxPercent(config.geTaxPercent()),
			PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT);
	}

	private long parseGeTaxMinimumValue(String configuredValue)
	{
		String valueToParse = configuredValue == null || configuredValue.trim().isEmpty()
			? PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE
			: configuredValue.trim();
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(valueToParse, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse GE tax minimum value {}; using default {}", valueToParse, PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, e);
			return defaultGeTaxMinimumValue();
		}
	}

	private long defaultGeTaxMinimumValue()
	{
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse built-in GE tax minimum {}; disabling GE tax threshold", PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, e);
			return 0L;
		}
	}

	private double sanitizeGeTaxPercent(double configuredPercent)
	{
		if (Double.isNaN(configuredPercent) || Double.isInfinite(configuredPercent) || configuredPercent < 0.0d)
		{
			log.warn("Invalid GE tax percent {}; using default {}", configuredPercent, PluginConfig.DEFAULT_GE_TAX_PERCENT);
			return PluginConfig.DEFAULT_GE_TAX_PERCENT;
		}
		return configuredPercent;
	}

	private List<Session> getThreadSessions(Session s)
	{
		String rootId = (s.getMotherId() == null) ? s.getId() : s.getMotherId();
		List<Session> thread = new ArrayList<>();
		Session mother = sessions.get(rootId);
		if (mother != null)
		{
			thread.add(mother);
		}
		for (Session candidate : sessions.values())
		{
			if (rootId.equals(candidate.getMotherId()))
			{
				thread.add(candidate);
			}
		}
		return thread;
	}


	/**
	 * Get all kills from all sessions that share the same mother session as the current session.
	 * Uses a cached list per mother to avoid recomputing on every UI update.
	 *
	 * @return a list containing all kill records from sessions with the same mother
	 */
	public List<Kill> getAllKills()
	{
		Session curr = getCurrentSession().orElse(null);
		if (curr == null)
		{
			return new ArrayList<>();
		}
		// Determine the mother id for this thread
		String motherId = (curr.getMotherId() == null) ? curr.getId() : curr.getMotherId();
		// If cached, return it
		List<Kill> cached = motherKillsCache.get(motherId);
		if (cached != null)
		{
			return Collections.unmodifiableList(cached);
		}
		// Build once in persisted session/list order. The edit-history UI can reorder
		// kills, so sorting by timestamp here would discard those edits on refresh.
		List<Kill> built = new ArrayList<>();
		for (Session session : sessions.values())
		{
			if (motherId.equals(session.getId()) || motherId.equals(session.getMotherId()))
			{
				built.addAll(session.getKills());
			}
		}
		motherKillsCache.put(motherId, built);
		return built;
	}


	/**
	 * Generate a random unique id for sessions.
	 */
	private String newId()
	{
		return UUID.randomUUID().toString();
	}


}
