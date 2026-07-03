package ru.playerreports.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
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
            getLogger().severe("PlaceholderAPI не найден.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        expansion = new ReportsExpansion(this);
        expansion.register();

        getLogger().info("PlaceholderAPI expansion registered: %playerreports_reports%");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
    }

    private static final class ReportsExpansion extends PlaceholderExpansion {
        private final PlayerReportsPAPIPlugin plugin;
        private long nextUpdate;
        private int cachedCount;
        private String lastStatus = "not-loaded";

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
            return handle(params);
        }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            return handle(params);
        }

        private String handle(String params) {
            if (params == null) return "";

            String key = params.toLowerCase(Locale.ROOT);

            if (key.equals("reports") || key.equals("open") || key.equals("total") || key.equals("count")) {
                return String.valueOf(getReportCount());
            }

            if (key.equals("status")) {
                getReportCount();
                return lastStatus;
            }

            return null;
        }

        private int getReportCount() {
            long now = System.currentTimeMillis();
            if (now < nextUpdate) {
                return cachedCount;
            }

            long cacheSeconds = plugin.getConfig().getLong("cache-seconds", 15);
            nextUpdate = now + Math.max(1, cacheSeconds) * 1000L;

            cachedCount = Math.max(0, resolveReportCount());
            return cachedCount;
        }

        private int resolveReportCount() {
            String pluginName = plugin.getConfig().getString("playerreports-plugin-name", "PlayerReports");
            Plugin reportsPlugin = Bukkit.getPluginManager().getPlugin(pluginName);

            if (reportsPlugin == null) {
                lastStatus = "playerreports-plugin-not-found:" + pluginName;
                return 0;
            }

            if (!reportsPlugin.isEnabled()) {
                lastStatus = "playerreports-disabled";
                return 0;
            }

            Integer reflected = tryFindCount(reportsPlugin, 0);
            if (reflected != null) {
                lastStatus = "ok:reflection";
                return reflected;
            }

            int files = countFallbackFiles(reportsPlugin.getDataFolder());
            lastStatus = "ok:file-fallback";
            return files;
        }

        private Integer tryFindCount(Object object, int depth) {
            if (object == null || depth > 3) return null;

            Integer direct = countValue(object, depth);
            if (direct != null) return direct;

            for (Method method : object.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;

                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!looksUseful(name)) continue;

                try {
                    method.setAccessible(true);
                    Object result = method.invoke(object);
                    Integer count = countValue(result, depth + 1);
                    if (count != null) return count;

                    Integer nested = tryFindCount(result, depth + 1);
                    if (nested != null) return nested;
                } catch (Throwable ignored) {
                }
            }

            for (Field field : object.getClass().getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!looksUseful(name)) continue;

                try {
                    field.setAccessible(true);
                    Object result = field.get(object);
                    Integer count = countValue(result, depth + 1);
                    if (count != null) return count;

                    Integer nested = tryFindCount(result, depth + 1);
                    if (nested != null) return nested;
                } catch (Throwable ignored) {
                }
            }

            return null;
        }

        private boolean looksUseful(String name) {
            return name.contains("report")
                    || name.contains("complaint")
                    || name.contains("storage")
                    || name.contains("database")
                    || name.contains("manager");
        }

        private Integer countValue(Object value, int depth) {
            if (value == null) return null;

            if (value instanceof Collection<?>) {
                return ((Collection<?>) value).size();
            }

            if (value instanceof Map<?, ?>) {
                return ((Map<?, ?>) value).size();
            }

            if (value.getClass().isArray()) {
                return Array.getLength(value);
            }

            return null;
        }

        private int countFallbackFiles(File dataFolder) {
            int total = 0;

            for (String folderName : plugin.getConfig().getStringList("fallback-report-folders")) {
                total += countReportFiles(new File(dataFolder, folderName));
            }

            return total;
        }

        private int countReportFiles(File file) {
            if (file == null || !file.exists()) return 0;

            if (file.isFile()) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json") ? 1 : 0;
            }

            File[] files = file.listFiles();
            if (files == null) return 0;

            int total = 0;
            for (File child : files) {
                total += countReportFiles(child);
            }

            return total;
        }
    }
}
