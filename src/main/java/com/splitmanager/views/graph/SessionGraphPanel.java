package com.splitmanager.views.graph;

import com.splitmanager.utils.Formats;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

public class SessionGraphPanel extends JPanel
{
	private static final Color LINE_COLOR = new Color(92, 180, 255);
	private static final Color BAR_COLOR = new Color(222, 168, 64);
	private static final Color POSITIVE_COLOR = new Color(86, 190, 124);
	private static final Color NEGATIVE_COLOR = new Color(215, 94, 94);
	private static final Color INACTIVE_COLOR = new Color(110, 110, 110);
	private static final Color GRID_COLOR = new Color(80, 80, 80);
	private static final int PAD_LEFT = 78;
	private static final int PAD_RIGHT = 24;
	private static final int PAD_TOP = 28;
	private static final int PAD_BOTTOM = 52;

	private SessionGraphSnapshot snapshot = SessionGraphSnapshot.empty(SessionGraphMode.GP_PER_HOUR);

	public SessionGraphPanel()
	{
		setPreferredSize(new Dimension(560, 360));
		setMinimumSize(new Dimension(360, 260));
		setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
	}

	public void setSnapshot(SessionGraphSnapshot snapshot)
	{
		this.snapshot = snapshot == null
			? SessionGraphSnapshot.empty(SessionGraphMode.GP_PER_HOUR)
			: snapshot;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			drawChart(g);
		}
		finally
		{
			g.dispose();
		}
	}

	private void drawChart(Graphics2D g)
	{
		int width = getWidth();
		int height = getHeight();
		List<SessionGraphEntry> entries = snapshot.getEntries();
		if (entries.isEmpty())
		{
			drawEmpty(g, width, height);
			return;
		}

		int x = PAD_LEFT;
		int y = PAD_TOP;
		int chartWidth = Math.max(1, width - PAD_LEFT - PAD_RIGHT);
		int chartHeight = Math.max(1, height - PAD_TOP - PAD_BOTTOM);

		drawGrid(g, x, y, chartWidth, chartHeight);
		if (snapshot.getMode().isLineChart())
		{
			drawLine(g, entries, x, y, chartWidth, chartHeight);
		}
		else
		{
			drawBars(g, entries, x, y, chartWidth, chartHeight);
		}
	}

	private void drawEmpty(Graphics2D g, int width, int height)
	{
		String message = "No loot data yet";
		FontMetrics fm = g.getFontMetrics();
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.drawString(message, (width - fm.stringWidth(message)) / 2, height / 2);
	}

	private void drawGrid(Graphics2D g, int x, int y, int width, int height)
	{
		g.setColor(GRID_COLOR);
		for (int i = 0; i <= 4; i++)
		{
			int lineY = y + (height * i / 4);
			g.drawLine(x, lineY, x + width, lineY);
		}
		g.drawLine(x, y, x, y + height);
		g.drawLine(x, y + height, x + width, y + height);
	}

	private void drawLine(Graphics2D g, List<SessionGraphEntry> entries, int x, int y, int width, int height)
	{
		long max = Math.max(1L, maxPositive(entries));
		Path2D path = new Path2D.Double();
		for (int i = 0; i < entries.size(); i++)
		{
			SessionGraphEntry entry = entries.get(i);
			int px = xForIndex(i, entries.size(), x, width);
			int py = y + height - (int) Math.round((Math.max(0L, entry.getValue()) / (double) max) * height);
			if (i == 0)
			{
				path.moveTo(px, py);
			}
			else
			{
				path.lineTo(px, py);
			}
		}

		g.setColor(LINE_COLOR);
		g.setStroke(new BasicStroke(2.5f));
		g.draw(path);

		for (int i = 0; i < entries.size(); i++)
		{
			SessionGraphEntry entry = entries.get(i);
			int px = xForIndex(i, entries.size(), x, width);
			int py = y + height - (int) Math.round((Math.max(0L, entry.getValue()) / (double) max) * height);
			g.fillOval(px - 3, py - 3, 6, 6);
		}

		drawYAxisLabels(g, x, y, height, 0L, max);
		drawXAxisLabels(g, entries, x, y + height, width);
	}

	private void drawBars(Graphics2D g, List<SessionGraphEntry> entries, int x, int y, int width, int height)
	{
		long maxAbs = Math.max(1L, maxAbsolute(entries));
		boolean hasNegative = entries.stream().anyMatch(entry -> entry.getValue() < 0L);
		int baseline = hasNegative ? y + height / 2 : y + height;
		int positiveHeight = baseline - y;
		int negativeHeight = y + height - baseline;
		int slot = Math.max(1, width / entries.size());
		int barWidth = Math.max(6, Math.min(44, slot - 8));

		if (hasNegative)
		{
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.drawLine(x, baseline, x + width, baseline);
		}

		for (int i = 0; i < entries.size(); i++)
		{
			SessionGraphEntry entry = entries.get(i);
			long value = entry.getValue();
			int centerX = xForBarIndex(i, entries.size(), x, width);
			int barX = centerX - barWidth / 2;
			int barHeight;
			int barY;
			if (value >= 0L)
			{
				barHeight = (int) Math.round((value / (double) maxAbs) * positiveHeight);
				barY = baseline - barHeight;
				g.setColor(snapshot.getMode() == SessionGraphMode.SPLIT_BALANCE ? POSITIVE_COLOR : BAR_COLOR);
			}
			else
			{
				barHeight = (int) Math.round((Math.abs(value) / (double) maxAbs) * negativeHeight);
				barY = baseline;
				g.setColor(NEGATIVE_COLOR);
			}
			if (!entry.isActive())
			{
				g.setColor(INACTIVE_COLOR);
			}
			g.fillRoundRect(barX, barY, barWidth, Math.max(2, barHeight), 6, 6);
		}

		drawYAxisLabels(g, x, y, height, hasNegative ? -maxAbs : 0L, maxAbs);
		drawXAxisLabels(g, entries, x, y + height, width, true);
	}

	private void drawYAxisLabels(Graphics2D g, int x, int y, int height, long min, long max)
	{
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i <= 4; i++)
		{
			long value = max - Math.round(((max - min) * i) / 4.0d);
			String label = formatAmount(value);
			int textY = y + (height * i / 4) + fm.getAscent() / 2;
			g.drawString(label, Math.max(4, x - fm.stringWidth(label) - 8), textY);
		}
	}

	private void drawXAxisLabels(Graphics2D g, List<SessionGraphEntry> entries, int x, int baseline, int width)
	{
		drawXAxisLabels(g, entries, x, baseline, width, false);
	}

	private void drawXAxisLabels(Graphics2D g, List<SessionGraphEntry> entries, int x, int baseline, int width, boolean barChart)
	{
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		FontMetrics fm = g.getFontMetrics();
		int labelEvery = Math.max(1, (entries.size() + 5) / 6);
		for (int i = 0; i < entries.size(); i++)
		{
			if (i % labelEvery != 0 && i != entries.size() - 1)
			{
				continue;
			}
			String label = ellipsize(entries.get(i).getLabel(), 12);
			int px = barChart
				? xForBarIndex(i, entries.size(), x, width)
				: xForIndex(i, entries.size(), x, width);
			g.drawString(label, px - fm.stringWidth(label) / 2, baseline + fm.getAscent() + 8);
		}
	}

	private int xForBarIndex(int index, int count, int x, int width)
	{
		if (count <= 0)
		{
			return x + width / 2;
		}
		double slot = width / (double) count;
		return x + (int) Math.round((index + 0.5d) * slot);
	}

	private int xForIndex(int index, int count, int x, int width)
	{
		if (count <= 1)
		{
			return x + width / 2;
		}
		return x + (int) Math.round((index / (double) (count - 1)) * width);
	}

	private long maxPositive(List<SessionGraphEntry> entries)
	{
		long max = 0L;
		for (SessionGraphEntry entry : entries)
		{
			max = Math.max(max, entry.getValue());
		}
		return max;
	}

	private long maxAbsolute(List<SessionGraphEntry> entries)
	{
		long max = 0L;
		for (SessionGraphEntry entry : entries)
		{
			max = Math.max(max, Math.abs(entry.getValue()));
		}
		return max;
	}

	private String ellipsize(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		if (value.length() <= max)
		{
			return value;
		}
		if (max <= 3)
		{
			return value.substring(0, max);
		}
		return value.substring(0, max - 3) + "...";
	}

	private String formatAmount(long amount)
	{
		long abs = Math.abs(amount);
		if (abs >= 1_000_000_000L)
		{
			return Formats.OsrsAmountFormatter.toSuffixString(amount, 'b');
		}
		if (abs >= 1_000_000L)
		{
			return Formats.OsrsAmountFormatter.toSuffixString(amount, 'm');
		}
		return Formats.OsrsAmountFormatter.toSuffixString(amount, 'k');
	}
}
