package com.splitmanager.views.components;

import com.splitmanager.PluginConfig;
import com.splitmanager.controllers.PanelActions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PanelTour
{
	public static final String UPDATE_INFO_VERSION = "3.1.0";
	private static final List<String> QUICK_STEPS = List.of(
		"Scroll down and add a new player: type a name in the text field and click Add Player.",
		"Optional: The 'Known alts' dropdown lets you link an alt account to a selected known player.",
		"Start a session using the Start button.",
		"Add players to the session: use the 'Not in session' dropdown, click Add. Tip: add 2 players to see splits.",
		"Record a split: use the 'Player' dropdown, enter an amount, then click Add.",
		"You can remove a player from settlement by clicking the 'x' button in the Settlement table.",
		"Share results: use the Copy MD button (great for Discord) or Copy JSON if you need raw data.",
		"Detected values: expand the 'Detected values' section. '!add' in clan chat will queue amounts here. See Settings > Chat detection to configure.",
		"Review the Recent Splits table.",
		"Stop the session when you are done."
	);
	private static final List<String> UPDATE_INFO_STEPS = List.of(
		"History loading: stopped sessions are saved in View history. Select one and click View to review an old split thread.",
		"Graph pop-up: click the graph button to open a wider dashboard with session charts and the same split controls.",
		"Edit mode: click the pencil button to open the pop-up in edit mode, then adjust saved split rows when history needs correction.",
		"GE tax: Settings > GE tax controls whether eligible loot deducts Grand Exchange tax from the seller before split balances are calculated."
	);

	private final PluginConfig config;
	private final Supplier<PanelActions> actionsSupplier;
	private final Targets targets;
	private JPanel panel;
	private JTextArea text;
	private JButton startButton;
	private JButton prevButton;
	private JButton nextButton;
	private JButton endButton;
	private boolean running;
	private int step;
	private Mode mode = Mode.QUICK;
	private Timer rainbowTimer;
	private JComponent highlighted;
	private Border originalBorder;

	public PanelTour(PluginConfig config, Supplier<PanelActions> actionsSupplier, Targets targets)
	{
		this.config = config;
		this.actionsSupplier = actionsSupplier;
		this.targets = targets;
	}

	public JPanel getPanel()
	{
		if (panel == null)
		{
			panel = buildPanel();
			updateUi(); // Ensure the UI is updated once the panel field is set
		}
		return panel;
	}

	public void startTour()
	{
		mode = shouldShowUpdateInfo() ? Mode.UPDATE_INFO : Mode.QUICK;
		running = true;
		step = 0;
		updateUi();
		highlightTargetForStep();
	}

	public void endTour()
	{
		running = false;
		step = 0;
		clearHighlight();
		updateUi();
		if (panel != null)
		{
			panel.revalidate();
			panel.repaint();
		}
	}

	public void endTourAndDisable()
	{
		final boolean updateInfo = isUpdateInfoActiveOrPending();
		PanelActions actions = actionsSupplier.get();
		if (actions != null)
		{
			actions.tourEnd(updateInfo);
			return;
		}

		running = false;
		step = 0;
		clearHighlight();
		try
		{
			if (updateInfo)
			{
				config.tourUpdateInfoSeenVersion(UPDATE_INFO_VERSION);
			}
			else
			{
				config.enableTour(false);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to persist tour state", e);
		}
		updateUi();
	}

	public void nextStep()
	{
		gotoStep(step + 1);
	}

	public void previousStep()
	{
		gotoStep(step - 1);
	}

	public void gotoStep(int nextStep)
	{
		int max = getSteps().size() - 1;
		if (nextStep < 0)
		{
			nextStep = 0;
		}
		if (nextStep > max)
		{
			endTourAndDisable();
			return;
		}
		step = nextStep;
		updateUi();
		highlightTargetForStep();
	}

	private JPanel buildPanel()
	{
		JPanel tourPanel = new JPanel(new BorderLayout());
		tourPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color.GRAY),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		text = new JTextArea("Welcome! Click Start tour to begin a quick walkthrough.");
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setEditable(false);
		text.setFont(text.getFont().deriveFont(Font.BOLD));
		text.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel hint = new JLabel("   Tip: You can disable this tour in settings");
		hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		left.add(text);
		left.add(Box.createVerticalStrut(3));
		left.add(hint);

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		startButton = new JButton("Start tour");
		prevButton = new JButton("Previous");
		nextButton = new JButton("Next");
		endButton = new JButton("End");

		Dimension small = new Dimension(90, 22);
		startButton.setPreferredSize(small);
		prevButton.setPreferredSize(small);
		nextButton.setPreferredSize(small);
		endButton.setPreferredSize(small);

		JPanel buttonRow1 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		buttonRow1.add(startButton);
		buttonRow1.add(endButton);

		JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonRow2.add(prevButton);
		buttonRow2.add(nextButton);

		right.add(buttonRow1);
		right.add(buttonRow2);

		startButton.addActionListener(e -> dispatchOrRun(PanelActions::tourStart, this::startTour));
		prevButton.addActionListener(e -> dispatchOrRun(PanelActions::tourPrev, this::previousStep));
		nextButton.addActionListener(e -> dispatchOrRun(PanelActions::tourNext, this::nextStep));
		endButton.addActionListener(e -> endTourAndDisable());

		tourPanel.add(left, BorderLayout.CENTER);
		tourPanel.add(right, BorderLayout.SOUTH);

		return tourPanel;
	}

	private void dispatchOrRun(ActionDispatcher dispatcher, Runnable fallback)
	{
		PanelActions actions = actionsSupplier.get();
		if (actions != null)
		{
			dispatcher.dispatch(actions);
			return;
		}
		fallback.run();
	}

	private void updateUi()
	{
		boolean enabled = config.enableTour();
		boolean updateInfoPending = shouldShowUpdateInfo();
		boolean show = running || updateInfoPending || enabled;
		if (panel != null)
		{
			panel.setVisible(show);
			if (panel.getParent() != null)
			{
				panel.getParent().revalidate();
				panel.getParent().repaint();
			}
		}
		if (text != null)
		{
			List<String> steps = getSteps();
			String msg = running
				? getStepPrefix() + " " + (step + 1) + "/" + steps.size() + ":\n" + steps.get(step)
				: getIdleMessage(updateInfoPending);
			text.setText(msg);
		}
		if (startButton != null)
		{
			startButton.setVisible(!running);
			startButton.setText(updateInfoPending ? "Update info" : "Start tour");
		}
		if (prevButton != null)
		{
			prevButton.setVisible(running);
		}
		if (nextButton != null)
		{
			nextButton.setVisible(running);
		}
		if (endButton != null)
		{
			endButton.setVisible(true);
		}
	}

	private void highlightTargetForStep()
	{
		clearHighlight();
		if (!running)
		{
			return;
		}
		if (mode == Mode.UPDATE_INFO)
		{
			highlightUpdateInfoTargetForStep();
			return;
		}
		switch (step)
		{
			case 0:
				highlight(targets.newPlayerField());
				break;
			case 1:
				highlight(targets.addAltDropdown());
				break;
			case 2:
				highlight(targets.startButton());
				break;
			case 3:
				highlight(targets.notInSessionDropdown());
				break;
			case 4:
				highlight(targets.currentSessionDropdown());
				break;
			case 5:
				highlight(targets.metricsTable());
				break;
			case 6:
				highlight(targets.copyMarkdownButton() != null ? targets.copyMarkdownButton() : targets.metricsTable());
				break;
			case 7:
				highlight(targets.detectedValuesDropdown());
				break;
			case 8:
				highlight(targets.recentSplitsTable());
				break;
			case 9:
				highlight(targets.stopButton());
				break;
			default:
				clearHighlight();
		}
	}

	private void highlightUpdateInfoTargetForStep()
	{
		switch (step)
		{
			case 0:
				highlight(targets.historyButton());
				break;
			case 1:
				highlight(targets.popoutButton());
				break;
			case 2:
				highlight(targets.editButton());
				break;
			case 3:
				highlight(targets.geTaxControl());
				break;
			default:
				clearHighlight();
		}
	}

	private boolean shouldShowUpdateInfo()
	{
		String seenVersion = config.tourUpdateInfoSeenVersion();
		return seenVersion == null || !UPDATE_INFO_VERSION.equals(seenVersion.trim());
	}

	private boolean isUpdateInfoActiveOrPending()
	{
		return mode == Mode.UPDATE_INFO || shouldShowUpdateInfo();
	}

	private List<String> getSteps()
	{
		return mode == Mode.UPDATE_INFO ? UPDATE_INFO_STEPS : QUICK_STEPS;
	}

	private String getStepPrefix()
	{
		return mode == Mode.UPDATE_INFO ? "Update info" : "Step";
	}

	private String getIdleMessage(boolean updateInfoPending)
	{
		return updateInfoPending
			? "New in " + UPDATE_INFO_VERSION + ": click Update info for history, graph, edit mode, and GE tax."
			: "Welcome! Click Start tour to begin a quick walkthrough.";
	}

	private void highlight(JComponent component)
	{
		if (component == null)
		{
			return;
		}
		clearHighlight();
		highlighted = component;
		originalBorder = component.getBorder();
		final float[] hue = {0f};
		if (rainbowTimer != null)
		{
			rainbowTimer.stop();
		}
		rainbowTimer = new Timer(80, e -> {
			hue[0] += 0.02f;
			if (hue[0] > 1f)
			{
				hue[0] = 0f;
			}
			Color color = Color.getHSBColor(hue[0], 1f, 1f);
			Border rainbowBorder = BorderFactory.createLineBorder(color, 3);
			Border padding = BorderFactory.createEmptyBorder(2, 2, 2, 2);
			component.setBorder(BorderFactory.createCompoundBorder(rainbowBorder, padding));
			component.repaint();
		});
		rainbowTimer.start();
		scrollToHighlighted();
	}

	private void scrollToHighlighted()
	{
		if (highlighted == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> {
			if (highlighted == null)
			{
				return;
			}
			int width = Math.max(highlighted.getWidth(), highlighted.getPreferredSize().width);
			int height = Math.max(highlighted.getHeight(), highlighted.getPreferredSize().height);
			highlighted.scrollRectToVisible(new Rectangle(0, 0, width, height));
		});
	}

	private void clearHighlight()
	{
		if (rainbowTimer != null)
		{
			rainbowTimer.stop();
			rainbowTimer = null;
		}
		if (highlighted != null)
		{
			highlighted.setBorder(originalBorder);
			highlighted.repaint();
			highlighted = null;
			originalBorder = null;
		}
	}

	private interface ActionDispatcher
	{
		void dispatch(PanelActions actions);
	}

	private enum Mode
	{
		QUICK,
		UPDATE_INFO
	}

	public interface Targets
	{
		JComponent newPlayerField();

		JComponent addAltDropdown();

		JComponent startButton();

		JComponent notInSessionDropdown();

		JComponent currentSessionDropdown();

		JComponent metricsTable();

		JComponent copyMarkdownButton();

		JComponent detectedValuesDropdown();

		JComponent recentSplitsTable();

		JComponent stopButton();

		JComponent historyButton();

		JComponent popoutButton();

		JComponent editButton();

		JComponent geTaxControl();
	}
}
