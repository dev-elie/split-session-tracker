package com.splitmanager.models;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.splitmanager.utils.Formats;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.table.AbstractTableModel;
import net.runelite.client.ui.ColorScheme;

public final class Metrics extends AbstractTableModel
{
	private final JButton removeBtn = createStyledButton("/com/splitmanager/icons/sleep.svg");
	private final JButton addBtn = createStyledButton("/com/splitmanager/icons/sunrise.svg");
	private List<PlayerMetrics> rows = List.of();
	private boolean hideTotalColumn = false;

	private static JButton createStyledButton(String iconPath)
	{
		JButton btn = new JButton(new FlatSVGIcon(Metrics.class.getResource(iconPath)).derive(16, 16));
		btn.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		return btn;
	}

	public void setHideTotalColumn(boolean hide)
	{
		if (this.hideTotalColumn != hide)
		{
			this.hideTotalColumn = hide;
			fireTableStructureChanged();
		}
	}

	public boolean isHidingTotalColumn()
	{
		return hideTotalColumn;
	}

	public void setData(List<PlayerMetrics> rows)
	{
		// Sort: active first, inactive at bottom; stable within groups
		this.rows = rows.stream()
			.sorted(Comparator.comparingInt(pm -> pm.activePlayer ? 0 : 1)) // active=true → 0, inactive=false → 1
			.collect(Collectors.toList());
		fireTableDataChanged();
	}

	// Helper for renderers/editors to know if a row is active
	public boolean isRowActive(int rowIndex)
	{
		if (rowIndex < 0 || rowIndex >= rows.size())
		{
			return false;
		}
		return rows.get(rowIndex).activePlayer;
	}

	@Override
	public int getRowCount()
	{
		return rows.size();
	}

	@Override
	public int getColumnCount()
	{
		return hideTotalColumn ? 3 : 4;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		var r = rows.get(rowIndex);
		boolean active = r.activePlayer;
		if (!hideTotalColumn)
		{
			switch (columnIndex)
			{
				case 0:
					return r.player;
				case 1:
					return Formats.OsrsAmountFormatter.toSuffixString(r.total, 'k');
				case 2:
					return Formats.OsrsAmountFormatter.toSuffixString(r.split, 'k');
				case 3:
					return updateActionButton(active);
				default:
					return "";
			}
		}
		else
		{
			switch (columnIndex)
			{
				case 0:
					return r.player;
				case 1:
					return Formats.OsrsAmountFormatter.toSuffixString(r.split, 'k');
				case 2:
					return updateActionButton(active);
				default:
					return "";
			}
		}
	}

	private JButton updateActionButton(boolean active)
	{
		removeBtn.setIcon(active
			? new FlatSVGIcon(Metrics.class.getResource("/com/splitmanager/icons/sleep.svg")).derive(16, 16)
			: new FlatSVGIcon(Metrics.class.getResource("/com/splitmanager/icons/sunrise.svg")).derive(16, 16));
		addBtn.setIcon(active
			? new FlatSVGIcon(Metrics.class.getResource("/com/splitmanager/icons/sleep.svg")).derive(16, 16)
			: new FlatSVGIcon(Metrics.class.getResource("/com/splitmanager/icons/sunrise.svg")).derive(16, 16));
		return active ? removeBtn : addBtn;
	}

	@Override
	public String getColumnName(int column)
	{
		if (!hideTotalColumn)
		{
			switch (column)
			{
				case 0:
					return "Player";
				case 1:
					return "Total";
				case 2:
					return "Split";
				case 3:
					return "X";
				default:
					return "";
			}
		}
		else
		{
			switch (column)
			{
				case 0:
					return "Player";
				case 1:
					return "Split";
				case 2:
					return "X";
				default:
					return "";
			}
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex)
	{
		// Use Object so we can render either a JButton (active) or a JLabel (inactive) in action col
		return Object.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		int actionCol = hideTotalColumn ? 2 : 3;
		return columnIndex == actionCol;
	}

	// Accessor for renderers: raw split value for row
	public long getRawSplitAt(int rowIndex)
	{
		if (rowIndex < 0 || rowIndex >= rows.size())
		{
			return 0L;
		}
		return rows.get(rowIndex).split;
	}
}
