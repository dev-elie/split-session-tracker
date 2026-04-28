package com.splitmanager.chat;

import com.splitmanager.PluginConfig;
import com.splitmanager.models.PendingValue;
import com.splitmanager.utils.Formats;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses supported loot/value chat messages into pending values without depending on RuneLite events.
 */
@Slf4j
public class ChatDetectionService
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

	public List<PendingValue> detect(PluginConfig config, ChatSource source, String sender, String message)
	{
		List<PendingValue> out = new ArrayList<>();
		if (config == null || source == null || message == null)
		{
			return out;
		}

		if (config.detectPvmValues())
		{
			Matcher pvm = configuredPattern(config.pvmDropRegex(), PluginConfig.DEFAULT_PVM_DROP_REGEX, "pvmDropRegex").matcher(message);
			if (pvm.find())
			{
				String player = group(pvm, "player", 1);
				String value = group(pvm, "value", 2);
				addParsedValue(out, PendingValue.Type.PVM, source, message, valueWithCoins(value), player, config);
				return out;
			}
		}

		if (config.detectPvpValues())
		{
			Matcher pvp = configuredPattern(config.pvpLootRegex(), PluginConfig.DEFAULT_PVP_LOOT_REGEX, "pvpLootRegex").matcher(message);
			if (pvp.find())
			{
				String player = group(pvp, "player", 1);
				String value = group(pvp, "value", 3, 2);
				addParsedValue(out, PendingValue.Type.PVP, source, message, valueWithCoins(value), player, config);
				return out;
			}
		}

		if (!config.detectPlayerValues())
		{
			return out;
		}

		Matcher add = configuredPattern(config.addCommandRegex(), PluginConfig.DEFAULT_ADD_COMMAND_REGEX, "addCommandRegex").matcher(message);
		if (!add.find())
		{
			return out;
		}

		String who = cleanSender(sender);
		String valuesText = group(add, "values", 1);
		if (valuesText == null || valuesText.isBlank())
		{
			return out;
		}
		String[] valueStrings = valuesText.split(configuredPatternText(
			config.addValueSeparatorRegex(),
			PluginConfig.DEFAULT_ADD_VALUE_SEPARATOR_REGEX,
			"addValueSeparatorRegex"));
		for (String valueString : valueStrings)
		{
			String normalized = normalizeAddValue(valueString, config);
			if (normalized == null)
			{
				continue;
			}
			addParsedValue(out, PendingValue.Type.ADD, source, "!add " + normalized, normalized, who, config);
		}
		return out;
	}

	private void addParsedValue(List<PendingValue> out,
	                            PendingValue.Type type,
	                            ChatSource source,
	                            String message,
	                            String amountText,
	                            String suggestedPlayer,
	                            PluginConfig config)
	{
		if (amountText == null || amountText.isBlank())
		{
			log.debug("Chat detection regex matched without a value for {}", type);
			return;
		}
		try
		{
			long value = Formats.OsrsAmountFormatter.stringAmountToLongAmount(amountText, config);
			out.add(PendingValue.of(type, source.getLabel(), message, value, suggestedPlayer));
		}
		catch (ParseException e)
		{
			log.debug("Failed to parse chat value {}", amountText, e);
		}
	}

	private String normalizeAddValue(String valueString, PluginConfig config)
	{
		if (valueString == null)
		{
			return null;
		}
		String trimmed = valueString.trim();
		if (trimmed.endsWith(","))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		if (trimmed.isEmpty())
		{
			return null;
		}

		Matcher single = configuredPattern(config.addValueRegex(), PluginConfig.DEFAULT_ADD_VALUE_REGEX, "addValueRegex").matcher(trimmed);
		if (!single.matches())
		{
			return null;
		}

		String number = group(single, "number", 1);
		if (number == null || number.isBlank())
		{
			return null;
		}

		String unit = group(single, "unit", 2);
		if (unit == null)
		{
			PluginConfig.ValueMultiplier multiplier = config.defaultValueMultiplier();
			if (multiplier == null || multiplier.getValue() == null || multiplier.getValue().isEmpty())
			{
				return number + " coins";
			}
			unit = multiplier.getValue();
		}
		return number + unit;
	}

	private String cleanSender(String sender)
	{
		if (sender == null)
		{
			return "";
		}
		return TAGS.matcher(sender).replaceAll("").trim();
	}

	private Pattern configuredPattern(String configured, String defaultPattern, String key)
	{
		return Pattern.compile(configuredPatternText(configured, defaultPattern, key));
	}

	private String configuredPatternText(String configured, String defaultPattern, String key)
	{
		String patternText = configured == null || configured.isBlank() ? defaultPattern : configured;
		try
		{
			Pattern.compile(patternText);
			return patternText;
		}
		catch (PatternSyntaxException e)
		{
			log.warn("Invalid chat detection regex for {}; falling back to default", key, e);
			return defaultPattern;
		}
	}

	private String group(Matcher matcher, String name, int... fallbackIndexes)
	{
		try
		{
			String value = matcher.group(name);
			if (value != null)
			{
				return value;
			}
		}
		catch (IllegalArgumentException e)
		{
			// Fall through to index-based compatibility.
			log.trace("Named chat regex group {} is not available", name, e);
		}

		for (int index : fallbackIndexes)
		{
			if (index <= matcher.groupCount())
			{
				String value = matcher.group(index);
				if (value != null)
				{
					return value;
				}
			}
		}
		return null;
	}

	private String valueWithCoins(String value)
	{
		if (value == null)
		{
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.matches(".*[A-Za-z].*"))
		{
			return trimmed;
		}
		return trimmed + " coins";
	}
}
