package com.splitmanager;

import com.splitmanager.utils.Formats;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FormatsTest
{
	@Mock
	private PluginConfig config;

	private Formats.OsrsAmountFormatter formatter;

	@Before
	public void setUp()
	{
		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.THOUSAND);
		Formats.setConfig(config);
		formatter = new Formats.OsrsAmountFormatter();
	}

	@After
	public void tearDown()
	{
		Formats.setConfig(null);
	}

	@Test
	public void testStringToValueParsesSupportedSuffixes() throws ParseException
	{
		assertEquals(1000L, formatter.stringToValue("1k"));
		assertEquals(1250000L, formatter.stringToValue("1.25m"));
		assertEquals(2000000000L, formatter.stringToValue("2b"));
		assertEquals(1234L, formatter.stringToValue("1,234 coins"));
		assertNull(formatter.stringToValue("   "));
	}

	@Test
	public void testStringToValueUsesConfiguredDefaultWhenSuffixIsMissing() throws ParseException
	{
		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.MILLION);

		assertEquals(25000000L, formatter.stringToValue("25"));
	}

	@Test
	public void testStringToValueFallsBackToCoinsWithoutConfig() throws ParseException
	{
		Formats.setConfig(null);

		assertEquals(25L, formatter.stringToValue("25"));
	}

	@Test
	public void testStringToValueRejectsInvalidInput()
	{
		try
		{
			formatter.stringToValue("abc");
			fail("Invalid amount should fail");
		}
		catch (ParseException expected)
		{
			assertTrue(true);
		}
	}

	@Test
	public void testStringAmountToLongAmountUsesProvidedOrStaticConfig() throws ParseException
	{
		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.BILLION);
		assertEquals(3000000000L, Formats.OsrsAmountFormatter.stringAmountToLongAmount("3", config));

		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.THOUSAND);
		assertEquals(3000L, Formats.OsrsAmountFormatter.stringAmountToLongAmount("3", null));
		assertEquals(3000000L, Formats.OsrsAmountFormatter.stringAmountToLongAmount("3m", null));
	}

	@Test
	public void testToSuffixStringFormatsCoinsWithTargetSuffix()
	{
		assertEquals("100K", Formats.OsrsAmountFormatter.toSuffixString(100000L, 'k'));
		assertEquals("1.5M", Formats.OsrsAmountFormatter.toSuffixString(1500000L, 'm'));
		assertEquals("2.5B", Formats.OsrsAmountFormatter.toSuffixString(2500000000L, 'b'));
		assertEquals("42GP", Formats.OsrsAmountFormatter.toSuffixString(42L, 'x'));
		assertEquals("1K", Formats.OsrsAmountFormatter.toSuffixString(1000L, null));
		assertEquals("1.5M", Formats.OsrsAmountFormatter.toSuffixString(1500000L, "m"));
	}

	@Test
	public void testValueToStringAndSharedFormatters()
	{
		assertEquals("123K", formatter.valueToString(123000L));
		assertEquals("", formatter.valueToString(null));
		assertEquals("1,234", Formats.getDecimalFormat().format(1234));
		assertFalse(Formats.getDateTime().format(Instant.EPOCH).isEmpty());
	}

	@Test
	public void testValueToStringDisplaysCommittedRawCoinsWithSuffix()
		throws ParseException
	{
		Object committedValue = formatter.stringToValue("12");

		assertEquals(12000L, committedValue);
		assertEquals("12K", formatter.valueToString(committedValue));
	}

	@Test
	public void testValueToStringUsesConfiguredDefaultSuffix()
		throws ParseException
	{
		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.MILLION);
		Object committedMillions = formatter.stringToValue("12");

		assertEquals(12000000L, committedMillions);
		assertEquals("12M", formatter.valueToString(committedMillions));

		when(config.defaultValueMultiplier()).thenReturn(PluginConfig.ValueMultiplier.BILLION);
		Object committedBillions = formatter.stringToValue("2");

		assertEquals(2000000000L, committedBillions);
		assertEquals("2B", formatter.valueToString(committedBillions));
	}

	@Test
	public void testFormatUpdates()
	{
		when(config.timeFormat()).thenReturn("[HH:mm]");
		when(config.dateFormat()).thenReturn("dd/MM/yyyy");
		Formats.updateFormats();

		Instant now = Instant.parse("2024-01-01T15:30:00Z");
		ZonedDateTime zdt = now.atZone(ZoneId.systemDefault());

		String formattedTime = Formats.getLocalTime().format(zdt);
		assertTrue("Formatted time should contain brackets: " + formattedTime, formattedTime.contains("[") && formattedTime.contains("]"));

		String formattedDate = Formats.getLocalDate().format(zdt);
		assertEquals("01/01/2024", formattedDate);

		// Test fallback
		when(config.timeFormat()).thenReturn("INVALID_PATTERN");
		when(config.dateFormat()).thenReturn("INVALID_DATE_PATTERN");
		Formats.updateFormats();

		// Should not throw and provide a default format
		String fallbackTime = Formats.getLocalTime().format(zdt);
		assertFalse("Fallback time should not be empty", fallbackTime.isEmpty());

		String fallbackDate = Formats.getLocalDate().format(zdt);
		assertFalse("Fallback date should not be empty", fallbackDate.isEmpty());
	}
}
