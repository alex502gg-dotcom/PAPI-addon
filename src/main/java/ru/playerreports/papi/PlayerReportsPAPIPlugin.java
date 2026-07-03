package ru.playerreports.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class PlayerReportsExpansion extends PlaceholderExpansion {

    private long nextUpdate;
    private int cachedReports;

    @Override
    public String getIdentifier() {
        return "playerreports";
    }

    @Override
    public String getAuthor() {
        return "Codex";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return Bukkit.getPluginManager().getPlugin("PlayerReports") != null;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }

        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("reports") || key.equals("count") || key.equals("total") || key.equals("open")) {
            return String.valueOf(getReportsCount());
        }

        if (key.equals("status")) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerReports");
            return plugin == null ? "PlayerReports not found" : "OK";
        }

        return null;
    }

    private int getReportsCount() {
        long now = System.currentTimeMillis();

        if (now < nextUpdate) {
            return cachedReports;
        }

        nextUpdate = now + 15000L;

        Plugin playerReports = Bukkit.getPluginManager().getPlugin("PlayerReports");

        if (playerReports == null || !playerReports.isEnabled()) {
            cachedReports = 0;
            return cachedReports;
        }

        Integer apiCount = findReportsCount(playerReports, 0);

        if (apiCount != null) {
            cachedReports = Math.max(0, apiCount);
            return cachedReports;
        }

        cachedReports = countReportFiles(playerReports.getDataFolder());
        return cachedReports;
    }

    private Integer findReportsCount(Object object, int depth) {
        if (object == null || depth > 4) {
            return null;
        }

        Integer directCount = countValue(object);

        if (directCount != null) {
            return directCount;
        }

        for (Method method : object.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0) {
                continue;
            }

            String name = method.getName().toLowerCase(Locale.ROOT);

            if (!isReportsRelated(name)) {
                continue;
            }

            try {
                method.setAccessible(true);

                Object result = method.invoke(object);
                Integer count = countValue(result);

                if (count != null) {
                    return count;
                }

                Integer nested = findReportsCount(result, depth + 1);

                if (nested != null) {
                    return nested;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : object.getClass().getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);

            if (!isReportsRelated(name)) {
                continue;
            }

            try {
                field.setAccessible(true);

                Object result = field.get(object);
                Integer count = countValue(result);

                if (count != null) {
                    return count;
                }

                Integer nested = findReportsCount(result, depth + 1);

                if (nested != null) {
                    return nested;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private boolean isReportsRelated(String name) {
        return name.contains("report")
                || name.contains("complaint")
                || name.contains("storage")
                || name.contains("database")
                || name.contains("manager");
    }

    private Integer countValue(Object value) {
        if (value == null) {
            return null;
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

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return null;
    }

    private int countReportFiles(File playerReportsFolder) {
        int total = 0;

        total += countFiles(new File(playerReportsFolder, "reports"));
        total += countFiles(new File(playerReportsFolder, "data"));

        return total;
    }

    private int countFiles(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }

        if (file.isFile()) {
            String name = file.getName().toLowerCase(Locale.ROOT);

            if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json")) {
                return 1;
            }

            return 0;
        }

        File[] files = file.listFiles();

        if (files == null) {
            return 0;
        }

        int total = 0;

        for (File child : files) {
            total += countFiles(child);
        }

        return total;
    }
}
