package com.splitmanager.chat;

public enum ChatSource
{
	CLAN("Clan"),
	FRIENDS("Friends");

	private final String label;

	ChatSource(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
