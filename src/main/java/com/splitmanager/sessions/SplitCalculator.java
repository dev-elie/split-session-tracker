package com.splitmanager.sessions;

import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure split calculation for a session thread.
 */
public class SplitCalculator
{
	public List<PlayerMetrics> compute(Session selectedSession,
	                                   List<Session> thread,
	                                   Set<String> knownPlayers,
	                                   boolean includeNonActivePlayers)
	{
		if (selectedSession == null)
		{
			return List.of();
		}

		List<Session> safeThread = thread == null ? List.of() : thread;
		Set<String> safeKnownPlayers = knownPlayers == null ? Set.of() : knownPlayers;

		LinkedHashSet<String> includedPlayers = new LinkedHashSet<>();
		if (includeNonActivePlayers)
		{
			includedPlayers.addAll(safeKnownPlayers);
			for (Session part : safeThread)
			{
				includedPlayers.addAll(part.getPlayers());
			}
		}
		else
		{
			includedPlayers.addAll(selectedSession.getPlayers());
		}

		Map<String, Long> totals = new LinkedHashMap<>();
		Map<String, Long> splits = new LinkedHashMap<>();
		for (String player : includedPlayers)
		{
			totals.put(player, 0L);
			splits.put(player, 0L);
		}

		for (Session part : safeThread)
		{
			applySessionSegment(part, totals, splits);
		}

		List<PlayerMetrics> out = new ArrayList<>();
		for (String player : includedPlayers)
		{
			boolean activeNow = selectedSession.getPlayers().stream().anyMatch(active -> active.equalsIgnoreCase(player));
			long total = totals.getOrDefault(player, 0L);
			long split = splits.getOrDefault(player, 0L);

			if (!activeNow && total == 0L && split == 0L)
			{
				continue;
			}

			out.add(new PlayerMetrics(player, total, split, activeNow));
		}
		return out;
	}

	private void applySessionSegment(Session part, Map<String, Long> totals, Map<String, Long> splits)
	{
		List<String> roster = new ArrayList<>(part.getPlayers());
		if (roster.isEmpty())
		{
			return;
		}

		Map<String, Long> perSessionTotals = new LinkedHashMap<>();
		for (String player : roster)
		{
			perSessionTotals.put(player, 0L);
		}

		for (Kill kill : part.getKills())
		{
			if (!kill.isLoot() || kill.getAmount() == null)
			{
				continue;
			}
			perSessionTotals.computeIfPresent(kill.getPlayer(), (player, value) -> value + kill.getAmount());
		}

		long sessionAverage = sum(perSessionTotals) / perSessionTotals.size();
		for (Map.Entry<String, Long> entry : perSessionTotals.entrySet())
		{
			String player = entry.getKey();
			long playerTotalThisSession = entry.getValue();
			if (totals.containsKey(player))
			{
				totals.compute(player, (ignored, value) -> value + playerTotalThisSession);
			}
			if (splits.containsKey(player))
			{
				splits.compute(player, (ignored, value) -> value + sessionAverage - playerTotalThisSession);
			}
		}
	}

	private long sum(Map<String, Long> values)
	{
		long total = 0L;
		for (Long value : values.values())
		{
			total += value;
		}
		return total;
	}
}
