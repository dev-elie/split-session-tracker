package com.splitmanager.sessions;

import com.splitmanager.models.SplitEvent;
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
		first.getEvents().add(new SplitEvent("first", "A", 100000L, Instant.EPOCH.plusSeconds(2)));
		SplitEvent joined = new SplitEvent("first", "A", 999999L, Instant.EPOCH.plusSeconds(3));
		joined.setType(SplitEvent.TYPE_JOINED);
		first.getEvents().add(joined);

		Session second = new Session("second", Instant.EPOCH.plusSeconds(4), "mother");
		second.getPlayers().addAll(Arrays.asList("B", "C"));
		second.getEvents().add(new SplitEvent("second", "C", 60000L, Instant.EPOCH.plusSeconds(5)));

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			second,
			Arrays.asList(mother, first, second),
			new LinkedHashSet<>(Arrays.asList("A", "B", "C", "Unused")),
			true);

		assertEquals(3, metrics.size());
		PlayerMetrics a = find(metrics, "A");
		PlayerMetrics b = find(metrics, "B");
		PlayerMetrics c = find(metrics, "C");

		assertEquals(100000L, a.total);
		assertEquals(-50000L, a.split);
		assertFalse(a.activePlayer);

		assertEquals(0L, b.total);
		assertEquals(80000L, b.split);
		assertTrue(b.activePlayer);

		assertEquals(60000L, c.total);
		assertEquals(-30000L, c.split);
		assertTrue(c.activePlayer);
	}

	@Test
	public void returnsEmptyForNullSession()
	{
		assertTrue(new SplitCalculator().compute(null, List.of(), new LinkedHashSet<>(), true).isEmpty());
	}

	@Test
	public void appliesGeTaxToSplitValue()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		Session current = new Session("current", Instant.EPOCH.plusSeconds(1), "mother");
		current.getPlayers().addAll(Arrays.asList("A", "B", "C", "D", "E"));
		current.getEvents().add(new SplitEvent("current", "A", 100000000L, Instant.EPOCH.plusSeconds(2)));

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			current,
			Arrays.asList(mother, current),
			new LinkedHashSet<>(current.getPlayers()),
			true,
			new SplitCalculator.GeTaxSettings(true, 15000000L, 2.0d, 5000000L));

		assertEquals(5, metrics.size());
		assertEquals(98000000L, find(metrics, "A").total);
		assertEquals(-78400000L, find(metrics, "A").split);
		assertEquals(19600000L, find(metrics, "B").split);
		assertEquals(19600000L, find(metrics, "C").split);
		assertEquals(19600000L, find(metrics, "D").split);
		assertEquals(19600000L, find(metrics, "E").split);
	}

	@Test
	public void appliesGeTaxToThreePersonSplitValue()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		Session current = new Session("current", Instant.EPOCH.plusSeconds(1), "mother");
		current.getPlayers().addAll(Arrays.asList("A", "B", "C"));
		current.getEvents().add(new SplitEvent("current", "A", 100000000L, Instant.EPOCH.plusSeconds(2)));

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			current,
			Arrays.asList(mother, current),
			new LinkedHashSet<>(current.getPlayers()),
			true,
			new SplitCalculator.GeTaxSettings(true, 15000000L, 2.0d, 5000000L));

		assertEquals(3, metrics.size());
		assertEquals(98000000L, find(metrics, "A").total);
		assertEquals(-65333334L, find(metrics, "A").split);
		assertEquals(32666666L, find(metrics, "B").split);
		assertEquals(32666666L, find(metrics, "C").split);
	}

	@Test
	public void respectsMinimumValueAndCapsTaxPerLoot()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		Session current = new Session("current", Instant.EPOCH.plusSeconds(1), "mother");
		current.getPlayers().addAll(Arrays.asList("A", "B"));
		current.getEvents().add(new SplitEvent("current", "A", 14000000L, Instant.EPOCH.plusSeconds(2)));
		current.getEvents().add(new SplitEvent("current", "A", 400000000L, Instant.EPOCH.plusSeconds(3)));

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			current,
			Arrays.asList(mother, current),
			new LinkedHashSet<>(current.getPlayers()),
			true,
			new SplitCalculator.GeTaxSettings(true, 15000000L, 2.0d, 5000000L));

		PlayerMetrics a = find(metrics, "A");
		PlayerMetrics b = find(metrics, "B");

		assertEquals(409000000L, a.total);
		assertEquals(-204500000L, a.split);
		assertEquals(204500000L, b.split);
	}

	@Test
	public void doesNotApplyGeTaxToRosterEvents()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		Session current = new Session("current", Instant.EPOCH.plusSeconds(1), "mother");
		current.getPlayers().addAll(Arrays.asList("A", "B"));

		SplitEvent joined = new SplitEvent("current", "A", 100000000L, Instant.EPOCH.plusSeconds(2));
		joined.setType(SplitEvent.TYPE_JOINED);
		current.getEvents().add(joined);

		List<PlayerMetrics> metrics = new SplitCalculator().compute(
			current,
			Arrays.asList(mother, current),
			new LinkedHashSet<>(current.getPlayers()),
			true,
			new SplitCalculator.GeTaxSettings(true, 15000000L, 2.0d, 5000000L));

		assertEquals(2, metrics.size());
		assertEquals(0L, find(metrics, "A").total);
		assertEquals(0L, find(metrics, "A").split);
		assertEquals(0L, find(metrics, "B").total);
		assertEquals(0L, find(metrics, "B").split);
	}

	private PlayerMetrics find(List<PlayerMetrics> metrics, String player)
	{
		return metrics.stream()
			.filter(m -> player.equals(m.player))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing metric for " + player));
	}
}
