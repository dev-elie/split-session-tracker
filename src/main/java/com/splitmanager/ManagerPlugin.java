package com.splitmanager;

import com.google.inject.Provides;
import com.splitmanager.chat.ChatDetectionService;
import com.splitmanager.chat.ChatSource;
import com.splitmanager.models.PendingValue;
import com.splitmanager.models.Session;
import com.splitmanager.utils.ChatStatusOverlay;
import com.splitmanager.utils.Formats;
import com.splitmanager.views.PanelView;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Auto Split Manager",
	description = "Automatically track and manage splits for group PvM/PvP. Features include: chat detection of drops, player roster management with alt support, split calculations, session tracking, settlement metrics, and configurable UI. JSON export available for sharing and backup.",
	tags = {"splits", "loot", "pvm", "pvp", "tracker", "clan", "group"}
)
/**
 * Main RuneLite plugin entry point for Auto Split Manager.
 * Wires up UI, session management, configuration, and chat/menu event handlers.
 */
public class ManagerPlugin extends Plugin
{
	private static final BufferedImage ICON = ImageUtil.loadImageResource(ManagerPlugin.class, "/com/splitmanager/icons/icon.png");
	private final ChatDetectionService chatDetectionService = new ChatDetectionService();
	@Inject
	private Client client;
	@Inject
	private ClientToolbar clientToolbar;
	@Getter
	@Inject
	private PluginConfig config;
	@Inject
	private OverlayManager overlayManager;
	private ChatStatusOverlay chatOverlay;
	private NavigationButton navButton;
	@Inject
	private ManagerPanel panelManager;
	@Inject
	private ManagerSession sessionManager;
	@Inject
	private ManagerKnownPlayers playerManager;
	private PanelView view;

	@Override
	/**
	 * Initialize plugin state and register the sidebar panel/navigation.
	 */
	protected void startUp()
	{
		// Direct payments are still hidden until the remove-player workflow supports them cleanly.
		config.directPayments(false);

		Formats.setConfig(config);
		Formats.updateFormats();
		playerManager.init();
		sessionManager.init();
		panelManager.init();

		chatOverlay = new ChatStatusOverlay();
		overlayManager.add(chatOverlay);
		view = panelManager.getView();
		navButton = NavigationButton.builder()
			.tooltip("Auto Split Manager")
			.icon(ICON)
			.priority(5)
			.panel(view)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	/**
	 * Persist state and remove UI elements when the plugin shuts down.
	 */
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (sessionManager != null)
		{
			sessionManager.saveToConfig();
		}

		if (chatOverlay != null)
		{
			overlayManager.remove(chatOverlay);
			chatOverlay = null;
		}
	}

