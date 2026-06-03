package com.splitmanager.persistence;

import com.splitmanager.models.Session;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class SessionStorageData
{
	public static final int CURRENT_SCHEMA_VERSION = 1;

	@Setter
	@Getter
	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	@Setter
	@Getter
	private String currentSessionId;
	@Setter
	@Getter
	private boolean historyLoaded;
	private List<Session> sessions = new ArrayList<>();

	public List<Session> getSessions()
	{
		if (sessions == null)
		{
			sessions = new ArrayList<>();
		}
		return sessions;
	}

	public void setSessions(List<Session> sessions)
	{
		this.sessions = sessions == null ? new ArrayList<>() : sessions;
	}
}
