package com.splitmanager.views.components;

import com.splitmanager.PluginConfig;
import com.splitmanager.controllers.PanelActions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.Timer;
import javax.swing.border.Border;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PanelTour
{
	private static final List<String> STEPS = List.of(
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
		}
		return panel;
	}

	public void startTour()
	{
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
	}

	public void endTourAndDisable()
	{
		PanelActions actions = actionsSupplier.get();
		if (actions != null)
		{
			actions.tourEnd();
			return;
		}

		running = false;
		step = 0;
		clearHighlight();
		try
		{
			config.enableTour(false);
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to persist tour disabled state", e);
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
		int max = STEPS.size() - 1;
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

		JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonRow2.add(prevButton);
		buttonRow2.add(nextButton);

		right.add(buttonRow1);
		right.add(buttonRow2);

		startButton.addActionListener(e -> dispatchOrRun(PanelActions::tourStart, this::startTour));
		prevButton.addActionListener(e -> dispatchOrRun(PanelActions::tourPrev, this::previousStep));
		nextButton.addActionListener(e -> dispatchOrRun(PanelActions::tourNext, this::nextStep));
		endButton.addActionListener(e -> dispatchOrRun(PanelActions::tourEnd, this::endTourAndDisable));

		tourPanel.add(left, BorderLayout.CENTER);
		tourPanel.add(right, BorderLayout.SOUTH);

		updateUi();
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
		boolean show = enabled || running;
		if (panel != null)
		{
			panel.setVisible(show);
		}
		if (text != null)
		{
			String msg = running
				? "Step " + (step + 1) + "/" + STEPS.size() + ": " + STEPS.get(step)
				: "Welcome! Click Start tour to begin a quick walkthrough.";
			text.setText(msg);
		}
		if (startButton != null)
		{
			startButton.setVisible(!running);
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
			endButton.setVisible(running);
		}
	}

	private void highlightTargetForStep()
	{
		clearHighlight();
		if (!running)
		{
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
	}
}
