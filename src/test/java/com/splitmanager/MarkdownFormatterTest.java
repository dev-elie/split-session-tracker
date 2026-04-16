package com.splitmanager;

import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import com.splitmanager.utils.MarkdownFormatter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MarkdownFormatterTest
{
	@Mock
	private PluginConfig config;

	@Test
	public void testBuildMetricsMarkdownWithDirectPayments()
	{
		when(config.directPayments()).thenReturn(true);
		
		// P1 has 200k, P2 has 0k. P1 split balance: -100k. P2 split balance: +100k.
		PlayerMetrics m1 = new PlayerMetrics("P1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 0L, 100000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		String markdown = MarkdownFormatter.buildMetricsMarkdown(data, config);
		
		// Should contain settlement instructions: P1 (debtor) pays P2 (creditor)
		assertTrue(markdown.contains("Suggested direct payments:"));
		assertTrue(markdown.contains("P1 -> P2: 100,000"));
	}

	@Test
	public void testBuildMetricsMarkdownWithoutDirectPayments()
	{
		when(config.directPayments()).thenReturn(false);
		
		PlayerMetrics m1 = new PlayerMetrics("P1", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("P2", 0L, 100000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		String markdown = MarkdownFormatter.buildMetricsMarkdown(data, config);
		
		// Should NOT contain settlement instructions
		assertFalse(markdown.contains("Suggested direct payments:"));
		assertFalse(markdown.contains("P1 -> P2: 100,000"));
	}

	@Test
	public void testBuildMetricsMarkdownForDiscordEscapesAndFlipsSplit()
	{
		when(config.directPayments()).thenReturn(false);
		when(config.copyForDiscord()).thenReturn(true);
		when(config.flipSettlementSign()).thenReturn(true);

		PlayerMetrics m1 = new PlayerMetrics("Player|One", 200000L, -100000L, true);
		PlayerMetrics m2 = new PlayerMetrics("LongPlayerName", 0L, 100000L, true);
		List<PlayerMetrics> data = Arrays.asList(m1, m2);

		String markdown = MarkdownFormatter.buildMetricsMarkdown(data, config);

		assertTrue(markdown.startsWith("```\n"));
		assertTrue(markdown.endsWith("```\n"));
		assertTrue(markdown.contains("Player\\|One"));
		assertTrue(markdown.contains("100,000"));
		assertTrue(markdown.contains("-100,000"));
	}

	@Test
	public void testBuildMetricsMarkdownWithDirectPaymentsUsesSimpleTable()
	{
		when(config.directPayments()).thenReturn(true);

		PlayerMetrics payer = new PlayerMetrics("VeryLongPayer", 200000L, -100000L, true);
		PlayerMetrics receiver = new PlayerMetrics("Receiver", 0L, 100000L, true);

		String markdown = MarkdownFormatter.buildMetricsMarkdown(Arrays.asList(payer, receiver), config);

		assertTrue(markdown.contains("Suggested direct payments:"));
		assertTrue(markdown.contains("VeryLongPayer -> Receiver: 100,000"));
		assertTrue(markdown.contains("| Player"));
		assertTrue(markdown.contains("Split"));
		assertFalse(markdown.contains("| Total"));
	}

	@Test
	public void testBuildMetricsJson()
	{
		ManagerSession sessionManager = mock(ManagerSession.class);
		Session current = new Session("session", Instant.EPOCH, "mother");
		List<PlayerMetrics> data = Arrays.asList(
			new PlayerMetrics("P1", 200000L, -100000L, true),
			new PlayerMetrics("P2", 0L, 100000L, false)
		);
		when(sessionManager.getCurrentSession()).thenReturn(Optional.of(current));
		when(sessionManager.computeMetricsFor(current, true)).thenReturn(data);

		assertEquals("[{\"player\":\"P1\",\"total\":200000,\"split\":-100000,\"active\":true},"
				+ "{\"player\":\"P2\",\"total\":0,\"split\":100000,\"active\":false}]",
			MarkdownFormatter.buildMetricsJson(sessionManager));
	}
}
