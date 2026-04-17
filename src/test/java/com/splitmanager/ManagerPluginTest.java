package com.splitmanager;

import com.splitmanager.models.PendingValue;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ManagerPluginTest
{
	@Mock
	private PluginConfig config;

	@Mock
	private ManagerSession sessionManager;

	@Mock
	private ManagerPanel panelManager;

	@Mock
	private net.runelite.api.Client client;

	@Mock
	private ManagerKnownPlayers playerManager;

	@InjectMocks
	private ManagerPlugin managerPlugin;

	@Before
	public void setUp()
	{
		lenient().when(config.enableChatDetection()).thenReturn(true);
		lenient().when(config.detectInClanChat()).thenReturn(true);
		lenient().when(config.detectInFriendsChat()).thenReturn(true);
		lenient().when(config.detectPvmValues()).thenReturn(true);
		lenient().when(config.detectPvpValues()).thenReturn(true);
		lenient().when(config.detectPlayerValues()).thenReturn(true);
		lenient().when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.THOUSAND);
	}

	@Test
	public void testAddFlowSingleValue()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.CLAN_CHAT);
		chatMessage.setName("Player1");
		chatMessage.setMessage("!add 100");

		managerPlugin.onChatMessage(chatMessage);

		ArgumentCaptor<PendingValue> captor = ArgumentCaptor.forClass(PendingValue.class);
		verify(sessionManager).addPendingValue(captor.capture());

		PendingValue pv = captor.getValue();
		assertEquals(PendingValue.Type.ADD, pv.getType());
		assertEquals("Player1", pv.getSuggestedPlayer());
		assertEquals(100000L, (long) pv.getValue()); // 100 * 1000 (default K multiplier)
	}

	@Test
	public void testAddFlowMultipleValues()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.CLAN_CHAT);
		chatMessage.setName("Player1");
		chatMessage.setMessage("!add 100, 200m 300k");

		managerPlugin.onChatMessage(chatMessage);

		ArgumentCaptor<PendingValue> captor = ArgumentCaptor.forClass(PendingValue.class);
		verify(sessionManager, atLeastOnce()).addPendingValue(captor.capture());

		assertEquals(3, captor.getAllValues().size());

		// 100 (using default K)
		assertEquals(100000L, (long) captor.getAllValues().get(0).getValue());
		// 200m
		assertEquals(200000000L, (long) captor.getAllValues().get(1).getValue());
		// 300k
		assertEquals(300000L, (long) captor.getAllValues().get(2).getValue());
	}

	@Test
	public void testAddFlowDisabled()
	{
		when(config.detectPlayerValues()).thenReturn(false);

		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.CLAN_CHAT);
		chatMessage.setName("Player1");
		chatMessage.setMessage("!add 100");

		managerPlugin.onChatMessage(chatMessage);

		verify(sessionManager, never()).addPendingValue(any());
	}

	@Test
	public void testAddFlowFriendsChat()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.FRIENDSCHAT);
		chatMessage.setName("Player2");
		chatMessage.setMessage("!add 500");

		managerPlugin.onChatMessage(chatMessage);

		ArgumentCaptor<PendingValue> captor = ArgumentCaptor.forClass(PendingValue.class);
		verify(sessionManager).addPendingValue(captor.capture());

		PendingValue pv = captor.getValue();
		assertEquals("Player2", pv.getSuggestedPlayer());
		assertEquals("Friends", pv.getSource());
		assertEquals(500000L, (long) pv.getValue());
	}

	@Test
	public void testPvmDropDetection()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.CLAN_CHAT);
		chatMessage.setName("System");
		chatMessage.setMessage("Player1 received a drop: Example item (1,234,567 coins)");

		managerPlugin.onChatMessage(chatMessage);

		ArgumentCaptor<PendingValue> captor = ArgumentCaptor.forClass(PendingValue.class);
		verify(sessionManager).addPendingValue(captor.capture());

		PendingValue pv = captor.getValue();
		assertEquals(PendingValue.Type.PVM, pv.getType());
		assertEquals("Clan", pv.getSource());
		assertEquals("Player1", pv.getSuggestedPlayer());
		assertEquals(1234567L, (long) pv.getValue());
	}

	@Test
	public void testPvpLootDetection()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.FRIENDSCHAT);
		chatMessage.setName("System");
		chatMessage.setMessage("PKer has defeated Victim and received (765,432 coins) worth of loot!");

		managerPlugin.onChatMessage(chatMessage);

		ArgumentCaptor<PendingValue> captor = ArgumentCaptor.forClass(PendingValue.class);
		verify(sessionManager).addPendingValue(captor.capture());

		PendingValue pv = captor.getValue();
		assertEquals(PendingValue.Type.PVP, pv.getType());
		assertEquals("Friends", pv.getSource());
		assertEquals("PKer", pv.getSuggestedPlayer());
		assertEquals(765432L, (long) pv.getValue());
	}

	@Test
	public void testChatDetectionRespectsChannelToggles()
	{
		when(config.detectInClanChat()).thenReturn(false);
		ChatMessage clanMessage = new ChatMessage();
		clanMessage.setType(ChatMessageType.CLAN_CHAT);
		clanMessage.setName("Player1");
		clanMessage.setMessage("!add 100");

		managerPlugin.onChatMessage(clanMessage);

		verify(sessionManager, never()).addPendingValue(any());

		when(config.detectInFriendsChat()).thenReturn(false);
		ChatMessage friendsMessage = new ChatMessage();
		friendsMessage.setType(ChatMessageType.FRIENDSCHAT);
		friendsMessage.setName("Player1");
		friendsMessage.setMessage("!add 100");

		managerPlugin.onChatMessage(friendsMessage);

		verify(sessionManager, never()).addPendingValue(any());
	}

	@Test
	public void testChatDetectionIgnoresNonClanAndNonFriendsMessages()
	{
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setType(ChatMessageType.PUBLICCHAT);
		chatMessage.setName("Player1");
		chatMessage.setMessage("!add 100");

		managerPlugin.onChatMessage(chatMessage);

		verify(sessionManager, never()).addPendingValue(any());
	}
}
