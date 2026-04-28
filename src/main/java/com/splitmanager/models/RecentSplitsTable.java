package com.splitmanager.models;


import com.splitmanager.PluginConfig;
import com.splitmanager.utils.Formats;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RecentSplitsTable extends javax.swing.table.AbstractTableModel
{
	private static final String[] COLS = {"Time", "Player", "Amount"};
	private static final java.time.ZoneId SYS_TZ = java.time.ZoneId.systemDefault();
	private final java.util.List<Row> rows = new java.util.ArrayList<>(10);
	private final PluginConfig config;
	@Setter
	private Listener listener;

	public RecentSplitsTable(PluginConfig config)
	{
		this.config = config;
	}

	@Override
	public int getRowCount()
	{
		return rows.size();
	}

	@Override
	public int getColumnCount()
	{
		return 3;
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
				return e.kill.getPlayer();
			case 2:
				String t = e.kill.getType();
				if (Kill.TYPE_JOINED.equalsIgnoreCase(t))
				{
					return "Joined";
				}
				if (Kill.TYPE_LEFT.equalsIgnoreCase(t))
				{
					return "Left";
				}
				return Formats.OsrsAmountFormatter.toSuffixString(e.kill.getAmount(), 'k');
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
			if (rows.get(rowIndex).kill.isRosterEvent())
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
				e.kill.setPlayer(v.trim());
			}
		}
		else if (columnIndex == 2) // amount (K)
		{
			try
			{
				Long k = Formats.OsrsAmountFormatter.stringAmountToLongAmount((String) aValue, config);
				e.kill.setAmount(k);
			}
			catch (Exception ex)
			{
				log.warn("Invalid amount: {}", aValue, ex);
			}
		}
		fireTableRowsUpdated(rowIndex, rowIndex);
		if (listener != null)
		{
			listener.onEdited(e.kill); // pass the edited kill so we know its sessionId
		}
	}

	// Optionally expose a getter to let editors query the kill of a row:
	public Kill getKillAt(int rowIndex)
	{
		return (rowIndex >= 0 && rowIndex < rows.size()) ? rows.get(rowIndex).kill : null;
	}

	private void addEntry(Kill k)
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

	public void setFromKills(java.util.List<Kill> kills)
	{
		clear();
		if (kills == null || kills.isEmpty())
		{
			fireTableDataChanged();
			return;
		}
		int n = kills.size();
		// Iterate from oldest to newest
		for (int i = 0; i < n; i++)
		{
			Kill k = kills.get(i);
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
		void onEdited(Kill editedKill);
	}

	private static final class Row
	{
		final Kill kill; // keep reference for editing
		final String time;

		Row(Kill kill, String time)
		{
			this.kill = kill;
			this.time = time;
		}
	}
}
