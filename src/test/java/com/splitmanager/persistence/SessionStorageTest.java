package com.splitmanager.persistence;

import com.google.gson.Gson;
import com.splitmanager.models.SplitEvent;
import com.splitmanager.models.Session;
import com.splitmanager.utils.InstantTypeAdapter;
import java.io.File;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

public class SessionStorageTest
{
	private final Gson gson = new Gson().newBuilder()
		.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
		.create();

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void savesAndLoadsVersionedSessionData() throws Exception
	{
		File file = temporaryFolder.newFile("sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);
		Session session = new Session("session-id", Instant.EPOCH, null);
		SessionStorageData data = new SessionStorageData();
		data.setCurrentSessionId("session-id");
		data.setHistoryLoaded(true);
		data.setSessions(Collections.singletonList(session));

		assertTrue(storage.save(data));

		SessionStorageData restored = storage.load();
		assertEquals(SessionStorageData.CURRENT_SCHEMA_VERSION, restored.getSchemaVersion());
		assertEquals("session-id", restored.getCurrentSessionId());
		assertTrue(restored.isHistoryLoaded());
		assertEquals(1, restored.getSessions().size());
		assertEquals("session-id", restored.getSessions().get(0).getId());
	}

	@Test
	public void loadsSchemaOneEventsFromLegacyKillsFieldAndSavesEventsField() throws Exception
	{
		File file = temporaryFolder.newFile("legacy-sessions.json");
		String legacyJson = "{"
			+ "\"schemaVersion\":1,"
			+ "\"currentSessionId\":\"session-id\","
			+ "\"historyLoaded\":true,"
			+ "\"sessions\":[{"
			+ "\"id\":\"session-id\","
			+ "\"start\":\"1970-01-01T00:00:00Z\","
			+ "\"players\":[\"Alice\"],"
			+ "\"kills\":[{"
			+ "\"sessionId\":\"session-id\","
			+ "\"at\":\"1970-01-01T00:00:01Z\","
			+ "\"player\":\"Alice\","
			+ "\"amount\":123"
			+ "}]"
			+ "}]"
			+ "}";
		Files.write(file.toPath(), Collections.singletonList(legacyJson), StandardCharsets.UTF_8);
		SessionStorage storage = new SessionStorage(file, gson);

		SessionStorageData restored = storage.load();

		assertEquals(1, restored.getSessions().size());
		Session restoredSession = restored.getSessions().get(0);
		assertEquals(1, restoredSession.getEvents().size());
		SplitEvent restoredEvent = restoredSession.getEvents().get(0);
		assertEquals("Alice", restoredEvent.getPlayer());
		assertEquals(123L, restoredEvent.getAmount().longValue());

		assertTrue(storage.save(restored));
		String savedJson = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		assertTrue(savedJson.contains("\"schemaVersion\":2"));
		assertTrue(savedJson.contains("\"events\""));
		assertFalse(savedJson.contains("\"kills\":"));
	}

	@Test
	public void missingFileReturnsEmptyData()
	{
		File file = new File(temporaryFolder.getRoot(), "missing/sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);

		assertFalse(storage.exists());
		SessionStorageData data = storage.load();

		assertEquals(SessionStorageData.CURRENT_SCHEMA_VERSION, data.getSchemaVersion());
		assertTrue(data.getSessions().isEmpty());
		assertNull(data.getCurrentSessionId());
		assertFalse(data.isHistoryLoaded());
	}

	@Test
	public void invalidJsonReturnsEmptyData() throws Exception
	{
		File file = temporaryFolder.newFile("sessions.json");
		Files.write(file.toPath(), Collections.singletonList("{not valid json"), StandardCharsets.UTF_8);
		SessionStorage storage = new SessionStorage(file, gson);

		SessionStorageData data = storage.load();

		assertTrue(data.getSessions().isEmpty());
		assertNull(data.getCurrentSessionId());
	}

	@Test
	public void saveCreatesParentDirectories()
	{
		File file = new File(temporaryFolder.getRoot(), "nested/sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);

		assertTrue(storage.save(new SessionStorageData()));
		assertTrue(file.exists());
	}

	@Test
	public void profileBackedStorageSanitizesProfileNameForFilePath()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		ConfigProfile profile = mock(ConfigProfile.class);
		when(configManager.getProfile()).thenReturn(profile);
		when(profile.getName()).thenReturn("../escaped/profile");
		when(profile.getId()).thenReturn(123L);

		SessionStorage storage = new SessionStorage(configManager, gson);

		Path pluginDir = new File(RuneLite.RUNELITE_DIR, "auto-split-manager").toPath().toAbsolutePath().normalize();
		Path storagePath = new File(storage.describeLocation()).toPath().toAbsolutePath().normalize();
		assertTrue(storagePath.startsWith(pluginDir));
		assertFalse(storagePath.toString().contains(".."));
	}
}
