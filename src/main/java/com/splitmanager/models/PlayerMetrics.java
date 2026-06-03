package com.splitmanager.models;

import lombok.Getter;

/**
 * Aggregate row to display per-player totals and split deltas for a session thread.
 * total = sum of that player's events across all sessions in the thread.
 * split = sum over each session in the thread of (avgOfThatSessionRoster - playerTotalInThatSession).
 * activePlayer indicates whether the player is on the provided session's current roster.
 */
@Getter
public class PlayerMetrics
{
	public final String player;
	public final long total;
	public final long split;
	public final boolean activePlayer;

	public PlayerMetrics(String player, long total, long split, boolean activePlayer)
	{
		this.player = player;
		this.total = total;
		this.split = split;
		this.activePlayer = activePlayer;
	}

}
