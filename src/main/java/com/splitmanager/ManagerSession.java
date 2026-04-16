package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.InstantTypeAdapter;
import com.splitmanager.views.PanelView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import lombok.Getter;

/**
 * Manages sessions, persistence, and all the logic for roster changes,
 * child sessions, and live split calculations.
 */
@Singleton
public class ManagerSession
{
	private final Gson gson;
	private final Map<String, Session> sessions = new LinkedHashMap<>();
	private final List<PendingValue> pendingValues = new ArrayList<>();
	private final ManagerKnownPlayers playerManager;
	private final PluginConfig config;
	// Cache of all kills grouped by mother session id to avoid recomputing on every UI refresh
	private final Map<String, List<Kill>> motherKillsCache = new LinkedHashMap<>();
	private String currentSessionId;
	private ManagerPlugin pluginManager;
	// TODO implement in newer versions
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

	// TODO add export all/ or specific session to json

	/**
	 * Loads configuration data into the application's runtime structures.
	 * <p>
	 * This method performs the following operations:
	 * <p>
	 * 1. Clears the list of known players and repopulates it from a CSV string
	 * retrieved from the configuration. Each player is trimmed of extraneous
	 * whitespace before being added to the collection.
	 * <p>
	 * 2. Clears the mapping of alternate accounts to main accounts and repopulates
	 * it from a JSON structure retrieved from the configuration. The JSON is
	 * parsed into a Map using GSON. If the parsing fails or the structure is
	 * invalid, the operation is gracefully ignored.
	 * <p>
	 * 3. Clears the session map and populates it with sessions retrieved from a
	 * JSON array in the configuration. Each session is parsed and added to
	 * the map by its ID.
	 * <p>
	 * 4. Updates the current session ID and sets whether the history has been
	 * loaded from the configuration.
	 */
	public void loadFromConfig()
	{
		sessions.clear();
		String json = config.sessionsJson();
		if (json != null && !json.isEmpty())
		{
			Session[] arr = gson.fromJson(json, Session[].class);
			if (arr != null)
			{
				for (Session s : arr)
				{
					sessions.put(s.getId(), s);
				}
			}
		}

		// Invalidate any cached mother->kills when loading fresh data
		motherKillsCache.clear();

		currentSessionId = emptyToNull(config.currentSessionId());
	}

	/**
	 * Persist sessions, current state, known players, and alt mappings to PluginConfig.
	 */
	public void saveToConfig()
	{
		Session[] arr = sessions.values().toArray(new Session[0]);
		config.sessionsJson(gson.toJson(arr));
		config.currentSessionId(nullToEmpty(currentSessionId));
	}

	/**
	 * Placeholder for exporting all sessions as JSON (for sharing/backups).
	 * Returns a JSON string. Implementation can reuse the existing gson and sessions map.
	 */
	public String exportAllSessionsJson()
	{
		// TODO Implement: return gson.toJson(sessions.values());
		return "";
	}

	/**
	 * Placeholder for exporting a single session by id as JSON.
	 * Returns a JSON string for the specified session or empty string if not found.
	 */
	public String exportSessionJson(String sessionId)
	{
		// TODO Implement: Session s = sessions.get(sessionId); return s != null ? gson.toJson(s) : "";
		return "";
	}

	/**
	 * @return unmodifiable set of all known player names (mains and alts).
	 */
	public Set<String> getKnownPlayers()
	{
		return Collections.unmodifiableSet(playerManager.getKnownMains());
	}

