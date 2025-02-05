package dk.lockfuglsang.minecraft.file;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Common file-utilities.
 */
public enum FileUtil {
    ;
    private static final Logger log = Logger.getLogger(FileUtil.class.getName());
    private static final Collection<String> alwaysOverwrite = new ArrayList<>();
    private static final Collection<String> neverOverwrite = new ArrayList<>();
    private static final Map<String, FileConfiguration> configFiles = new ConcurrentHashMap<>();
    private static Locale locale = Locale.getDefault();
    private static File dataFolder;

    public static void setAlwaysOverwrite(String... configs) {
        for (String s : configs) {
            if (!alwaysOverwrite.contains(s)) {
                alwaysOverwrite.add(s);
            }
        }
    }

    public static void readConfig(FileConfiguration config, File file) {
        if (file == null) {
            log.log(Level.INFO, "No config file found, it will be created");
            return;
        }
        File configFile = file;
        File localeFile = new File(configFile.getParentFile(), getLocaleName(file.getName()));
        if (localeFile.exists() && localeFile.canRead()) {
            configFile = localeFile;
        }
        if (!configFile.exists()) {
            log.log(Level.INFO, "No " + configFile + " found, it will be created");
            return;
        }
        try (Reader rdr = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            config.load(rdr);
        } catch (InvalidConfigurationException e) {
            log.log(Level.SEVERE, "Unable to read config file " + configFile, e);
            if (configFile.exists()) {
                try {
                    Files.copy(Paths.get(configFile.toURI()), Paths.get(configFile.getParent(), configFile.getName() + ".err"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e1) {
                    // Ignore - we tried...
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to read config file " + configFile, e);
        }
    }

    public static void readConfig(FileConfiguration config, InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try (Reader rdr = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            config.load(rdr);
        } catch (InvalidConfigurationException | IOException e) {
            log.log(Level.SEVERE, "Unable to read configuration", e);
        }
    }

    public static String getBasename(String file) {
        String[] lastPart = file.split("([/\\\\])");
        file = lastPart[lastPart.length - 1];
        if (file != null && file.lastIndexOf('.') != -1) {
            return file.substring(0, file.lastIndexOf('.'));
        }
        return file;
    }

    public static String getExtension(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            return fileName.substring(getBasename(fileName).length() + 1);
        }
        return "";
    }

    private static File getDataFolder() {
        return dataFolder != null ? dataFolder : new File(".");
    }

    /**
     * System-encoding agnostic config-reader
     * Reads and returns the configuration found in:
     * <pre>
     *   a) the datafolder
     *
     *     a.1) if a config named "config_en.yml" exists - that is read.
     *
     *     a.2) otherwise "config.yml" is read (created if need be).
     *
     *   b) if the version differs from the same resource on the classpath
     *
     *      b.1) all nodes in the jar-file-version is merged* into the local-file
     *
     *      b.2) unless the configName is in the allwaysOverwrite - then the jar-version wins
     *
     * *merged: using data-conversion of special nodes.
     * </pre>
     */
    public static FileConfiguration getYmlConfiguration(String configName) {
        // Caching, for your convenience! (and a bigger memory print!)

        if (!configFiles.containsKey(configName)) {
            FileConfiguration config = new YamlConfiguration();
            try {
                // read from datafolder!
                File configFile = getConfigFile(configName);
                YamlConfiguration configJar = new YamlConfiguration();
                readConfig(config, configFile);
                readConfig(configJar, getResource(configName));
                if (!configFile.exists() || config.getInt("version", 0) < configJar.getInt("version", 0)) {
                    if (configFile.exists()) {
                        if (neverOverwrite.contains(configName)) {
                            configFiles.put(configName, config);
                            return config;
                        }
                        File backupFolder = new File(getDataFolder(), "backup");
                        backupFolder.mkdirs();
                        String bakFile = String.format("%1$s-%2$tY%2$tm%2$td-%2$tH%2$tM.yml", getBasename(configName), new Date());
                        log.log(Level.INFO, "Moving existing config " + configName + " to backup/" + bakFile);
                        Files.move(Paths.get(configFile.toURI()),
                            Paths.get(new File(backupFolder, bakFile).toURI()),
                            StandardCopyOption.REPLACE_EXISTING);
                        if (alwaysOverwrite.contains(configName)) {
                            FileUtil.copy(getResource(configName), configFile);
                            config = configJar;
                        } else {
                            config = mergeConfig(configJar, config);
                            config.save(configFile);
                            config.load(configFile);
                        }
                    } else {
                        config = mergeConfig(configJar, config);
                        config.save(configFile);
                        config.load(configFile);
                    }
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unable to handle config-file " + configName, e);
            }
            configFiles.put(configName, config);
        }
        return configFiles.get(configName);
    }

    private static InputStream getResource(String configName) {
        String resourceName = getLocaleName(configName);
        ClassLoader loader = FileUtil.class.getClassLoader();
        InputStream resourceAsStream = loader.getResourceAsStream(resourceName);
        if (resourceAsStream != null) {
            return resourceAsStream;
        }
        return loader.getResourceAsStream(configName);
    }

    private static String getLocaleName(String fileName) {
        String baseName = getBasename(fileName);
        return baseName + "_" + locale + fileName.substring(baseName.length());
    }

    public static File getConfigFile(String configName) {
        File file = new File(getDataFolder(), getLocaleName(configName));
        if (file.exists()) {
            return file;
        }
        return new File(getDataFolder(), configName);
    }

    public static void copy(InputStream stream, File file) throws IOException {
        if (stream == null || file == null) {
            throw new IOException("Invalid resource for " + file);
        }
        Files.copy(stream, Paths.get(file.toURI()), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Merges the important keys from src to destination.
     *
     * @param src  The source (containing the new values).
     * @param dest The destination (containing old-values).
     */
    private static FileConfiguration mergeConfig(FileConfiguration src, FileConfiguration dest) {
        int existing = dest.getInt("version");
        int version = src.getInt("version", existing);
        dest.setDefaults(src);
        dest.options().copyDefaults(true);
        dest.set("version", version);
        removeExcludes(dest);
        moveNodes(src, dest);
        replaceDefaults(src, dest);
        return dest;
    }

    /**
     * Removes nodes from dest.defaults, that are specifically excluded in the config
     */
    private static void removeExcludes(FileConfiguration dest) {
        List<String> keys = dest.getStringList("merge-ignore");
        for (String key : keys) {
            requireNonNull(dest.getDefaults()).set(key, null);
        }
    }

    private static void replaceDefaults(FileConfiguration src, FileConfiguration dest) {
        ConfigurationSection forceSection = src.getConfigurationSection("force-replace");
        if (forceSection != null) {
            for (String key : forceSection.getKeys(true)) {
                Object def = forceSection.get(key, null);
                Object value = dest.get(key, def);
                Object newDef = src.get(key, null);
                if (def != null && def.equals(value)) {
                    dest.set(key, newDef);
                }
            }
        }
        dest.set("force-replace", null);
        requireNonNull(dest.getDefaults()).set("force-replace", null);
    }

    private static void moveNodes(FileConfiguration src, FileConfiguration dest) {
        ConfigurationSection moveSection = src.getConfigurationSection("move-nodes");
        if (moveSection != null) {
            List<String> keys = new ArrayList<>(moveSection.getKeys(true));
            Collections.reverse(keys); // Depth first
            for (String key : keys) {
                if (moveSection.isString(key)) {
                    String srcPath = key;
                    String tgtPath = moveSection.getString(key, key);
                    Object value = dest.get(srcPath);
                    if (value != null) {
                        dest.set(tgtPath, value);
                        dest.set(srcPath, null);
                    }
                } else if (moveSection.isConfigurationSection(key)) {
                    // Check to see if dest section should be nuked...
                    if (dest.isConfigurationSection(key) && dest.getConfigurationSection(key).getKeys(false).isEmpty()) {
                        dest.set(key, null);
                    }
                }
            }
        }
        dest.set("move-nodes", null);
        requireNonNull(dest.getDefaults()).set("move-nodes", null);
    }

    public static void setDataFolder(File dataFolder) {
        FileUtil.dataFolder = dataFolder;
        configFiles.clear();
    }

    public static void setLocale(Locale loc) {
        locale = loc != null ? loc : locale;
    }

    public static void reload() {
        for (Map.Entry<String, FileConfiguration> e : configFiles.entrySet()) {
            File configFile = new File(getDataFolder(), e.getKey());
            readConfig(e.getValue(), configFile);
        }
    }
}
