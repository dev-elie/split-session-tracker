package com.splitmanager.views.graph;

import com.splitmanager.models.SplitEvent;
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
	                                         List<SplitEvent> events,
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
		List<SplitEvent> lootEvents = safeLootEvents(events);
		List<PlayerMetrics> safeMetrics = metrics == null ? List.of() : metrics;
		List<Session> thread = threadSessions(currentSession, allSessions);
		Instant start = sessionStart(currentSession, thread, lootEvents, safeNow);
		Instant end = sessionEnd(currentSession, thread, lootEvents, safeNow, start);
		long totalLoot = sumLoot(lootEvents);
		long gpPerHour = hourlyRate(totalLoot, Duration.between(start, end));
		PlayerMetrics topPlayer = topPlayer(safeMetrics);
		List<SessionGraphEntry> entries = entriesFor(selectedMode, lootEvents, safeMetrics, start, end, currentSession.isActive());

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
	                                                  List<SplitEvent> lootEvents,
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
				return gpPerHourEntries(lootEvents, start, end, activeSession);
		}
	}

	private static List<SessionGraphEntry> gpPerHourEntries(List<SplitEvent> lootEvents, Instant start, Instant end, boolean activeSession)
	{
		List<SessionGraphEntry> entries = new ArrayList<>();
		long cumulative = 0L;
		Instant lastLootAt = null;
		for (SplitEvent event : lootEvents)
		{
			cumulative += event.getAmount();
			Instant at = event.getAt() == null ? start : event.getAt();
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

	private static List<SplitEvent> safeLootEvents(List<SplitEvent> events)
	{
		if (events == null)
		{
			return List.of();
		}
		return events.stream()
			.filter(event -> event != null && event.isLootEvent() && event.getAmount() != null)
			.sorted(Comparator.comparing(SplitEvent::getAt, Comparator.nullsLast(Comparator.naturalOrder())))
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

	private static Instant sessionStart(Session currentSession, List<Session> thread, List<SplitEvent> lootEvents, Instant now)
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
		for (SplitEvent event : lootEvents)
		{
			if (event.getAt() != null)
			{
				return event.getAt();
			}
		}
		return now;
	}

	private static Instant sessionEnd(Session currentSession,
	                                  List<Session> thread,
	                                  List<SplitEvent> lootEvents,
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
		for (SplitEvent event : lootEvents)
		{
			Instant at = event.getAt();
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

	private static long sumLoot(List<SplitEvent> lootEvents)
	{
		long total = 0L;
		for (SplitEvent event : lootEvents)
		{
			total += event.getAmount();
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
