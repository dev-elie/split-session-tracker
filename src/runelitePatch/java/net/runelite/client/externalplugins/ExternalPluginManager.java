package net.runelite.client.externalplugins;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

/**
 * Dev-launch replacement for RuneLite's external plugin manager.
 *
 * <p>RuneLite 1.12.28 schedules installed-plugin reporting from the upstream
 * constructor. When the initial random delay is zero, that task can read config
 * before {@code ConfigManager.load()}, producing a startup NPE. The external
 * plugin hub is not needed for this project's {@code ./gradlew run} path; the
 * launcher only needs builtin external plugin loading.</p>
 */
@Singleton
public class ExternalPluginManager
{
	private static Class<? extends Plugin>[] builtinExternals;

	private final PluginManager pluginManager;

	@Inject
	private ExternalPluginManager(PluginManager pluginManager)
	{
		this.pluginManager = pluginManager;
	}

	public void loadExternalPlugins() throws PluginInstantiationException
	{
		if (builtinExternals != null)
		{
			pluginManager.loadPlugins(Lists.newArrayList(builtinExternals), null);
		}
	}

	public List<String> getInstalledExternalPlugins()
	{
		return Collections.emptyList();
	}

	public void install(String key)
	{
	}

	public void remove(String key)
	{
	}

	public void update()
	{
	}

	public static PluginHubManifest.JarData getJarData(Class<? extends Plugin> plugin)
	{
		return null;
	}

	public static PluginHubManifest.DisplayData getDisplayData(Class<? extends Plugin> plugin)
	{
		return null;
	}

	public static String getInternalName(Class<? extends Plugin> plugin)
	{
		return plugin.getName();
	}

	@SafeVarargs
	public static void loadBuiltin(Class<? extends Plugin>... plugins)
	{
		builtinExternals = plugins;
	}
}
