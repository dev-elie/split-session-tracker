package com.splitmanager.views.graph;

public enum SessionGraphMode
{
	GP_PER_HOUR("GP/hr over time", "Cumulative loot divided by elapsed session time.", true),
	HIGHEST_EARNINGS("Highest earnings", "Total loot recorded per player.", false),
	SPLIT_BALANCE("Settlement balance", "Positive receives. Negative owes.", false);

	private final String label;
	private final String description;
	private final boolean lineChart;

	SessionGraphMode(String label, String description, boolean lineChart)
	{
		this.label = label;
		this.description = description;
		this.lineChart = lineChart;
	}

	public String getLabel()
	{
		return label;
	}

	public String getDescription()
	{
		return description;
	}

	public boolean isLineChart()
	{
		return lineChart;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
