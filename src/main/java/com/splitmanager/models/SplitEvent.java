package com.splitmanager.models;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A single split-session event attributed to a player for a session segment.
 */
@Getter
@Setter
public class SplitEvent
{
	public static final String TYPE_LOOT = "LOOT";
	public static final String TYPE_JOINED = "JOINED";
	public static final String TYPE_LEFT = "LEFT";
	private final String sessionId;
	private final Instant at;
	private String player;
	private Long amount;
	/**
	 * Optional event type. When null or "LOOT", this entry is a normal loot record.
	 * When set to "JOINED" or "LEFT", this entry represents a roster change and is excluded
	 * from split math but shown in the recent splits table.
	 */
	private String type; // null or "LOOT" for loot; "JOINED"/"LEFT" for roster events

	public SplitEvent(String sessionId, String player, Long amount, Instant at)
	{
		this.sessionId = sessionId;
		this.player = player;
		this.amount = amount;
		this.at = at;
	}

	public boolean isLoot()
	{
		return type == null || TYPE_LOOT.equalsIgnoreCase(type);
	}

	public boolean isRosterEvent()
	{
		return TYPE_JOINED.equalsIgnoreCase(type) || TYPE_LEFT.equalsIgnoreCase(type);
	}
}
