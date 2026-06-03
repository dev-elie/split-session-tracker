package com.splitmanager.chat;

import lombok.Getter;

@Getter
public enum ChatSource
{
	CLAN("Clan"),
	FRIENDS("Friends");

	private final String label;

	ChatSource(String label)
	{
		this.label = label;
	}

}
