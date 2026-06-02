package com.splitmanager.persistence;

import com.google.gson.Gson;
import com.splitmanager.models.Session;
import com.splitmanager.utils.InstantTypeAdapter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
	public void missingFileReturnsEmptyData()
	{
		File file = new File(temporaryFolder.getRoot(), "missing/sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);

		assertFalse(storage.exists());
		SessionStorageData data = storage.load();

		assertEquals(SessionStorageData.CURRENT_SCHEMA_VERSION, data.getSchemaVersion());
		assertTrue(data.getSessions().isEmpty());
		assertEquals(null, data.getCurrentSessionId());
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
		assertEquals(null, data.getCurrentSessionId());
	}

	@Test
	public void saveCreatesParentDirectories()
	{
		File file = new File(temporaryFolder.getRoot(), "nested/sessions.json");
		SessionStorage storage = new SessionStorage(file, gson);

		assertTrue(storage.save(new SessionStorageData()));
		assertTrue(file.exists());
	}
}
