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
