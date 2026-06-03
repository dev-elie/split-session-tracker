package com.splitmanager.utils;

import com.splitmanager.PluginConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.swing.JFormattedTextField;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Formats
{
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd")
		.withZone(ZoneId.systemDefault());
	private static final java.util.Locale LOCALE = java.util.Locale.getDefault();
	private static final DecimalFormat DF = new DecimalFormat("#,##0");
	private static final DecimalFormat DF_3DP = new DecimalFormat("#,##0.###");
	private static DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
		.withLocale(java.util.Locale.getDefault())
		.withZone(ZoneId.systemDefault());
	private static DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ofPattern("dd-MMM", Locale.ENGLISH)
		.withZone(ZoneId.systemDefault());
	@Setter
	private static PluginConfig config;

	public static void updateFormats()
	{
		if (config != null)
		{
			try
			{
				String timePattern = config.timeFormat();
				timePattern = timePattern.replace("[", "'['").replace("]", "']'");
				LOCAL_TIME = DateTimeFormatter.ofPattern(timePattern)
					.withZone(ZoneId.systemDefault());
			}
			catch (Exception e)
			{
				log.warn("Invalid time format: {}, falling back to default", config.timeFormat());
				LOCAL_TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
					.withLocale(java.util.Locale.getDefault())
					.withZone(ZoneId.systemDefault());
			}

			try
			{
				String datePattern = config.dateFormat();
				datePattern = datePattern.replace("[", "'['").replace("]", "']'");
				LOCAL_DATE = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH)
					.withZone(ZoneId.systemDefault());
			}
			catch (Exception e)
			{
				log.warn("Invalid date format: {}, falling back to default", config.dateFormat());
				LOCAL_DATE = DateTimeFormatter.ofPattern("dd-MMM", Locale.ENGLISH)
					.withZone(ZoneId.systemDefault());
			}
		}
	}

	public static DateTimeFormatter getDateTime()
	{
		return TS;
	}

	public static DateTimeFormatter getLocalTime()
	{
		return LOCAL_TIME;
	}

	public static DateTimeFormatter getLocalDate()
	{
		return LOCAL_DATE;
	}

	public static DecimalFormat getDecimalFormat()
	{
		return DF;
	}

	public static final class OsrsAmountFormatter extends JFormattedTextField.AbstractFormatter
	{
		private static final Pattern P =
			Pattern.compile("(?i)^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([kmb]| coins)?\\s*$");

		private static BigDecimal getBigDecimal(BigDecimal number, char suffix) throws ParseException
		{
			log.debug("Converting {} to BigDecimal, with {}", number, suffix);
			if (number.signum() < 0)
			{
				throw new ParseException("Negative not allowed", 0);
			}

			long multiplierK;
			switch (suffix)
			{
				case 'k':
					multiplierK = 1_000L;
					break;
				case 'm':
					multiplierK = 1_000_000L;
					break;
				case 'b':
					multiplierK = 1_000_000_000L;
					break;
				default:
					multiplierK = 1L;
					break;
			}

			return number.multiply(BigDecimal.valueOf(multiplierK));
		}

		/**
		 * Convert a K-based long amount to a human string with a target suffix.
		 * Examples:
		 * amountK=1500, 'm' -> "1.5M"
		 * amountK=250,  'k' -> "250K"
		 * amountK=3,    'b' -> "0.003B"
		 *
		 * @param amountK value expressed in K-units (thousands)
		 * @param suffix  desired suffix: 'k', 'm', or 'b' (case-insensitive)
		 * @return formatted string with suffix
		 */
		public static String toSuffixString(long amountK, char suffix)
		{
			DecimalFormat localDF = DF;
			String s = String.valueOf(Character.toLowerCase(suffix));
			long divK; // how many K per target unit
			switch (s)
			{
				case "k":
					divK = 1_000L;
					break;              // 1 K per K
				case "m":
					divK = 1_000_000L;
					localDF = DF_3DP;
					break;          // 1,000 K per M
				case "b":
					divK = 1_000_000_000L;
					localDF = DF_3DP;
					break;      // 1,000,000 K per B
				default:
					divK = 1L;
					s = "gp";
					break;
			}

			// Use BigDecimal to avoid precision issues for large numbers
			BigDecimal val = BigDecimal.valueOf(amountK)
				.divide(BigDecimal.valueOf(divK), 3, RoundingMode.HALF_UP);

			// Format with up to 3 decimals, trimming trailing zeros
			String num = localDF.format(val);

			// Uppercase the suffix in output
			return num + s.toUpperCase();
		}

		/**
		 * Overload that accepts a String suffix ("k", "m", "b").
		 */
		public static String toSuffixString(long amountK, String suffix)
		{
			if (suffix == null || suffix.isEmpty())
			{
				return toSuffixString(amountK, 'k');
			}
			return toSuffixString(amountK, suffix.charAt(0));
		}

		public static long stringAmountToLongAmount(String amount, PluginConfig config) throws ParseException
		{
			String valueStr = amount;

			log.debug("Parsing amount: {}", valueStr);
			// Check if the value has no unit (k, m, b) and append the default
			java.util.regex.Pattern unitPattern = java.util.regex.Pattern.compile("(?i)^\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?\\s*$");
			java.util.regex.Matcher matcher = unitPattern.matcher(valueStr);

			if (matcher.matches())
			{
				String numberTxt = matcher.group(1);
				String unitTxt = matcher.group(2);
				log.debug("Number: {}, Unit: {}", numberTxt, unitTxt);

				if (unitTxt == null && config != null)
				{
					// No unit specified, append the default multiplier
					valueStr = numberTxt + config.defaultValueMultiplier().getValue();
				}
				log.debug("Final value: {}", valueStr);
			}

			log.debug("Parsed amount: {}", valueStr);
			Object k = new Formats.OsrsAmountFormatter().stringToValue(valueStr);
			if (k == null)
			{
				throw new ParseException("Invalid amount", 0);
			}
			return (Long) k;
		}

		@Override
		public Object stringToValue(@Nonnull String text) throws ParseException
		{
			log.debug("Parsing bujiamount: {}", text);
			String s = text.replace(",", "").trim(); // ignore commas
			if (s.isEmpty())
			{
				return null;
			}

			java.util.regex.Matcher m = P.matcher(s);
			if (!m.matches())
			{
				throw new ParseException("Invalid amount", 0);
			}

			BigDecimal number = new BigDecimal(m.group(1));
			String suffixTxt = m.group(2);
			// If no suffix provided by user, fall back to config default if available
			char suffix;
			if (config == null)
			{
				log.debug("No config available, falling back to global config");
			}
			if (suffixTxt == null)
			{
				log.debug("No suffix provided, falling back to config default");
				String def = config == null ? null : config.defaultValueMultiplier().getValue();
				suffix = def != null && !def.isEmpty() ? Character.toLowerCase(def.charAt(0)) : ' ';
			}
			else
			{
				suffix = Character.toLowerCase(suffixTxt.charAt(0));
				log.debug("Suffix provided: {}", suffix);
			}

			log.debug("Parsed amo00unt: {}{}", number, suffix);

			// Convert to raw coins
			BigDecimal coinsValue = getBigDecimal(number, suffix);
			// Return the exact long value (no normalization to K units)
			return coinsValue.setScale(0, RoundingMode.FLOOR).longValueExact();
		}

		@Override
		public String valueToString(Object value)
		{
			if (value == null)
			{
				return "";
			}
			long coins = ((Number) value).longValue();
			String suffix = config == null || config.defaultValueMultiplier() == null
				? "k"
				: config.defaultValueMultiplier().getValue();
			return toSuffixString(coins, suffix);
		}

	}

}
