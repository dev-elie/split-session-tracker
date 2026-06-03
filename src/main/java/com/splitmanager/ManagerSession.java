package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.SplitEvent;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.models.SettlementConfigSnapshot;
import com.splitmanager.persistence.SessionStorage;
import com.splitmanager.persistence.SessionStorageData;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages sessions, persistence, and all the logic for roster changes,
 * child sessions, and live split calculations.
 */
@Singleton
@Slf4j
public class ManagerSession
{
	private static final String MIN_HISTORY_EDIT_VERSION = Session.CURRENT_PLUGIN_VERSION;

	private final Gson gson;
	private final Map<String, Session> sessions = new LinkedHashMap<>();
	private final List<PendingValue> pendingValues = new ArrayList<>();
	private final ManagerKnownPlayers playerManager;
	private final PluginConfig config;
	private final ManagerPlugin pluginManager;
	private final SessionStorage sessionStorage;
	private final SplitCalculator splitCalculator;
	// Cache of all events grouped by mother session id to avoid recomputing on every UI refresh
	private final Map<String, List<SplitEvent>> motherEventsCache = new LinkedHashMap<>();
	@Setter
	private BooleanSupplier historyEditWarningHandler;
	private boolean historyEditWarningAccepted;
	@Getter
	private boolean historyDirty;
	private String historyOriginalSessionsJson;
	private String currentSessionId;
	@Getter
	private boolean historyLoaded;

	/**
	 * Construct a new ManagerSession bound to the session storage.
	 * This instance owns all in-memory session state and persists it via a versioned JSON file.
	 *
	 * @param config backing configuration/store used to load and save state
	 */
	@Inject
	public ManagerSession(PluginConfig config, ManagerKnownPlayers playerManager, ManagerPlugin pluginManager, Gson gson,
	                      SessionStorage sessionStorage)
	{
		this.config = config;
		this.playerManager = playerManager;
		// Use injected client's Gson, customize via newBuilder per guidelines
		this.gson = gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
		this.pluginManager = pluginManager;
		this.sessionStorage = sessionStorage;
		this.splitCalculator = new SplitCalculator();
	}

	public ManagerSession(PluginConfig config, ManagerKnownPlayers playerManager, ManagerPlugin pluginManager, Gson gson)
	{
		this(config, playerManager, pluginManager, gson, SessionStorage.legacyConfig(config, gson));
	}

	/**
	 * Utility: convert empty string to null when reading config values.
	 */
	private static String emptyToNull(String s)
	{
		return s == null || s.isEmpty() ? null : s;
	}

	/**
	 * Force a full rebuild of the events cache for the current session's thread.
	 */
	public void invalidateEventsCache()
	{
		getCurrentSession().ifPresent(curr -> {
			String motherId = (curr.getMotherId() == null) ? curr.getId() : curr.getMotherId();
			motherEventsCache.remove(motherId);
		});
	}

	public void insertLootAt(int index, String player, Long amount)
	{
		Session editable = getCurrentEditableSession().orElse(null);
		if (editable == null || amount == null)
		{
			return;
		}

		List<SplitEvent> allEvents = getAllEvents();
		if (index < 0 || index > allEvents.size())
		{
			return;
		}

		String mainPlayer = resolveMainName(player);
		if (mainPlayer == null)
		{
			return;
		}
		if (!prepareHistoryMutation())
		{
			return;
		}

		String targetSessionId = editable.getId();
		if (!allEvents.isEmpty())
		{
			int targetIndex = index < allEvents.size() ? index : allEvents.size() - 1;
			targetSessionId = allEvents.get(targetIndex).getSessionId();
		}

		Session targetSession = sessions.get(targetSessionId);
		if (targetSession == null)
		{
			targetSession = editable;
		}

		SplitEvent event = new SplitEvent(targetSession.getId(), mainPlayer, amount, Instant.now());
		event.setType(SplitEvent.TYPE_LOOT);

		int localTargetIndex = 0;
		for (int i = 0; i < index; i++)
		{
			if (targetSession.getId().equals(allEvents.get(i).getSessionId()))
			{
				localTargetIndex++;
			}
		}
		targetSession.getEvents().add(localTargetIndex, event);
		invalidateEventsCache();
		saveAfterMutation();
	}

	public void removeEventAt(int index)
	{
		removeEventsAt(Collections.singletonList(index));
	}

