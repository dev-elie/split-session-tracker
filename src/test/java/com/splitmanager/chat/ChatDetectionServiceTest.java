package com.splitmanager.chat;

import com.splitmanager.PluginConfig;
import com.splitmanager.models.PendingValue;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChatDetectionServiceTest
{
	@Mock
	private PluginConfig config;

	private ChatDetectionService service;

	@Before
	public void setUp()
	{
		service = new ChatDetectionService();
		lenient().when(config.detectPvmValues()).thenReturn(true);
		lenient().when(config.detectPvpValues()).thenReturn(true);
		lenient().when(config.detectPlayerValues()).thenReturn(true);
		lenient().when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.THOUSAND);
	}

	@Test
	public void detectsPvmDrop()
	{
		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "System",
			"Player1 received a drop: Example item (1,234,567 coins)");

		assertEquals(1, values.size());
		assertEquals(PendingValue.Type.PVM, values.get(0).getType());
		assertEquals("Clan", values.get(0).getSource());
		assertEquals("Player1", values.get(0).getSuggestedPlayer());
		assertEquals(1234567L, (long) values.get(0).getValue());
	}

	@Test
	public void detectsPvpLoot()
	{
		List<PendingValue> values = service.detect(config, ChatSource.FRIENDS, "System",
			"PKer has defeated Victim and received (765,432 coins) worth of loot!");

		assertEquals(1, values.size());
		assertEquals(PendingValue.Type.PVP, values.get(0).getType());
		assertEquals("Friends", values.get(0).getSource());
		assertEquals("PKer", values.get(0).getSuggestedPlayer());
		assertEquals(765432L, (long) values.get(0).getValue());
	}

	@Test
	public void detectsMultipleAddValuesAndCleansSender()
	{
		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "<img=1>Player1",
			"!add 100, 200m 1,234");

		assertEquals(3, values.size());
		assertEquals(100000L, (long) values.get(0).getValue());
		assertEquals(200000000L, (long) values.get(1).getValue());
		assertEquals(1234000L, (long) values.get(2).getValue());
		assertEquals("Player1", values.get(0).getSuggestedPlayer());
	}

	@Test
	public void usesConfiguredPvmRegexWithNamedGroups()
	{
		when(config.pvmDropRegex()).thenReturn("^DROP (?<player>.+?) :: (?<value>[0-9,]+)$");

		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "System",
			"DROP CustomPlayer :: 9,876,543");

		assertEquals(1, values.size());
		assertEquals(PendingValue.Type.PVM, values.get(0).getType());
		assertEquals("CustomPlayer", values.get(0).getSuggestedPlayer());
		assertEquals(9876543L, (long) values.get(0).getValue());
	}

	@Test
	public void usesConfiguredAddRegexesAndSeparator()
	{
		when(config.addCommandRegex()).thenReturn("(?i)^split:\\s*(?<values>.+)$");
		when(config.addValueSeparatorRegex()).thenReturn("\\s*;\\s*");
		when(config.addValueRegex()).thenReturn("(?i)^gp\\((?<number>[0-9,]+)(?<unit>[kmb])?\\)$");

		List<PendingValue> values = service.detect(config, ChatSource.FRIENDS, "Player1",
			"split: gp(100); gp(2m)");

		assertEquals(2, values.size());
		assertEquals(100000L, (long) values.get(0).getValue());
		assertEquals(2000000L, (long) values.get(1).getValue());
		assertEquals("Player1", values.get(0).getSuggestedPlayer());
	}

	@Test
	public void addDetectionFallsBackForInvalidValueSeparatorAndValueRegex()
	{
		when(config.addValueSeparatorRegex()).thenReturn("[invalid");
		when(config.addValueRegex()).thenReturn("[also invalid");

		List<PendingValue> values = service.detect(config, ChatSource.CLAN, null,
			"!add 100, 200m nope");

		assertEquals(2, values.size());
		assertEquals(100000L, (long) values.get(0).getValue());
		assertEquals(200000000L, (long) values.get(1).getValue());
		assertEquals("", values.get(0).getSuggestedPlayer());
	}

	@Test
	public void addDetectionUsesCoinsWhenDefaultMultiplierIsMissing()
	{
		when(config.defaultValueMultiplier()).thenReturn(null);

		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "Player1", "!add 123");

		assertEquals(1, values.size());
		assertEquals(123L, (long) values.get(0).getValue());
		assertEquals("!add 123 coins", values.get(0).getMessage());
	}

	@Test
	public void addDetectionReturnsEmptyForNoCommandBlankValuesAndInvalidAmounts()
	{
		assertTrue(service.detect(config, ChatSource.CLAN, "Player1", "hello").isEmpty());

		when(config.addCommandRegex()).thenReturn("^add\\s*(?<values>.*)$");
		assertTrue(service.detect(config, ChatSource.CLAN, "Player1", "add   ").isEmpty());

		when(config.addCommandRegex()).thenReturn("(?i)^split:\\s*(?<values>.+)$");
		when(config.addValueRegex()).thenReturn("(?i)^(?<number>[0-9]+)(?<unit>[z])?$");
		assertTrue(service.detect(config, ChatSource.CLAN, "Player1", "split: 10z").isEmpty());
	}

	@Test
	public void pvmDetectionIgnoresMatchesWithoutUsableValue()
	{
		when(config.pvmDropRegex()).thenReturn("^DROP (?<player>.+)$");

		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "System",
			"DROP Player1");

		assertTrue(values.isEmpty());
	}

	@Test
	public void pvpDetectionSupportsUnnamedRegexFallbackGroups()
	{
		when(config.pvpLootRegex()).thenReturn("^(.+?) pked .+ for ([0-9,]+)$");

		List<PendingValue> values = service.detect(config, ChatSource.FRIENDS, "System",
			"PKer pked Victim for 321,000");

		assertEquals(1, values.size());
		assertEquals(PendingValue.Type.PVP, values.get(0).getType());
		assertEquals("PKer", values.get(0).getSuggestedPlayer());
		assertEquals(321000L, (long) values.get(0).getValue());
	}

	@Test
	public void invalidConfiguredRegexFallsBackToDefault()
	{
		when(config.pvmDropRegex()).thenReturn("[not valid");

		List<PendingValue> values = service.detect(config, ChatSource.CLAN, "System",
			"Player1 received a drop: Example item (1,234,567 coins)");

		assertEquals(1, values.size());
		assertEquals(PendingValue.Type.PVM, values.get(0).getType());
		assertEquals("Player1", values.get(0).getSuggestedPlayer());
		assertEquals(1234567L, (long) values.get(0).getValue());
	}

	@Test
	public void returnsEmptyWhenDisabledOrInvalid()
	{
		when(config.detectPvmValues()).thenReturn(false);
		when(config.detectPvpValues()).thenReturn(false);
		when(config.detectPlayerValues()).thenReturn(false);

		assertTrue(service.detect(config, ChatSource.CLAN, "Player1", "!add 100").isEmpty());
		assertTrue(service.detect(null, ChatSource.CLAN, "Player1", "!add 100").isEmpty());
		assertTrue(service.detect(config, null, "Player1", "!add 100").isEmpty());
		assertTrue(service.detect(config, ChatSource.CLAN, "Player1", null).isEmpty());
	}
}
