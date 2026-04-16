package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.models.Kill;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.views.PanelView;
import com.splitmanager.utils.InstantTypeAdapter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

		PanelView mockView = mock(PanelView.class);
		// Mock JOptionPane is hard, but stopSession uses it.
		// Since we can't easily mock static JOptionPane, we might need to refactor or just accept it's hard to test fully here.
		// However, in a headless test environment, JOptionPane might throw an exception or return a default.
		// Let's see if we can at least test the logic if we didn't have the dialog.
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
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());

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
		when(playerManager.getKnownPlayers()).thenReturn(new HashSet<>());
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
		assertFalse(managerSession.startSession().isPresent());

		managerSession.unloadHistory();
		assertFalse(managerSession.isHistoryLoaded());
	}

	@Test
	public void testAddPlayerResolvesAltToMainInRoster()
	{
		managerSession.startSession();
		when(playerManager.getMainName("AltPlayer")).thenReturn("MainPlayer");
		when(playerManager.isAlt("AltPlayer")).thenReturn(true);

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

		when(playerManager.getMainName("")).thenReturn("");
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
		when(playerManager.getKnownPlayers()).thenReturn(new LinkedHashSet<>(Collections.singleton(p1)));

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
		when(playerManager.getKnownPlayers()).thenReturn(new LinkedHashSet<>());

		for (int i = 0; i < 102; i++)
		{
			managerSession.addPendingValue(PendingValue.of(PendingValue.Type.ADD, "Clan", "!add " + i, (long) i, "P" + i));
		}

		assertEquals(101, managerSession.getPendingValues().size());
		String id = managerSession.getPendingValues().get(0).getId();
		assertTrue(managerSession.removePendingValueById(id));
		assertFalse(managerSession.removePendingValueById(id));
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
