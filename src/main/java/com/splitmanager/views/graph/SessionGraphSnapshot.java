package com.splitmanager.views.graph;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public final class SessionGraphSnapshot
{
	private final SessionGraphMode mode;
	private final List<SessionGraphEntry> entries;
	private final long totalLoot;
	private final long gpPerHour;
	private final String topPlayer;
	private final long topPlayerTotal;
	private final Instant start;
	private final Instant end;

	public SessionGraphSnapshot(SessionGraphMode mode,
	                            List<SessionGraphEntry> entries,
	                            long totalLoot,
	                            long gpPerHour,
	                            String topPlayer,
	                            long topPlayerTotal,
	                            Instant start,
	                            Instant end)
	{
		this.mode = mode == null ? SessionGraphMode.GP_PER_HOUR : mode;
		this.entries = entries == null
			? Collections.emptyList()
			: List.copyOf(entries);
		this.totalLoot = totalLoot;
		this.gpPerHour = gpPerHour;
		this.topPlayer = topPlayer == null ? "" : topPlayer;
		this.topPlayerTotal = topPlayerTotal;
		this.start = start;
		this.end = end;
	}

	public static SessionGraphSnapshot empty(SessionGraphMode mode)
	{
		return new SessionGraphSnapshot(mode, Collections.emptyList(), 0L, 0L, "", 0L, null, null);
	}

	public boolean isEmpty()
	{
		return entries.isEmpty();
	}
}
