package com.splitmanager.persistence;

import com.splitmanager.models.Session;
import java.util.ArrayList;
import java.util.List;

public class SessionStorageData
{
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	private String currentSessionId;
	private boolean historyLoaded;
	private List<Session> sessions = new ArrayList<>();

	public int getSchemaVersion()
	{
		return schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion)
	{
		this.schemaVersion = schemaVersion;
	}

	public String getCurrentSessionId()
	{
		return currentSessionId;
	}

	public void setCurrentSessionId(String currentSessionId)
	{
		this.currentSessionId = currentSessionId;
	}

	public boolean isHistoryLoaded()
	{
		return historyLoaded;
	}

	public void setHistoryLoaded(boolean historyLoaded)
	{
		this.historyLoaded = historyLoaded;
	}

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