	/**
	 * Compute known main players not currently active in the session roster.
	 *
	 * @return set of eligible names to add to the current session
	 */
	public Set<String> getNonActivePlayers()
	{
		Session curr = getCurrentSession().orElse(null);
		java.util.Set<String> mains = playerManager.getKnownMains();

		if (curr == null || !curr.isActive())
		{
			return java.util.Collections.unmodifiableSet(mains);
		}

		return mains.stream()
			.filter(p -> !curr.getPlayers().contains(p))
			.collect(Collectors.toCollection(LinkedHashSet::new));
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
	 * Exit read-only history mode and return to live mode.
	 * Persists the flag immediately.
	 */
	public void unloadHistory()
	{
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
		if (s == null)
		{
			return Optional.empty();
		}
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
		pluginManager.updateChatWarningStatus();
		return Optional.of(child);
	}

	/**
	 * Stop the currently active child session. If its mother session is still active,
	 * it will be ended as well. No-op in history mode.
	 *
	 * @return true if an active session was stopped
	 */
	public boolean stopSession(PanelView view)
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

		if (
			JOptionPane.showConfirmDialog(view,
				"Are you sure you want to stop the session")
				!= 0
		)
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
		pluginManager.updateChatWarningStatus();
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
			return false; // TODO support his
		}
		Session curr = getCurrentSession().orElse(null);
		if (curr == null || !curr.isActive())
		{
			return false;
		}

		String mainPlayer = playerManager.getMainName(player == null ? null : player.trim());
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
			joinEvent.setType("JOINED");
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
			joinEvent.setType("JOINED");
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
		player = playerManager.getMainName(player.trim());
		if (player == null || player.isBlank())
		{
			return false;
		}
		if (!curr.getPlayers().contains(player))
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
			leaveEvent.setType("LEFT");
			newChild.getKills().add(leaveEvent);

			// Update mother cache incrementally
			motherKillsCache.computeIfAbsent(motherId, k -> new ArrayList<>()).add(leaveEvent);

