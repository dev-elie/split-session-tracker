package com.splitmanager.views.components.table;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.splitmanager.ManagerPanel;
import com.splitmanager.ManagerSession;
import com.splitmanager.controllers.PanelActions;
import com.splitmanager.models.Metrics;
import static com.splitmanager.utils.Utils.toast;
import java.awt.Component;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import net.runelite.client.ui.ColorScheme;

/**
 * Table cell editor rendering an action button:
 * - Sleep icon to remove active players from the session
 * - Sunrise icon to add inactive players back to the session
 */
public class RemoveButtonEditor extends DefaultCellEditor
{
	private static final FlatSVGIcon REMOVE_ICON = new FlatSVGIcon(
		RemoveButtonEditor.class.getResource("/com/splitmanager/icons/sleep.svg")).derive(16, 16);
	private static final FlatSVGIcon ADD_ICON = new FlatSVGIcon(
		RemoveButtonEditor.class.getResource("/com/splitmanager/icons/sunrise.svg")).derive(16, 16);

	private final JButton button = new JButton();
	private int row = -1;

	@Inject
	private ManagerPanel managerPanel;

	/**
	 * Create the editor and wire the add/remove behavior.
	 *
	 * @param parent       parent component for toasts
	 * @param manager      session manager
	 * @param metricsTable table with player rows
	 * @param actions      callbacks to refresh the view
	 */
	public RemoveButtonEditor(Component parent, ManagerSession manager, JTable metricsTable, PanelActions actions)
	{
		super(new JCheckBox());

		button.setOpaque(true);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		button.setRolloverEnabled(true);
		button.addActionListener(e -> {
			if (row >= 0 && !manager.isHistoryLoaded())
			{
				String player = (String) metricsTable.getModel().getValueAt(row, 0);
				boolean isActive = false;
				if (metricsTable.getModel() instanceof Metrics)
				{
					isActive = ((Metrics) metricsTable.getModel()).isRowActive(row);
				}

				boolean ok;
				if (isActive)
				{
					ok = manager.removePlayerFromSession(player);
					if (!ok)
					{
						toast(parent, "Failed to remove player.");
					}
				}
				else
				{
					ok = manager.addPlayerToActive(player);
					if (!ok)
					{
						toast(parent, "Failed to add player.");
					}
				}

				if (ok)
				{
					actions.refreshSharedViews();
				}
			}
			fireEditingStopped();
		});

	}

	@Override
	public Object getCellEditorValue()
	{
		return button.getIcon();
	}

	@Override
	public boolean stopCellEditing()
	{
		this.row = -1;
		return super.stopCellEditing();
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
	{
		this.row = row;
		// Adjust label based on active/inactive state
		if (table.getModel() instanceof Metrics)
		{
			boolean active = ((Metrics) table.getModel()).isRowActive(row);
			button.setIcon(active ? REMOVE_ICON : ADD_ICON);
		}
		else
		{
			button.setIcon(REMOVE_ICON);
		}
		return button;
	}

}
