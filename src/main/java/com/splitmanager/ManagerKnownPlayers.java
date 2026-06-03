package com.splitmanager;

import com.google.gson.Gson;
import com.splitmanager.utils.InstantTypeAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages known players and alt-to-main relationships persisted through plugin config.
 */
@Singleton
@Slf4j
public class ManagerKnownPlayers
{
	private final Gson gson;
	private final PluginConfig config;
	private final Set<String> knownPlayers = new LinkedHashSet<>();
	private final Map<String, String> altMainMapping = new LinkedHashMap<>();

	@Inject
	public ManagerKnownPlayers(PluginConfig config, Gson gson)
	{
		this.config = config;
		// Use the client's injected Gson and customize via newBuilder per PluginHub guidelines
		this.gson = gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
	}

	public void loadFromConfig()
	{
		knownPlayers.clear();
		String csv = config.knownPlayersCsv();
		if (csv != null && !csv.isEmpty())
		{
			for (String p : csv.split(","))
			{
				String t = p.trim();
				if (!t.isEmpty())
				{
					addKnownPlayerFromConfig(t);
				}
			}
		}

		altMainMapping.clear();
		String altsJson = config.altsJson();
		if (altsJson != null && !altsJson.isEmpty())
		{
			try
			{
				Map<?, ?> m = gson.fromJson(altsJson, Map.class);
				if (m != null)
				{
					for (Map.Entry<?, ?> entry : m.entrySet())
					{
						if (entry.getKey() instanceof String && entry.getValue() instanceof String)
						{
							altMainMapping.put((String) entry.getKey(), (String) entry.getValue());
						}
					}
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to parse alt mappings from config", e);
			}
		}
	}

	public void saveToConfig()
	{
		config.knownPlayersCsv(String.join(",", knownPlayers));
		try
		{
			config.altsJson(gson.toJson(altMainMapping));
		}
		catch (Exception e)
		{
			log.warn("Failed to save alt mappings to config", e);
		}
	}

	public Set<String> getKnownPlayers()
	{
		return Collections.unmodifiableSet(knownPlayers);
	}

	public Map<String, String> getAltMainMapping()
	{
		return Collections.unmodifiableMap(altMainMapping);
	}

	/**
	 * Parse the alt name from a selected entry.
	 */
	public String parseAltFromEntry(String selectedEntry)
	{
		if (selectedEntry == null)
		{
			return "";
		}
		if (selectedEntry.contains(" is an alt of "))
		{
			String[] parts = selectedEntry.split(" is an alt of ", 2);
			if (parts.length == 2)
			{
				return parts[0].trim();
			}
		}
		return selectedEntry.trim();
	}

	/**
	 * @return unmodifiable set of known players that are mains (exclude names mapped as alts).
	 */
	public Set<String> getKnownMains()
	{
		LinkedHashSet<String> mains = knownPlayers.stream()
			.filter(p -> !isAlt(p))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return Collections.unmodifiableSet(mains);
	}

	/**
	 * Validate whether an alt may be linked to a main.
	 * Rules:
	 * - alt and main must be non-empty and not equal (case-insensitive)
	 * - the chosen main cannot itself be an alt
	 * - the alt cannot already point to a different main
	 * - the alt cannot be someone else's main (prevents cycles)
	 *
	 * @return true if the link is allowed
	 */
	public boolean canLinkAltToMain(String alt, String main)
	{
		if (alt == null || main == null)
		{
			return false;
		}
		String a = alt.trim();
		String m = main.trim();
		if (a.isEmpty() || m.isEmpty())
		{
			return false;
		}
		if (a.equalsIgnoreCase(m))
		{
			return false;
		}
		if (isAlt(m))
		{
			return false;
		}
		String existingMain = getMappedMain(a);
		if (existingMain != null && !existingMain.equalsIgnoreCase(m))
		{
			return false;
		}
		for (Map.Entry<String, String> e : altMainMapping.entrySet())
		{
			if (e.getValue() != null && e.getValue().equalsIgnoreCase(a))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Attempt to persist an alt->main link, enforcing the validation from canLinkAltToMain().
	 * Adds both names to the known list on success.
	 *
	 * @return true if the mapping either already existed (same) or was created
	 */
	public boolean trySetAltMain(String alt, String main)
	{
		if (!canLinkAltToMain(alt, main))
		{
			return false;
		}
		String a = alt.trim();
		String m = main.trim();
		String existingMain = getMappedMain(a);
		if (existingMain != null && existingMain.equalsIgnoreCase(m))
		{
			return true;
		}
		altMainMapping.put(a, m);
		knownPlayers.add(a);
		knownPlayers.add(m);
		saveToConfig();
		return true;
	}

	/**
	 * Unlink the given alt from its main.
	 *
	 * @param alt the alt name to unlink
	 * @return true if an existing mapping was removed
	 */
	public boolean unlinkAlt(String alt)
	{
		if (alt == null || alt.trim().isEmpty())
		{
			return false;
		}
		String a = alt.trim();
		String existingAlt = findMappedAltKey(a);
		if (existingAlt == null)
		{
			return false;
		}
		altMainMapping.remove(existingAlt);
		saveToConfig();
		return true;
	}

	/**
	 * Resolve a display name to its main account by following the alt->main mapping.
	 * If the name is not an alt, returns the original trimmed name. Protects against cycles.
	 *
	 * @param name main or alt name
	 * @return resolved main name (or the input trimmed if not an alt)
	 */
	public String getMainName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim();
		if (n.isEmpty())
		{
			return null;
		}
		String visited = null;
		// resolve chain up to a few steps to avoid cycles
		for (int i = 0; i < 5; i++)
		{
			String m = getMappedMain(n);
			if (m == null || m.equalsIgnoreCase(n))
			{
				return n;
			}
			if (visited != null && visited.equalsIgnoreCase(m))
			{
				break;
			}
			visited = n;
			n = m;
		}
		return n;
	}

	/**
	 * @param name player name
	 * @return true if the given name is present as a key in the alt->main mapping
	 */
	public boolean isAlt(String name)
	{
		if (name == null)
		{
			return false;
		}
		return findMappedAltKey(name.trim()) != null;
	}

	/**
	 * List all alts currently linked to the given main.
	 *
	 * @param main main account name
	 * @return sorted list of alt names linked to this main (case-insensitive compare)
	 */
	public List<String> getAltsOf(String main)
	{
		if (main == null || main.isBlank())
		{
			return List.of();
		}
		String m = main.trim();
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> e : altMainMapping.entrySet())
		{
			if (e.getValue() != null && e.getValue().equalsIgnoreCase(m))
			{
				out.add(e.getKey());
			}
		}
		out.sort(String::compareToIgnoreCase);
		return out;
	}


	/**
	 * Add a player name to the known-players list.
	 *
	 * @param name display name
	 * @return true if added
	 */
	public boolean addKnownPlayer(String name)
	{
		String normalized = name == null ? null : name.trim();
		if (normalized == null || normalized.isEmpty())
		{
			return false;
		}
		if (knownPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(normalized)))
		{
			return false;
		}
		boolean added = knownPlayers.add(normalized);
		if (added)
		{
			saveToConfig();
		}
		return added;
	}

