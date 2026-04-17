package com.splitmanager.chat;

import com.splitmanager.PluginConfig;
import com.splitmanager.models.PendingValue;
import com.splitmanager.utils.Formats;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses supported loot/value chat messages into pending values without depending on RuneLite events.
 */
@Slf4j
public class ChatDetectionService
{
	private static final Pattern PVM_DROP = Pattern.compile("^(.+?) received a drop: .*?\\((\\d[\\d,]*) coins\\)");
	private static final Pattern PVP_LOOT = Pattern.compile("^(.+?) has defeated (.+?) and received \\((\\d[\\d,]*) coins\\) worth of loot!");
	private static final Pattern ADD_COMMAND = Pattern.compile("(?i)!add\\s+(.+)");
	private static final Pattern ADD_VALUE = Pattern.compile("(?i)^([0-9][0-9,]*(?:\\.[0-9]+)?)([kmb])?$");
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
			Matcher pvm = PVM_DROP.matcher(message);
			if (pvm.find())
			{
				addParsedValue(out, PendingValue.Type.PVM, source, message, pvm.group(2) + " coins", pvm.group(1), config);
				return out;
			}
		}

		if (config.detectPvpValues())
		{
			Matcher pvp = PVP_LOOT.matcher(message);
			if (pvp.find())
			{
				addParsedValue(out, PendingValue.Type.PVP, source, message, pvp.group(3) + " coins", pvp.group(1), config);
				return out;
			}
		}

		if (!config.detectPlayerValues())
		{
			return out;
		}

		Matcher add = ADD_COMMAND.matcher(message);
		if (!add.find())
		{
			return out;
		}

		String who = cleanSender(sender);
		String valuesText = add.group(1);
		String[] valueStrings = valuesText.split("\\s*,\\s+|\\s+");
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

		Matcher single = ADD_VALUE.matcher(trimmed);
		if (!single.matches())
		{
			return null;
		}

		String unit = single.group(2);
		if (unit == null)
		{
			PluginConfig.ValueMultiplier multiplier = config.defaultValueMultiplier();
			if (multiplier == null || multiplier.getValue() == null || multiplier.getValue().isEmpty())
			{
				return single.group(1) + " coins";
			}
			unit = multiplier.getValue();
		}
		return single.group(1) + unit;
	}

	private String cleanSender(String sender)
	{
		if (sender == null)
		{
			return "";
		}
		return TAGS.matcher(sender).replaceAll("").trim();
	}
}
