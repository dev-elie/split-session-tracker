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
import java.util.Arrays;
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

	private final ConfigManager configManager;
	private final File fixedFile;
	private final Gson gson;
	private final PluginConfig legacyConfig;

	@Inject
	public SessionStorage(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.fixedFile = null;
		this.legacyConfig = null;
		this.gson = buildGson(gson);
	}

	public SessionStorage(File fixedFile, Gson gson)
	{
		this.configManager = null;
		this.fixedFile = fixedFile;
		this.legacyConfig = null;
		this.gson = buildGson(gson);
	}

	private SessionStorage(PluginConfig legacyConfig, Gson gson)
	{
		this.configManager = null;
		this.fixedFile = null;
		this.legacyConfig = legacyConfig;
		this.gson = buildGson(gson);
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
		return new File(pluginDir, profile.getName() + "-" + profile.getId() + FILE_SUFFIX);
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
