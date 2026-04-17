package com.splitmanager;

import com.splitmanager.controllers.PanelController;
import com.splitmanager.views.PanelView;
import com.splitmanager.views.PopoutView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Composition root: builds Model, View, Controller and wires them together.
 * No UI logic or event handling lives here anymore.
 */
@Slf4j
@Singleton
public class ManagerPanel
{

	private final ManagerSession manager;
	private final PluginConfig config;
	private final ManagerKnownPlayers playerManager;
	private JFrame popoutFrame;
	private JButton popOutBtn;
	private PanelController controller;
	private PanelController popoutController;
	private PopoutView popoutView;
	@Getter
	@Setter
	private PanelView view;

	/**
	 * Construct a new plugin panel and bootstrap its MVC components.
	 *
	 * @param sessionManager session/state sessionManager for split tracking
	 * @param config         plugin configuration
	 */
	@Inject
	public ManagerPanel(ManagerSession sessionManager, PluginConfig config, ManagerKnownPlayers playerManager)
	{
		this.manager = sessionManager;
		this.config = config;
		this.playerManager = playerManager;
	}

	/**
	 * Refresh all view sections via the controller.
	 */
	public void refreshAllView()
	{
		if (controller != null)
		{
			controller.refreshAllView();
		}
		if (popoutController != null)
		{
			popoutController.refreshAllView();
		}
	}

	/**
	 * Initialize and wire the view and controller, and perform an initial sync.
	 */
	private void startPanel()
	{
		controller = new PanelController(manager, config, playerManager, this);
		view = new PanelView(manager, config, playerManager, controller);
		controller.setView(view);

		popOutBtn = new JButton("Pop Out");
		popOutBtn.addActionListener(e -> togglePopOutWindow());
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		topBar.add(popOutBtn);
		addTopBar(topBar);

		controller.refreshAllView();
	}

	private void addTopBar(JPanel topBar)
	{
		Component[] existingComponents = view.getComponents();
		view.removeAll();

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.add(topBar);
		for (Component component : existingComponents)
		{
			wrapper.add(component);
		}
		view.add(wrapper, BorderLayout.NORTH);
	}


	private void togglePopOutWindow()
	{
		if (popoutFrame != null && popoutFrame.isDisplayable())
		{
			popoutFrame.toFront();
			popoutFrame.requestFocus();
			return;
		}
		if (popOutBtn != null)//hide button if window open
		{
			popOutBtn.setVisible(false);
			view.revalidate();
			view.repaint();
		}

		popoutController = new PanelController(manager, config, playerManager, this);
		popoutView = new PopoutView(manager, config, playerManager, popoutController);
		popoutController.setView(popoutView);

		popoutController.refreshAllView();

		popoutFrame = new JFrame("Auto Split Manager");
		popoutFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		popoutFrame.getContentPane().add(popoutView, BorderLayout.CENTER);
		popoutFrame.setMinimumSize(new Dimension(1000, 650));
		popoutFrame.setSize(new Dimension(1100, 720));
		popoutFrame.setLocationRelativeTo(null);
		popoutFrame.setVisible(true);

		//show button if window closes
		popoutFrame.addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override
			public void windowClosed(java.awt.event.WindowEvent e)
			{
				popoutFrame = null;
				popoutController = null;
				popoutView = null;
				if (popOutBtn != null)
				{
					popOutBtn.setVisible(true);
					view.revalidate();
					view.repaint();
				}
			}
		});
	}


	/**
	 * Recreate the panel components from scratch.
	 */
	public void restart()
	{
		if (popoutFrame != null)
		{
			popoutFrame.dispose();
			popoutFrame = null;
			popoutController = null;
			popoutView = null;
		}
		if (view != null)
		{
			view.removeAll();
		}
		startPanel();
	}

	public void init()
	{
		startPanel();
	}
}
