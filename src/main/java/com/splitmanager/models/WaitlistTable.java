package com.splitmanager.models;

import com.splitmanager.PluginConfig;
import com.splitmanager.utils.Formats;
import static com.splitmanager.utils.Formats.OsrsAmountFormatter.toSuffixString;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WaitlistTable extends AbstractTableModel
{
	private final String[] cols = {"Type", "Value", "Player"};
	private final List<PendingValue> rows = new ArrayList<>();
	private Runnable editListener;

	public void setEditListener(Runnable editListener)
	{
		this.editListener = editListener;
	}

	public void setData(List<PendingValue> pending)
	{
		rows.clear();
		if (pending != null)
		{
			rows.addAll(pending);
		}
		fireTableDataChanged();
	}

	public PendingValue getRow(int idx)
	{
		if (idx < 0 || idx >= rows.size())
		{
			return null;
		}
		return rows.get(idx);
	}

	@Override
	public int getRowCount()
	{
		return rows.size();
	}

	@Override
	public int getColumnCount()
	{
		return cols.length;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		PendingValue pv = rows.get(rowIndex);
		switch (columnIndex)
		{
			case 0:
				return pv.getType().name();
			case 1:
				long value = pv.getValue();
				//TODO get this from config
				return toSuffixString(value, "k");
			case 2:
				return pv.getSuggestedPlayer() == null ? "" : pv.getSuggestedPlayer();
		}
		return null;
	}

	@Override
	public String getColumnName(int column)
	{
		return cols[column];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex)
	{
		switch (columnIndex)
		{
			case 0:
			case 2:
				return String.class;
			case 1:
				return String.class; // formatted K/M/B string
		}
		return Object.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		// Allow editing Player and Value columns
		return columnIndex == 1 || columnIndex == 2;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex)
	{
		PendingValue pv = rows.get(rowIndex);
		if (columnIndex == 2)
		{
			pv.setSuggestedPlayer(aValue == null ? null : aValue.toString());
			fireTableCellUpdated(rowIndex, columnIndex);
			notifyEdited();
			return;
		}
		if (columnIndex == 1)
		{
			String txt = aValue == null ? "" : aValue.toString();
			try
			{
				// Parse using default config multiplier if needed; null config will default inside formatter
				long parsed = Formats.OsrsAmountFormatter.stringAmountToLongAmount(txt, (PluginConfig) null);
				pv.setValue(parsed);
				fireTableCellUpdated(rowIndex, columnIndex);
				notifyEdited();
			}
			catch (ParseException e)
			{
				log.warn("Failed to parse amount '{}' for pending value row {}", txt, rowIndex, e);
			}
		}
	}

	private void notifyEdited()
	{
		if (editListener != null)
		{
			editListener.run();
		}
	}
}