	public void removeEventsAt(List<Integer> indices)
	{
		List<SplitEvent> allEvents = getAllEvents();
		if (indices == null || indices.isEmpty())
		{
			return;
		}
		List<SplitEvent> eventsToRemove = new ArrayList<>();
		for (Integer index : indices)
		{
			if (index != null && index >= 0 && index < allEvents.size())
			{
				SplitEvent event = allEvents.get(index);
				if (!eventsToRemove.contains(event))
				{
					eventsToRemove.add(event);
				}
			}
		}
		if (eventsToRemove.isEmpty())
		{
			return;
		}
		if (!prepareHistoryMutation())
		{
			return;
		}

		repairRostersBeforeRemoving(eventsToRemove);
		for (Session s : sessions.values())
		{
			s.getEvents().removeAll(eventsToRemove);
		}
		invalidateEventsCache();
		saveAfterMutation();
	}

	private void repairRostersBeforeRemoving(List<SplitEvent> eventsToRemove)
	{
		for (SplitEvent event : eventsToRemove)
		{
			if (event == null || !event.isRosterEvent())
			{
				continue;
			}
			if (SplitEvent.TYPE_LEFT.equalsIgnoreCase(event.getType()))
			{
				addPlayerFromSessionUntilNextEvent(event.getSessionId(), event.getPlayer(), eventsToRemove);
			}
			else if (SplitEvent.TYPE_JOINED.equalsIgnoreCase(event.getType()))
			{
				removePlayerFromSessionUntilLinkedLeft(event.getSessionId(), event.getPlayer(), eventsToRemove);
			}
		}
	}

	private void addPlayerFromSessionUntilNextEvent(String sessionId, String player, List<SplitEvent> ignoredEvents)
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

	private void removePlayerFromSessionUntilLinkedLeft(String sessionId, String player, List<SplitEvent> ignoredEvents)
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
			if (hasIgnoredRosterEventForPlayer(session, player, ignoredEvents, SplitEvent.TYPE_LEFT))
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

