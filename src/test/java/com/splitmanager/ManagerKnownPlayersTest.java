package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.utils.InstantTypeAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ManagerKnownPlayersTest
{
	private final Gson gson = new Gson().newBuilder()
		.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
		.create();
	@Mock
	private PluginConfig config;
	private ManagerKnownPlayers playerManager;

	@Before
	public void setUp()
	{
		playerManager = new ManagerKnownPlayers(config, gson);
	}

	@Test
	public void testAddKnownPlayer()
	{
		String p1 = "Player1";
		boolean added = playerManager.addKnownPlayer(p1);

		assertTrue(added);
		assertTrue(playerManager.getKnownPlayers().contains(p1));
		assertFalse(playerManager.addKnownPlayer("Player1"));
		assertFalse(playerManager.addKnownPlayer("player1"));
		verify(config).knownPlayersCsv(p1);
	}

	@Test
	public void testRemoveKnownPlayer()
	{
		String p1 = "Player1";
		playerManager.addKnownPlayer(p1);

		boolean removed = playerManager.removeKnownPlayer(p1);
		assertTrue(removed);
		assertFalse(playerManager.getKnownPlayers().contains(p1));
		verify(config, atLeastOnce()).knownPlayersCsv("");
	}

	@Test
	public void testCanLinkAltToMain()
	{
		String main = "MainPlayer";
		String alt = "AltPlayer";

		// Basic link
		assertTrue(playerManager.canLinkAltToMain(alt, main));
		assertFalse(playerManager.canLinkAltToMain(null, main));
		assertFalse(playerManager.canLinkAltToMain(alt, null));
		assertFalse(playerManager.canLinkAltToMain(" ", main));
		assertFalse(playerManager.canLinkAltToMain(alt, " "));

		// Same player
		assertFalse(playerManager.canLinkAltToMain(main, main));

		// Linking to someone who is an alt
		playerManager.trySetAltMain("AnotherAlt", alt);
		assertFalse(playerManager.canLinkAltToMain(main, "AnotherAlt"));

		// Already linked to a different main
		playerManager.trySetAltMain(alt, main);
		assertFalse(playerManager.canLinkAltToMain(alt, "DifferentMain"));
	}

	@Test
	public void testTrySetAltMain()
	{
		String main = "MainPlayer";
		String alt = "AltPlayer";

		boolean success = playerManager.trySetAltMain(alt, main);
		assertTrue(success);
		assertEquals(main, playerManager.getMainName(alt));
		assertTrue(playerManager.isAlt(alt));
		assertFalse(playerManager.isAlt(main));
	}

	@Test
	public void testGetMainNameResolution()
	{
		// Mapping: Alt1 -> Main
		playerManager.trySetAltMain("Alt1", "Main");

		assertEquals("Main", playerManager.getMainName("Alt1"));
		assertEquals("Main", playerManager.getMainName("Main"));
		assertEquals("Unknown", playerManager.getMainName("Unknown"));
	}

	@Test
	public void testGetMainNameResolutionChain()
	{
		// Manually inject a chain via config to test resolution loop
		when(config.altsJson()).thenReturn("{\"Alt2\":\"Alt1\", \"Alt1\":\"Main\"}");
		playerManager.loadFromConfig();

		assertEquals("Main", playerManager.getMainName("Alt2"));
		assertEquals("Main", playerManager.getMainName("Alt1"));
	}

	@Test
	public void testGetAltsOf()
	{
		playerManager.trySetAltMain("Alt1", "Main");
		playerManager.trySetAltMain("Alt2", "Main");
		playerManager.trySetAltMain("OtherAlt", "OtherMain");

		List<String> alts = playerManager.getAltsOf("Main");
		assertEquals(2, alts.size());
		assertTrue(alts.contains("Alt1"));
		assertTrue(alts.contains("Alt2"));
	}

	@Test
	public void testUnlinkAlt()
	{
		playerManager.trySetAltMain("Alt1", "Main");
		assertTrue(playerManager.isAlt("Alt1"));

		boolean unlinked = playerManager.unlinkAlt("Alt1");
		assertTrue(unlinked);
		assertFalse(playerManager.isAlt("Alt1"));
		assertEquals("Alt1", playerManager.getMainName("Alt1"));
		assertFalse(playerManager.unlinkAlt(null));
		assertFalse(playerManager.unlinkAlt(" "));
		assertFalse(playerManager.unlinkAlt("Alt1"));
	}

	@Test
	public void testGetKnownMains()
	{
		playerManager.addKnownPlayer("Main1");
		playerManager.addKnownPlayer("Main2");
		playerManager.trySetAltMain("Alt1", "Main1");

		Set<String> mains = playerManager.getKnownMains();
		assertEquals(2, mains.size());
		assertTrue(mains.contains("Main1"));
		assertTrue(mains.contains("Main2"));
		assertFalse(mains.contains("Alt1"));
	}

	@Test
	public void testPersistence()
	{
		// Mock config returning data
		when(config.knownPlayersCsv()).thenReturn("P1,P2,P3");
		// Mock mapping {"Alt":"Main"}
		when(config.altsJson()).thenReturn("{\"Alt\":\"Main\"}");

		playerManager.loadFromConfig();

		assertTrue(playerManager.getKnownPlayers().contains("P1"));
		assertTrue(playerManager.getKnownPlayers().contains("P2"));
		assertTrue(playerManager.getKnownPlayers().contains("P3"));
		assertEquals("Main", playerManager.getMainName("Alt"));
		assertTrue(playerManager.isAlt("Alt"));
	}

	@Test
	public void testLoadFromConfigIgnoresBlankCsvAndInvalidAltJson()
	{
		when(config.knownPlayersCsv()).thenReturn(" P1, ,P2 ");
		when(config.altsJson()).thenReturn("{not valid json");

		playerManager.loadFromConfig();

		assertTrue(playerManager.getKnownPlayers().contains("P1"));
		assertTrue(playerManager.getKnownPlayers().contains("P2"));
		assertTrue(playerManager.getAltMainMapping().isEmpty());
	}

	@Test
	public void testParseAltFromEntry()
	{
		assertEquals("AltName", playerManager.parseAltFromEntry("AltName is an alt of MainName"));
		assertEquals("JustName", playerManager.parseAltFromEntry("JustName"));
	}

	@Test
	public void testGetMainNameResolutionCycle()
	{
		// Manually inject a cycle: Alt1 -> Alt2, Alt2 -> Alt1
		when(config.altsJson()).thenReturn("{\"Alt1\":\"Alt2\", \"Alt2\":\"Alt1\"}");
		playerManager.loadFromConfig();

		// Should not infinite loop and return a reasonable result (likely Alt1 or Alt2 depending on entry point)
		String res = playerManager.getMainName("Alt1");
		assertTrue(res.equals("Alt1") || res.equals("Alt2"));
	}

	@Test
	public void testIsKnownPlayerAutoSave()
	{
		String p1 = "NewPlayer";
		assertFalse(playerManager.isKnownPlayer(p1));

		boolean res = playerManager.isKnownPlayer(p1, true);
		assertTrue(res);
		assertTrue(playerManager.isKnownPlayer(p1));
		verify(config).knownPlayersCsv(p1);
	}

	@Test
	public void testRemoveKnownPlayerAlsoRemovesAltRelationships()
	{
		playerManager.trySetAltMain("Alt1", "Main");
		playerManager.trySetAltMain("Alt2", "OtherMain");

		assertTrue(playerManager.removeKnownPlayer("Main"));
		assertFalse(playerManager.getKnownPlayers().contains("Main"));
		assertFalse(playerManager.isAlt("Alt1"));
		assertTrue(playerManager.isAlt("Alt2"));

		assertFalse(playerManager.removeKnownPlayer(null));
		assertFalse(playerManager.removeKnownPlayer(" "));
		assertFalse(playerManager.removeKnownPlayer("Missing"));
	}

	@Test
	public void testKnownPlayerViewsAreReadOnlyAndNullSafe()
	{
		assertFalse(playerManager.addKnownPlayer(null));
		assertFalse(playerManager.addKnownPlayer(" "));
		assertFalse(playerManager.isKnownPlayer(null));
		assertNull(playerManager.getMainName(null));

		playerManager.addKnownPlayer("Player1");
		try
		{
			playerManager.getKnownPlayers().add("Player2");
			fail("Known player view should be read-only");
		}
		catch (UnsupportedOperationException expected)
		{
			assertTrue(true);
		}
	}
}
