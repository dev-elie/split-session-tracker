package com.splitmanager.views.components;

import com.splitmanager.PluginConfig;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PanelTourTest
{
	@Test
	public void showsUpdateInfoOnceEvenWhenQuickTourDisabled()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(false);
		when(config.tourUpdateInfoSeenVersion()).thenReturn("");

		PanelTour tour = new PanelTour(config, () -> null, targets());
		JPanel panel = tour.getPanel();

		assertTrue(panel.isVisible());
	}

	@Test
	public void hidesWhenQuickTourDisabledAndUpdateInfoAlreadySeen()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(false);
		when(config.tourUpdateInfoSeenVersion()).thenReturn(PanelTour.UPDATE_INFO_VERSION);

		PanelTour tour = new PanelTour(config, () -> null, targets());
		JPanel panel = tour.getPanel();

		assertFalse(panel.isVisible());
	}

	@Test
	public void endingPendingUpdateInfoStoresSeenVersionWithoutDisablingQuickTour()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(false);
		when(config.tourUpdateInfoSeenVersion()).thenReturn("");

		PanelTour tour = new PanelTour(config, () -> null, targets());
		tour.endTourAndDisable();

		verify(config).tourUpdateInfoSeenVersion(PanelTour.UPDATE_INFO_VERSION);
		verify(config, never()).enableTour(false);
	}

	@Test
	public void startingUpdateInfoCanHighlightAndScrollToFirstTarget()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(false);
		when(config.tourUpdateInfoSeenVersion()).thenReturn("");

		PanelTour tour = new PanelTour(config, () -> null, targets());
		tour.startTour();

		assertTrue(tour.getPanel().isVisible());
	}

	@Test
	public void endingQuickTourDisablesQuickTour()
	{
		PluginConfig config = mock(PluginConfig.class);
		when(config.enableTour()).thenReturn(true);
		when(config.tourUpdateInfoSeenVersion()).thenReturn(PanelTour.UPDATE_INFO_VERSION);

		PanelTour tour = new PanelTour(config, () -> null, targets());
		tour.startTour();
		tour.endTourAndDisable();

		verify(config).enableTour(false);
		verify(config, never()).tourUpdateInfoSeenVersion(PanelTour.UPDATE_INFO_VERSION);
	}

	private static PanelTour.Targets targets()
	{
		return new PanelTour.Targets()
		{
			@Override
			public JComponent newPlayerField()
			{
				return new JButton();
			}

			@Override
			public JComponent addAltDropdown()
			{
				return new JButton();
			}

			@Override
			public JComponent startButton()
			{
				return new JButton();
			}

			@Override
			public JComponent notInSessionDropdown()
			{
				return new JButton();
			}

			@Override
			public JComponent currentSessionDropdown()
			{
				return new JButton();
			}

			@Override
			public JComponent metricsTable()
			{
				return new JButton();
			}

			@Override
			public JComponent copyMarkdownButton()
			{
				return new JButton();
			}

			@Override
			public JComponent detectedValuesDropdown()
			{
				return new JButton();
			}

			@Override
			public JComponent recentSplitsTable()
			{
				return new JButton();
			}

			@Override
			public JComponent stopButton()
			{
				return new JButton();
			}

			@Override
			public JComponent historyButton()
			{
				return new JButton();
			}

			@Override
			public JComponent popoutButton()
			{
				return new JButton();
			}

			@Override
			public JComponent editButton()
			{
				return new JButton();
			}

			@Override
			public JComponent geTaxControl()
			{
				return new JButton();
			}
		};
	}
}
