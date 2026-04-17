package com.splitmanager;

import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;


/**
 * The PluginConfig interface defines a configuration for the Split Manager plugin.
 * It provides persistent storage of various user and system-defined settings. Additionally, this interface
 * manages settings related to settlement, chat detection, and session handling.
 */
@ConfigGroup(PluginConfig.GROUP)
public interface PluginConfig extends Config
{
	String GROUP = "Split Manager";

	String KEY_SESSIONS_JSON = "sessionsJson";
	String KEY_CURRENT_SESSION_ID = "currentSessionId";
	String KEY_HISTORY_LOADED = "historyLoaded";
	String KEY_PEOPLE_CSV = "PlayersCsv";
	String DEFAULT_PVM_DROP_REGEX = "^(?<player>.+?) received a drop: .*?\\((?<value>\\d[\\d,]*) coins\\)";
	String DEFAULT_PVP_LOOT_REGEX = "^(?<player>.+?) has defeated .+? and received \\((?<value>\\d[\\d,]*) coins\\) worth of loot!";
	String DEFAULT_ADD_COMMAND_REGEX = "(?i)!add\\s+(?<values>.+)";
	String DEFAULT_ADD_VALUE_REGEX = "(?i)^(?<number>[0-9][0-9,]*(?:\\.[0-9]+)?)(?<unit>[kmb])?$";
	String DEFAULT_ADD_VALUE_SEPARATOR_REGEX = "\\s*,\\s+|\\s+";
	String DEFAULT_CHAT_LEAVE_OR_KICK_REGEX = "(?i)^\\s*(?:you\\s+(?:have\\s+)?left\\s+(?:the\\s+)?(?:chat-)?channel\\.?|you\\s+(?:are|aren't|are\\s+not)\\s+currently\\s+in\\s+(?:a|the|your)\\s+(?:chat-)?channel\\.?|you\\s+have\\s+been\\s+kicked\\s+from\\s+the\\s+channel\\.?)\\s*$";
	String DEFAULT_CHAT_JOIN_REGEX = "(?i)^\\s*now\\s+talking\\s+in\\s+(?:the\\s+)?(?:chat-)?channel\\.?\\s*$";
	//TODO Create a new configitem that allows the user to submit any forms on enter, e.g. 1) user fills in split amount 2) presses enter 3) The same function as button press is called
	@ConfigSection(
		name = "Settlement",
		description = "Settlement config",
		position = 3
	)
	String settlementSection = "Settlement";
	// Chat detection settings
	@ConfigSection(
		name = "Chat detection",
		description = "Detect and queue values from chat",
		position = 1
	)
	String chatDetectionSection = "Chat detection";
	@ConfigSection(
		name = "Chat regex",
		description = "Customize chat detection regex patterns",
		position = 2
	)
	String chatRegexSection = "Chat regex";
	// Alt/main mapping persistence (hidden JSON)
	String KEY_ALTS_JSON = "altsJson";

