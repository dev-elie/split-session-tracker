package com.splitmanager.views.graph;

import lombok.Getter;

@Getter
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

}
