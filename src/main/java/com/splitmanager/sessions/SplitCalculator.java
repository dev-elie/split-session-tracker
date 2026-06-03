package com.splitmanager.sessions;

import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

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
		return compute(selectedSession, thread, knownPlayers, includeNonActivePlayers, GeTaxSettings.disabled());
	}

	public List<PlayerMetrics> compute(Session selectedSession,
	                                   List<Session> thread,
	                                   Set<String> knownPlayers,
	                                   boolean includeNonActivePlayers,
	                                   GeTaxSettings geTaxSettings)
	{
		if (selectedSession == null)
		{
			return List.of();
		}

		List<Session> safeThread = thread == null ? List.of() : thread;
		Set<String> safeKnownPlayers = knownPlayers == null ? Set.of() : knownPlayers;
		GeTaxSettings safeGeTaxSettings = geTaxSettings == null ? GeTaxSettings.disabled() : geTaxSettings;

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
			applySessionSegment(part, totals, splits, safeGeTaxSettings);
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

	private void applySessionSegment(Session part,
	                                 Map<String, Long> totals,
	                                 Map<String, Long> splits,
	                                 GeTaxSettings geTaxSettings)
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
			long killAmount = kill.getAmount();
			long splitAmount = computeSplitAmount(killAmount, geTaxSettings);
			perSessionTotals.computeIfPresent(kill.getPlayer(), (player, value) -> value + splitAmount);
		}

		long sessionAverage = sum(perSessionTotals) / perSessionTotals.size();
		for (Map.Entry<String, Long> entry : perSessionTotals.entrySet())
		{
			String player = entry.getKey();
			long playerTotalThisSession = entry.getValue();
			if (totals.containsKey(player))
			{
				Long currentTotal = totals.get(player);
				totals.put(player, (currentTotal == null ? 0L : currentTotal) + playerTotalThisSession);
			}
			if (splits.containsKey(player))
			{
				Long currentSplit = splits.get(player);
				splits.put(player, (currentSplit == null ? 0L : currentSplit) + sessionAverage - playerTotalThisSession);
			}
		}
	}

	private long computeSplitAmount(long killAmount, GeTaxSettings geTaxSettings)
	{
		if (!geTaxSettings.enabled || killAmount < geTaxSettings.minimumValue)
		{
			return killAmount;
		}

		long geTax = computeGeTax(killAmount, geTaxSettings);
		if (geTax <= 0L)
		{
			return killAmount;
		}

		return Math.max(killAmount - geTax, 0L);
	}

	private long computeGeTax(long killAmount, GeTaxSettings geTaxSettings)
	{
		BigDecimal calculated = BigDecimal.valueOf(killAmount)
			.multiply(BigDecimal.valueOf(geTaxSettings.percent))
			.divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
		long capped = Math.min(geTaxSettings.maxTaxPerLoot, calculated.longValue());
		return Math.max(capped, 0L);
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

	@Getter
	public static final class GeTaxSettings
	{
		private final boolean enabled;
		private final long minimumValue;
		private final double percent;
		private final long maxTaxPerLoot;

		public GeTaxSettings(boolean enabled, long minimumValue, double percent, long maxTaxPerLoot)
		{
			this.enabled = enabled;
			this.minimumValue = minimumValue;
			this.percent = percent;
			this.maxTaxPerLoot = maxTaxPerLoot;
		}

		public static GeTaxSettings disabled()
		{
			return new GeTaxSettings(false, 0L, 0.0d, 0L);
		}

	}
}
