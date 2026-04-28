package com.splitmanager.sessions;

import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SplitCalculatorTest
{
	@Test
	public void computesThreadMetricsAndIgnoresRosterEvents()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		Session first = new Session("first", Instant.EPOCH.plusSeconds(1), "mother");
		first.getPlayers().addAll(Arrays.asList("A", "B"));
		first.getKills().add(new Kill("first", "A", 100000L, Instant.EPOCH.plusSeconds(2)));
		Kill joined = new Kill("first", "A", 999999L, Instant.EPOCH.plusSeconds(3));
		joined.setType(Kill.TYPE_JOINED);
		first.getKills().add(joined);

		Session second = new Session("second", Instant.EPOCH.plusSeconds(4), "mother");
		second.getPlayers().addAll(Arrays.asList("B", "C"));
		second.getKills().add(new Kill("second", "C", 60000L, Instant.EPOCH.plusSeconds(5)));

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			second,
			Arrays.asList(mother, first, second),
			new LinkedHashSet<>(Arrays.asList("A", "B", "C", "Unused")),
			true);

		assertEquals(3, metrics.size());
		PlayerMetrics a = find(metrics, "A");
		PlayerMetrics b = find(metrics, "B");
		PlayerMetrics c = find(metrics, "C");

		assertEquals(100000L, (long) a.total);
		assertEquals(-50000L, (long) a.split);
		assertFalse(a.activePlayer);

		assertEquals(0L, (long) b.total);
		assertEquals(80000L, (long) b.split);
		assertTrue(b.activePlayer);

		assertEquals(60000L, (long) c.total);
		assertEquals(-30000L, (long) c.split);
		assertTrue(c.activePlayer);
	}

	@Test
	public void returnsEmptyForNullSession()
	{
		assertTrue(new SplitCalculator().compute(null, List.of(), new LinkedHashSet<>(), true).isEmpty());
	}

	private PlayerMetrics find(List<PlayerMetrics> metrics, String player)
	{
		return metrics.stream().filter(m -> player.equals(m.player)).findFirst().get();
	}
}
