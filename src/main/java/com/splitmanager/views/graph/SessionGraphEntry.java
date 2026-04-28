package com.splitmanager.views.graph;

public final class SessionGraphEntry
{
	private final String label;
	private final long value;
	private final boolean active;

	public SessionGraphEntry(String label, long value, boolean active)
	{
		this.label = label == null ? "" : label;
		this.value = value;
		this.active = active;
	}

	public String getLabel()
	{
		return label;
	}

	public long getValue()
	{
		return value;
	}

	public boolean isActive()
	{
		return active;
	}
}
