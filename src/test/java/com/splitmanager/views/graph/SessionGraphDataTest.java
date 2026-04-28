package com.splitmanager.views.graph;

import com.splitmanager.models.Kill;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionGraphDataTest
{
	@Test
	public void testGpPerHourUsesThreadStartAndLootOnly()
	{
		Instant start = Instant.parse("2024-01-01T00:00:00Z");
		Session mother = new Session("mother", start, null);
		Session firstChild = new Session("first", start.plusSeconds(1), "mother");
		Session currentChild = new Session("current", start.plusSeconds(1800), "mother");

		Kill joined = new Kill("first", "Alice", 0L, start.plusSeconds(60));
		joined.setType(Kill.TYPE_JOINED);
		Kill firstLoot = new Kill("first", "Alice", 600_000L, start.plusSeconds(1800));
		Kill secondLoot = new Kill("current", "Bob", 600_000L, start.plusSeconds(3600));

		List<PlayerMetrics> metrics = Arrays.asList(
			new PlayerMetrics("Alice", 600_000L, -100_000L, true),
			new PlayerMetrics("Bob", 600_000L, 100_000L, true));

		SessionGraphSnapshot snapshot = SessionGraphData.build(
			currentChild,
			Arrays.asList(currentChild, firstChild, mother),
			Arrays.asList(secondLoot, joined, firstLoot),
			metrics,
			SessionGraphMode.GP_PER_HOUR,
			start.plusSeconds(7200));

		assertEquals(start, snapshot.getStart());
		assertEquals(1_200_000L, snapshot.getTotalLoot());
		assertEquals(600_000L, snapshot.getGpPerHour());
		assertEquals(3, snapshot.getEntries().size());
		assertEquals(1_200_000L, snapshot.getEntries().get(0).getValue());
		assertEquals(1_200_000L, snapshot.getEntries().get(1).getValue());
		assertEquals(600_000L, snapshot.getEntries().get(2).getValue());
	}

	@Test
	public void testHighestEarningsSortsByTotal()
	{
		Session current = new Session("current", Instant.parse("2024-01-01T00:00:00Z"), null);
		List<PlayerMetrics> metrics = Arrays.asList(
			new PlayerMetrics("Low", 250_000L, 10L, true),
			new PlayerMetrics("Zero", 0L, 20L, true),
			new PlayerMetrics("High", 2_000_000L, -30L, false));

		SessionGraphSnapshot snapshot = SessionGraphData.build(
			current,
			Collections.singletonList(current),
			Collections.emptyList(),
			metrics,
			SessionGraphMode.HIGHEST_EARNINGS,
			Instant.parse("2024-01-01T01:00:00Z"));

		assertEquals("High", snapshot.getTopPlayer());
		assertEquals(2_000_000L, snapshot.getTopPlayerTotal());
		assertEquals(2, snapshot.getEntries().size());
		assertEquals("High", snapshot.getEntries().get(0).getLabel());
		assertEquals(2_000_000L, snapshot.getEntries().get(0).getValue());
		assertEquals("Low", snapshot.getEntries().get(1).getLabel());
	}

	@Test
	public void testSplitBalanceSortsByAbsoluteSplitAndPreservesSign()
	{
		Session current = new Session("current", Instant.parse("2024-01-01T00:00:00Z"), null);
		List<PlayerMetrics> metrics = Arrays.asList(
			new PlayerMetrics("Receiver", 100L, 500_000L, true),
			new PlayerMetrics("Payer", 200L, -900_000L, true),
			new PlayerMetrics("Settled", 300L, 0L, true));

		SessionGraphSnapshot snapshot = SessionGraphData.build(
			current,
			Collections.singletonList(current),
			Collections.emptyList(),
			metrics,
			SessionGraphMode.SPLIT_BALANCE,
			Instant.parse("2024-01-01T01:00:00Z"));

		assertEquals(2, snapshot.getEntries().size());
		assertEquals("Payer", snapshot.getEntries().get(0).getLabel());
		assertEquals(-900_000L, snapshot.getEntries().get(0).getValue());
		assertEquals("Receiver", snapshot.getEntries().get(1).getLabel());
		assertEquals(500_000L, snapshot.getEntries().get(1).getValue());
	}

	@Test
	public void testNullCurrentSessionReturnsEmptySnapshot()
	{
		SessionGraphSnapshot snapshot = SessionGraphData.build(
			null,
			null,
			null,
			null,
			SessionGraphMode.HIGHEST_EARNINGS,
			null);

		assertTrue(snapshot.isEmpty());
		assertEquals(SessionGraphMode.HIGHEST_EARNINGS, snapshot.getMode());
		assertEquals(0L, snapshot.getTotalLoot());
	}
}
