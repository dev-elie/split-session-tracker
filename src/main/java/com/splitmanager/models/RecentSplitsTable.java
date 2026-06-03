package com.splitmanager.models;


import com.splitmanager.PluginConfig;
import com.splitmanager.sessions.SplitCalculator;
import com.splitmanager.utils.Formats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RecentSplitsTable extends javax.swing.table.AbstractTableModel
{

	private static final String[] COLS = {"Time", "Player", "Amount", "Tax"};
	private static final java.time.ZoneId SYS_TZ = java.time.ZoneId.systemDefault();
	private final java.util.List<Row> rows = new java.util.ArrayList<>(10);
	private final PluginConfig config;
	private SettlementConfigSnapshot settlementConfigSnapshot;
	private SplitCalculator.GeTaxSettings geTaxSettings;
	@Setter
	private Listener listener;

	public RecentSplitsTable(PluginConfig config)
	{
		this.config = config;
	}

	public void setSettlementConfigSnapshot(SettlementConfigSnapshot settlementConfigSnapshot)
	{
		this.settlementConfigSnapshot = settlementConfigSnapshot;
		this.geTaxSettings = null;
		fireTableDataChanged();
	}

	public void setGeTaxSettings(SplitCalculator.GeTaxSettings geTaxSettings)
	{
		this.geTaxSettings = geTaxSettings;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount()
	{
		return rows.size();
	}

	@Override
	public int getColumnCount()
	{
		return 4;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		Row e = rows.get(rowIndex);
		switch (columnIndex)
		{
			case 0:
				return e.time;
			case 1:
				return e.event.getPlayer();
			case 2:
				String t = e.event.getType();
				if (SplitEvent.TYPE_JOINED.equalsIgnoreCase(t))
				{
					return "Joined";
				}
				if (SplitEvent.TYPE_LEFT.equalsIgnoreCase(t))
				{
					return "Left";
				}
				return Formats.OsrsAmountFormatter.toSuffixString(cleanSplitAmount(e.event), 'k');
			case 3:
				long geTax = geTaxAmount(e.event);
				return geTax > 0L ? Formats.OsrsAmountFormatter.toSuffixString(geTax, 'm') : "";
			default:
				return "";
		}
	}

	@Override
	public String getColumnName(int column)
	{
		return COLS[column];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex)
	{
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		// Disable editing for JOINED/LEFT rows
		if (rowIndex >= 0 && rowIndex < rows.size())
		{
			if (rows.get(rowIndex).event.isRosterEvent())
			{
				return false;
			}
		}
		return columnIndex == 1 || columnIndex == 2; // player, amount
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex)
	{
		if (rowIndex < 0 || rowIndex >= rows.size())
		{
			return;
		}
		Row e = rows.get(rowIndex);
		if (columnIndex == 1) // player
		{
			String v = aValue == null ? null : aValue.toString();
			if (v != null && !v.isBlank())
			{
				e.event.setPlayer(v.trim());
			}
		}
		else if (columnIndex == 2) // amount (K)
		{
			try
			{
				Long k = Formats.OsrsAmountFormatter.stringAmountToLongAmount((String) aValue, config);
				e.event.setAmount(k);
			}
			catch (Exception ex)
			{
				log.warn("Invalid amount: {}", aValue, ex);
			}
		}
		fireTableRowsUpdated(rowIndex, rowIndex);
		if (listener != null)
		{
			listener.onEdited(e.event); // pass the edited event so we know its sessionId
		}
	}

	private long cleanSplitAmount(SplitEvent event)
	{
		if (event == null || event.getAmount() == null)
		{
			return 0L;
		}
		return Math.max(event.getAmount() - geTaxAmount(event), 0L);
	}

	private long geTaxAmount(SplitEvent event)
	{
		if (geTaxSettings != null)
		{
			return geTaxAmount(event, geTaxSettings);
		}
		if (event == null || !event.isLootEvent() || event.getAmount() == null || !accountForGeTax())
		{
			return 0L;
		}
		double percent = geTaxPercent();
		if (Double.isNaN(percent) || Double.isInfinite(percent) || percent < 0.0d)
		{
			percent = PluginConfig.DEFAULT_GE_TAX_PERCENT;
		}
		if (percent <= 0.0d)
		{
			return 0L;
		}
		if (event.getAmount() < geTaxMinimumValue())
		{
			return 0L;
		}
		BigDecimal calculated = BigDecimal.valueOf(event.getAmount())
			.multiply(BigDecimal.valueOf(percent))
			.divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
		long capped = Math.min(geTaxMaxPerLoot(), calculated.longValue());
		return Math.max(capped, 0L);
	}

	private long geTaxAmount(SplitEvent event, SplitCalculator.GeTaxSettings settings)
	{
		if (event == null
			|| !event.isLootEvent()
			|| event.getAmount() == null
			|| settings == null
			|| !settings.isEnabled()
			|| event.getAmount() < settings.getMinimumValue())
		{
			return 0L;
		}
		BigDecimal calculated = BigDecimal.valueOf(event.getAmount())
			.multiply(BigDecimal.valueOf(settings.getPercent()))
			.divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
		long capped = Math.min(settings.getMaxTaxPerLoot(), calculated.longValue());
		return Math.max(capped, 0L);
	}

	private long geTaxMinimumValue()
	{
		String configured = settlementConfigSnapshot == null ? config.geTaxMinimumValue() : settlementConfigSnapshot.getGeTaxMinimumValue();
		String value = configured == null || configured.trim().isEmpty()
			? PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE
			: configured.trim();
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(value, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse GE tax minimum value {}; using default {}", value, PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, e);
			try
			{
				return Formats.OsrsAmountFormatter.stringAmountToLongAmount(PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, null);
			}
			catch (ParseException defaultError)
			{
				log.warn("Failed to parse built-in GE tax minimum {}; disabling GE tax marker", PluginConfig.DEFAULT_GE_TAX_MINIMUM_VALUE, defaultError);
				return Long.MAX_VALUE;
			}
		}
	}

	private long geTaxMaxPerLoot()
	{
		String configured = settlementConfigSnapshot == null ? config.geTaxMaxPerLoot() : settlementConfigSnapshot.getGeTaxMaxPerLoot();
		String value = configured == null || configured.trim().isEmpty()
			? PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE
			: configured.trim();
		try
		{
			return Formats.OsrsAmountFormatter.stringAmountToLongAmount(value, null);
		}
		catch (ParseException e)
		{
			log.warn("Failed to parse GE tax max per loot {}; using default {}", value, PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, e);
			try
			{
				return Formats.OsrsAmountFormatter.stringAmountToLongAmount(PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, null);
			}
			catch (ParseException defaultError)
			{
				log.warn("Failed to parse built-in GE tax max per loot {}; using default {}", PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT_VALUE, PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT, defaultError);
				return PluginConfig.DEFAULT_GE_TAX_MAX_PER_LOOT;
			}
		}
	}

	private boolean accountForGeTax()
	{
		if (settlementConfigSnapshot != null)
		{
			return settlementConfigSnapshot.isAccountForGeTax();
		}
		return config != null && config.accountForGeTax();
	}

	private double geTaxPercent()
	{
		if (settlementConfigSnapshot != null)
		{
			return settlementConfigSnapshot.getGeTaxPercent();
		}
		return config == null ? PluginConfig.DEFAULT_GE_TAX_PERCENT : config.geTaxPercent();
	}

	// Optionally expose a getter to let editors query the event of a row:
	public SplitEvent getEventAt(int rowIndex)
	{
		return (rowIndex >= 0 && rowIndex < rows.size()) ? rows.get(rowIndex).event : null;
	}

	private void addEntry(SplitEvent k)
	{
		String timeStr = "";
		if (k.getAt() != null)
		{
			timeStr = Formats.getLocalTime().format(java.time.ZonedDateTime.ofInstant(k.getAt(), SYS_TZ));
		}
		// newest on top (insert at index 0)
		rows.add(0, new Row(k, timeStr));
		fireTableDataChanged();
	}

	public void setFromEvents(java.util.List<SplitEvent> events)
	{
		clear();
		if (events == null || events.isEmpty())
		{
			fireTableDataChanged();
			return;
		}
		// Iterate from oldest to newest
		for (SplitEvent k : events)
		{
			addEntry(k);
		}
		fireTableDataChanged();
	}

	public void clear()
	{
		rows.clear();
		fireTableDataChanged();
	}

	public interface Listener
	{
		void onEdited(SplitEvent editedEvent);
	}

	private static final class Row
	{
		final SplitEvent event; // keep reference for editing
		final String time;

		Row(SplitEvent event, String time)
		{
			this.event = event;
			this.time = time;
		}
	}
}
