package com.splitmanager.utils;

import com.splitmanager.ManagerSession;
import com.splitmanager.PluginConfig;
import com.splitmanager.models.PlayerMetrics;
import com.splitmanager.models.Session;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles formatting player metrics into markdown tables and other text formats.
 */
public class MarkdownFormatter
{

	/**
	 * Build metrics table as markdown.
	 */
	public static String buildMetricsMarkdown(List<PlayerMetrics> data, PluginConfig config)
	{
		DecimalFormat df = Formats.getDecimalFormat();
		StringBuilder sb = new StringBuilder();

		if (config.directPayments())
		{
			List<String> transfers = PaymentProcessor.computeDirectPayments(data);
			if (!transfers.isEmpty())
			{
				sb.append('\n').append("Suggested direct payments:\n");
				for (String line : transfers)
				{
					sb.append("- ").append(line).append('\n');
				}
			}
		}

		if (config.copyForDiscord())
		{
			sb.append("```\n");
		}

		if (!config.directPayments())
		{
			appendFullTable(data, df, sb, config);
		}
		else
		{
			appendSimpleTable(data, df, sb);
		}

		if (config.copyForDiscord())
		{
			sb.append("```\n");
		}

		return sb.toString();
	}

	private static void appendFullTable(List<PlayerMetrics> data,
	                                    DecimalFormat df,
	                                    StringBuilder sb,
	                                    PluginConfig config)
	{
		List<String[]> rows = new ArrayList<>();
		int maxPlayer = "Player".length();
		int maxTotal = "Total".length();
		int maxSplit = "Split".length();

		for (PlayerMetrics pm : data)
		{
			String player = pm.player == null ? "" : pm.player.replace("|", "\\|");
			String total = df.format(pm.total);
			long dispSplit = pm.split;

			if (config.flipSettlementSign())
			{
				dispSplit = -dispSplit;
			}

			String split = df.format(dispSplit);
			rows.add(new String[]{player, total, split});

			if (player.length() > maxPlayer)
			{
				maxPlayer = player.length();
			}
			if (total.length() > maxTotal)
			{
				maxTotal = total.length();
			}
			if (split.length() > maxSplit)
			{
				maxSplit = split.length();
			}
		}

		sb.append("| ")
			.append(padRight("Player", maxPlayer)).append(" | ")
			.append(padLeft("Total", maxTotal)).append(" | ")
			.append(padLeft("Split", maxSplit)).append(" |\n");

		sb.append("| ")
			.append(repeat(maxPlayer)).append(" | ")
			.append(repeat(maxTotal - 1)).append(":").append(" | ")
			.append(repeat(maxSplit - 1)).append(":").append(" |\n");

		for (String[] r : rows)
		{
			sb.append("| ")
				.append(padRight(r[0], maxPlayer)).append(" | ")
				.append(padLeft(r[1], maxTotal)).append(" | ")
				.append(padLeft(r[2], maxSplit)).append(" |\n");
		}
	}

	private static void appendSimpleTable(List<PlayerMetrics> data,
	                                      DecimalFormat df,
	                                      StringBuilder sb)
	{
		List<String[]> rows = new ArrayList<>();
		int maxPlayer = "Player".length();
		int maxSplit = "Split".length();

		for (var pm : data)
		{
			String player = pm.player == null ? "" : pm.player.replace("|", "\\|");
			long dispSplit = pm.split;
			String split = df.format(dispSplit);

			rows.add(new String[]{player, split});

			if (player.length() > maxPlayer)
			{
				maxPlayer = player.length();
			}
			if (split.length() > maxSplit)
			{
				maxSplit = split.length();
			}
		}

		sb.append("| ")
			.append(padRight("Player", maxPlayer)).append(" | ")
			.append(padLeft("Split", maxSplit)).append(" |\n");

		sb.append("| ")
			.append(repeat(maxPlayer)).append(" | ")
			.append(repeat(maxSplit - 1)).append(":").append(" |\n");
		for (String[] r : rows)
		{
			sb.append("| ").append(padRight(r[0], maxPlayer)).append(" | ")
				.append(padLeft(r[1], maxSplit)).append(" |\n");
		}
	}

	private static String padRight(String s, int width)
	{
		if (s == null)
		{
			s = "";
		}
		if (s.length() >= width)
		{
			return s;
		}
		return s + " ".repeat(width - s.length());
	}

	private static String padLeft(String s, int width)
	{
		if (s == null)
		{
			s = "";
		}
		return " ".repeat(Math.max(0, width - s.length())) + s;
	}

	private static String repeat(int count)
	{
		if (count <= 0)
		{
			return "";
		}
		return "-".repeat(count);
	}

	/**
	 * Builds a JSON representation of the player metrics for the current session.
	 * The method retrieves the current session and computes metrics for each player in that session.
	 * The metrics are then formatted as a JSON array, where each entry contains the player's name,
	 * total count, split count, and active player status.
	 *
	 * @return A JSON string representing the computed player metrics for the current session.
	 * Returns an empty JSON array "[]" if no session is active or there are no metrics.
	 */
	public static String buildMetricsJson(ManagerSession sessionManager)
	{
		Session currentSession = sessionManager.getCurrentSession().orElse(null);
		List<PlayerMetrics> data = sessionManager.computeMetricsFor(currentSession, true);
		StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (int i = 0; i < data.size(); i++)
		{
			PlayerMetrics pm = data.get(i);
			sb.append("{\"player\":\"").append(pm.player).append("\",")
				.append("\"total\":").append(pm.total).append(",")
				.append("\"split\":").append(pm.split).append(",")
				.append("\"active\":").append(pm.activePlayer).append("}");
			if (i < data.size() - 1)
			{
				sb.append(",");
			}
		}
		sb.append("]");

		return sb.toString();
	}
}