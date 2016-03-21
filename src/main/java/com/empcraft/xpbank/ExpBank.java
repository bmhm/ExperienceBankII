package com.empcraft.xpbank;

import code.husky.mysql.MySQL;

import com.empcraft.xpbank.events.SignBreakListener;
import com.empcraft.xpbank.events.SignChangeEventListener;
import com.empcraft.xpbank.logic.PermissionsHelper;
import com.empcraft.xpbank.text.MessageUtils;
import com.empcraft.xpbank.threads.ChangeExperienceThread;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class ExpBank extends JavaPlugin implements Listener {
  ExpBank plugin;
  private YamlConfiguration exp;
  private File expFile;
  private InSignsNano ISN;
  private Connection connection;
  private Statement statement;

  private Map<UUID, Integer> expMap = new HashMap<>();

  public final String version = getDescription().getVersion();

  /**
   * Use ylp.getMessage("");
   */
  private YamlLanguageProvider ylp;

  String evaluate(String mystring, Player player) {
    if (mystring.contains("{player}")) {
      mystring = mystring.replace("{player}", player.getName());
    }

    if (mystring.contains("{expbank}")) {
      mystring = mystring.replace("{expbank}", "" + getExp(player.getUniqueId()));
    }

    ExperienceManager expMan = new ExperienceManager(player);
    if (mystring.contains("{exp}")) {
      mystring = mystring.replace("{exp}", "" + expMan.getCurrentExp());
    }

    if (mystring.contains("{lvl}")) {
      mystring = mystring.replace("{lvl}", "" + player.getLevel());
    }

    if (mystring.contains("{lvlbank}")) {
      mystring = mystring.replace("{lvlbank}",
          "" + expMan.getLevelForExp(getExp(player.getUniqueId())));
    }

    if (mystring.contains("{lvlbank2}")) {
      mystring = mystring.replace("{lvlbank2}",
          "" + (expMan.getLevelForExp(expMan.getCurrentExp() + getExp(player.getUniqueId()))
              - player.getLevel()));
    }

    return MessageUtils.colorise(mystring);
  }

  @Override
  public void onDisable() {
    saveConfig();
  }

  @Override
  public void onEnable() {
    MessageUtils.sendMessageToAll(getServer(), "&8===&a[&7EXPBANK&a]&8===");
    plugin = this;
    expFile = new File(getDataFolder() + File.separator + "xplist.yml");
    exp = YamlConfiguration.loadConfiguration(expFile);
    saveResource("english.yml", true);
    saveResource("spanish.yml", true);
    saveResource("catalan.yml", true);

    Map<String, Object> options = new HashMap<>();
    getConfig().set("version", version);
    options.put("language", "english");
    options.put("storage.default", 825);
    options.put("text.create", "[EXP]");
    options.put("text.1", "&8---&aEXP&8---");
    options.put("text.2", "{player}");
    options.put("text.3", "{expbank}");
    options.put("text.4", "&8---&a===&8---");
    options.put("mysql.enabled", false);
    options.put("mysql.connection.port", 3306);
    options.put("mysql.connection.host", "localhost");
    options.put("mysql.connection.username", "root");
    options.put("mysql.connection.password", "");
    options.put("mysql.connection.database", "mysql");
    options.put("mysql.connection.table", "expbank");

    for (final Entry<String, Object> node : options.entrySet()) {
      if (!getConfig().contains(node.getKey())) {
        getConfig().set(node.getKey(), node.getValue());
      }
    }

    saveConfig();

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
        new File(getDataFolder(), getConfig().getString("language").toLowerCase() + ".yml"));
    ylp = new YamlLanguageProvider(yaml, getLogger());

    if (getConfig().getBoolean("mysql.enabled")) {
      Statement createIfNotExists = null;
      Statement countEntries = null;
      Statement uidAndExp = null;
      Statement newPlayer = null;

      MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("MYSQL"));
      MySQL MySQL = new MySQL(plugin, getConfig().getString("mysql.connection.host"),
          getConfig().getString("mysql.connection.port"),
          getConfig().getString("mysql.connection.database"),
          getConfig().getString("mysql.connection.username"),
          getConfig().getString("mysql.connection.password"));

      try {
        connection = MySQL.openConnection();
        createIfNotExists = connection.createStatement();
        MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("SUCCESS"));
        createIfNotExists.executeUpdate("CREATE TABLE IF NOT EXISTS "
            + getConfig().getString("mysql.connection.table") + " ( UUID VARCHAR(36), EXP INT )");
        createIfNotExists.close();

        countEntries = connection.createStatement();
        ResultSet result = countEntries.executeQuery(
            "SELECT COUNT(*) FROM " + getConfig().getString("mysql.connection.table"));
        int length = 0;

        if (result.next()) {
          length = result.getInt(1);
        }

        result.close();
        countEntries.close();

        if (length == 0) {
          Set<String> players = exp.getKeys(false);

          if (!players.isEmpty()) {
            MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("CONVERT"));

            for (String player : players) {
              newPlayer = connection.createStatement();
              newPlayer
                  .executeUpdate("INSERT INTO " + getConfig().getString("mysql.connection.table")
                      + " VALUES('" + player + "'," + exp.get(player) + ")");
              newPlayer.close();
            }

            MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("DONE"));
            exp = null;
            expFile = null;
          }
        }

        uidAndExp = connection.createStatement();
        result = uidAndExp.executeQuery(
            "SELECT UUID, EXP FROM " + getConfig().getString("mysql.connection.table") + ";");
        while (result.next()) {
          try {
            int experience = result.getInt("EXP");
            String uuid_s = result.getString("UUID");
            UUID uuid = UUID.fromString(uuid_s);
            expMap.put(uuid, experience);
          } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not get exp for players.", e);
          }
        }

        result.close();
        uidAndExp.close();
        connection.close();
      } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Clould not complete onEnable()-Queries.", e);

        MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("MYSQL-CONNECT"));
      } finally {
        if (statement != null) {
          try {
            statement.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Statement statement.", e1);
          }
        }

        if (newPlayer != null) {
          try {
            newPlayer.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Statement newPlayer.", e1);
          }
        }

        if (uidAndExp != null) {
          try {
            uidAndExp.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Statement uidAndExp.", e1);
          }
        }

        if (countEntries != null) {
          try {
            countEntries.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Statement countEntries.", e1);
            ;
          }
        }

        if (createIfNotExists != null) {
          try {
            createIfNotExists.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Statement createIfNotExists.", e1);
          }
        }

        if (connection != null) {
          try {
            connection.close();
          } catch (SQLException e1) {
            getLogger().log(Level.WARNING, "Could not close Connection.", e1);
          }
        }
      }
    } else {
      Set<String> players = exp.getKeys(false);

      for (String player : players) {
        try {
          int experience = exp.getInt(player);
          UUID uuid = UUID.fromString(player);
          expMap.put(uuid, experience);
        } catch (Exception e) {
          getLogger().log(Level.WARNING, "Could not register Players.", e);
        }
      }
      MessageUtils.sendMessageToAll(getServer(), ylp.getMessage("YAML"));
    }

    boolean manual = true;
    Plugin protocolPlugin = Bukkit.getServer().getPluginManager().getPlugin("ProtocolLib");

    if ((protocolPlugin != null && protocolPlugin.isEnabled())) {
      MessageUtils.sendMessageToAll(getServer(), "&aUsing ProtocolLib for packets");
      manual = false;
    }

    ISN = new InSignsNano(plugin, false, manual) {
      @Override
      public String[] getValue(String[] lines, Player player, Sign sign) {
        if (lines[0].equals(MessageUtils.colorise(getConfig().getString("text.create")))) {
          lines[0] = evaluate(getConfig().getString("text.1"), player);
          lines[1] = evaluate(getConfig().getString("text.2"), player);
          lines[2] = evaluate(getConfig().getString("text.3"), player);
          lines[3] = evaluate(getConfig().getString("text.4"), player);
        }

        return lines;
      }
    };

    /* Register sign change event. */
    Bukkit.getServer().getPluginManager().registerEvents(
        new SignChangeEventListener(ISN, getConfig(), ylp),
        this);

    /* Register sign break event. */
    Bukkit.getServer().getPluginManager().registerEvents(
        new SignBreakListener(ISN, getConfig()),
        this);
    Bukkit.getServer().getPluginManager().registerEvents(this, this);
    BukkitScheduler scheduler = Bukkit.getServer().getScheduler();

    // Save any changes to the config
    scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
      @Override
      public void run() {
        saveConfig();
      }
    }, 24000L, 24000L);
  }

  private void runTask(final Runnable r) {
    Bukkit.getScheduler().runTaskAsynchronously(this, r);
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (!(event.getAction() == Action.RIGHT_CLICK_BLOCK
        || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
      return;
    }

    Block block = event.getClickedBlock();
    if ((block.getType() == Material.SIGN_POST) || (block.getType() == Material.WALL_SIGN)) {
      Sign sign = (Sign) block.getState();
      Player player = event.getPlayer();
      String[] lines = sign.getLines();

      if (lines[0].equals(MessageUtils.colorise(getConfig().getString("text.create")))) {
        if (PermissionsHelper.playerHasPermission(player, "expbank.use")) {
          ExperienceManager expMan = new ExperienceManager(player);
          int amount;
          int myExp = getExp(player.getUniqueId());

          if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            if (player.isSneaking()) {
              amount = myExp;
            } else {
              amount = expMan.getXpForLevel(expMan.getLevelForExp(expMan.getCurrentExp()) + 1)
                  - expMan.getCurrentExp();
              if (amount > myExp) {
                amount = myExp;
              }
            }

            if (player.getInventory().getItemInMainHand().getType() == Material.GLASS_BOTTLE
                && PermissionsHelper.playerHasPermission(player, "expbank.use.bottle")) {
              int bottles = player.getInventory().getItemInMainHand().getAmount();

              if (bottles * 7 > myExp) {
                MessageUtils.sendMessageToPlayer(player, ylp.getMessage("BOTTLE-ERROR"));
                return;
              } else {
                amount = bottles * 7;
                player.getInventory().getItemInMainHand().setType(Material.EXP_BOTTLE);
                event.setCancelled(true);
              }

            } else {
              expMan.changeExp(amount);
            }
          } else {
            if (player.isSneaking()) {
              amount = -expMan.getCurrentExp();
            } else {
              if (expMan.getCurrentExp() > 17) {
                amount = -(expMan.getCurrentExp()
                    - expMan.getXpForLevel(expMan.getLevelForExp(expMan.getCurrentExp()) - 1));
              } else {
                amount = -expMan.getCurrentExp();
              }
            }

            int max = getMaxExp(player);

            if (amount == 0) {
              MessageUtils.sendMessageToPlayer(player, ylp.getMessage("EXP-NONE"));
            } else if (myExp - amount > max) {
              amount = -(max - myExp);
              if (amount == 0) {
                MessageUtils.sendMessageToPlayer(player, ylp.getMessage("EXP-LIMIT"));
              }
            }
            expMan.changeExp(amount);
          }

          changeExp(player.getUniqueId(), -amount);
          ISN.scheduleUpdate(player, sign, 1);
        } else {
          MessageUtils.sendMessageToPlayer(player,
              ylp.getMessage("NOPERM").replace("{STRING}", "expbank.use" + ""));
        }
      }
    }
  }

  public int getMaxExp(Player player) {
    Set<String> nodes = getConfig().getConfigurationSection("storage").getKeys(false);
    int max = 0;

    for (String perm : nodes) {
      if ("default".equals(perm) || PermissionsHelper.playerHasPermission(player, "expbank.limit." + perm)) {
        int value = getConfig().getInt("storage." + perm);

        if (value > max) {
          max = value;
        }
      }
    }
    return max;
  }

  public int getExp(UUID uuid) {
    Integer value = expMap.get(uuid);

    if (value == null) {
      return 0;
    }

    return value;
  }

  public void changeExp(final UUID uuid, final int value) {
    if (exp == null) {
      Runnable changeExp = new ChangeExperienceThread(uuid, value, getConfig(), ylp, getServer(),
          getLogger());
      runTask(changeExp);
    } else {
      exp.set(uuid.toString(), value + getExp(uuid));

      try {
        exp.save(expFile);
      } catch (IOException e) {
        getLogger().log(Level.WARNING,
            "Could not save experience level for [" + uuid.toString() + "].", e);
      }
    }

    expMap.put(uuid, getExp(uuid) + value);
  }

}