	@ConfigItem(
		keyName = "enablePopout",
		name = "Enable popout",
		hidden = true,
		description = "Show a popout button that enables the user to pop the plugin out into its own window."
	)
	default boolean enablePopout()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enablePopout",
		name = "Enable popout",
		hidden = true,
		description = "Show a popout button that enables the user to pop the plugin out into its own window."
	)
	void enablePopout(boolean value);

	@ConfigItem(
		keyName = "defaultValueMultiplier",
		name = "Default value multiplier",
		description = "The default multiplier that is used upon adding split values"
	)
	default ValueMultiplier defaultValueMultiplier()
	{
		return ValueMultiplier.THOUSAND;
	}

	@ConfigItem(
		keyName = "enableTour",
		name = "Enable tour",
		description = "Show a guided step-by-step tutorial panel at the top of the plugin UI"
	)
	default boolean enableTour()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableTour",
		name = "Enable tour",
		description = "Show a guided step-by-step tutorial panel at the top of the plugin UI",
		hidden = true
	)
	void enableTour(boolean value);

	@ConfigItem(
		keyName = "WarnNotInFC",
		name = "Warning not in FC",
		description = "Give a warning on OSRS canvas that you are not in a FC, usefull if you have !add on",
		section = chatDetectionSection
	)
	default boolean warnNotFC()
	{
		return false;
	}

	/**
	 * Retrieves the JSON string representation of serialized sessions.
	 *
	 * @return a JSON string representing the serialized sessions, or an empty string if no sessions are serialized
	 */
	@ConfigItem(
		keyName = KEY_SESSIONS_JSON,
		name = "Sessions JSON",
		description = "Serialized sessions",
		hidden = true
	)
	default String sessionsJson()
	{
		return "";
	}

	/**
	 * Sets the JSON string representation of serialized sessions.
	 *
	 * @param value a JSON string representing the serialized sessions
	 */
	@ConfigItem(
		keyName = KEY_SESSIONS_JSON,
		name = "Sessions JSON",
		description = "Serialized sessions",
		hidden = true
	)
	void sessionsJson(String value);

	/**
	 * Retrieves the identifier of the current active session.
	 *
	 * @return the current session ID as a string, or an empty string if no session is active
	 */
	@ConfigItem(
		keyName = KEY_CURRENT_SESSION_ID,
		name = "Current Session Id",
		description = "Active session id",
		hidden = true
	)
	default String currentSessionId()
	{
		return "";
	}

	/**
	 * Sets the identifier of the current active session.
	 *
	 * @param value the current session ID to set as a string
	 */
	@ConfigItem(
		keyName = KEY_CURRENT_SESSION_ID,
		name = "Current Session Id",
		description = "Active session id",
		hidden = true
	)
	void currentSessionId(String value);

	@ConfigItem(
		keyName = KEY_HISTORY_LOADED,
		name = "History Loaded",
		description = "Whether a historical session is currently being viewed",
		hidden = true
	)
	default boolean historyLoaded()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_HISTORY_LOADED,
		name = "History Loaded",
		description = "Whether a historical session is currently being viewed",
		hidden = true
	)
	void historyLoaded(boolean value);

	/**
	 * Retrieves a comma-separated string of known players.
	 *
	 * @return a string containing the known players separated by commas,
	 * or an empty string if no players are defined
	 */
	@ConfigItem(
		keyName = KEY_PEOPLE_CSV,
		name = "Players",
		description = "Comma-separated known players",
		hidden = true
	)
	default String knownPlayersCsv()
	{
		return "";
	}

	/**
	 * Sets a comma-separated string of known players.
	 *
	 * @param value a string containing the known players separated by commas
	 */
	@ConfigItem(
		keyName = KEY_PEOPLE_CSV,
		name = "Players",
		description = "Comma-separated known players",
		hidden = true
	)
	void knownPlayersCsv(String value);

	/**
	 * Determines whether the Markdown table should be wrapped in triple backticks (` ``` `)
	 * and columns padded for monospaced display when copying the table for Discord.
	 *
	 * @return true if the table should be formatted for Discord with Markdown wrapping
	 * and monospaced column padding; false otherwise.
	 */
	// Markdown / copy settings
	@ConfigItem(
		keyName = "copyForDiscord",
		name = "Copy for Discord",
		description = "Wrap copied Markdown table in ``` and pad columns for monospaced display",
		section = settlementSection
	)
	default boolean copyForDiscord()
	{
		return true;
	}

	// TODO When Direct Payement is used there is no method to remove players, this needs to be fixed before direct payment mode is enabled again

	/**
	 * Determines whether direct payments between players are enabled, bypassing a middleman or bank.
	 * If enabled, the settlement guidance assumes players directly settle payments with one another.
	 * If disabled, a middleman or bank is used for settling payments.
	 *
	 * @return true if direct payments are enabled, false if middleman mode is used.
	 */
	@ConfigItem(
		keyName = "directPayments",
		name = "Direct payments (no middleman)",
		description = "If enabled, settlement guidance assumes players pay each other directly instead of settling via a bank/middleman. Off = middleman mode.",
		section = settlementSection,
		hidden = true
	)
	default boolean directPayments()
	{
		return false;
	}

	@ConfigItem(
		keyName = "directPayments",
		name = "Direct payments (no middleman)",
		description = "If enabled, settlement guidance assumes players pay each other directly instead of settling via a bank/middleman. Off = middleman mode.",
		section = settlementSection,
		hidden = true
	)
	void directPayments(boolean value);

	/**
	 * Determines whether chat detection is enabled.
	 * This method checks if values from clan or friends chat should be detected
	 * and queued in a waitlist as specified by the configuration.
	 *
	 * @return true if chat detection is enabled; false otherwise
	 */
	@ConfigItem(
		keyName = "enableChatDetection",
		name = "Enable chat detection",
		description = "Detect values from clan/friends chat and queue them in a waitlist",
		section = chatDetectionSection
	)
	default boolean enableChatDetection()
	{
		return true;
	}

	/**
	 * Determines whether the detection of values should be enabled in the clan chat.
	 *
	 * @return true if detection in the clan chat is enabled, otherwise false.
	 */
	@ConfigItem(
		keyName = "detectInClanChat",
		name = "Detect in Clan Chat",
		description = "Listen for values in clan chat",
		section = chatDetectionSection
	)
	default boolean detectInClanChat()
	{
		return true;
	}

	/**
	 * Determines whether the system should listen for values in the friends chat.
	 *
	 * @return true if listening for values in friends chat is enabled; false otherwise.
	 */
	@ConfigItem(
		keyName = "detectInFriendsChat",
		name = "Detect in Friends Chat",
		description = "Listen for values in friends chat",
		section = chatDetectionSection
	)
	default boolean detectInFriendsChat()
	{
		return true;
	}

	/**
	 * Determines whether the detection of PvM (Player vs Monster) values
	 * from drop messages is enabled. When enabled, values related to
	 * PvM drops are queued for further processing.
	 *
	 * @return true if PvM value detection is enabled, false otherwise
	 */
	@ConfigItem(
		keyName = "detectPvmValues",
		name = "Detect PvM values",
		description = "Queue values detected from PvM drop messages",
		section = chatDetectionSection
	)
	default boolean detectPvmValues()
	{
		return true;
	}

	/**
	 * Determines if the detection of PvP values from loot messages should
	 * be enabled. When enabled, it queues the values detected from PvP
	 * loot messages for further processing.
	 *
	 * @return true if PvP value detection is enabled, false otherwise
	 */
	@ConfigItem(
		keyName = "detectPvpValues",
		name = "Detect PvP values",
		description = "Queue values detected from PvP loot messages",
		section = chatDetectionSection
	)
	default boolean detectPvpValues()
	{
		return true;
	}

	/**
	 * Enables or disables the detection of player values when they send a command
	 * using the format !add {value}.
	 *
	 * @return true if the detection of player values is enabled, false otherwise
	 */
	@ConfigItem(
		keyName = "detectPlayerValues",
		name = "Detect player !add",
		description = "Allow players to queue values by sending !add {value}",
		section = chatDetectionSection
	)
	default boolean detectPlayerValues()
	{
		return true;
	}

	/**
	 * Determines if the system should automatically apply when the suggested player
	 * (or their main account) is already in the active session, bypassing the waitlist.
	 *
	 * @return true if auto-apply is enabled when the player is in session; false otherwise
	 */
	@ConfigItem(
		keyName = "autoApplyWhenInSession",
		name = "Auto-apply when in session",
		description = "Skip waitlist if suggested player (or its main) is already in the active session",
		section = chatDetectionSection
	)
	default boolean autoApplyWhenInSession()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pvmDropRegex",
		name = "PvM drop regex",
		description = "Regex for PvM drops. Prefer named groups player and value. Index fallback: group 1 player, group 2 value.",
		section = chatRegexSection
	)
	default String pvmDropRegex()
	{
		return DEFAULT_PVM_DROP_REGEX;
	}

	@ConfigItem(
		keyName = "pvpLootRegex",
		name = "PvP loot regex",
		description = "Regex for PvP loot. Prefer named groups player and value. Index fallback: group 1 player, group 3 or 2 value.",
		section = chatRegexSection
	)
	default String pvpLootRegex()
	{
		return DEFAULT_PVP_LOOT_REGEX;
	}

	@ConfigItem(
		keyName = "addCommandRegex",
		name = "!add command regex",
		description = "Regex for !add messages. Prefer named group values. Index fallback: group 1 values.",
		section = chatRegexSection
	)
	default String addCommandRegex()
	{
		return DEFAULT_ADD_COMMAND_REGEX;
	}

	@ConfigItem(
		keyName = "addValueRegex",
		name = "!add value regex",
		description = "Regex for each !add value token. Prefer named groups number and unit. Index fallback: group 1 number, group 2 unit.",
		section = chatRegexSection,
		hidden = true
	)
	default String addValueRegex()
	{
		return DEFAULT_ADD_VALUE_REGEX;
	}

	@ConfigItem(
		keyName = "addValueSeparatorRegex",
		name = "!add value separator regex",
		description = "Regex used to split multiple !add values. Default splits on whitespace or comma followed by whitespace.",
		section = chatRegexSection
	)
	default String addValueSeparatorRegex()
	{
		return DEFAULT_ADD_VALUE_SEPARATOR_REGEX;
	}

	@ConfigItem(
		keyName = "chatLeaveOrKickRegex",
		name = "Chat leave regex",
		description = "Regex for chat-channel leave/kick messages used to refresh warning status.",
		section = chatRegexSection,
		hidden = true
	)
	default String chatLeaveOrKickRegex()
	{
		return DEFAULT_CHAT_LEAVE_OR_KICK_REGEX;
	}

	@ConfigItem(
		keyName = "chatJoinRegex",
		name = "Chat join regex",
		description = "Regex for chat-channel join messages used to refresh warning status.",
		section = chatRegexSection,
		hidden = true
	)
	default String chatJoinRegex()
	{
		return DEFAULT_CHAT_JOIN_REGEX;
	}

	/**
	 * Indicates whether to flip the sign of settlement values for display purposes.
	 * When disabled, a positive value indicates that the bank pays the player.
	 * When enabled, a positive value indicates that the player pays the bank. This setting
	 * applies only in middleman mode and does not affect the actual transaction values.
	 *
	 * @return true if settlement sign flipping is enabled, false otherwise.
	 */
	@ConfigItem(
		keyName = "flipSettlementSign",
		name = "Flip settlement sign (perspective)",
		description = "Display-only: flips the sign of Split values. Off = + means bank pays the player; On = + means player pays the bank (middleman mode only).",
		section = settlementSection
	)
	default boolean flipSettlementSign()
	{
		return false;
	}

	/**
	 * Retrieves a JSON-formatted string that represents the mapping of alternate accounts (alts) to their main account names.
	 * This configuration item is hidden and intended for internal use.
	 *
	 * @return a JSON string containing the alt-to-main name mapping. Defaults to an empty string if not configured.
	 */
	@ConfigItem(
		keyName = KEY_ALTS_JSON,
		name = "Alts JSON",
		description = "alt->main name mapping",
		hidden = true
	)
	default String altsJson()
	{
		return "";
	}

	/**
	 * Sets the JSON string representing an alt-to-main name mapping.
	 *
	 * @param value a JSON string defining the mapping from alternate accounts to main accounts
	 */
	@ConfigItem(
		keyName = KEY_ALTS_JSON,
		name = "Alts JSON",
		description = "alt->main name mapping",
		hidden = true
	)
	void altsJson(String value);

	// Define an enum for your dropdown options
	@Getter
	enum ValueMultiplier
	{
		COINS("None, 1 = 1gp", " coins"),
		THOUSAND("k, aka a thousand", "k"),
		MILLION("m, aka a million", "m"),
		BILLION("b, aka a billion", "b");

		private final String description;
		private final String value;

		ValueMultiplier(String description, String value)
		{
			this.description = description;
			this.value = value;
		}

		@Override
		public String toString()
		{
			return description;
		}
	}
}
