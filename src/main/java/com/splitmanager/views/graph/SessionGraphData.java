package com.splitmanager.views.graph;

import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.Formats;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SessionGraphData
{
	private static final int MAX_BAR_ENTRIES = 12;

	private SessionGraphData()
	{
	}

	public static SessionGraphSnapshot build(Session currentSession,
	                                         List<Session> allSessions,
	                                         List<Kill> kills,
	                                         List<PlayerMetrics> metrics,
	                                         SessionGraphMode mode,
	                                         Instant now)
	{
		SessionGraphMode selectedMode = mode == null ? SessionGraphMode.GP_PER_HOUR : mode;
		if (currentSession == null)
		{
			return SessionGraphSnapshot.empty(selectedMode);
		}

		Instant safeNow = now == null ? Instant.now() : now;
		List<Kill> lootKills = safeLootKills(kills);
		List<PlayerMetrics> safeMetrics = metrics == null ? List.of() : metrics;
		List<Session> thread = threadSessions(currentSession, allSessions);
		Instant start = sessionStart(currentSession, thread, lootKills, safeNow);
		Instant end = sessionEnd(currentSession, thread, lootKills, safeNow, start);
		long totalLoot = sumLoot(lootKills);
		long gpPerHour = hourlyRate(totalLoot, Duration.between(start, end));
		PlayerMetrics topPlayer = topPlayer(safeMetrics);
		List<SessionGraphEntry> entries = entriesFor(selectedMode, lootKills, safeMetrics, start, end, currentSession.isActive());

		return new SessionGraphSnapshot(
			selectedMode,
			entries,
			totalLoot,
			gpPerHour,
			topPlayer == null ? "" : topPlayer.getPlayer(),
			topPlayer == null ? 0L : topPlayer.getTotal(),
			start,
			end);
	}

	private static List<SessionGraphEntry> entriesFor(SessionGraphMode mode,
	                                                  List<Kill> lootKills,
	                                                  List<PlayerMetrics> metrics,
	                                                  Instant start,
	                                                  Instant end,
	                                                  boolean activeSession)
	{
		switch (mode)
		{
			case HIGHEST_EARNINGS:
				return metrics.stream()
					.filter(metric -> metric.getTotal() != 0L)
					.sorted(Comparator.comparingLong(PlayerMetrics::getTotal).reversed())
					.limit(MAX_BAR_ENTRIES)
					.map(metric -> new SessionGraphEntry(metric.getPlayer(), metric.getTotal(), metric.isActivePlayer()))
					.collect(Collectors.toList());
			case SPLIT_BALANCE:
				return metrics.stream()
					.filter(metric -> metric.getSplit() != 0L)
					.sorted(Comparator.comparingLong((PlayerMetrics metric) -> Math.abs(metric.getSplit())).reversed())
					.limit(MAX_BAR_ENTRIES)
					.map(metric -> new SessionGraphEntry(metric.getPlayer(), metric.getSplit(), metric.isActivePlayer()))
					.collect(Collectors.toList());
			case GP_PER_HOUR:
			default:
				return gpPerHourEntries(lootKills, start, end, activeSession);
		}
	}

	private static List<SessionGraphEntry> gpPerHourEntries(List<Kill> lootKills, Instant start, Instant end, boolean activeSession)
	{
		List<SessionGraphEntry> entries = new ArrayList<>();
		long cumulative = 0L;
		Instant lastLootAt = null;
		for (Kill kill : lootKills)
		{
			cumulative += kill.getAmount();
			Instant at = kill.getAt() == null ? start : kill.getAt();
			lastLootAt = at;
			long value = hourlyRate(cumulative, Duration.between(start, at));
			entries.add(new SessionGraphEntry(Formats.getLocalTime().format(at), value, true));
		}
		if (activeSession && cumulative > 0L && end != null && !end.equals(lastLootAt))
		{
			long value = hourlyRate(cumulative, Duration.between(start, end));
			entries.add(new SessionGraphEntry(Formats.getLocalTime().format(end), value, true));
		}
		return entries;
	}

	private static List<Kill> safeLootKills(List<Kill> kills)
	{
		if (kills == null)
		{
			return List.of();
		}
		return kills.stream()
			.filter(kill -> kill != null && kill.isLoot() && kill.getAmount() != null)
			.sorted(Comparator.comparing(Kill::getAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.collect(Collectors.toList());
	}

	private static List<Session> threadSessions(Session currentSession, List<Session> allSessions)
	{
		String rootId = currentSession.getMotherId() == null ? currentSession.getId() : currentSession.getMotherId();
		List<Session> sessions = allSessions == null ? List.of() : allSessions;
		List<Session> thread = sessions.stream()
			.filter(Objects::nonNull)
			.filter(session -> rootId.equals(session.getId()) || rootId.equals(session.getMotherId()))
			.sorted(Comparator.comparing(Session::getStart, Comparator.nullsLast(Comparator.naturalOrder())))
			.collect(Collectors.toList());
		if (thread.isEmpty())
		{
			thread.add(currentSession);
		}
		return thread;
	}

	private static Instant sessionStart(Session currentSession, List<Session> thread, List<Kill> lootKills, Instant now)
	{
		for (Session session : thread)
		{
			if (session != null && session.getMotherId() == null && session.getStart() != null)
			{
				return session.getStart();
			}
		}
		for (Session session : thread)
		{
			if (session != null && session.getStart() != null)
			{
				return session.getStart();
			}
		}
		if (currentSession.getStart() != null)
		{
			return currentSession.getStart();
		}
		for (Kill kill : lootKills)
		{
			if (kill.getAt() != null)
			{
				return kill.getAt();
			}
		}
		return now;
	}

	private static Instant sessionEnd(Session currentSession,
	                                  List<Session> thread,
	                                  List<Kill> lootKills,
	                                  Instant now,
	                                  Instant start)
	{
		Instant end = null;
		if (currentSession.isActive())
		{
			end = now;
		}
		else
		{
			for (Session session : thread)
			{
				if (session != null && session.getEnd() != null && (end == null || session.getEnd().isAfter(end)))
				{
					end = session.getEnd();
				}
			}
		}
		for (Kill kill : lootKills)
		{
			Instant at = kill.getAt();
			if (at != null && (end == null || at.isAfter(end)))
			{
				end = at;
			}
		}
		if (end == null)
		{
			end = now;
		}
		if (end.isBefore(start))
		{
			return start;
		}
		return end;
	}

	private static long sumLoot(List<Kill> lootKills)
	{
		long total = 0L;
		for (Kill kill : lootKills)
		{
			total += kill.getAmount();
		}
		return total;
	}

	private static long hourlyRate(long amount, Duration duration)
	{
		long seconds = Math.max(1L, duration == null ? 1L : duration.getSeconds());
		return Math.round((amount * 3600.0d) / seconds);
	}

	private static PlayerMetrics topPlayer(List<PlayerMetrics> metrics)
	{
		return metrics.stream()
			.filter(metric -> metric != null && metric.getTotal() > 0L)
			.max(Comparator.comparingLong(PlayerMetrics::getTotal))
			.orElse(null);
	}
}
