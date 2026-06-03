package com.splitmanager.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.splitmanager.PluginConfig;
import com.splitmanager.models.Session;
import com.splitmanager.utils.InstantTypeAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

@Singleton
@Slf4j
public class SessionStorage
{
	private static final String FILE_SUFFIX = ".auto-split-manager.sessions.json";
	private static final String PLUGIN_DIR = "auto-split-manager";
	private static final long DEFAULT_MAX_PRIMARY_FILE_BYTES = 5L * 1024L * 1024L;

	private final ConfigManager configManager;
	private final File fixedFile;
	private final Gson gson;
	private final PluginConfig legacyConfig;
	private final long maxPrimaryFileBytes;

	@Inject
	public SessionStorage(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.fixedFile = null;
		this.legacyConfig = null;
		this.gson = buildGson(gson);
		this.maxPrimaryFileBytes = DEFAULT_MAX_PRIMARY_FILE_BYTES;
	}

	public SessionStorage(File fixedFile, Gson gson)
	{
		this(fixedFile, gson, DEFAULT_MAX_PRIMARY_FILE_BYTES);
	}

	public SessionStorage(File fixedFile, Gson gson, long maxPrimaryFileBytes)
	{
		this.configManager = null;
		this.fixedFile = fixedFile;
		this.legacyConfig = null;
		this.gson = buildGson(gson);
		this.maxPrimaryFileBytes = maxPrimaryFileBytes;
	}

	private SessionStorage(PluginConfig legacyConfig, Gson gson)
	{
		this.configManager = null;
		this.fixedFile = null;
		this.legacyConfig = legacyConfig;
		this.gson = buildGson(gson);
		this.maxPrimaryFileBytes = DEFAULT_MAX_PRIMARY_FILE_BYTES;
	}

	public static SessionStorage legacyConfig(PluginConfig legacyConfig, Gson gson)
	{
		return new SessionStorage(legacyConfig, gson);
	}

	private static Gson buildGson(Gson gson)
	{
		return gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
	}

	private static String nullToEmpty(String value)
	{
		return value == null ? "" : value;
	}

	private static String emptyToNull(String value)
	{
		return value == null || value.isEmpty() ? null : value;
	}

