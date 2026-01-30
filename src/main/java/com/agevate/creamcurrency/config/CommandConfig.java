package com.agevate.creamcurrency.config;

import com.agevate.creamcurrency.CreamCurrency;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Manages command configuration including subcommand aliases.
 */
public class CommandConfig {

    private final CreamCurrency plugin;
    private FileConfiguration config;

    // Subcommand type -> list of valid aliases (including the main name)
    private final Map<SubCommand, Set<String>> aliases = new EnumMap<>(SubCommand.class);

    // Top command messages
    private String topHeader;
    private String topEntry;
    private String topEmpty;
    private final Map<Integer, String> topColors = new HashMap<>();
    private String topLoading;
    private String topTotal;
    private String topPage;
    private String topDefaultColor;

    public enum SubCommand {
        BALANCE, PAY, TOP, GIVE, SET, REMOVE, TOGGLE
    }

    public CommandConfig(CreamCurrency plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        // Save default if not exists
        File file = new File(plugin.getDataFolder(), "commands.yml");
        if (!file.exists()) {
            plugin.saveResource("commands.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        loadAliases();
        loadMessages();

        plugin.getLogger().info("Command configuration loaded.");
    }

    private void loadAliases() {
        aliases.clear();

        ConfigurationSection subcommands = config.getConfigurationSection("subcommands");
        if (subcommands == null) {
            // Use defaults
            setDefaultAliases();
            return;
        }

        for (SubCommand cmd : SubCommand.values()) {
            Set<String> aliasSet = new HashSet<>();
            String cmdName = cmd.name().toLowerCase();
            aliasSet.add(cmdName); // Always include the main name

            ConfigurationSection cmdSection = subcommands.getConfigurationSection(cmdName);
            if (cmdSection != null) {
                List<String> configAliases = cmdSection.getStringList("aliases");
                for (String alias : configAliases) {
                    aliasSet.add(alias.toLowerCase());
                }
            }

            aliases.put(cmd, aliasSet);
        }
    }

    private void setDefaultAliases() {
        aliases.put(SubCommand.BALANCE, Set.of("balance", "bal", "b"));
        aliases.put(SubCommand.PAY, Set.of("pay", "transfer", "ver"));
        aliases.put(SubCommand.TOP, Set.of("top", "baltop", "leaderboard"));
        aliases.put(SubCommand.GIVE, Set.of("give", "add", "ekle"));
        aliases.put(SubCommand.SET, Set.of("set", "ayarla"));
        aliases.put(SubCommand.REMOVE, Set.of("remove", "take", "cikar"));
        aliases.put(SubCommand.TOGGLE, Set.of("toggle", "kapasit", "ac"));
    }

    private void loadMessages() {
        ConfigurationSection messages = config.getConfigurationSection("command-messages");
        if (messages == null) {
            topHeader = "&e&l----- %currency% Top %limit% -----";
            topEntry = "%color%#%rank% &f%player% &8- &a%balance%";
            topEmpty = "&7No data found.";
            topLoading = "&7Loading...";
            topTotal = "&7Total Economy: &a%amount%";
            topPage = "&7Page: %page%";
            topDefaultColor = "&7";
            topColors.put(1, "&6&l");
            topColors.put(2, "&f&l");
            topColors.put(3, "&c&l");
            return;
        }

        topHeader = messages.getString("top-header", "&e&l----- %currency% Top %limit% -----");
        topEntry = messages.getString("top-entry", "%color%#%rank% &f%player% &8- &a%balance%");
        topEmpty = messages.getString("top-empty", "&7No data found.");
        topLoading = messages.getString("top-loading", "&7Loading...");
        topTotal = messages.getString("top-total", "&7Total Economy: &a%amount%");
        topPage = messages.getString("top-page", "&7Page: %page%");

        ConfigurationSection colors = messages.getConfigurationSection("top-colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                if (key.equalsIgnoreCase("default")) {
                    topDefaultColor = colors.getString(key, "&7");
                } else {
                    try {
                        int rank = Integer.parseInt(key);
                        topColors.put(rank, colors.getString(key));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (topDefaultColor == null)
            topDefaultColor = "&7";
    }

    /**
     * Checks if the given input matches a subcommand (including aliases).
     */
    public SubCommand matchSubCommand(String input) {
        String lower = input.toLowerCase();
        for (Map.Entry<SubCommand, Set<String>> entry : aliases.entrySet()) {
            if (entry.getValue().contains(lower)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets all valid aliases for tab completion.
     */
    public List<String> getTabCompletions(boolean isAdmin) {
        List<String> completions = new ArrayList<>();
        // Only show basic commands in tab completion as requested
        completions.add("balance");
        completions.add("pay");
        completions.add("top");
        completions.add("toggle");

        return completions;
    }

    // Getters for top messages
    public String getTopHeader() {
        return topHeader;
    }

    public String getTopEntry() {
        return topEntry;
    }

    public String getTopEmpty() {
        return topEmpty;
    }

    public String getTopLoading() {
        return topLoading;
    }

    public String getTopTotal() {
        return topTotal;
    }

    public String getTopPage() {
        return topPage;
    }

    public String getTopColor(int rank) {
        return topColors.getOrDefault(rank, topDefaultColor);
    }
}
