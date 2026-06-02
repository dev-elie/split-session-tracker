package com.splitmanager.models;

/**
 * Aggregate row to display per-player totals and split deltas for a session thread.
 * total = sum of that player's kills across all sessions in the thread.
 * split = sum over each session in the thread of (avgOfThatSessionRoster - playerTotalInThatSession).
 * activePlayer indicates whether the player is on the provided session's current roster.
 */
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

	public String getPlayer()
	{
		return player;
	}

	public long getTotal()
	{
		return total;
	}

	public long getSplit()
	{
		return split;
	}

	public boolean isActivePlayer()
	{
		return activePlayer;
	}
}
