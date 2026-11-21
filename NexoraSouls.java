package com.nexora.souls;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class NexoraSouls extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NexoraSouls ativado!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Não foi possível criar a pasta do plugin.");
        }
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    getLogger().warning("Não foi possível criar data.yml.");
                }
            } catch (IOException e) {
                getLogger().severe("Erro ao criar data.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Erro ao salvar data.yml: " + e.getMessage());
        }
    }

    public int getSouls(UUID uuid) {
        return data.getInt("players." + uuid, 0);
    }

    public void setSouls(UUID uuid, int amount) {
        data.set("players." + uuid, Math.max(0, amount));
        saveData();
    }

    public void addSouls(UUID uuid, int amount) {
        if (amount > 0) setSouls(uuid, getSouls(uuid) + amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String message(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, "");
        return color(prefix + body);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("almas")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando só pode ser usado por um jogador."));
            return true;
        }
        String msg = message("balance").replace("{souls}", String.valueOf(getSouls(player.getUniqueId())));
        player.sendMessage(msg);
        return true;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        double percentage = getConfig().getDouble("settings.pvp-soul-transfer-percentage", 50.0);
        percentage = Math.max(0.0, Math.min(100.0, percentage));

        int victimBalance = getSouls(victim.getUniqueId());
        int transferred = (int) Math.floor(victimBalance * percentage / 100.0);
        if (transferred <= 0) return;

        setSouls(victim.getUniqueId(), victimBalance - transferred);
        addSouls(killer.getUniqueId(), transferred);

        victim.sendMessage(color(getConfig().getString("messages.prefix", "")
                + getConfig().getString("messages.pvp-loss", "")
                .replace("{souls}", String.valueOf(transferred))
                .replace("{killer}", killer.getName())));
        killer.sendMessage(color(getConfig().getString("messages.prefix", "")
                + getConfig().getString("messages.pvp-gain", "")
                .replace("{souls}", String.valueOf(transferred))
                .replace("{victim}", victim.getName())));
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Entity entity = event.getEntity();
        String type = entity.getType().name();
        int reward = getConfig().getInt("mob-rewards." + type, 0);
        if (reward <= 0) return;

        addSouls(killer.getUniqueId(), reward);
        String text = getConfig().getString("messages.prefix", "")
                + getConfig().getString("messages.mob-reward", "");
        killer.sendMessage(color(text.replace("{souls}", String.valueOf(reward))));
    }
}
