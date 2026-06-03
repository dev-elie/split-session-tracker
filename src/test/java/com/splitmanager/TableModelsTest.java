package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.SplitEvent;
import com.splitmanager.models.Metrics;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.RecentSplitsTable;
import com.splitmanager.models.SettlementConfigSnapshot;
import com.splitmanager.models.WaitlistTable;
import com.splitmanager.utils.InstantTypeAdapter;
import com.splitmanager.views.PanelView;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TableModelsTest
{
	@Test
	public void testWaitlistTableDisplaysAndEditsPendingValues()
	{
		WaitlistTable table = new WaitlistTable();
		PendingValue pending = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, null);
		AtomicInteger edits = new AtomicInteger();
		table.setEditListener(edits::incrementAndGet);

		table.setData(Collections.singletonList(pending));

		assertEquals(1, table.getRowCount());
		assertEquals(3, table.getColumnCount());
		assertEquals("Type", table.getColumnName(0));
		assertEquals("Value", table.getColumnName(1));
		assertEquals("Player", table.getColumnName(2));
		assertSame(pending, table.getRow(0));
		assertNull(table.getRow(-1));
		assertNull(table.getRow(10));
		assertEquals(String.class, table.getColumnClass(0));
		assertEquals(String.class, table.getColumnClass(1));
		assertEquals(String.class, table.getColumnClass(2));
		assertEquals(Object.class, table.getColumnClass(99));
		assertFalse(table.isCellEditable(0, 0));
		assertTrue(table.isCellEditable(0, 1));
		assertTrue(table.isCellEditable(0, 2));
		assertEquals("ADD", table.getValueAt(0, 0));
		assertEquals("100K", table.getValueAt(0, 1));
		assertEquals("", table.getValueAt(0, 2));
		assertNull(table.getValueAt(0, 99));

		table.setValueAt("Player1", 0, 2);
		assertEquals("Player1", pending.getSuggestedPlayer());
		assertEquals("Player1", table.getValueAt(0, 2));
		assertEquals(1, edits.get());

		table.setValueAt("2m", 0, 1);
		assertEquals(2000000L, (long) pending.getValue());
		assertEquals(2, edits.get());
		table.setValueAt("bad", 0, 1);
		assertEquals(2000000L, (long) pending.getValue());
		assertEquals(2, edits.get());

		table.setData(null);
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testRecentSplitsTableDisplaysEventsAndLootNewestFirst()
	{
		RecentSplitsTable table = new RecentSplitsTable(mock(PluginConfig.class));
		SplitEvent loot = new SplitEvent("session", "Alice", 150000L, Instant.parse("2024-01-01T00:00:00Z"));
		SplitEvent joined = new SplitEvent("session", "Bob", 0L, Instant.parse("2024-01-01T00:01:00Z"));
		joined.setType("JOINED");
		SplitEvent left = new SplitEvent("session", "Cara", 0L, Instant.parse("2024-01-01T00:02:00Z"));
		left.setType("LEFT");

		table.setFromEvents(Arrays.asList(loot, joined, left));

		assertEquals(3, table.getRowCount());
		assertEquals(4, table.getColumnCount());
		assertEquals("Time", table.getColumnName(0));
		assertEquals("Player", table.getColumnName(1));
		assertEquals("Amount", table.getColumnName(2));
		assertEquals("Tax", table.getColumnName(3));
		assertEquals(String.class, table.getColumnClass(0));
		assertNotNull(table.getValueAt(0, 0));
		assertEquals("Cara", table.getValueAt(0, 1));
		assertEquals("Left", table.getValueAt(0, 2));
		assertEquals("", table.getValueAt(0, 3));
		assertEquals("Bob", table.getValueAt(1, 1));
		assertEquals("Joined", table.getValueAt(1, 2));
		assertEquals("", table.getValueAt(1, 3));
		assertEquals("Alice", table.getValueAt(2, 1));
		assertEquals("150K", table.getValueAt(2, 2));
		assertEquals("", table.getValueAt(2, 3));
		assertEquals("", table.getValueAt(2, 99));
		assertFalse(table.isCellEditable(0, 1));
		assertFalse(table.isCellEditable(1, 2));
		assertTrue(table.isCellEditable(2, 1));
		assertTrue(table.isCellEditable(2, 2));
		assertFalse(table.isCellEditable(2, 0));
		assertFalse(table.isCellEditable(2, 3));
		assertSame(loot, table.getEventAt(2));
		assertNull(table.getEventAt(-1));

		table.clear();
		assertEquals(0, table.getRowCount());
		table.setFromEvents(null);
		assertEquals(0, table.getRowCount());
	}

	@Test
	public void testRecentSplitsTableDisplaysTaxAndCleanSplitAmounts()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		RecentSplitsTable table = new RecentSplitsTable(config);
		SplitEvent untaxed = new SplitEvent("session", "Alice", 14000000L, Instant.parse("2024-01-01T00:00:00Z"));
		SplitEvent taxed = new SplitEvent("session", "Bob", 100000000L, Instant.parse("2024-01-01T00:01:00Z"));
		SplitEvent joined = new SplitEvent("session", "Cara", 100000000L, Instant.parse("2024-01-01T00:02:00Z"));
		joined.setType("JOINED");

		table.setFromEvents(Arrays.asList(untaxed, taxed, joined));

		assertEquals("Joined", table.getValueAt(0, 2));
		assertEquals("", table.getValueAt(0, 3));
		assertEquals("98,000K", table.getValueAt(1, 2));
		assertEquals("2M", table.getValueAt(1, 3));
		assertEquals("14,000K", table.getValueAt(2, 2));
		assertEquals("", table.getValueAt(2, 3));
	}

	@Test
	public void testRecentSplitsTableUsesConfiguredGeTaxCap()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("10m");
		RecentSplitsTable table = new RecentSplitsTable(config);
		SplitEvent taxed = new SplitEvent("session", "Alice", 1000000000L, Instant.parse("2024-01-01T00:00:00Z"));

		table.setFromEvents(Collections.singletonList(taxed));

		assertEquals("990,000K", table.getValueAt(0, 2));
		assertEquals("10M", table.getValueAt(0, 3));
	}

	@Test
	public void testRecentSplitsTableUsesSavedSettlementConfigSnapshot()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(10.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("10m");
		RecentSplitsTable table = new RecentSplitsTable(config);
		table.setSettlementConfigSnapshot(new SettlementConfigSnapshot(true, "15m", 2.0d, "5m"));
		SplitEvent taxed = new SplitEvent("session", "Alice", 100000000L, Instant.parse("2024-01-01T00:00:00Z"));

		table.setFromEvents(Collections.singletonList(taxed));

		assertEquals("98,000K", table.getValueAt(0, 2));
		assertEquals("2M", table.getValueAt(0, 3));
	}

	@Test
	public void testRecentSplitsTableEditsLootRowsAndNotifiesListener()
	{
		RecentSplitsTable table = new RecentSplitsTable(mock(PluginConfig.class));
		SplitEvent loot = new SplitEvent("session", "Alice", 150000L, Instant.parse("2024-01-01T00:00:00Z"));
		table.setFromEvents(Collections.singletonList(loot));
		AtomicReference<SplitEvent> edited = new AtomicReference<>();
		table.setListener(edited::set);

		table.setValueAt(" Alice Main ", 0, 1);
		assertEquals("Alice Main", loot.getPlayer());
		assertSame(loot, edited.get());

		table.setValueAt("2m", 0, 2);
		assertEquals(2000000L, (long) loot.getAmount());
		assertSame(loot, edited.get());

		table.setValueAt("bad", 0, 2);
		assertEquals(2000000L, (long) loot.getAmount());

		table.setValueAt(null, 0, 1);
		assertEquals("Alice Main", loot.getPlayer());
		table.setValueAt(" ", 0, 1);
		assertEquals("Alice Main", loot.getPlayer());
		table.setValueAt("ignored", 0, 99);
		assertEquals("Alice Main", loot.getPlayer());

		table.setValueAt("ignored", -1, 1);
		assertEquals("Alice Main", loot.getPlayer());
	}

	@Test
	public void testRecentSplitsTableGeTaxDisplayHandlesInvalidConfig()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.accountForGeTax()).thenReturn(true);
		RecentSplitsTable table = new RecentSplitsTable(config);
		SplitEvent taxed = new SplitEvent("session", "Alice", 1000000000L, Instant.parse("2024-01-01T00:00:00Z"));
		table.setFromEvents(Collections.singletonList(taxed));

		when(config.geTaxPercent()).thenReturn(Double.NaN);
		when(config.geTaxMinimumValue()).thenReturn("not-an-amount");
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		assertEquals("995,000K", table.getValueAt(0, 2));
		assertEquals("5M", table.getValueAt(0, 3));

		when(config.geTaxPercent()).thenReturn(0.0d);
		assertEquals("1,000,000K", table.getValueAt(0, 2));
		assertEquals("", table.getValueAt(0, 3));
	}

	@Test
	public void testMetricsTableSortsAndDisplaysRows()
	{
		Metrics table = new Metrics();
		PlayerMetrics inactive = new PlayerMetrics("Inactive", 0L, 50000L, false);
		PlayerMetrics active = new PlayerMetrics("Active", 150000L, -50000L, true);

		assertEquals(0, table.getRowCount());
		assertEquals(4, table.getColumnCount());
		assertFalse(table.isHidingTotalColumn());
		assertFalse(table.isRowActive(-1));
		assertEquals(0L, table.getRawSplitAt(0));

		table.setData(Arrays.asList(inactive, active));

		assertEquals(2, table.getRowCount());
		assertTrue(table.isRowActive(0));
		assertFalse(table.isRowActive(1));
		assertEquals("Active", table.getValueAt(0, 0));
		assertEquals("150K", table.getValueAt(0, 1));
		assertEquals("-50K", table.getValueAt(0, 2));
		assertTrue(table.getValueAt(0, 3) instanceof JButton);
		assertEquals("", table.getValueAt(0, 99));
		assertEquals("Player", table.getColumnName(0));
		assertEquals("Total", table.getColumnName(1));
		assertEquals("Split", table.getColumnName(2));
		assertEquals("X", table.getColumnName(3));
		assertEquals("", table.getColumnName(99));
		assertEquals(Object.class, table.getColumnClass(0));
		assertFalse(table.isCellEditable(0, 0));
		assertTrue(table.isCellEditable(0, 3));
		assertEquals(-50000L, table.getRawSplitAt(0));
		assertEquals(0L, table.getRawSplitAt(-1));

		table.setHideTotalColumn(true);
		assertTrue(table.isHidingTotalColumn());
		assertEquals(3, table.getColumnCount());
		assertEquals("Active", table.getValueAt(0, 0));
		assertEquals("-50K", table.getValueAt(0, 1));
		assertTrue(table.getValueAt(0, 2) instanceof JButton);
		assertEquals("", table.getValueAt(0, 99));
		assertEquals("Player", table.getColumnName(0));
		assertEquals("Split", table.getColumnName(1));
		assertEquals("X", table.getColumnName(2));
		assertEquals("", table.getColumnName(99));
		assertFalse(table.isCellEditable(0, 1));
		assertTrue(table.isCellEditable(0, 2));

		table.setHideTotalColumn(true);
		assertTrue(table.isHidingTotalColumn());
	}

	@Test
	public void testPanelViewRefreshMetricsUsesEditableHistorySession() throws Exception
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(false);
		when(config.directPayments()).thenReturn(false);
		when(config.copyForDiscord()).thenReturn(false);
		when(config.flipSettlementSign()).thenReturn(false);
		when(config.accountForGeTax()).thenReturn(false);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");

		ManagerKnownPlayers playerManager = mock(ManagerKnownPlayers.class);
		when(playerManager.getKnownPlayers()).thenReturn(Collections.emptySet());
		when(playerManager.getKnownMains()).thenReturn(Collections.emptySet());
		when(playerManager.getMainName("Alice")).thenReturn("Alice");

		ManagerSession sessionManager = new ManagerSession(config, playerManager, mock(ManagerPlugin.class), new Gson().newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create());
		sessionManager.startSession();
		sessionManager.addPlayerToActive("Alice");
		sessionManager.addLoot("Alice", 100000L);
		sessionManager.stopSession();
		String rootId = sessionManager.getHistorySessionsNewestFirst().get(0).getId();
		sessionManager.loadHistory(rootId);

		AtomicReference<PanelView> viewRef = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> viewRef.set(new PanelView(sessionManager, config, playerManager, mock(com.splitmanager.controllers.PanelController.class))));

		Metrics table = (Metrics) viewRef.get().getMetricsTable().getModel();
		assertTrue(table.getRowCount() > 0);
		assertTrue(table.isRowActive(0));
	}
}