	private boolean hasRosterEventForPlayer(Session session, String player, List<SplitEvent> ignoredEvents)
	{
		for (SplitEvent event : session.getEvents())
		{
			if (event.isRosterEvent()
				&& !ignoredEvents.contains(event)
				&& player.equalsIgnoreCase(event.getPlayer()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasIgnoredRosterEventForPlayer(Session session, String player, List<SplitEvent> ignoredEvents, @SuppressWarnings("SameParameterValue") String type)
	{
		for (SplitEvent event : session.getEvents())
		{
			if (ignoredEvents.contains(event)
				&& type.equalsIgnoreCase(event.getType())
				&& player.equalsIgnoreCase(event.getPlayer()))
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

	public boolean moveEvent(int fromIndex, int toIndex)
	{
		List<SplitEvent> allEvents = getAllEvents();
		if (fromIndex < 0 || fromIndex >= allEvents.size() || toIndex < 0 || toIndex > allEvents.size())
		{
			return false;
		}
		if (toIndex == fromIndex || toIndex == fromIndex + 1)
		{
			return false;
		}

		SplitEvent event = allEvents.get(fromIndex);
		List<SplitEvent> remainingEvents = new ArrayList<>(allEvents);
		remainingEvents.remove(fromIndex);

		int insertionIndex = toIndex;
		if (fromIndex < insertionIndex)
		{
			insertionIndex--;
		}
		insertionIndex = Math.min(insertionIndex, remainingEvents.size());
		List<SplitEvent> reorderedEvents = new ArrayList<>(remainingEvents);
		reorderedEvents.add(insertionIndex, event);
		if (!hasValidRosterEventOrder(reorderedEvents))
		{
			return false;
		}
		if (!prepareHistoryMutation())
		{
			return false;
		}

		String targetSessionId = currentSessionId;
		if (!remainingEvents.isEmpty())
		{
			int targetIndex = insertionIndex < remainingEvents.size() ? insertionIndex : remainingEvents.size() - 1;
			targetSessionId = remainingEvents.get(targetIndex).getSessionId();
		}

		removeEventFromSession(event);

		Session targetSession = sessions.get(targetSessionId);
		if (targetSession == null)
		{
			targetSession = getCurrentSession().orElse(null);
		}
		if (targetSession == null)
		{
			invalidateEventsCache();
			saveAfterMutation();
			return true;
		}

		SplitEvent movedEvent = event;
		if (!targetSession.getId().equals(event.getSessionId()))
		{
			movedEvent = copyEventForSession(event, targetSession.getId());
		}

		int localTargetIndex = 0;
		for (int i = 0; i < insertionIndex; i++)
		{
			if (targetSession.getId().equals(remainingEvents.get(i).getSessionId()))
			{
				localTargetIndex++;
			}
		}
		targetSession.getEvents().add(localTargetIndex, movedEvent);
		if (movedEvent.isRosterEvent())
		{
			rebuildThreadRosters(targetSession);
		}

		invalidateEventsCache();
		saveAfterMutation();
		return true;
	}

	private void rebuildThreadRosters(Session session)
	{
		List<Session> thread = getThreadSessions(session);
		Set<String> activeRoster = new LinkedHashSet<>();
		for (Session part : thread)
		{
			if (part.getMotherId() == null)
			{
				continue;
			}

			Set<String> segmentRoster = new LinkedHashSet<>(activeRoster);
			for (SplitEvent event : part.getEvents())
			{
				applyRosterEvent(segmentRoster, event);
			}
			part.getPlayers().clear();
			part.getPlayers().addAll(segmentRoster);
			activeRoster = segmentRoster;
		}
	}

	private void applyRosterEvent(Set<String> roster, SplitEvent event)
	{
		if (event == null || event.getPlayer() == null)
		{
			return;
		}
		if (SplitEvent.TYPE_JOINED.equalsIgnoreCase(event.getType()))
		{
			addPlayerName(roster, event.getPlayer());
		}
		else if (SplitEvent.TYPE_LEFT.equalsIgnoreCase(event.getType()))
		{
			removePlayerName(roster, event.getPlayer());
		}
	}

	private void addPlayerName(Set<String> roster, String player)
	{
		for (String existing : roster)
		{
			if (existing.equalsIgnoreCase(player))
			{
				return;
			}
		}
		roster.add(player);
	}

	private void removePlayerName(Set<String> roster, String player)
	{
		roster.removeIf(existing -> existing.equalsIgnoreCase(player));
	}

	private boolean hasValidRosterEventOrder(List<SplitEvent> events)
	{
		Map<String, Boolean> seenJoinByPlayer = new LinkedHashMap<>();
		Set<String> playersWithJoin = events.stream()
			.filter(event -> event != null && SplitEvent.TYPE_JOINED.equalsIgnoreCase(event.getType()) && event.getPlayer() != null)
			.map(event -> event.getPlayer().toLowerCase(Locale.ENGLISH))
			.collect(Collectors.toSet());
		for (SplitEvent event : events)
		{
			if (event == null || event.getPlayer() == null)
			{
				continue;
			}
			String player = event.getPlayer().toLowerCase(Locale.ENGLISH);
			if (SplitEvent.TYPE_JOINED.equalsIgnoreCase(event.getType()))
			{
				seenJoinByPlayer.put(player, true);
			}
			else if (SplitEvent.TYPE_LEFT.equalsIgnoreCase(event.getType())
				&& playersWithJoin.contains(player)
				&& !seenJoinByPlayer.containsKey(player))
			{
				return false;
			}
		}
		return true;
	}

	private void removeEventFromSession(SplitEvent event)
	{
		for (Session s : sessions.values())
		{
			if (s.getEvents().remove(event))
			{
				return;
			}
		}
	}

	private SplitEvent copyEventForSession(SplitEvent event, String sessionId)
	{
		SplitEvent copy = new SplitEvent(sessionId, event.getPlayer(), event.getAmount(), event.getAt());
		copy.setType(event.getType());
		return copy;
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
	 * Loads persisted session data into the application's runtime structures.
	 * <p>
	 * This method performs the following operations:
	 * <p>
	 * 1. Clears the session map and populates it with sessions retrieved from a
	 * versioned JSON document. Each session is parsed and added to
	 * the map by its ID.
	 * <p>
	 * 2. Updates the current session ID and clears it when the persisted id
	 * does not point to a loaded session.
	 */
	public void loadFromConfig()
	{
		sessions.clear();
		SessionStorageData data = loadPersistedData();
		for (Session s : data.getSessions())
		{
			if (s != null && s.getId() != null)
			{
				sessions.put(s.getId(), s);
			}
		}

		// Invalidate any cached mother->events when loading fresh data
		motherEventsCache.clear();

		currentSessionId = emptyToNull(data.getCurrentSessionId());
		if (currentSessionId != null && !sessions.containsKey(currentSessionId))
		{
			log.warn("Persisted current session id {} was not found in persisted sessions", currentSessionId);
			currentSessionId = null;
		}
		Session current = getCurrentSession().orElse(null);
		historyLoaded = current != null && !current.isActive() && data.isHistoryLoaded();
	}

	/**
	 * Persist sessions and current view state to the plugin's versioned JSON store.
	 */
	public void saveToConfig()
	{
		if (!sessionStorage.save(buildStorageData()))
		{
			log.warn("Failed to save sessions to {}", sessionStorage.describeLocation());
		}
	}

	private SessionStorageData loadPersistedData()
	{
		if (sessionStorage.exists())
		{
			return sessionStorage.load();
		}

		if (sessionStorage.isLegacyConfigStore() || !sessionStorage.hasLegacyData(config))
		{
			return sessionStorage.load();
		}
		if (!sessionStorage.canMigrateLegacy(config))
		{
			return sessionStorage.load();
		}

		SessionStorageData legacyData = sessionStorage.loadLegacy(config);
		if (sessionStorage.save(legacyData))
		{
			sessionStorage.clearLegacySessionConfig(config);
		}
		else
		{
			log.warn("Legacy session config was not cleared because migration to {} failed", sessionStorage.describeLocation());
		}
		return legacyData;
	}

	private SessionStorageData buildStorageData()
	{
		SessionStorageData data = new SessionStorageData();
		data.setSessions(new ArrayList<>(sessions.values()));
		data.setCurrentSessionId(currentSessionId);
		data.setHistoryLoaded(historyLoaded);
		return data;
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

	/**
	 * Import one or more completed history threads from JSON.
	 * The payload must contain at least one closed mother session and all children must reference
	 * a root session present in the same payload. Imported sessions are remapped to fresh ids.
	 *
	 * @param json JSON payload containing an array of completed sessions
	 * @return number of imported mother sessions, or 0 when the payload is invalid
	 */
	public int importHistorySessionsJson(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return 0;
		}

		try
		{
			Session[] arr = gson.fromJson(json, Session[].class);
			if (arr == null || arr.length == 0)
			{
				return 0;
			}

			LinkedHashMap<String, Session> importedById = new LinkedHashMap<>();
			for (Session session : arr)
			{
				if (!isImportableHistorySession(session))
				{
					return 0;
				}
				if (importedById.put(session.getId(), session) != null)
				{
					return 0;
				}
			}

			List<Session> roots = importedById.values().stream()
				.filter(session -> session.getMotherId() == null)
				.collect(Collectors.toList());
			if (roots.isEmpty())
			{
				return 0;
			}

			for (Session session : importedById.values())
			{
				if (session.getMotherId() == null)
				{
					continue;
				}
				Session mother = importedById.get(session.getMotherId());
				if (mother == null || mother.getMotherId() != null)
				{
					return 0;
				}
			}

			List<Session> importedSessions = new ArrayList<>();
			for (Session root : roots)
			{
				String rootId = root.getId();
				String newRootId = newId();
				importedSessions.add(copySessionForImport(root, newRootId, null));

				List<Session> children = importedById.values().stream()
					.filter(session -> rootId.equals(session.getMotherId()))
					.sorted(Comparator.comparing(Session::getStart))
					.collect(Collectors.toList());
				for (Session child : children)
				{
					importedSessions.add(copySessionForImport(child, newId(), newRootId));
				}
			}

			for (Session session : importedSessions)
			{
				sessions.put(session.getId(), session);
			}
			motherEventsCache.clear();
			saveToConfig();
			return roots.size();
		}
		catch (Exception e)
		{
			log.warn("Failed to import history sessions from JSON", e);
			return 0;
		}
	}

	private String getRootSessionId(Session session)
	{
		return session.getMotherId() == null ? session.getId() : session.getMotherId();
	}

	private boolean isImportableHistorySession(Session session)
	{
		if (session == null || session.getId() == null || session.getStart() == null)
		{
			return false;
		}
		if (session.isActive())
		{
			return false;
		}
		return session.getMotherId() == null || !session.getMotherId().trim().isEmpty();
	}

	private Session copySessionForImport(Session source, String id, String motherId)
	{
		Session copy = new Session(id, source.getStart(), motherId);
		copy.setEnd(source.getEnd());
		copy.setPluginVersion(source.getPluginVersion());
		copy.setSettlementConfigAtStart(source.getSettlementConfigAtStart());
		copy.setSettlementConfigAtEnd(source.getSettlementConfigAtEnd());
		if (source.getPlayers() != null)
		{
			copy.getPlayers().addAll(source.getPlayers());
		}
		if (source.getEvents() != null)
		{
			for (SplitEvent event : source.getEvents())
			{
				if (event == null)
				{
					continue;
				}
				copy.getEvents().add(copyEventForSession(event, id));
			}
		}
		return copy;
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
		Session curr = getCurrentEditableSession().orElse(null);
		Set<String> mains = playerManager.getKnownMains();
		if (mains == null)
		{
			return Collections.emptySet();
		}

		if (curr == null || (!historyLoaded && !curr.isActive()))
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
	 * Exit history mode and return to live mode.
	 * Persists the flag immediately.
	 */
	public void unloadHistory()
	{
		if (historyLoaded)
		{
			currentSessionId = null;
		}
		if (historyDirty)
		{
			restoreOriginalHistorySessions();
		}
		historyLoaded = false;
		historyDirty = false;
		historyEditWarningAccepted = false;
		historyOriginalSessionsJson = null;
		saveToConfig();
	}

	/**
	 * Enter history mode by selecting a completed session to view or edit.
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
		historyOriginalSessionsJson = gson.toJson(sessions.values());
		historyDirty = false;
		historyEditWarningAccepted = false;
		currentSessionId = s.getId();
		historyLoaded = true;
		saveToConfig();
		return Optional.of(s);
	}

	public boolean saveHistoryChanges()
	{
		if (!historyLoaded)
		{
			return false;
		}
		historyDirty = false;
		historyEditWarningAccepted = false;
		historyOriginalSessionsJson = gson.toJson(sessions.values());
		saveToConfig();
		return true;
	}

	public boolean discardHistoryChanges()
	{
		if (!historyLoaded)
		{
			return false;
		}
		String selectedSessionId = currentSessionId;
		if (historyDirty)
		{
			restoreOriginalHistorySessions();
		}
		currentSessionId = selectedSessionId;
		historyLoaded = true;
		historyDirty = false;
		historyEditWarningAccepted = false;
		historyOriginalSessionsJson = gson.toJson(sessions.values());
		saveToConfig();
		return true;
	}

	private void restoreOriginalHistorySessions()
	{
		if (historyOriginalSessionsJson == null || historyOriginalSessionsJson.trim().isEmpty())
		{
			loadFromConfig();
			return;
		}
		try
		{
			Session[] arr = gson.fromJson(historyOriginalSessionsJson, Session[].class);
			sessions.clear();
			if (arr != null)
			{
				for (Session session : arr)
				{
					if (session != null && session.getId() != null)
					{
						sessions.put(session.getId(), session);
					}
				}
			}
			motherEventsCache.clear();
		}
		catch (Exception e)
		{
			log.warn("Failed to restore unsaved history edits; reloading persisted sessions", e);
			loadFromConfig();
		}
	}

	/**
	 * @return true if there is a current child session and its end time is null (active).
	 */
	public boolean hasActiveSession()
	{
		return getCurrentSession().map(Session::isActive).orElse(false);
	}

	public Optional<Session> getCurrentEditableSession()
	{
		Session current = getCurrentSession().orElse(null);
		if (current == null)
		{
			return Optional.empty();
		}
		if (!historyLoaded || current.getMotherId() != null)
		{
			return Optional.of(current);
		}
		List<Session> thread = getThreadSessions(current);
		for (int i = thread.size() - 1; i >= 0; i--)
		{
			Session session = thread.get(i);
			if (session != null && session.getMotherId() != null)
			{
				return Optional.of(session);
			}
		}
		return Optional.empty();
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
		mother.setSettlementConfigAtStart(currentSettlementConfigSnapshot());
		sessions.put(mother.getId(), mother);
		// initialize empty cache list for this mother thread
		motherEventsCache.put(mother.getId(), new ArrayList<>());

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
				mother.setSettlementConfigAtEnd(currentSettlementConfigSnapshot());
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
	 * Add a player to the currently active child session. If the child already has loot events recorded,
	 * a new child session is forked (same mother), roster is copied, the player is added, and the
	 * previous child is ended to preserve historical rosters per split segment.
	 * Alt names are resolved to main before checks. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @return true if the roster changed (player added)
	 */
	public boolean addPlayerToActive(String player)
	{
		Session curr = getCurrentEditableSession().orElse(null);
		if (curr == null || (!historyLoaded && !curr.isActive()))
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
		if (!prepareHistoryMutation())
		{
			return false;
		}

		if (historyLoaded)
		{
			curr.getPlayers().add(fMain);
			SplitEvent joinEvent = new SplitEvent(curr.getId(), fMain, 0L, Instant.now());
			joinEvent.setType(SplitEvent.TYPE_JOINED);
			curr.getEvents().add(joinEvent);
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(joinEvent);
			saveAfterMutation();
			return true;
		}

		if (curr.hasLootEvents())
		{
			// Create a new child session, copy players, add this player, end current child
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			Session newChild = new Session(newId(), Instant.now(), motherId);
			// copy players
			newChild.getPlayers().addAll(curr.getPlayers());
			// add the new player (main)
			newChild.getPlayers().add(fMain);

			// End current child (but keep events)
			curr.setEnd(Instant.now());

			// Record a JOINED event in the new child
			SplitEvent joinEvent = new SplitEvent(newChild.getId(), fMain, 0L, Instant.now());
			joinEvent.setType(SplitEvent.TYPE_JOINED);
			newChild.getEvents().add(joinEvent);

			// Update mother cache incrementally
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(joinEvent);

			// Activate new child
			sessions.put(newChild.getId(), newChild);
			currentSessionId = newChild.getId();
		}
		else
		{
			curr.getPlayers().add(fMain);
			// Record a JOINED event in the current child (no loot events yet)
			SplitEvent joinEvent = new SplitEvent(curr.getId(), fMain, 0L, Instant.now());
			joinEvent.setType(SplitEvent.TYPE_JOINED);
			curr.getEvents().add(joinEvent);

			// Update mother cache incrementally
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(joinEvent);
		}
		saveAfterMutation();
		return true;
	}

	/**
	 * Remove a player from the active child session. If the current child already has loot events,
	 * a new child is created (same mother) without this player, and the current child is ended
	 * to keep per-segment rosters intact. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @return true if the roster changed (player removed)
	 */
	public boolean removePlayerFromSession(String player)
	{
		Session curr = getCurrentEditableSession().orElse(null);
		if (curr == null || (!historyLoaded && !curr.isActive()))
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
		if (!prepareHistoryMutation())
		{
			return false;
		}

		if (historyLoaded)
		{
			String finalPlayer = player;
			curr.getPlayers().removeIf(p -> p.equalsIgnoreCase(finalPlayer));
			SplitEvent leaveEvent = new SplitEvent(curr.getId(), player, 0L, Instant.now());
			leaveEvent.setType(SplitEvent.TYPE_LEFT);
			curr.getEvents().add(leaveEvent);
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);
			saveAfterMutation();
			return true;
		}

		if (curr.hasLootEvents())
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

			// Record a LEFT event in the new child
			SplitEvent leaveEvent = new SplitEvent(newChild.getId(), finalPlayer, 0L, Instant.now());
			leaveEvent.setType(SplitEvent.TYPE_LEFT);
			newChild.getEvents().add(leaveEvent);

			// Update mother cache incrementally
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);

			sessions.put(newChild.getId(), newChild);
			currentSessionId = newChild.getId();
		}
		else
		{
			String finalPlayer = player;
			curr.getPlayers().removeIf(p -> p.equalsIgnoreCase(finalPlayer));
			// Record a LEFT event in the current child (no loot events yet)
			SplitEvent leaveEvent = new SplitEvent(curr.getId(), player, 0L, Instant.now());
			leaveEvent.setType(SplitEvent.TYPE_LEFT);
			curr.getEvents().add(leaveEvent);

			// Update mother cache incrementally
			String motherId = curr.getMotherId() == null ? curr.getId() : curr.getMotherId();
			motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);
		}
		saveAfterMutation();
		return true;
	}

	/**
	 * Record a loot value for a player in the active session. The player is resolved to its main
	 * and must be on the active roster. No-op in history mode.
	 *
	 * @param player display name (main or alt)
	 * @param amount value in coins (may be negative if allowed by config)
	 * @return true if recorded
	 */
	public boolean addLoot(String player, Long amount)
	{
		Session currentSession = getCurrentEditableSession().orElse(null);
		if (currentSession == null || (!historyLoaded && !currentSession.isActive()))
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
		if (!prepareHistoryMutation())
		{
			return false;
		}

		SplitEvent newLoot = new SplitEvent(currentSession.getId(), mainPlayer, amount, Instant.now());
		currentSession.getEvents().add(newLoot);

		// Update mother cache incrementally
		String motherId = currentSession.getMotherId() == null ? currentSession.getId() : currentSession.getMotherId();
		motherEventsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(newLoot);

		saveAfterMutation();
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
	 * method records a loot and does not queue. A small cap prevents unbounded growth.
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
				addLoot(resolvedPlayer, pendingValue.getValue());
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
	 * The player is resolved to its main; the underlying addLoot() enforces roster rules.
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
		boolean ok = addLoot(target, pv.getValue());
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

	public boolean prepareHistoryMutation()
	{
		if (!historyLoaded)
		{
			return true;
		}
		if (isCurrentHistoryEditLocked())
		{
			return false;
		}
		if (!historyEditWarningAccepted)
		{
			boolean accepted = historyEditWarningHandler == null || historyEditWarningHandler.getAsBoolean();
			if (!accepted)
			{
				return false;
			}
			historyEditWarningAccepted = true;
		}
		historyDirty = true;
		return true;
	}

	public boolean isCurrentHistoryEditLocked()
	{
		if (!historyLoaded)
		{
			return false;
		}
		Session current = getCurrentSession().orElse(null);
		return isHistoryEditLocked(current);
	}

	private boolean isHistoryEditLocked(Session session)
	{
		if (session == null)
		{
			return true;
		}
		for (Session threadSession : getThreadSessions(session))
		{
			if (isVersionBefore(threadSession.getPluginVersion(), MIN_HISTORY_EDIT_VERSION))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isVersionBefore(String version, String minimumVersion)
	{
		if (version == null || version.trim().isEmpty())
		{
			return true;
		}
		int[] parsedVersion = parseVersion(version);
		int[] parsedMinimum = parseVersion(minimumVersion);
		for (int i = 0; i < parsedMinimum.length; i++)
		{
			if (parsedVersion[i] != parsedMinimum[i])
			{
				return parsedVersion[i] < parsedMinimum[i];
			}
		}
		return false;
	}

	private static int[] parseVersion(String version)
	{
		int[] parts = new int[]{0, 0, 0};
		String[] tokens = version.split("\\.");
		for (int i = 0; i < parts.length && i < tokens.length; i++)
		{
			try
			{
				parts[i] = Integer.parseInt(tokens[i].replaceAll("[^0-9].*$", ""));
			}
			catch (NumberFormatException e)
			{
				parts[i] = 0;
			}
		}
		return parts;
	}

	public void markHistoryMutation()
	{
		if (historyLoaded)
		{
			historyDirty = true;
			invalidateEventsCache();
			return;
		}
		saveToConfig();
	}

	private void saveAfterMutation()
	{
		if (historyLoaded)
		{
			historyDirty = true;
			return;
		}
		saveToConfig();
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
			buildGeTaxSettings(s));
	}

	public List<PlayerMetrics> computeMetricsFor(Session s,
	                                             boolean includeNonActivePlayers,
	                                             SettlementConfigSnapshot overrideSnapshot)
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
			buildGeTaxSettings(overrideSnapshot));
	}

	private SplitCalculator.GeTaxSettings buildGeTaxSettings(Session session)
	{
		SettlementConfigSnapshot snapshot = getHistoricalSettlementConfigSnapshot(session);
		if (snapshot != null)
		{
			return buildGeTaxSettings(snapshot);
		}
		return buildGeTaxSettings(currentSettlementConfigSnapshot());
	}

	private SplitCalculator.GeTaxSettings buildGeTaxSettings(SettlementConfigSnapshot snapshot)
	{
		if (snapshot == null || !snapshot.isAccountForGeTax())
		{
			return SplitCalculator.GeTaxSettings.disabled();
		}

		return new SplitCalculator.GeTaxSettings(
			true,
			parseGeTaxMinimumValue(snapshot.getGeTaxMinimumValue()),
			sanitizeGeTaxPercent(snapshot.getGeTaxPercent()),
			parseGeTaxMaxPerLoot(snapshot.getGeTaxMaxPerLoot()));
	}

	public SplitCalculator.GeTaxSettings getGeTaxSettingsFor(SettlementConfigSnapshot snapshot)
	{
		return buildGeTaxSettings(snapshot);
	}

	public SettlementConfigSnapshot getSettlementConfigSnapshotFor(Session session)
	{
		SettlementConfigSnapshot historicalSnapshot = getHistoricalSettlementConfigSnapshot(session);
		return historicalSnapshot == null ? currentSettlementConfigSnapshot() : historicalSnapshot;
	}

	public boolean updateSettlementConfigSnapshotFor(Session session, SettlementConfigSnapshot snapshot)
	{
		if (session == null || snapshot == null)
		{
			return false;
		}
		Session mother = sessions.get(getRootSessionId(session));
		if (mother == null || mother.isActive())
		{
			return false;
		}
		if (!prepareHistoryMutation())
		{
			return false;
		}
		mother.setSettlementConfigAtEnd(snapshot);
		if (mother.getSettlementConfigAtStart() == null)
		{
			mother.setSettlementConfigAtStart(snapshot);
		}
		saveAfterMutation();
		return true;
	}

	private SettlementConfigSnapshot getHistoricalSettlementConfigSnapshot(Session session)
	{
		if (session == null)
		{
			return null;
		}
		if (session.isActive())
		{
			return null;
		}
		Session mother = sessions.get(getRootSessionId(session));
		if (mother == null || mother.isActive())
		{
			return null;
		}
		if (mother.getSettlementConfigAtEnd() != null)
		{
			return mother.getSettlementConfigAtEnd();
		}
		return mother.getSettlementConfigAtStart();
	}

	private SettlementConfigSnapshot currentSettlementConfigSnapshot()
	{
		if (config == null)
		{
			return new SettlementConfigSnapshot(false,
				PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE,
				PluginConfig.DEFAULT_GE_TAX_PERCENT,
				PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE);
		}
		return new SettlementConfigSnapshot(
			config.accountForGeTax(),
			defaultString(config.geTaxMinimumValue(), PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE),
			config.geTaxPercent(),
			defaultString(config.geTaxMaxPerLoot(), PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE));
	}

	private String defaultString(String value, String defaultValue)
	{
		return value == null || value.trim().isEmpty() ? defaultValue : value;
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

	private long parseGeTaxMaxPerLoot(String configuredValue)
	{
		String valueToParse = configuredValue == null || configuredValue.trim().isEmpty()
			? PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE
			: configuredValue.trim();
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(valueToParse, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse GE tax max per loot {}; using default {}", valueToParse, PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, e);
			return defaultGeTaxMaxPerLoot();
		}
	}

	private long defaultGeTaxMaxPerLoot()
	{
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse built-in GE tax max per loot {}; using default {}", PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT, e);
			return PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT;
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
	 * Get all events from all sessions that share the same mother session as the current session.
	 * Uses a cached list per mother to avoid recomputing on every UI update.
	 *
	 * @return a list containing all event records from sessions with the same mother
	 */
	public List<SplitEvent> getAllEvents()
	{
		Session curr = getCurrentSession().orElse(null);
		if (curr == null)
		{
			return new ArrayList<>();
		}
		// Determine the mother id for this thread
		String motherId = (curr.getMotherId() == null) ? curr.getId() : curr.getMotherId();
		// If cached, return it
		List<SplitEvent> cached = motherEventsCache.get(motherId);
		if (cached != null)
		{
			return Collections.unmodifiableList(cached);
		}
		// Build once in persisted session/list order. The edit-history UI can reorder
		// events, so sorting by timestamp here would discard those edits on refresh.
		List<SplitEvent> built = new ArrayList<>();
		for (Session session : sessions.values())
		{
			if (motherId.equals(session.getId()) || motherId.equals(session.getMotherId()))
			{
				built.addAll(session.getEvents());
			}
		}
		motherEventsCache.put(motherId, built);
		return Collections.unmodifiableList(built);
	}


	/**
	 * Generate a random unique id for sessions.
	 */
	private String newId()
	{
		return UUID.randomUUID().toString();
	}


}