	private void addKnownPlayerFromConfig(String name)
	{
		if (knownPlayers.stream().noneMatch(p -> p.equalsIgnoreCase(name)))
		{
			knownPlayers.add(name);
		}
	}

	public boolean isKnownPlayer(String name)
	{
		return isKnownPlayer(name, false);
	}

	public boolean isKnownPlayer(String name, boolean save)
	{
		if (name == null || name.trim().isEmpty())
		{
			return false;
		}

		String normalized = name.trim();
		boolean known = knownPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(normalized));

		if (save && !known)
		{
			addKnownPlayer(normalized);
			return true;
		}

		return known;
	}

	public boolean removeKnownPlayer(String name)
	{
		String n = name == null ? null : name.trim();
		if (n == null || n.isEmpty())
		{
			return false;
		}
		String knownName = knownPlayers.stream()
			.filter(p -> p.equalsIgnoreCase(n))
			.findFirst()
			.orElse(null);
		boolean rem = knownName != null && knownPlayers.remove(knownName);
		String mappedAlt = findMappedAltKey(n);
		boolean removedAlt = mappedAlt != null && altMainMapping.remove(mappedAlt) != null;
		boolean removedMainLinks = altMainMapping.entrySet().removeIf(e -> e.getValue() != null && e.getValue().equalsIgnoreCase(n));

		if (rem || removedAlt || removedMainLinks)
		{
			saveToConfig();
		}
		return rem;
	}

	private String getMappedMain(String alt)
	{
		String key = findMappedAltKey(alt);
		return key == null ? null : altMainMapping.get(key);
	}

	private String findMappedAltKey(String alt)
	{
		if (alt == null)
		{
			return null;
		}
		for (String key : altMainMapping.keySet())
		{
			if (key != null && key.equalsIgnoreCase(alt))
			{
				return key;
			}
		}
		return null;
	}

	public void init()
	{
		loadFromConfig();
	}
}