	/**
	 * Provide injectable configuration instance.
	 *
	 * @param configManager RuneLite config manager
	 * @return plugin config
	 */
	@Provides
	PluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PluginConfig.class);
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged e)
	{
		updateChatWarningStatus();
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged e)
	{
		updateChatWarningStatus();
	}

	@Subscribe
	public void onWorldChanged(WorldChanged e)
	{
		updateChatWarningStatus();
	}

	/**
	 * React to plugin configuration changes that require a panel refresh/restart.
	 *
	 * @param e config change event
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!"Split Manager".equals(e.getGroup()))
		{
			return;
		}

		switch (e.getKey())
		{
			case "directPayments":
				log.info("Direct payments changed, refreshing panel");
				restartViewFix();
				break;
			case "WarnNotInFC":
				updateChatWarningStatus();
				break;
			case "enablePopout":
			case "enableTour":
				restartViewFix();
				break;
			case "accountForGeTax":
			case "geTaxMinimumValue":
			case "geTaxPercent":
			case "geTaxMaxPerLoot":
				if (panelManager != null)
				{
					panelManager.refreshAllView();
				}
				break;
			case "timeFormat":
			case "dateFormat":
				Formats.updateFormats();
				if (panelManager != null)
				{
					panelManager.refreshAllView();
				}
				break;
		}
	}

	private void restartViewFix()
	{
		panelManager.restart();
		view = panelManager.getView();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = NavigationButton.builder()
				.tooltip("Auto Split Manager")
				.icon(navButton.getIcon())
				.priority(5)
				.panel(view)
				.build();
			clientToolbar.addNavigation(navButton);
		}
	}

	/**
	 * Parse chat messages to detect values and enqueue PendingValue suggestions.
	 *
	 * @param event chat message event
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event == null)
		{
			return;
		}

		// Disabled for now, this should be covered by other checks
		if (checkChatJoinLeave(event))
		{
			return;
		}

		if (!config.enableChatDetection())
		{
			return;
		}

		ChatMessageType type = event.getType();
		ChatSource source = getChatSource(type);

		if (source == null)
		{
			return;
		}
		if (source == ChatSource.CLAN && !config.detectInClanChat())
		{
			return;
		}
		if (source == ChatSource.FRIENDS && !config.detectInFriendsChat())
		{
			return;
		}

		List<PendingValue> pendingValues = chatDetectionService.detect(config, source, event.getName(), event.getMessage());
		for (PendingValue pendingValue : pendingValues)
		{
			queuePending(pendingValue);
		}
	}

	private ChatSource getChatSource(ChatMessageType type)
	{
		if (type == null)
		{
			return null;
		}
		String typeName = type.name();
		if (typeName.contains("CLAN"))
		{
			return ChatSource.CLAN;
		}
		if (typeName.contains("FRIEND"))
		{
			return ChatSource.FRIENDS;
		}
		return null;
	}


	/**
	 * Enqueue a pending value suggestion for user approval.
	 *
	 * @param pendingValue pending value to add
	 */
	private void queuePending(PendingValue pendingValue)
	{
		if (sessionManager == null || pendingValue == null)
		{
			return;
		}
		sessionManager.addPendingValue(pendingValue);

		panelManager.refreshAllView();
	}

	private boolean checkChatJoinLeave(ChatMessage event)
	{
		if (event == null || event.getMessage() == null || event.getType() == null)
		{
			return false;
		}

		String plain = Text.removeTags(event.getMessage()).trim();

		ChatMessageType t = event.getType();
		boolean isSystemish = t == ChatMessageType.GAMEMESSAGE
			|| t == ChatMessageType.CLAN_MESSAGE
			|| t == ChatMessageType.CLAN_CHAT
			|| t == ChatMessageType.CLAN_GUEST_CHAT
			|| t.name().contains("CLAN");

		if (!isSystemish)
		{
			return false;
		}

		//LEAVE/KICK Chat
		if (configuredPattern(config.chatLeaveOrKickRegex(),
			PluginConfig.DEFAULT_CHAT_LEAVE_OR_KICK_REGEX,
			"chatLeaveOrKickRegex").matcher(plain).find())
		{
			updateChatWarningStatus();
			return true;
		}


		//JOIN Chat
		if (configuredPattern(config.chatJoinRegex(),
			PluginConfig.DEFAULT_CHAT_JOIN_REGEX,
			"chatJoinRegex").matcher(plain).find())
		{
			updateChatWarningStatus();
			return false;
		}
		return false;
	}

	private Pattern configuredPattern(String configured, String defaultPattern, String key)
	{
		String patternText = configured == null || configured.isBlank() ? defaultPattern : configured;
		try
		{
			return Pattern.compile(patternText);
		}
		catch (PatternSyntaxException e)
		{
			log.warn("Invalid chat status regex for {}; falling back to default", key, e);
			return Pattern.compile(defaultPattern);
		}
	}


	/**
	 * Recompute overlay purely from member lists (no timers, no message heuristics).
	 */
	public void updateChatWarningStatus()
	{
		if (chatOverlay == null)
		{
			return;
		}

		if (sessionManager.getCurrentSession().isEmpty() || !config.warnNotFC())
		{
			chatOverlay.setVisible(false);
			return;
		}

		log.debug("Updating chat overlay status; friends chat active={}", isFriendsChatOn());

		if (isFriendsChatOn())
		{
			chatOverlay.setVisible(false);
			chatOverlay.setChatchanOn(true);
			return;
		}

		chatOverlay.setVisible(true);
		chatOverlay.setChatchanOn(false);
	}


	/**
	 * Track context (in game) menu openings to add an option to add/remove players from session.
	 * This Triggers when you right click a player in the friends/clan chat.
	 *
	 * @param event menu entry added event
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int componentId = event.getActionParam1();
		int groupId = WidgetUtil.componentToInterface(componentId);

		Session currentSession = null;
		if (sessionManager == null)
		{
			return;
		}
		else
		{
			currentSession = sessionManager.getCurrentSession().orElse(null);
		}
		if (!(groupId == InterfaceID.FRIENDS || groupId == InterfaceID.CHATCHANNEL_CURRENT
			|| componentId == InterfaceID.ClansSidepanel.PLAYERLIST || componentId == InterfaceID.ClansGuestSidepanel.PLAYERLIST))
		{
			return;
		}
		String playername = Text.removeTags(event.getTarget());

		if (currentSession == null)
		{
			String option = "Add to known players";

			if (playerManager.isKnownPlayer(playername))
			{
				return;
			}
			if (menuHasOption(option))
			{
				return;
			}

			client.getMenu().createMenuEntry(-1)
				.setOption(option)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					playerManager.addKnownPlayer(playername);
					panelManager.refreshAllView();
				});
			return;
		}


		if (sessionManager.currentSessionHasPlayer(playername))
		{
			String option = "Remove from session";

			if (menuHasOption(option))
			{
				return;
			}

			client.getMenu().createMenuEntry(-1)
				.setOption(option)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					sessionManager.removePlayerFromSession(playername);
					panelManager.refreshAllView();
				});
			return;
		}

		String option = "Add to session";

		if (menuHasOption(option))
		{
			return;
		}

		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				if (playerManager.isKnownPlayer(playername, true))
				{
					sessionManager.addPlayerToActive(playername);
				}
				panelManager.refreshAllView();
			});
	}

	private boolean menuHasOption(String option)
	{
		return Arrays.stream(client.getMenu().getMenuEntries()).anyMatch(e -> option.equals(e.getOption()));
	}


	/**
	 * Local player's cleaned display name ("" if not ready).
	 */
	private String myCleanName()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return "";
		}
		return net.runelite.client.util.Text.toJagexName(
			net.runelite.client.util.Text.removeTags(me.getName()));
	}

	/**
	 * True iff the given clan channel currently contains *you*.
	 */
	private boolean channelHasSelf(ClanChannel ch)
	{
		if (ch == null || ch.getMembers() == null)
		{
			return false;
		}
		String me = myCleanName();
		if (me.isEmpty())
		{
			return false;
		}

		for (ClanChannelMember m : ch.getMembers())
		{
			if (m == null)
			{
				continue;
			}
			String n = m.getName();
			if (n == null)
			{
				continue;
			}
			n = net.runelite.client.util.Text.toJagexName(
				net.runelite.client.util.Text.removeTags(n));
			if (me.equalsIgnoreCase(n))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Joined to main / guest chat-channel (based solely on your presence).
	 */
	private boolean isMainClanChatOn()
	{
		return channelHasSelf(client.getClanChannel());
	}

	private boolean isGuestClanChatOn()
	{
		return channelHasSelf(client.getGuestClanChannel());
	}

	/**
	 * Joined to Friends Chat ("Chat Channel")?
	 */
	private boolean isFriendsChatOn()
	{
		return client.getFriendsChatManager() != null;
	}
}
