package com.splitmanager.views.components.table;

import com.splitmanager.ManagerPanel;
import com.splitmanager.ManagerSession;
import com.splitmanager.controllers.PanelActions;
import com.splitmanager.models.Metrics;
import static com.splitmanager.utils.Utils.toast;
import java.awt.Component;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * Table cell editor rendering an action button:
 * - Trash icon to remove active players from the session
 * - Trash icon with up arrow to add inactive players back to the session
 */
public class RemoveButtonEditor extends DefaultCellEditor
{
	private static final ImageIcon REMOVE_ICON;
	private static final ImageIcon ADD_ICON;

	static
	{
		BufferedImage removeImg = ImageUtil.loadImageResource(RemoveButtonEditor.class, "/com/splitmanager/icons/trash-solid-full.png");
		REMOVE_ICON = new ImageIcon(ImageUtil.resizeImage(removeImg, 16, 16));
		BufferedImage addImg = ImageUtil.loadImageResource(RemoveButtonEditor.class, "/com/splitmanager/icons/trash-arrow-up-solid-full.png");
		ADD_ICON = new ImageIcon(ImageUtil.resizeImage(addImg, 16, 16));
	}

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
