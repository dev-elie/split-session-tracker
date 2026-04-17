package com.splitmanager.models;

import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A single kill record attributed to a player for a given session.
 */
@Getter
@Setter
public class Kill implements Serializable
{
	public static final String TYPE_LOOT = "LOOT";
	public static final String TYPE_JOINED = "JOINED";
	public static final String TYPE_LEFT = "LEFT";

	private final String sessionId;
	private final Instant at;
	private String player;
	private Long amount;
	/**
	 * Optional type for this record. When null or "LOOT", this entry is a normal loot record.
	 * When set to "JOINED" or "LEFT", this entry represents a roster change event and should
	 * be excluded from split math but shown in the recent splits table.
	 */
	private String type; // null or "LOOT" for regular loot; "JOINED"/"LEFT" for events

	public Kill(String sessionId, String player, Long amount, Instant at)
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