	private static String sanitizeFileNamePart(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "profile";
		}
		String sanitized = value.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
		while (sanitized.contains(".."))
		{
			sanitized = sanitized.replace("..", "_");
		}
		return sanitized.isEmpty() ? "profile" : sanitized;
	}

	public boolean isLegacyConfigStore()
	{
		return legacyConfig != null;
	}

	public boolean exists()
	{
		if (isLegacyConfigStore())
		{
			return hasLegacyData(legacyConfig);
		}
		File file = resolveFile();
		return file != null && file.exists();
	}

	public SessionStorageData load()
	{
		if (isLegacyConfigStore())
		{
			return loadLegacy(legacyConfig);
		}

		File file = resolveFile();
		if (file == null || !file.exists())
		{
			return new SessionStorageData();
		}

		try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8))
		{
			SessionStorageData data = gson.fromJson(reader, SessionStorageData.class);
			if (data == null)
			{
				return new SessionStorageData();
			}
			if (data.getSchemaVersion() > SessionStorageData.CURRENT_SCHEMA_VERSION)
			{
				log.warn("Session storage schema {} is newer than supported schema {} in {}",
					data.getSchemaVersion(), SessionStorageData.CURRENT_SCHEMA_VERSION, file);
				return new SessionStorageData();
			}
			data.getSessions();
			return data;
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Failed to load session storage from {}", file, e);
			return new SessionStorageData();
		}
	}

	public boolean save(SessionStorageData data)
	{
		if (isLegacyConfigStore())
		{
			return saveLegacy(data);
		}

		File file = resolveFile();
		if (file == null)
		{
			log.warn("Cannot save session storage because no RuneLite profile file could be resolved");
			return false;
		}

		return writeDataToFile(file, data);
	}

	public Optional<SessionStorageData> archivePrimaryIfNeeded()
	{
		if (isLegacyConfigStore() || maxPrimaryFileBytes <= 0L)
		{
			return Optional.empty();
		}

		File file = resolveFile();
		if (file == null || !file.exists() || file.length() <= maxPrimaryFileBytes)
		{
			return Optional.empty();
		}

		SessionStorageData data = load();
		return archiveOverflowIfNeeded(file, data) ? Optional.of(data) : Optional.empty();
	}

	private boolean writeDataToFile(File file, SessionStorageData data)
	{
		try
		{
			File parent = file.getParentFile();
			if (parent != null)
			{
				Files.createDirectories(parent.toPath());
			}

			File temp = File.createTempFile(file.getName(), ".tmp", parent);
			try (FileOutputStream out = new FileOutputStream(temp);
			     FileChannel channel = out.getChannel();
			     OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8))
			{
				data.setSchemaVersion(SessionStorageData.CURRENT_SCHEMA_VERSION);
				gson.toJson(data, writer);
				writer.flush();
				channel.force(true);
			}

			try
			{
				Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		}
		catch (IOException e)
		{
			log.warn("Failed to save session storage to {}", file, e);
			return false;
		}
	}

	private boolean archiveOverflowIfNeeded(File file, SessionStorageData data)
	{
		if (maxPrimaryFileBytes <= 0L || file.length() <= maxPrimaryFileBytes)
		{
			return false;
		}

		ArchiveSplit split = splitForArchive(data);
		if (split.archiveSessions.isEmpty())
		{
			log.warn("Session storage {} exceeds {} bytes, but no completed history can be archived", file, maxPrimaryFileBytes);
			return false;
		}

		boolean archived = false;
		while (!split.archiveSessions.isEmpty())
		{
			File archiveFile = nextArchiveFile(file);
			SessionStorageData archiveData = new SessionStorageData();
			archiveData.setSessions(split.archiveSessions);
			if (!writeDataToFile(archiveFile, archiveData))
			{
				log.warn("Could not archive old sessions to {}; keeping primary storage unchanged", archiveFile);
				return archived;
			}

			if (!writeDataToFile(file, split.primaryData))
			{
				log.warn("Archived old sessions to {}, but failed to rewrite primary storage {}", archiveFile, file);
				return archived;
			}
			copyStorageData(split.primaryData, data);
			archived = true;

			if (file.length() <= maxPrimaryFileBytes)
			{
				return true;
			}

			split = splitForArchive(split.primaryData);
		}

		if (file.length() > maxPrimaryFileBytes)
		{
			log.warn("Session storage {} still exceeds {} bytes after archiving all eligible history", file, maxPrimaryFileBytes);
		}
		return archived;
	}

	private void copyStorageData(SessionStorageData source, SessionStorageData target)
	{
		target.setSchemaVersion(source.getSchemaVersion());
		target.setSessions(new ArrayList<>(source.getSessions()));
		target.setCurrentSessionId(source.getCurrentSessionId());
		target.setHistoryLoaded(source.isHistoryLoaded());
	}

	private ArchiveSplit splitForArchive(SessionStorageData data)
	{
		List<Session> sessions = new ArrayList<>(data.getSessions());
		String currentRootId = findCurrentRootId(sessions, data.getCurrentSessionId());

		List<String> candidateRootIds = sessions.stream()
			.filter(session -> session != null && session.getMotherId() == null)
			.filter(session -> !session.isActive())
			.filter(session -> currentRootId == null || !currentRootId.equals(session.getId()))
			.sorted(Comparator.comparing(Session::getStart))
			.map(Session::getId)
			.collect(java.util.stream.Collectors.toList());

		if (candidateRootIds.isEmpty())
		{
			return new ArchiveSplit(data, List.of());
		}

		List<Session> retainedSessions = new ArrayList<>();
		List<Session> archivedSessions = new ArrayList<>();
		Set<String> archivedRootIds = new LinkedHashSet<>();
		for (String candidateRootId : candidateRootIds)
		{
			archivedRootIds.add(candidateRootId);
			retainedSessions.clear();
			archivedSessions.clear();
			for (Session session : sessions)
			{
				String rootId = rootId(session);
				if (rootId != null && archivedRootIds.contains(rootId))
				{
					archivedSessions.add(session);
				}
				else
				{
					retainedSessions.add(session);
				}
			}

			SessionStorageData retained = new SessionStorageData();
			retained.setSessions(new ArrayList<>(retainedSessions));
			retained.setCurrentSessionId(data.getCurrentSessionId());
			retained.setHistoryLoaded(data.isHistoryLoaded());
			if (serializedSize(retained) <= maxPrimaryFileBytes)
			{
				return new ArchiveSplit(retained, new ArrayList<>(archivedSessions));
			}
		}

		SessionStorageData retained = new SessionStorageData();
		retained.setSessions(new ArrayList<>(retainedSessions));
		retained.setCurrentSessionId(data.getCurrentSessionId());
		retained.setHistoryLoaded(data.isHistoryLoaded());
		return new ArchiveSplit(retained, new ArrayList<>(archivedSessions));
	}

	private String findCurrentRootId(List<Session> sessions, String currentSessionId)
	{
		if (currentSessionId == null)
		{
			return null;
		}
		for (Session session : sessions)
		{
			if (session != null && currentSessionId.equals(session.getId()))
			{
				return rootId(session);
			}
		}
		return null;
	}

	private String rootId(Session session)
	{
		if (session == null)
		{
			return null;
		}
		return session.getMotherId() == null ? session.getId() : session.getMotherId();
	}

	private long serializedSize(SessionStorageData data)
	{
		data.setSchemaVersion(SessionStorageData.CURRENT_SCHEMA_VERSION);
		return gson.toJson(data).getBytes(StandardCharsets.UTF_8).length;
	}

	private File nextArchiveFile(File primaryFile)
	{
		File parent = primaryFile.getParentFile();
		String baseName = archiveBaseName(primaryFile);
		int index = 1;
		File candidate;
		do
		{
			candidate = new File(parent, baseName + ".archive-" + String.format("%03d", index) + FILE_SUFFIX);
			index++;
		}
		while (candidate.exists());
		return candidate;
	}

	private String archiveBaseName(File primaryFile)
	{
		String name = primaryFile.getName();
		if (name.endsWith(FILE_SUFFIX))
		{
			return name.substring(0, name.length() - FILE_SUFFIX.length());
		}
		return name;
	}

	public boolean hasArchives()
	{
		File file = resolveFile();
		if (file == null)
		{
			return false;
		}
		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory())
		{
			return false;
		}
		String baseName = archiveBaseName(file);
		String prefix = baseName + ".archive-";
		File[] archiveFiles = parent.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(FILE_SUFFIX));
		return archiveFiles != null && archiveFiles.length > 0;
	}

	private static final class ArchiveSplit
	{
		private final SessionStorageData primaryData;
		private final List<Session> archiveSessions;

		private ArchiveSplit(SessionStorageData primaryData, List<Session> archiveSessions)
		{
			this.primaryData = primaryData;
			this.archiveSessions = archiveSessions;
		}
	}

	public void clearLegacySessionConfig(PluginConfig config)
	{
		if (configManager != null)
		{
			configManager.unsetConfiguration(PluginConfig.GROUP, PluginConfig.KEY_SESSIONS_JSON);
			configManager.unsetConfiguration(PluginConfig.GROUP, PluginConfig.KEY_CURRENT_SESSION_ID);
			configManager.unsetConfiguration(PluginConfig.GROUP, PluginConfig.KEY_HISTORY_LOADED);
			return;
		}

		config.sessionsJson("");
		config.currentSessionId("");
		config.historyLoaded(false);
	}

	public String describeLocation()
	{
		File file = resolveFile();
		return file == null ? "unresolved session storage" : file.toString();
	}

	private File resolveFile()
	{
		if (fixedFile != null)
		{
			return fixedFile;
		}
		if (configManager == null || configManager.getProfile() == null)
		{
			return null;
		}
		ConfigProfile profile = configManager.getProfile();
		File pluginDir = new File(RuneLite.RUNELITE_DIR, PLUGIN_DIR);
		return new File(pluginDir, sanitizeFileNamePart(profile.getName()) + "-" + profile.getId() + FILE_SUFFIX);
	}

	public boolean hasLegacyData(PluginConfig config)
	{
		String sessionsJson = config.sessionsJson();
		String currentSessionId = config.currentSessionId();
		return (sessionsJson != null && !sessionsJson.trim().isEmpty())
			|| (currentSessionId != null && !currentSessionId.trim().isEmpty())
			|| config.historyLoaded();
	}

	public boolean canMigrateLegacy(PluginConfig config)
	{
		String json = config.sessionsJson();
		if (json == null || json.trim().isEmpty())
		{
			return true;
		}
		try
		{
			gson.fromJson(json, Session[].class);
			return true;
		}
		catch (Exception e)
		{
			log.warn("Legacy session config JSON is invalid and will not be migrated", e);
			return false;
		}
	}

	public SessionStorageData loadLegacy(PluginConfig config)
	{
		SessionStorageData data = new SessionStorageData();
		String json = config.sessionsJson();
		if (json != null && !json.isEmpty())
		{
			try
			{
				Session[] arr = gson.fromJson(json, Session[].class);
				if (arr != null)
				{
					data.setSessions(Arrays.asList(arr));
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load sessions from legacy config JSON", e);
			}
		}
		data.setCurrentSessionId(emptyToNull(config.currentSessionId()));
		data.setHistoryLoaded(config.historyLoaded());
		return data;
	}

	private boolean saveLegacy(SessionStorageData data)
	{
		try
		{
			legacyConfig.sessionsJson(gson.toJson(data.getSessions().toArray(new Session[0])));
			legacyConfig.currentSessionId(nullToEmpty(data.getCurrentSessionId()));
			legacyConfig.historyLoaded(data.isHistoryLoaded());
			return true;
		}
		catch (Exception e)
		{
			log.warn("Failed to save sessions to legacy config", e);
			return false;
		}
	}
}
