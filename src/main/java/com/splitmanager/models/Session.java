package com.splitmanager.models;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Immutable-ish data model representing a single segment of a session thread.
 * <p>
 * How sessions are tracked overall
 * - The plugin tracks "threads" of activity consisting of a root (mother) session and
 * zero or more child sessions. A new child is created whenever the roster changes AFTER
 * at least one event has been recorded, preserving historical rosters for split math.
 * - The root (mother) session has motherId = null and represents the thread start. The first
 * active child is created immediately when a thread starts so that events and roster are
 * always associated with a child segment. All subsequent children reference the same motherId.
 * - ManagerSession owns a Map<String, Session> of all sessions and switches the current active
 * child by storing its id. ManagerSession also forks new children on roster changes and
 * appends events to the active child.
 * <p>
 * What each Session holds
 * - id: unique identifier (UUID string) used as the key in ManagerSession's map and persisted.
 * - start/end: timestamps for this segment; end == null indicates the segment is currently active.
 * - motherId: null for the mother/root; otherwise the id of the mother that all children share.
 * - players: the roster for this segment (names are stored as mains; alts resolved earlier).
 * - events: ordered list of SplitEvent records attributed during this segment.
 * - settlementConfigAtStart/end: calculation context stored on mother sessions for historical metrics.
 * <p>
 * Lifecycle notes
 * - A thread starts by creating a mother + an initial child (active). Events are written to the
 * active child only. If a roster mutation happens and the current child already has events,
 * ManagerSession ends the current child and creates a new child with a copied roster and the
 * mutation applied. If there are no events yet, the roster is edited in-place on the current child.
 * - Stopping a thread ends the active child, and if the mother is still open, it is ended too.
 * <p>
 * Persistence/serialization
 * - This model is Gson-serializable; SessionStorage persists session data as versioned JSON in
 * the plugin's per-profile .runelite directory. Instant fields are serialized via a custom adapter.
 */
@Getter
public class Session
{
	public static final String CURRENT_PLUGIN_VERSION = "3.1.0";

	/**
	 * Unique identifier for this segment (UUID string). Used as the key in persistence and lookup.
	 */
	private final String id;

	/**
	 * Wall-clock time when this segment became active (creation time for children; thread start for mother).
	 */
	private final Instant start;
	/**
	 * Id of the mother (root) session. Null means this instance is the mother/root of the thread.
	 */
	private final String motherId; // null for mother; otherwise id of mother
	/**
	 * Roster (set) of players for this segment. Order is insertion order as displayed in UI.
	 * Names are expected to be mains; alt->main resolution happens before mutation.
	 */
	private final Set<String> players = new LinkedHashSet<>();
	/**
	 * Events recorded during this segment, in insertion order.
	 */
	@SerializedName(value = "events", alternate = {"kills"})
	private final List<SplitEvent> events = new ArrayList<>();
	/**
	 * Plugin version that created this segment layout. Missing on legacy data from versions before tracking.
	 */
	@Setter
	private String pluginVersion;
	/**
	 * When non-null, marks the time this segment was closed. Null implies the segment is active.
	 */
	@Setter
	private Instant end; // null when active
	/**
	 * Settlement-affecting config captured when a mother thread starts. Only meaningful on mother sessions.
	 */
	@Setter
	private SettlementConfigSnapshot settlementConfigAtStart;
	/**
	 * Settlement-affecting config captured when a mother thread ends. Only meaningful on mother sessions.
	 */
	@Setter
	private SettlementConfigSnapshot settlementConfigAtEnd;

	/**
	 * Create a new Session segment.
	 *
	 * @param id       unique identifier (UUID string)
	 * @param start    segment start time
	 * @param motherId null for mother/root; otherwise id of the mother this child belongs to
	 */
	public Session(String id, Instant start, String motherId)
	{
		this.id = id;
		this.start = start;
		this.motherId = motherId;
		this.pluginVersion = CURRENT_PLUGIN_VERSION;
	}

	/**
	 * @return true when this segment is currently active (end == null).
	 */
	public boolean isActive()
	{
		return end == null;
	}

	/**
	 * @return true if at least one event has been recorded in this segment.
	 */
	public boolean hasEvents()
	{
		return !events.isEmpty();
	}

	/**
	 * @return true if at least one loot event has been recorded in this segment.
	 */
	public boolean hasLootEvents()
	{
		return events.stream().anyMatch(SplitEvent::isLootEvent);
	}
}
