package com.splitmanager.utils;

import java.awt.Color;
import java.awt.Dimension;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Getter
@Setter
public class ChatStatusOverlay extends OverlayPanel
{
	private boolean visible = false;
	private boolean chatchanOn = false;

	public ChatStatusOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(java.awt.Graphics2D g)
	{
		if (!visible)
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(120, 0));

		final String title = "Cant track splits:";
		final Color titleColor = new Color(255, 80, 80);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(title)
			.color(titleColor)
			.build());

		if (!chatchanOn)
		{
			addStatusLine("Chat Channel", chatchanOn);
		}

		return super.render(g);
	}

	public void addStatusLine(String label, boolean on)
	{
		final String statusText = on ? "ON" : "OFF";
		final Color statusCol = on ? new Color(120, 255, 120) : new Color(255, 120, 120);

		panelComponent.getChildren().add(LineComponent.builder()
			.left(label)
			.right(statusText)
			.leftColor(Color.WHITE)
			.rightColor(statusCol)
			.build());
	}


}
