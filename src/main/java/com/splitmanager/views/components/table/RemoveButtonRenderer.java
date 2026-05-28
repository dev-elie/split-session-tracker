package com.splitmanager.views.components.table;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import net.runelite.client.ui.ColorScheme;

public class RemoveButtonRenderer extends JButton implements TableCellRenderer
{
	private static final long serialVersionUID = 1L;

	public RemoveButtonRenderer()
	{
		setOpaque(true);
		setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		setRolloverEnabled(true);
	}

	@Override
	public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
	{
		if (value instanceof java.awt.Component)
		{
			return (java.awt.Component) value;
		}
		return this;
	}
}