			sessions.put(newChild.getId(), newChild);
			currentSessionId = newChild.getId();
		}
		else
		{
			curr.getPlayers().remove(player);
			// Record a LEFT event kill in the current child (no kills yet)
			Kill leaveEvent = new Kill(curr.getId(), player, 0L, Instant.now());
			leaveEvent.setType("LEFT");
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
	public boolean addKill(@Nonnull String player, @Nonnull Long amount)
	{
		if (historyLoaded)
		{
			return false; //TODO support altering history
		}

		Session currentSession = getCurrentSession().orElse(null);
		if (currentSession == null || !currentSession.isActive())
		{
			return false;
		}

		String mainPlayer = playerManager.getMainName(player.trim());
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
	public void addPendingValue(@Nonnull PendingValue pendingValue)
	{
		// Normalize suggestedPlayer player to main for all downstream uses
		String suggestedPlayer = pendingValue.getSuggestedPlayer();
		String resolvedPlayer = playerManager.getMainName(suggestedPlayer);

		pendingValue.setSuggestedPlayer(resolvedPlayer);

		if (!playerManager.getKnownPlayers().contains(resolvedPlayer))
		{
			playerManager.getKnownPlayers().add(resolvedPlayer);
			saveToConfig();
		}

		// Auto-apply if configured and player already in session
		if (config.autoApplyWhenInSession() && hasActiveSession())
		{
			Session currentSession = getCurrentSession().orElse(null);
			if (currentSession != null && currentSession.getPlayers().stream().anyMatch(p -> p.equalsIgnoreCase(resolvedPlayer)))
			{
				addKill(resolvedPlayer, pendingValue.getValue());
				return; // do not queue
			}
		}

		// Limit size to avoid unbounded growth
		if (pendingValues.size() > 100)
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
		String target = playerManager.getMainName(player);
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
	public boolean currentSessionHasPlayer(@Nonnull String player)
	{
		return sessionHasPlayer(player, getCurrentSession().orElse(null));
	}

	/**
	 * Returns true if the given player (main or alt) is present in the roster of the provided session.
	 * Alts are resolved to their main before the check.
	 */
	public boolean sessionHasPlayer(@Nonnull String player, Session session)
	{
		if (playerManager.isAlt(player))
		{
			player = playerManager.getMainName(player);
		}

		String finalPlayer = player;

		return session.getPlayers().stream().anyMatch(e ->
			e.equals(Objects.requireNonNull(finalPlayer)));
	}

	public void init()
	{
		loadFromConfig();
	}

	//TODO make this not recalc everything if it does

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

		// Determine the mother (root) id for the session thread
		String rootId = (s.getMotherId() == null) ? s.getId() : s.getMotherId();

		// Collect all sessions in the thread: mother + all children
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

		// Build included players:
		// - includeNonActivePlayers: union of knownPlayers and everyone who appeared in the thread
		// - otherwise: only the current session's active roster
		LinkedHashSet<String> includedPlayers = new LinkedHashSet<>();
		if (includeNonActivePlayers)
		{
			includedPlayers.addAll(playerManager.getKnownPlayers());
			for (Session part : thread)
			{
				includedPlayers.addAll(part.getPlayers());
			}
		}
		else
		{
			includedPlayers.addAll(s.getPlayers());
		}

		// Initialize aggregate totals and splits
		Map<String, Long> totals = new LinkedHashMap<>();
		Map<String, Long> splits = new LinkedHashMap<>();
		for (String p : includedPlayers)
		{
			totals.put(p, 0L);
			splits.put(p, 0L);
		}

		// For each session in the thread:
		// - compute that session's per-player totals for its own roster
		// - compute that session's average across its roster (including zeroes)
		// - for players active in that session, accumulate (playerTotal - sessionAvg) into splits
		for (Session part : thread)
		{
			// Roster for this part (the only players eligible for this part's split)
			List<String> roster = new ArrayList<>(part.getPlayers());
			if (roster.isEmpty())
			{
				continue;
			}

			// Per-session totals for players in this roster
			Map<String, Long> perSessionTotals = new LinkedHashMap<>();
			for (String p : roster)
			{
				perSessionTotals.put(p, 0L);
			}
			for (Kill k : part.getKills())
			{
				// Ignore JOINED/LEFT events in split math; only count regular loot
				String t = k.getType();
				if (t != null && !t.equalsIgnoreCase("LOOT"))
				{
					continue;
				}
				perSessionTotals.computeIfPresent(k.getPlayer(), (k1, v) -> v + k.getAmount());
			}

			// Session average across the entire roster
			Long sessionAvg = 0L;
			if (!perSessionTotals.isEmpty())
			{
				Long sum = 0L;
				for (Long v : perSessionTotals.values())
				{
					sum += v;
				}
				sessionAvg = sum / perSessionTotals.size();
			}

			// Accumulate totals and splits into the aggregate maps
			for (Map.Entry<String, Long> e : perSessionTotals.entrySet())
			{
				String player = e.getKey();
				Long playerTotalThisSession = e.getValue();

				// Aggregate total (only if we're showing this player)
				if (totals.containsKey(player))
				{
					totals.compute(player, (k, v) -> (v) + playerTotalThisSession);
				}

				// Aggregate split only for players active in THIS session
				if (splits.containsKey(player))
				{
					Long delta = sessionAvg - playerTotalThisSession;
					splits.compute(player, (k, v) -> (v) + delta);
				}
			}
		}

		// Build output rows, marking active based on the provided session's current roster
		List<PlayerMetrics> out = new ArrayList<>();
		for (String p : includedPlayers)
		{
			boolean isActiveNow = s.getPlayers().contains(p);
			Long total = totals.getOrDefault(p, 0L);
			Long split = splits.getOrDefault(p, 0L);

			// Skip players with total = 0, unless they are active now
			if (!isActiveNow && total == 0.0 && split == 0.0)
			{
				continue;
			}

			out.add(new PlayerMetrics(p, total, split, isActiveNow));
		}
		return out;
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
		// Build once, sort by time ascending (oldest first), and cache
		List<Kill> built = new ArrayList<>();
		for (Session session : sessions.values())
		{
			if (motherId.equals(session.getId()) || motherId.equals(session.getMotherId()))
			{
				built.addAll(session.getKills());
			}
		}
		built.sort(Comparator.comparing(Kill::getAt, Comparator.nullsLast(Comparator.naturalOrder())));
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
