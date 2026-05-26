package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.InstantTypeAdapter;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ManagerSessionTest
{
	@Mock
	private PluginConfig config;

	@Mock
	private ManagerKnownPlayers playerManager;

	@Mock
	private ManagerPlugin pluginManager;

	private Gson gson = new Gson().newBuilder()
		.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
		.create();

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

	@Test
	public void testStartSession()
	{
		Optional<Session> sessionOpt = managerSession.startSession();
		assertTrue(sessionOpt.isPresent());
		Session child = sessionOpt.get();
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
		// Verify kill was added (implicitly via session state)
		assertTrue(managerSession.getCurrentSession().get().hasKills());
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
		Session child = managerSession.getCurrentSession().get();

		// p1 gets a 100k drop
		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100", 100000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);

		assertEquals(2, metrics.size());

		// Basic split check: 100k total, 2 players -> 50k each.
		// p1: 100k total, -50k split (50k - 100k)
		// p2: 0 total, +50k split (50k - 0)
		PlayerMetrics m1 = metrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics m2 = metrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();

		assertEquals(100000L, (long) m1.total);
		assertEquals(-50000L, (long) m1.split);

		assertEquals(0L, (long) m2.total);
		assertEquals(50000L, (long) m2.split);
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

		Session child = managerSession.getCurrentSession().get();

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = metrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics m2 = metrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();

		assertEquals(100000000L, (long) m1.total);
		assertEquals(-52000000L, (long) m1.split);
		assertEquals(0L, (long) m2.total);
		assertEquals(50000000L, (long) m2.split);
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

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		Session child = managerSession.getCurrentSession().get();

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = metrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics m2 = metrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();

		assertEquals(100000000L, (long) m1.total);
		assertEquals(-52000000L, (long) m1.split);
		assertEquals(50000000L, (long) m2.split);
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

		managerSession.addPlayerToActive(p1);
		managerSession.addPlayerToActive(p2);
		Session child = managerSession.getCurrentSession().get();

		PendingValue pv = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 100m", 100000000L, p1);
		managerSession.addPendingValue(pv);
		managerSession.applyPendingValueToPlayer(pv.getId(), p1);

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(child);
		PlayerMetrics m1 = metrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics m2 = metrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();

		assertEquals(100000000L, (long) m1.total);
		assertEquals(-52000000L, (long) m1.split);
		assertEquals(50000000L, (long) m2.split);
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

		// Segment 2: Add P3 (forks because Segment 1 has kills)
		managerSession.addPlayerToActive(p3);

		// P2 drops 300k
		PendingValue pv2 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 300", 300000L, p2);
		managerSession.addPendingValue(pv2);
		managerSession.applyPendingValueToPlayer(pv2.getId(), p2);

		// Segment 3: Remove P1 (forks because Segment 2 has kills)
		managerSession.removePlayerFromSession(p1);

		// P3 drops 60k
		PendingValue pv3 = PendingValue.of(PendingValue.Type.ADD, "Clan", "!add 60", 60000L, p3);
		managerSession.addPendingValue(pv3);
		managerSession.applyPendingValueToPlayer(pv3.getId(), p3);

		// Current session is Segment 3 (P2, P3 active)
		Session current = managerSession.getCurrentSession().get();
		assertEquals(2, current.getPlayers().size());

		// Test with includeNonActivePlayers = true (what the UI uses)
		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(current, true);

		// Should include P1, P2, P3
		// P1 Total: 100k, Split: +50k
		// P2 Total: 300k, Split: -120k
		// P3 Total: 60k, Split: +70k
		assertEquals(3, metrics.size());

		PlayerMetrics m1 = metrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics m2 = metrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();
		PlayerMetrics m3 = metrics.stream().filter(m -> m.player.equals(p3)).findFirst().get();

		assertEquals(100000L, (long) m1.total);
		assertEquals(50000L, (long) m1.split);
		assertFalse(m1.activePlayer);

		assertEquals(300000L, (long) m2.total);
		assertEquals(-120000L, (long) m2.split);
		assertTrue(m2.activePlayer);

		assertEquals(60000L, (long) m3.total);
		assertEquals(70000L, (long) m3.split);
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
		Session finalSession = managerSession.getCurrentSession().get();
		assertEquals(3, finalSession.getPlayers().size());

		List<PlayerMetrics> finalMetrics = managerSession.computeMetricsFor(finalSession, true);
		assertEquals(3, finalMetrics.size());

		PlayerMetrics fm1 = finalMetrics.stream().filter(m -> m.player.equals(p1)).findFirst().get();
		PlayerMetrics fm2 = finalMetrics.stream().filter(m -> m.player.equals(p2)).findFirst().get();
		PlayerMetrics fm3 = finalMetrics.stream().filter(m -> m.player.equals(p3)).findFirst().get();

		// P1: Total 400k, Split -150k
		assertEquals(400000L, (long) fm1.total);
		assertEquals(-150000L, (long) fm1.split);
		assertTrue(fm1.activePlayer);

		// P2: Total 300k, Split -20k
		assertEquals(300000L, (long) fm2.total);
		assertEquals(-20000L, (long) fm2.split);
		assertTrue(fm2.activePlayer);

		// P3: Total 60k, Split +170k
		assertEquals(60000L, (long) fm3.total);
		assertEquals(170000L, (long) fm3.split);
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
		assertEquals("archived", managerSession.getCurrentSession().get().getId());
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

		Session current = managerSession.getCurrentSession().get();
		assertTrue(current.getPlayers().contains("MainPlayer"));
		assertFalse(current.getPlayers().contains("AltPlayer"));
		assertTrue(managerSession.currentSessionHasPlayer("AltPlayer"));

		assertTrue(managerSession.removePlayerFromSession("AltPlayer"));
		assertFalse(managerSession.getCurrentSession().get().getPlayers().contains("MainPlayer"));
	}

	@Test
	public void testAddKillRejectsInvalidStatesAndPlayers()
	{
		assertFalse(managerSession.addKill("Ghost", 1L));

		managerSession.startSession();
		when(playerManager.getMainName("Ghost")).thenReturn("Ghost");
		assertFalse(managerSession.addKill("Ghost", 1L));

		String p1 = "Player1";
		resolveToSelf(p1);
		assertTrue(managerSession.addPlayerToActive(p1));
		assertTrue(managerSession.addKill(p1, 42L));
		assertFalse(managerSession.addKill(p1, null));

		assertFalse(managerSession.addKill(" ", 42L));
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
		assertTrue(managerSession.getCurrentSession().get().getKills().stream()
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
		assertFalse(managerSession.sessionHasPlayer(null, managerSession.getCurrentSession().get()));
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
	public void testGetAllKillsUsesCacheAndRebuildsAfterLoad()
	{
		assertTrue(managerSession.getAllKills().isEmpty());

		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		managerSession.addKill(p1, 100000L);

		List<Kill> cached = managerSession.getAllKills();
		assertTrue(cached.size() >= 2);
		try
		{
			cached.add(new Kill("ignored", p1, 1L, Instant.now()));
			fail("Cached kills should be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertTrue(true);
		}

		String currentId = managerSession.getCurrentSession().get().getId();
		when(config.sessionsJson()).thenReturn(gson.toJson(managerSession.getAllSessionsNewestFirst()));
		when(config.currentSessionId()).thenReturn(currentId);

		ManagerSession reloaded = new ManagerSession(config, playerManager, pluginManager, gson);
		reloaded.loadFromConfig();

		assertEquals(cached.size(), reloaded.getAllKills().size());
	}

	@Test
	public void testMoveKillReordersWithinSessionAndAllowsDropAfterLastRow()
	{
		managerSession.startSession();
		String p1 = "Player1";
		resolveToSelf(p1);
		managerSession.addPlayerToActive(p1);
		managerSession.addKill(p1, 100000L);
		managerSession.addKill(p1, 200000L);

		managerSession.moveKill(1, 3);

		List<Kill> kills = managerSession.getAllKills();
		assertEquals(0L, kills.get(0).getAmount().longValue());
		assertEquals(200000L, kills.get(1).getAmount().longValue());
		assertEquals(100000L, kills.get(2).getAmount().longValue());
	}

	@Test
	public void testMoveKillAcrossSessionSegmentsUsesTargetSegment()
	{
		managerSession.startSession();
		String p1 = "Player1";
		String p2 = "Player2";
		resolveToSelf(p1, p2);
		managerSession.addPlayerToActive(p1);
		managerSession.addKill(p1, 100000L);
		managerSession.addPlayerToActive(p2);
		managerSession.addKill(p2, 200000L);

		managerSession.moveKill(1, 4);

		List<Kill> kills = managerSession.getAllKills();
		assertEquals(4, kills.size());
		assertEquals(0L, kills.get(0).getAmount().longValue());
		assertEquals(0L, kills.get(1).getAmount().longValue());
		assertEquals(200000L, kills.get(2).getAmount().longValue());
		assertEquals(100000L, kills.get(3).getAmount().longValue());
		assertEquals(kills.get(2).getSessionId(), kills.get(3).getSessionId());
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
		managerSession.addKill(p1, 100000L);
		assertTrue(managerSession.removePlayerFromSession(p2));
		assertFalse(managerSession.getCurrentSession().get().getPlayers().contains(p2));

		List<Kill> kills = managerSession.getAllKills();
		int leftIndex = -1;
		for (int i = 0; i < kills.size(); i++)
		{
			if (Kill.TYPE_LEFT.equals(kills.get(i).getType()) && p2.equals(kills.get(i).getPlayer()))
			{
				leftIndex = i;
				break;
			}
		}
		assertTrue(leftIndex >= 0);

		managerSession.removeKillAt(leftIndex);

		assertTrue(managerSession.getCurrentSession().get().getPlayers().contains(p2));
		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(managerSession.getCurrentSession().get(), true);
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

		List<PlayerMetrics> metrics = managerSession.computeMetricsFor(managerSession.getCurrentSession().get(), true);

		assertTrue(metrics.stream().anyMatch(m -> active.equals(m.player) && m.activePlayer));
		assertFalse(metrics.stream().anyMatch(m -> "Unused".equals(m.player)));
	}
}
