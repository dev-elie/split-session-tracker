package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.SplitEvent;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.persistence.SessionStorage;
import com.splitmanager.persistence.SessionStorageData;
import com.splitmanager.utils.InstantTypeAdapter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ManagerSessionTest
{
	private final Gson gson = new Gson().newBuilder()
		.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
		.create();
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	@Mock
	private PluginConfig config;
	@Mock
	private ManagerKnownPlayers playerManager;
	@Mock
	private ManagerPlugin pluginManager;
	private ManagerSession managerSession;

	@Before
	public void setUp()
	{
		managerSession = new ManagerSession(config, playerManager, pluginManager, gson);
	}

	private void resolveToSelf(String... players)
	{
		for (String player : players)
		{
			when(playerManager.getMainName(player)).thenReturn(player);
		}
	}

	private Session requireSession(Optional<Session> session)
	{
		assertTrue(session.isPresent());
		return session.orElseThrow(() -> new AssertionError("Expected session to be present"));
	}

	private PlayerMetrics requireMetric(List<PlayerMetrics> metrics, String player)
	{
		return metrics.stream()
			.filter(metric -> player.equals(metric.player))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing metric for " + player));
	}

	private int findEventIndex(String type, String player)
	{
		List<SplitEvent> events = managerSession.getAllEvents();
		for (int i = events.size() - 1; i >= 0; i--)
		{
			SplitEvent event = events.get(i);
			if (type.equalsIgnoreCase(event.getType()) && player.equalsIgnoreCase(event.getPlayer()))
			{
				return i;
			}
		}
		return -1;
	}

	private int findLootEventIndex(String player, long amount)
	{
		List<SplitEvent> events = managerSession.getAllEvents();
		for (int i = 0; i < events.size(); i++)
		{
			SplitEvent event = events.get(i);
			if (event.isLootEvent()
				&& player.equalsIgnoreCase(event.getPlayer())
				&& event.getAmount() != null
				&& event.getAmount() == amount)
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void testStartSession()
	{
		Optional<Session> sessionOpt = managerSession.startSession();
		assertTrue(sessionOpt.isPresent());
		Session child = requireSession(sessionOpt);
		assertNotNull(child.getMotherId());
		assertTrue(managerSession.hasActiveSession());
		verify(config, atLeastOnce()).sessionsJson(anyString());
		verify(pluginManager).updateChatWarningStatus();
	}

	@Test
	public void testStopSession()
	{
		managerSession.startSession();
		assertTrue(managerSession.hasActiveSession());

		assertTrue(managerSession.stopSession());
		assertFalse(managerSession.hasActiveSession());
	}

	@Test
	public void testExportSelectedHistoryIncludesRootAndChildren()
	{
		managerSession.startSession();
		managerSession.stopSession();
		Session root = managerSession.getHistorySessionsNewestFirst().get(0);

		Session[] exported = gson.fromJson(managerSession.exportSessionThreadJson(root.getId()), Session[].class);

		assertEquals(2, exported.length);
		assertEquals(1, Arrays.stream(exported).filter(session -> session.getMotherId() == null).count());
		assertEquals(1, Arrays.stream(exported).filter(session -> root.getId().equals(session.getMotherId())).count());
	}

	@Test
	public void testExportAllHistoryExcludesActiveSession()
	{
		managerSession.startSession();
		managerSession.stopSession();
		managerSession.startSession();

		Session[] exported = gson.fromJson(managerSession.exportHistorySessionsJson(), Session[].class);

		assertEquals(2, exported.length);
		assertTrue(Arrays.stream(exported).noneMatch(Session::isActive));
	}

	@Test
	public void testImportHistorySessionsRemapsIdsAndPreservesMotherThread()
	{
		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		managerSession.addLoot(p1, 100000L);
		managerSession.stopSession();

		Session sourceRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		String json = managerSession.exportSessionThreadJson(sourceRoot.getId());

		ManagerSession imported = new ManagerSession(config, playerManager, pluginManager, gson);
		int importedThreads = imported.importHistorySessionsJson(json);

		assertEquals(1, importedThreads);
		assertEquals(2, imported.getAllSessionsNewestFirst().size());
		assertTrue(imported.getCurrentSession().isEmpty());

		Session importedRoot = imported.getHistorySessionsNewestFirst().get(0);
		assertNull(importedRoot.getMotherId());
		assertNotEquals(sourceRoot.getId(), importedRoot.getId());
		assertFalse(importedRoot.isActive());

		Session importedChild = imported.getAllSessionsNewestFirst().stream()
			.filter(session -> importedRoot.getId().equals(session.getMotherId()))
			.findFirst()
			.orElse(null);
		assertNotNull(importedChild);
		assertEquals(importedRoot.getId(), importedChild.getMotherId());
		assertEquals(2, importedChild.getEvents().size());
		assertTrue(importedChild.getEvents().stream().anyMatch(event -> p1.equals(event.getPlayer()) && Long.valueOf(100000L).equals(event.getAmount())));
		assertTrue(importedChild.getEvents().stream().allMatch(event -> importedChild.getId().equals(event.getSessionId())));
	}

	@Test
	public void testImportHistorySessionsRejectsChildWithoutMother()
	{
		Session child = new Session("child", Instant.EPOCH.plusSeconds(1), "missing-mother");
		child.setEnd(Instant.EPOCH.plusSeconds(2));
		String json = gson.toJson(new Session[]{child});

		assertEquals(0, managerSession.importHistorySessionsJson(json));
		assertTrue(managerSession.getAllSessionsNewestFirst().isEmpty());
	}

	@Test
	public void testImportHistorySessionsRejectsActiveSessions()
	{
		Session mother = new Session("mother", Instant.EPOCH, null);
		mother.setEnd(Instant.EPOCH.plusSeconds(1));
		Session child = new Session("child", Instant.EPOCH.plusSeconds(2), "mother");
		String json = gson.toJson(new Session[]{mother, child});

		assertEquals(0, managerSession.importHistorySessionsJson(json));
		assertTrue(managerSession.getAllSessionsNewestFirst().isEmpty());
	}

	@Test
	public void testAddPlayerToActive()
	{
		managerSession.startSession();
		String playerName = "Player1";
		when(playerManager.getMainName(playerName)).thenReturn(playerName);

		boolean added = managerSession.addPlayerToActive(playerName);
		assertTrue(added);
		assertTrue(managerSession.currentSessionHasPlayer(playerName));
	}

	@Test
	public void testAddPendingValue()
	{
		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, "Player1");
		when(playerManager.getMainName("Player1")).thenReturn("Player1");

		managerSession.addPendingValue(pv);

		assertEquals(1, managerSession.getPendingValues().size());
		assertEquals("Player1", managerSession.getPendingValues().get(0).getSuggestedPlayer());
	}

	@Test
	public void testApplyPendingValueToPlayer()
	{
		managerSession.startSession();
		String playerName = "Player1";
		when(playerManager.getMainName(playerName)).thenReturn(playerName);
		managerSession.addPlayerToActive(playerName);

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, playerName);
		managerSession.addPendingValue(pv);

		boolean applied = managerSession.applyPendingValueToPlayer(pv.getId(), playerName);
		assertTrue(applied);
		assertEquals(0, managerSession.getPendingValues().size());
		// Verify event was added (implicitly via session state)
		assertTrue(requireSession(managerSession.getCurrentSession()).hasEvents());
	}

	@Test
	public void testComputeMetrics()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);

		// Get the LATEST session from managerSession after additions
		Session child = requireSession(managerSession.getCurrentSession());

		// p1 gets a 100k drop
		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);

		assertEquals(2, metrics.size());

		// Basic split check: 100k total, 2 players -> 50k each.
		// p1: 100k total, -50k split (50k - 100k)
		// p2: 0 total, +50k split (50k - 0)
		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);

		assertEquals(100000L, m1.total);
		assertEquals(-50000L, m1.split);

		assertEquals(0L, m2.total);
		assertEquals(50000L, m2.split);
	}

	@Test
	public void testComputeMetricsAppliesConfiguredGeTax()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);

		Session child = requireSession(managerSession.getCurrentSession());

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);

		assertEquals(98000000L, m1.total);
		assertEquals(-49000000L, m1.split);
		assertEquals(0L, m2.total);
		assertEquals(49000000L, m2.split);
	}

	@Test
	public void testComputeMetricsFallsBackForInvalidGeTaxMinimum()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("not-an-amount");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		Session child = requireSession(managerSession.getCurrentSession());

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 1b", 1000000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);

		assertEquals(995000000L, m1.total);
		assertEquals(-497500000L, m1.split);
		assertEquals(497500000L, m2.split);
	}

	@Test
	public void testComputeMetricsFallsBackForInvalidGeTaxPercent()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(-1.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		Session child = requireSession(managerSession.getCurrentSession());

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);

		assertEquals(98000000L, m1.total);
		assertEquals(-49000000L, m1.split);
		assertEquals(49000000L, m2.split);
	}

	@Test
	public void testComputeMetricsUsesConfiguredGeTaxCap()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("10m");

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		Session child = requireSession(managerSession.getCurrentSession());

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 1b", 1000000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);

		assertEquals(990000000L, m1.total);
		assertEquals(-495000000L, m1.split);
		assertEquals(495000000L, m2.split);
	}

	@Test
	public void testClosedHistoryUsesSavedSettlementConfigContext()
	{
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, "Player1");
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), "Player1");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertNotNull(historyRoot.getSettlementConfigAtStart());
		assertNotNull(historyRoot.getSettlementConfigAtEnd());
		assertEquals(2.0d, historyRoot.getSettlementConfigAtEnd().getGeTaxPercent(), 0.0d);

		lenient().when(config.geTaxPercent()).thenReturn(10.0d);
		lenient().when(config.geTaxMaxPerLoot()).thenReturn("10m");

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(historyRoot, true);
		PlayerMetrics m1 = requireMetric(metrics, "Player1");
		PlayerMetrics m2 = requireMetric(metrics, "Player2");

		assertEquals(98000000L, m1.total);
		assertEquals(-49000000L, m1.split);
		assertEquals(49000000L, m2.split);
	}

	@Test
	public void testUpdatingSavedHistorySettlementContextChangesHistoryMetrics()
	{
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, "Player1");
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), "Player1");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertTrue(managerSession.updateSettlementConfigSnapshotFor(
			historyRoot,
			new com.splitmanager.models.SettlementConfigSnapshot(true, "15m", 10.0d, "10m")));

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(historyRoot, true);
		PlayerMetrics m1 = requireMetric(metrics, "Player1");
		PlayerMetrics m2 = requireMetric(metrics, "Player2");

		assertEquals(90000000L, m1.total);
		assertEquals(-45000000L, m1.split);
		assertEquals(45000000L, m2.split);
	}

	@Test
	public void testHistoryEditWarningCanBlockStagedHistoryChange()
	{
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		managerSession.loadHistory(historyRoot.getId());
		managerSession.setHistoryEditWarningHandler(() -> false);

		assertFalse(managerSession.updateSettlementConfigSnapshotFor(
			historyRoot,
			new com.splitmanager.models.SettlementConfigSnapshot(true, "15m", 10.0d, "10m")));
		assertFalse(managerSession.isHistoryDirty());
		assertEquals(2.0d, historyRoot.getSettlementConfigAtEnd().getGeTaxPercent(), 0.0d);
	}

	@Test
	public void testLegacyVersionHistoryIsReadOnly()
	{
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addLoot("Player1", 100000L);
		managerSession.stopSession();

		for (Session session : managerSession.getAllSessionsNewestFirst())
		{
			session.setPluginVersion("3.0.1");
		}

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertTrue(managerSession.loadHistory(historyRoot.getId()).isPresent());
		assertTrue(managerSession.isCurrentHistoryEditLocked());
		managerSession.setHistoryEditWarningHandler(() -> true);

		assertFalse(managerSession.addPlayerToActive("Player2"));
		assertFalse(managerSession.updateSettlementConfigSnapshotFor(
			historyRoot,
			new com.splitmanager.models.SettlementConfigSnapshot(true, "15m", 10.0d, "10m")));
		assertFalse(managerSession.isHistoryDirty());
	}

	@Test
	public void testNewSessionHistoryCarriesEditableVersion()
	{
		resolveToSelf("Player1");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addLoot("Player1", 100000L);
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertEquals(Session.CURRENT_PLUGIN_VERSION, historyRoot.getPluginVersion());
		assertTrue(managerSession.loadHistory(historyRoot.getId()).isPresent());
		assertFalse(managerSession.isCurrentHistoryEditLocked());
	}

	@Test
	public void testImportedHistoryWithoutVersionIsReadOnly()
	{
		String json = "["
			+ "{\"id\":\"root\",\"start\":\"1970-01-01T00:00:00Z\",\"end\":\"1970-01-01T00:00:10Z\",\"players\":[],\"events\":[]},"
			+ "{\"id\":\"child\",\"start\":\"1970-01-01T00:00:01Z\",\"end\":\"1970-01-01T00:00:10Z\",\"motherId\":\"root\","
			+ "\"players\":[\"Player1\"],\"events\":[{\"sessionId\":\"child\",\"at\":\"1970-01-01T00:00:02Z\","
			+ "\"player\":\"Player1\",\"amount\":100000}]}"
			+ "]";

		assertEquals(1, managerSession.importHistorySessionsJson(json));
		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);

		assertTrue(managerSession.loadHistory(historyRoot.getId()).isPresent());
		assertTrue(managerSession.isCurrentHistoryEditLocked());
	}

	@Test
	public void testDiscardHistoryChangesRestoresStagedHistoryEdit()
	{
		when(config.accountForGeTax()).thenReturn(true);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		managerSession.loadHistory(historyRoot.getId());

		assertTrue(managerSession.updateSettlementConfigSnapshotFor(
			historyRoot,
			new com.splitmanager.models.SettlementConfigSnapshot(true, "15m", 10.0d, "10m")));
		assertTrue(managerSession.isHistoryDirty());

		assertTrue(managerSession.discardHistoryChanges());
		Session restoredRoot = requireSession(managerSession.getCurrentSession());

		assertFalse(managerSession.isHistoryDirty());
		assertEquals(2.0d, restoredRoot.getSettlementConfigAtEnd().getGeTaxPercent(), 0.0d);
	}

	@Test
	public void testCanStagePlayerAndSplitAdditionsInLoadedHistory()
	{
		when(config.accountForGeTax()).thenReturn(false);
		when(config.geTaxMinimumValue()).thenReturn("15m");
		when(config.geTaxPercent()).thenReturn(2.0d);
		when(config.geTaxMaxPerLoot()).thenReturn("5m");
		resolveToSelf("Player1", "Player2");

		managerSession.startSession();
		managerSession.addPlayerToActive("Player1");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		managerSession.loadHistory(historyRoot.getId());

		assertTrue(managerSession.addPlayerToActive("Player2"));
		assertTrue(managerSession.addLoot("Player2", 100000L));
		assertTrue(managerSession.isHistoryDirty());

		Session editable = requireSession(managerSession.getCurrentEditableSession());
		assertTrue(editable.getPlayers().contains("Player2"));
		assertTrue(managerSession.getAllEvents().stream()
			.anyMatch(event -> "Player2".equals(event.getPlayer()) && Long.valueOf(100000L).equals(event.getAmount())));
	}

	@Test
	public void testComputeMetricsComprehensive()
	{
		managerSession.startSession();

		String p1 = "Player1";
		String p2 = "Player2";
		String p3 = "Player3";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);
		when(playerManager.getMainName(p3)).thenReturn(p3);
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());

		// Segment 1: P1, P2
		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);

		// P1 drops 100k
		PendingValue pv1 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, p1);
		managerSession.addPendingValue(pv1);
		managerSession.applyPendingValueToPlayer(pv1.getId(), p1);

		// Segment 2: Add P3 (forks because Segment 1 has loot events)
		managerSession.addPlayerToActive(p3);

		// P2 drops 300k
		PendingValue pv2 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 300", 300000L, p2);
		managerSession.addPendingValue(pv2);
		managerSession.applyPendingValueToPlayer(pv2.getId(), p2);

		// Segment 3: Remove P1 (forks because Segment 2 has loot events)
		managerSession.removePlayerFromSession(p1);

		// P3 drops 60k
		PendingValue pv3 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 60", 60000L, p3);
		managerSession.addPendingValue(pv3);
		managerSession.applyPendingValueToPlayer(pv3.getId(), p3);

		// Current session is Segment 3 (P2, P3 active)
		Session current = requireSession(managerSession.getCurrentSession());
		assertEquals(2, current.getPlayers().size());

		// Test with includeNonActivePlayers = true (what the UI uses)
		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(current, true);

		// Should include P1, P2, P3
		// P1 Total: 100k, Split: +50k
		// P2 Total: 300k, Split: -120k
		// P3 Total: 60k, Split: +70k
		assertEquals(3, metrics.size());

		PlayerMetrics m1 = requireMetric(metrics, p1);
		PlayerMetrics m2 = requireMetric(metrics, p2);
		PlayerMetrics m3 = requireMetric(metrics, p3);

		assertEquals(100000L, m1.total);
		assertEquals(50000L, m1.split);
		assertFalse(m1.activePlayer);

		assertEquals(300000L, m2.total);
		assertEquals(-120000L, m2.split);
		assertTrue(m2.activePlayer);

		assertEquals(60000L, m3.total);
		assertEquals(70000L, m3.split);
		assertTrue(m3.activePlayer);

		// Test with includeNonActivePlayers = false (only active roster)
		List<PlayerMetrics> activeMetrics = managerSession.computeMetricsFor(current, false);
		assertEquals(2, activeMetrics.size());
		assertFalse(activeMetrics.stream().anyMatch(m -> m.player.equals(p1)));

		// Segment 4: Add P1 back
		managerSession.addPlayerToActive(p1);

		// P1 drops 300k
		PendingValue pv4 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 300", 300000L, p1);
		managerSession.addPendingValue(pv4);
		managerSession.applyPendingValueToPlayer(pv4.getId(), p1);

		// Current is Segment 4 (P1, P2, P3 active)
		Session finalSession = requireSession(managerSession.getCurrentSession());
		assertEquals(3, finalSession.getPlayers().size());

		List<PlayerMetrics> finalMetrics = managerSession.computeMetricsFor(finalSession, true);
		assertEquals(3, finalMetrics.size());

		PlayerMetrics fm1 = requireMetric(finalMetrics, p1);
		PlayerMetrics fm2 = requireMetric(finalMetrics, p2);
		PlayerMetrics fm3 = requireMetric(finalMetrics, p3);

		// P1: Total 400k, Split -150k
		assertEquals(400000L, fm1.total);
		assertEquals(-150000L, fm1.split);
		assertTrue(fm1.activePlayer);

		// P2: Total 300k, Split -20k
		assertEquals(300000L, fm2.total);
		assertEquals(-20000L, fm2.split);
		assertTrue(fm2.activePlayer);

		// P3: Total 60k, Split +170k
		assertEquals(60000L, fm3.total);
		assertEquals(170000L, fm3.split);
		assertTrue(fm3.activePlayer);
	}

	@Test
	public void testRemovePlayerFromSession()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		when(playerManager.getMainName(p1)).thenReturn(p1);
		when(playerManager.getMainName(p2)).thenReturn(p2);

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);

		assertTrue(managerSession.currentSessionHasPlayer(p1));
		assertTrue(managerSession.currentSessionHasPlayer(p2));

		managerSession.removePlayerFromSession(p1);

		assertFalse(managerSession.currentSessionHasPlayer(p1));
		assertTrue(managerSession.currentSessionHasPlayer(p2));
	}

	@Test
	public void testPersistence()
	{
		managerSession.startSession();
		managerSession.saveToConfig();

		verify(config, atLeastOnce()).sessionsJson(anyString());

		when(config.sessionsJson()).thenReturn(gson.toJson(managerSession.getAllSessionsNewestFirst()));

		ManagerSession newManager = new ManagerSession(config, playerManager, pluginManager, gson);
		newManager.loadFromConfig();

		assertEquals(managerSession.getAllSessionsNewestFirst().size(), newManager.getAllSessionsNewestFirst().size());
	}

	@Test
	public void testFileBackedPersistence() throws Exception
	{
		File file = temporaryFolder.newFile("sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);
		ManagerSession fileManager = new ManagerSession(config, playerManager, pluginManager, gson, storage);

		fileManager.startSession();
		String currentId = requireSession(fileManager.getCurrentSession()).getId();

		ManagerSession restored = new ManagerSession(config, playerManager, pluginManager, gson, storage);
		restored.loadFromConfig();

		assertEquals(fileManager.getAllSessionsNewestFirst().size(), restored.getAllSessionsNewestFirst().size());
		assertEquals(currentId, requireSession(restored.getCurrentSession()).getId());
		verify(config, never()).sessionsJson(anyString());
	}

	@Test
	public void testMigratesLegacyConfigToFileAndClearsOldKeys() throws Exception
	{
		File file = new File(temporaryFolder.getRoot(), "sessions.json");
		Session archived = new Session("archived", Instant.EPOCH, null);
		when(config.sessionsJson()).thenReturn(gson.toJson(new Session[]{archived}));
		when(config.currentSessionId()).thenReturn("archived");
		when(config.historyLoaded()).thenReturn(false);
		SessionStorage storage = new SessionStorage(file, gson);
		ManagerSession fileManager = new ManagerSession(config, playerManager, pluginManager, gson, storage);

		fileManager.loadFromConfig();

		assertTrue(file.exists());
		assertEquals(1, fileManager.getAllSessionsNewestFirst().size());
		assertEquals("archived", requireSession(fileManager.getCurrentSession()).getId());
		verify(config).sessionsJson("");
		verify(config).currentSessionId("");
		verify(config).historyLoaded(false);
	}

	@Test
	public void testMigratesProvidedLegacyProfileFileToVersionedSessionFile() throws Exception
	{
		Properties properties = new Properties();
		try (InputStream stream = getClass().getResourceAsStream("/com/splitmanager/legacy-profile-sessions.properties"))
		{
			assertNotNull(stream);
			properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		String legacySessionsJson = properties.getProperty("Split Manager.sessionsJson");
		String legacyCurrentSessionId = properties.getProperty("Split Manager.currentSessionId");
		String legacyHistoryLoaded = properties.getProperty("Split Manager.historyLoaded", "false");
		String legacyKnownPlayersCsv = properties.getProperty("Split Manager.PlayersCsv");
		String legacyAltsJson = properties.getProperty("Split Manager.altsJson");
		assertNotNull(legacySessionsJson);
		assertNotNull(legacyKnownPlayersCsv);
		assertNotNull(legacyAltsJson);
		assertTrue(legacyKnownPlayersCsv.contains("Player001"));
		assertTrue(legacyAltsJson.contains("Player"));

		File migratedFile = new File(temporaryFolder.getRoot(), "migrated-sessions.json");
		when(config.sessionsJson()).thenReturn(legacySessionsJson);
		when(config.currentSessionId()).thenReturn(legacyCurrentSessionId);
		when(config.historyLoaded()).thenReturn(Boolean.parseBoolean(legacyHistoryLoaded));
		SessionStorage storage = new SessionStorage(migratedFile, gson);
		ManagerSession fileManager = new ManagerSession(config, playerManager, pluginManager, gson, storage);

		fileManager.loadFromConfig();

		assertTrue(migratedFile.exists());
		assertFalse(fileManager.getAllSessionsNewestFirst().isEmpty());
		assertEquals(legacyCurrentSessionId, requireSession(fileManager.getCurrentSession()).getId());
		SessionStorageData migrated = storage.load();
		assertEquals(SessionStorageData.CURRENT_SCHEMA_VERSION, migrated.getSchemaVersion());
		assertEquals(legacyCurrentSessionId, migrated.getCurrentSessionId());
		assertEquals(fileManager.getAllSessionsNewestFirst().size(), migrated.getSessions().size());
		verify(config).sessionsJson("");
		verify(config).currentSessionId("");
		verify(config).historyLoaded(false);
	}

	@Test
	public void testDoesNotClearLegacyConfigWhenMigrationWriteFails() throws Exception
	{
		File parentFile = temporaryFolder.newFile("not-a-directory");
		File file = new File(parentFile, "sessions.json");
		Session archived = new Session("archived", Instant.EPOCH, null);
		when(config.sessionsJson()).thenReturn(gson.toJson(new Session[]{archived}));
		when(config.currentSessionId()).thenReturn("archived");
		when(config.historyLoaded()).thenReturn(false);
		SessionStorage storage = new SessionStorage(file, gson);
		ManagerSession fileManager = new ManagerSession(config, playerManager, pluginManager, gson, storage);

		fileManager.loadFromConfig();

		assertEquals(1, fileManager.getAllSessionsNewestFirst().size());
		verify(config, never()).sessionsJson("");
		verify(config, never()).currentSessionId("");
		verify(config, never()).historyLoaded(false);
	}

	@Test
	public void testDoesNotClearInvalidLegacyConfigDuringMigration() throws Exception
	{
		File file = new File(temporaryFolder.getRoot(), "sessions.json");
		when(config.sessionsJson()).thenReturn("{not valid json");
		SessionStorage storage = new SessionStorage(file, gson);
		ManagerSession fileManager = new ManagerSession(config, playerManager, pluginManager, gson, storage);

		fileManager.loadFromConfig();

		assertFalse(file.exists());
		assertTrue(fileManager.getAllSessionsNewestFirst().isEmpty());
		verify(config, never()).sessionsJson("");
		verify(config, never()).currentSessionId("");
		verify(config, never()).historyLoaded(false);
	}

	@Test
	public void testKnownAndNonActivePlayers()
	{
		String p1 = "Player1";
		String p2 = "Player2";
		String p3 = "Player3";
		Set<String> mains = new LinkedHashSet<>(Arrays.asList(p1, p2, p3));
		when(playerManager.getKnownMains()).thenReturn(mains);

		assertEquals(mains, managerSession.getKnownPlayers());
		assertEquals(mains, managerSession.getNonActivePlayers());

		managerSession.startSession();
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);

		Set<String> nonActive = managerSession.getNonActivePlayers();
		assertFalse(nonActive.contains(p1));
		assertTrue(nonActive.contains(p2));
		assertTrue(nonActive.contains(p3));
	}

	@Test
	public void testLoadAndUnloadHistory()
	{
		Session archived = new Session("archived", Instant.EPOCH, null);
		archived.setEnd(Instant.EPOCH.plusSeconds(1));
		when(config.sessionsJson()).thenReturn(gson.toJson(new Session[]{archived}));
		when(config.currentSessionId()).thenReturn("");

		managerSession.loadFromConfig();

		assertFalse(managerSession.loadHistory("missing").isPresent());
		Optional<Session> loaded = managerSession.loadHistory("archived");
		assertTrue(loaded.isPresent());
		assertTrue(managerSession.isHistoryLoaded());
		assertEquals("archived", requireSession(managerSession.getCurrentSession()).getId());
		assertFalse(managerSession.startSession().isPresent());

		managerSession.unloadHistory();
		assertFalse(managerSession.isHistoryLoaded());
		assertFalse(managerSession.getCurrentSession().isPresent());
	}

	@Test
	public void testAddPlayerResolvesAltToMainInRoster()
	{
		managerSession.startSession();
		when(playerManager.getMainName("AltPlayer")).thenReturn("MainPlayer");

		assertTrue(managerSession.addPlayerToActive("AltPlayer"));

		Session current = requireSession(managerSession.getCurrentSession());
		assertTrue(current.getPlayers().contains("MainPlayer"));
		assertFalse(current.getPlayers().contains("AltPlayer"));
		assertTrue(managerSession.currentSessionHasPlayer("AltPlayer"));

		assertTrue(managerSession.removePlayerFromSession("AltPlayer"));
		assertFalse(requireSession(managerSession.getCurrentSession()).getPlayers().contains("MainPlayer"));
	}

	@Test
	public void testAddLootRejectsInvalidStatesAndPlayers()
	{
		assertFalse(managerSession.addLoot("Ghost", 1L));

		managerSession.startSession();
		when(playerManager.getMainName("Ghost")).thenReturn("Ghost");
		assertFalse(managerSession.addLoot("Ghost", 1L));

		String p1 = "Player1";
		resolveToSelf(p1);
		assertTrue(managerSession.addPlayerToActive(p1));
		assertTrue(managerSession.addLoot(p1, 42L));
		assertFalse(managerSession.addLoot(p1, null));

		assertFalse(managerSession.addLoot(" ", 42L));
	}

	@Test
	public void testRosterOnlyEventsDoNotForkChildSessions()
	{
		managerSession.startSession();
		resolveToSelf("Player1", "Player2", "Player3");

		assertTrue(managerSession.addPlayerToActive("Player1"));
		Session firstChild = requireSession(managerSession.getCurrentSession());

		assertTrue(managerSession.addPlayerToActive("Player2"));
		assertTrue(managerSession.removePlayerFromSession("Player1"));

		Session current = requireSession(managerSession.getCurrentSession());
		assertEquals(firstChild.getId(), current.getId());
		assertEquals(2, managerSession.getAllSessionsNewestFirst().size());
		assertFalse(current.hasLootEvents());
		assertTrue(current.hasEvents());

		assertTrue(managerSession.addLoot("Player2", 42L));
		assertTrue(managerSession.addPlayerToActive("Player3"));

		Session forkedChild = requireSession(managerSession.getCurrentSession());
		assertNotEquals(firstChild.getId(), forkedChild.getId());
		assertEquals(3, managerSession.getAllSessionsNewestFirst().size());
	}

	@Test
	public void testAddPendingValueAutoAppliesForActiveRoster()
	{
		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		when(config.autoApplyWhenInSession()).thenReturn(true);

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 50", 50000L, p1);
		managerSession.addPendingValue(pv);

		assertTrue(managerSession.getPendingValues().isEmpty());
		assertTrue(requireSession(managerSession.getCurrentSession()).getEvents().stream()
			.anyMatch(k -> p1.equals(k.getPlayer()) && Long.valueOf(50000L).equals(k.getAmount())));
	}

	@Test
	public void testPendingValuesCanBeRemovedAndAreCapped()
	{
		when(playerManager.getMainName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

		for (int i = 0; i < 102; i++)
		{
			managerSession.addPendingValue(PendingValue.of(PendingValue.Type.ADD, "Clan", "!add " + i, (long) i, "P" + i));
		}

		assertEquals(100, managerSession.getPendingValues().size());
		String id = managerSession.getPendingValues().get(0).getId();
		assertTrue(managerSession.removePendingValueById(id));
		assertFalse(managerSession.removePendingValueById(id));
	}

	@Test
	public void testSessionHasPlayerHandlesNulls()
	{
		assertFalse(managerSession.sessionHasPlayer(null, null));
		managerSession.startSession();
		assertFalse(managerSession.sessionHasPlayer(null, requireSession(managerSession.getCurrentSession())));
	}

	@Test
	public void testLoadFromConfigHandlesInvalidJson()
	{
		when(config.sessionsJson()).thenReturn("{not valid json");
		when(config.currentSessionId()).thenReturn("missing");

		managerSession.loadFromConfig();

		assertTrue(managerSession.getAllSessionsNewestFirst().isEmpty());
		assertTrue(managerSession.getCurrentSession().isEmpty());
	}

	@Test
	public void testGetAllEventsUsesCacheAndRebuildsAfterLoad()
	{
		assertTrue(managerSession.getAllEvents().isEmpty());

		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		managerSession.addLoot(p1, 100000L);

		List<SplitEvent> cached = managerSession.getAllEvents();
		assertTrue(cached.size() >= 2);
		try
		{
			cached.add(new SplitEvent("ignored", p1, 1L, Instant.now()));
			fail("Cached events should be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertTrue(true);
		}

		String currentId = requireSession(managerSession.getCurrentSession()).getId();
		when(config.sessionsJson()).thenReturn(gson.toJson(managerSession.getAllSessionsNewestFirst()));
		when(config.currentSessionId()).thenReturn(currentId);

		ManagerSession reloaded = new ManagerSession(config, playerManager, pluginManager, gson);
		reloaded.loadFromConfig();

		assertEquals(cached.size(), reloaded.getAllEvents().size());
	}

	@Test
	public void testMoveEventReordersWithinSessionAndAllowsDropAfterLastRow()
	{
		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		managerSession.addLoot(p1, 100000L);
		managerSession.addLoot(p1, 200000L);

		managerSession.moveEvent(1, 3);

		List<SplitEvent> events = managerSession.getAllEvents();
		assertEquals(0L, events.get(0).getAmount().longValue());
		assertEquals(200000L, events.get(1).getAmount().longValue());
		assertEquals(100000L, events.get(2).getAmount().longValue());
	}

	@Test
	public void testMoveEventAcrossSessionSegmentsUsesTargetSegment()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		resolveToSelf(p1, p2);
		managerSession.addPlayerToActive(p1);
		managerSession.addLoot(p1, 100000L);
		managerSession.addPlayerToActive(p2);
		managerSession.addLoot(p2, 200000L);

		managerSession.moveEvent(1, 4);

		List<SplitEvent> events = managerSession.getAllEvents();
		assertEquals(4, events.size());
		assertEquals(0L, events.get(0).getAmount().longValue());
		assertEquals(0L, events.get(1).getAmount().longValue());
		assertEquals(200000L, events.get(2).getAmount().longValue());
		assertEquals(100000L, events.get(3).getAmount().longValue());
		assertEquals(events.get(2).getSessionId(), events.get(3).getSessionId());
	}

	@Test
	public void testMoveLootEventInHistoryModeRecalculatesMetricsForTargetSegment()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		String p3 = "Player3";
		resolveToSelf(p1, p2, p3);

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		managerSession.addLoot(p1, 100000L);
		managerSession.addPlayerToActive(p3);
		managerSession.addLoot(p2, 300000L);
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertTrue(managerSession.loadHistory(historyRoot.getId()).isPresent());
		Session editable = requireSession(managerSession.getCurrentEditableSession());

		List<PlayerMetrics> before = managerSession.computeMetricsFor(editable, true);
		assertEquals(50000L, requireMetric(before, p1).split);
		assertEquals(-150000L, requireMetric(before, p2).split);
		assertEquals(100000L, requireMetric(before, p3).split);

		int lootIndex = findLootEventIndex(p1, 100000L);
		assertTrue(lootIndex >= 0);
		assertTrue(managerSession.moveEvent(lootIndex, managerSession.getAllEvents().size()));

		List<PlayerMetrics> after = managerSession.computeMetricsFor(editable, true);
		assertEquals(100000L, requireMetric(after, p1).total);
		assertEquals(33333L, requireMetric(after, p1).split);
		assertEquals(300000L, requireMetric(after, p2).total);
		assertEquals(-166667L, requireMetric(after, p2).split);
		assertEquals(0L, requireMetric(after, p3).total);
		assertEquals(133333L, requireMetric(after, p3).split);
	}

	@Test
	public void testMoveEventRejectsLeftBeforeJoined()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		resolveToSelf(p1, p2);

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		assertTrue(managerSession.removePlayerFromSession(p2));

		List<SplitEvent> before = managerSession.getAllEvents();
		int joinedIndex = -1;
		int leftIndex = -1;
		for (int i = 0; i < before.size(); i++)
		{
			SplitEvent event = before.get(i);
			if (p2.equals(event.getPlayer()) && SplitEvent.TYPE_JOINED.equals(event.getType()))
			{
				joinedIndex = i;
			}
			if (p2.equals(event.getPlayer()) && SplitEvent.TYPE_LEFT.equals(event.getType()))
			{
				leftIndex = i;
			}
		}
		assertTrue(joinedIndex >= 0);
		assertTrue(leftIndex > joinedIndex);

		assertFalse(managerSession.moveEvent(leftIndex, joinedIndex));

		List<SplitEvent> after = managerSession.getAllEvents();
		assertEquals(SplitEvent.TYPE_JOINED, after.get(joinedIndex).getType());
		assertEquals(p2, after.get(joinedIndex).getPlayer());
		assertEquals(SplitEvent.TYPE_LEFT, after.get(leftIndex).getType());
		assertEquals(p2, after.get(leftIndex).getPlayer());
	}

	@Test
	public void testInvalidRosterEventMoveInHistoryModePreservesMetrics()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		resolveToSelf(p1, p2);

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		managerSession.addLoot(p1, 100000L);
		assertTrue(managerSession.removePlayerFromSession(p2));
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		assertTrue(managerSession.loadHistory(historyRoot.getId()).isPresent());
		Session editable = requireSession(managerSession.getCurrentEditableSession());
		List<PlayerMetrics> beforeMetrics = managerSession.computeMetricsFor(editable, true);

		int joinedIndex = findEventIndex(SplitEvent.TYPE_JOINED, p2);
		int leftIndex = findEventIndex(SplitEvent.TYPE_LEFT, p2);
		assertTrue(joinedIndex >= 0);
		assertTrue(leftIndex > joinedIndex);

		assertFalse(managerSession.moveEvent(leftIndex, joinedIndex));

		List<SplitEvent> afterEvents = managerSession.getAllEvents();
		assertEquals(SplitEvent.TYPE_JOINED, afterEvents.get(joinedIndex).getType());
		assertEquals(p2, afterEvents.get(joinedIndex).getPlayer());
		assertEquals(SplitEvent.TYPE_LEFT, afterEvents.get(leftIndex).getType());
		assertEquals(p2, afterEvents.get(leftIndex).getPlayer());

		List<PlayerMetrics> afterMetrics = managerSession.computeMetricsFor(editable, true);
		assertEquals(requireMetric(beforeMetrics, p1).total, requireMetric(afterMetrics, p1).total);
		assertEquals(requireMetric(beforeMetrics, p1).split, requireMetric(afterMetrics, p1).split);
		assertEquals(requireMetric(beforeMetrics, p2).total, requireMetric(afterMetrics, p2).total);
		assertEquals(requireMetric(beforeMetrics, p2).split, requireMetric(afterMetrics, p2).split);
	}

	@Test
	public void testRemoveLeftRosterEventReactivatesPlayerForSettlement()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		resolveToSelf(p1, p2);
		when(playerManager.getKnownPlayers()).thenReturn(new LinkedHashSet<>(Arrays.asList(p1, p2)));

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		managerSession.addLoot(p1, 100000L);
		assertTrue(managerSession.removePlayerFromSession(p2));
		assertFalse(requireSession(managerSession.getCurrentSession()).getPlayers().contains(p2));

		List<SplitEvent> events = managerSession.getAllEvents();
		int leftIndex = -1;
		for (int i = 0; i < events.size(); i++)
		{
			if (SplitEvent.TYPE_LEFT.equals(events.get(i).getType()) && p2.equals(events.get(i).getPlayer()))
			{
				leftIndex = i;
				break;
			}
		}
		assertTrue(leftIndex >= 0);

		managerSession.removeEventAt(leftIndex);

		assertTrue(requireSession(managerSession.getCurrentSession()).getPlayers().contains(p2));
		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(requireSession(managerSession.getCurrentSession()), true);
		assertTrue(metrics.stream().anyMatch(m -> p2.equals(m.player) && m.activePlayer));
	}

	@Test
	public void testComputeMetricsHandlesNullAndSkipsZeroInactivePlayers()
	{
		assertTrue(managerSession.computeMetricsFor(null, true).isEmpty());

		managerSession.startSession();
		String active = "Active";
		resolveToSelf(active);
		when(playerManager.getKnownPlayers()).thenReturn(new LinkedHashSet<>(Arrays.asList(active, "Unused")));
		managerSession.addPlayerToActive(active);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(requireSession(managerSession.getCurrentSession()), true);

		assertTrue(metrics.stream().anyMatch(m -> active.equals(m.player) && m.activePlayer));
		assertFalse(metrics.stream().anyMatch(m -> "Unused".equals(m.player)));
	}

	@Test
	public void testRemoveEventsRepairsRosterAcrossJoinAndLeaveEventsInHistoryMode()
	{
		managerSession.startSession();
		resolveToSelf("Player1", "Player2");

		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		managerSession.stopSession();

		Session historyRoot = managerSession.getHistorySessionsNewestFirst().get(0);
		managerSession.loadHistory(historyRoot.getId());
		managerSession.addPlayerToActive("Player2");
		managerSession.removePlayerFromSession("Player2");

		int joinedIndex = findEventIndex(SplitEvent.TYPE_JOINED, "Player2");
		int leftIndex = findEventIndex(SplitEvent.TYPE_LEFT, "Player2");
		assertTrue(joinedIndex >= 0);
		assertTrue(leftIndex >= 0);

		managerSession.removeEventsAt(Arrays.asList(joinedIndex, leftIndex));

		Session current = requireSession(managerSession.getCurrentSession());
		assertFalse(managerSession.getAllEvents().stream().anyMatch(event ->
			"Player2".equalsIgnoreCase(event.getPlayer()) && event.isRosterEvent()));
	}

	@Test
	public void testInsertAndMoveEventEdgeCases()
	{
		managerSession.startSession();
		resolveToSelf("Player1", "Player2");

		managerSession.addPlayerToActive("Player1");
		managerSession.addPlayerToActive("Player2");
		managerSession.addLoot("Player1", 100L);

		managerSession.insertLootAt(0, "Player1", 250L);
		assertTrue(managerSession.getAllEvents().stream().anyMatch(event ->
			Long.valueOf(250L).equals(event.getAmount()) && SplitEvent.TYPE_LOOT.equalsIgnoreCase(event.getType())));

		assertFalse(managerSession.moveEvent(-1, 0));
		assertFalse(managerSession.moveEvent(0, 0));

		int insertIndex = findEventIndex(SplitEvent.TYPE_LOOT, "Player1");
		assertTrue(insertIndex >= 0);
		assertTrue(managerSession.moveEvent(insertIndex, 0));
	}

	@Test
	public void testPendingValueAutoApplyAndQueueLimit()
	{
		managerSession.startSession();
		resolveToSelf("Player1");
		when(config.autoApplyWhenInSession()).thenReturn(true);
		managerSession.addPlayerToActive("Player1");

		PendingValue autoApply = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 50", 50000L, "Player1");
		managerSession.addPendingValue(autoApply);

		assertTrue(requireSession(managerSession.getCurrentSession()).hasEvents());
		assertTrue(managerSession.getPendingValues().isEmpty());

		when(config.autoApplyWhenInSession()).thenReturn(false);
		for (int i = 0; i < 101; i++)
		{
			managerSession.addPendingValue(PendingValue.of(
				PendingValue.Type.ADD,
				"Clan",
				"!add 1",
				1L,
				"Player1"));
		}

		assertEquals(100, managerSession.getPendingValues().size());
		assertEquals(1L, (long) managerSession.getPendingValues().get(99).getValue());
	}

	@Test
	public void testHistoryAndConfigEdgeCases()
	{
		when(config.sessionsJson()).thenReturn("{not valid json");
		managerSession.loadFromConfig();

		assertFalse(managerSession.hasActiveSession());
		assertTrue(managerSession.getAllSessionsNewestFirst().isEmpty());
		assertEquals("", managerSession.exportSessionJson("missing"));
		assertEquals("", managerSession.exportSessionThreadJson("missing"));
		assertEquals(0, managerSession.importHistorySessionsJson("   "));
		assertEquals(0, managerSession.importHistorySessionsJson(gson.toJson(new Session[]{})));

		Session root = new Session("root", Instant.EPOCH, null);
		root.setEnd(Instant.EPOCH.plusSeconds(1));
		Session child = new Session("child", Instant.EPOCH.plusSeconds(2), "root");
		child.setEnd(Instant.EPOCH.plusSeconds(3));
		Session duplicateIdRoot = new Session("root", Instant.EPOCH.plusSeconds(4), null);
		duplicateIdRoot.setEnd(Instant.EPOCH.plusSeconds(5));
		assertEquals(0, managerSession.importHistorySessionsJson(gson.toJson(new Session[]{root, duplicateIdRoot})));
		assertEquals(0, managerSession.importHistorySessionsJson(gson.toJson(new Session[]{child})));

		when(playerManager.getKnownMains()).thenReturn(null);
		assertTrue(managerSession.getKnownPlayers().isEmpty());
		assertTrue(managerSession.getNonActivePlayers().isEmpty());

		when(playerManager.getKnownMains()).thenReturn(new LinkedHashSet<>(Arrays.asList("Alpha", "Beta")));
		assertEquals(new LinkedHashSet<>(Arrays.asList("Alpha", "Beta")), managerSession.getNonActivePlayers());
		assertFalse(managerSession.saveHistoryChanges());
		assertFalse(managerSession.discardHistoryChanges());
		assertEquals(Collections.emptyList(), managerSession.computeMetricsFor(null));
		assertEquals(Collections.emptyList(), managerSession.computeMetricsFor(null, true, null));
		assertFalse(managerSession.updateSettlementConfigSnapshotFor(null, null));
		assertFalse(managerSession.updateSettlementConfigSnapshotFor(root, null));
		assertNotNull(managerSession.getSettlementConfigSnapshotFor(null));
		assertFalse(managerSession.getSettlementConfigSnapshotFor(null).isAccountForGeTax());
	}
}
