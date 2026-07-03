package ru.playerreports.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class PlayerReportsPAPIPlugin extends JavaPlugin {
    private ReportsExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI not found. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        expansion = new ReportsExpansion(this);
        expansion.register();
        getLogger().info("Registered PlaceholderAPI placeholders.");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
    }

    private static final class ReportsExpansion extends PlaceholderExpansion {
        private final PlayerReportsPAPIPlugin plugin;
        private long nextRefreshAt;
        private int cachedCount;

        private ReportsExpansion(PlayerReportsPAPIPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return plugin.getConfig().getString("placeholder-identifier", "playerreports");
        }

        @Override
        public String getAuthor() {
            return "Codex";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
            if (key.equals("reports") || key.equals("open") || key.equals("total") || key.equals("count")) {
                return Integer.toString(getReportCount());
            }
            return null;
        }

        private int getReportCount() {
            long now = System.currentTimeMillis();
            if (now < nextRefreshAt) {
                return cachedCount;
            }

            FileConfiguration config = plugin.getConfig();
            long cacheMillis = Math.max(1L, config.getLong("cache-seconds", 15L)) * 1000L;
            nextRefreshAt = now + cacheMillis;
            cachedCount = Math.max(0, resolveReportCount());
            return cachedCount;
        }

        private int resolveReportCount() {
            Plugin reportsPlugin = Bukkit.getPluginManager().getPlugin(
                    plugin.getConfig().getString("playerreports-plugin-name", "PlayerReports")
            );

            if (reportsPlugin != null && reportsPlugin.isEnabled()) {
                Integer reflected = tryReflectCount(reportsPlugin);
                if (reflected != null) {
                    return reflected;
                }
            }

            return countFallbackFiles(reportsPlugin);
        }

        private Integer tryReflectCount(Object target) {
            Integer direct = tryCountFromMembers(target, 0);
            if (direct != null) {
                return direct;
            }

            for (Method method : target.getClass().getMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }

                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("report") && !name.contains("complaint")) {
                    continue;
                }

                try {
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    Integer count = countValue(value, 1);
                    if (count != null) {
                        return count;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next possible PlayerReports implementation shape.
                }
            }

            return null;
        }

        private Integer tryCountFromMembers(Object target, int depth) {
            if (target == null || depth > 2) {
                return null;
            }

            for (Method method : target.getClass().getMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }

                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!looksLikeReportAccessor(name)) {
                    continue;
                }

                try {
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    Integer count = countValue(value, depth + 1);
                    if (count != null) {
                        return count;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep scanning.
                }
            }

            for (Field field : target.getClass().getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!looksLikeReportAccessor(name)) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Integer count = countValue(field.get(target), depth + 1);
                    if (count != null) {
                        return count;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep scanning.
                }
            }

            return null;
        }

        private boolean looksLikeReportAccessor(String name) {
            return name.contains("report")
                    || name.contains("complaint")
                    || name.equals("getstorage")
                    || name.equals("getdatabase")
                    || name.equals("getmanager");
        }

        private Integer countValue(Object value, int depth) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof Collection<?>) {
                return ((Collection<?>) value).size();
            }
            if (value instanceof Map<?, ?>) {
                return ((Map<?, ?>) value).size();
            }
            if (value.getClass().isArray()) {
                return Array.getLength(value);
            }
            return tryCountFromMembers(value, depth);
        }

        private int countFallbackFiles(Plugin reportsPlugin) {
            File dataFolder = reportsPlugin != null
                    ? reportsPlugin.getDataFolder()
                    : new File(plugin.getDataFolder().getParentFile(), plugin.getConfig().getString("playerreports-plugin-name", "PlayerReports"));

            int total = 0;
            for (String folderName : plugin.getConfig().getStringList("fallback-report-folders")) {
                File folder = new File(dataFolder, folderName);
                total += countReportFiles(folder);
            }
            return total;
        }

        private int countReportFiles(File file) {
            if (file == null || !file.exists()) {
                return 0;
            }
            if (file.isFile()) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                return name.endsWith(".yml")
                        || name.endsWith(".yaml")
                        || name.endsWith(".json") ? 1 : 0;
            }

            File[] children = file.listFiles();
            if (children == null) {
                return 0;
            }

            int total = 0;
            for (File child : children) {
                total += countReportFiles(child);
            }
            return total;
        }
    }
}
