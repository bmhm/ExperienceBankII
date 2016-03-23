package com.empcraft.xpbank.logic;

import code.husky.DatabaseConnectorException;
import code.husky.mysql.MySQL;
import code.husky.sqlite.SqLite;

import com.empcraft.xpbank.ExpBankConfig;
import com.empcraft.xpbank.dao.PlayerExperienceDao;
import com.empcraft.xpbank.dao.impl.mysql.MySqlPlayerExperienceDao;
import com.empcraft.xpbank.text.MessageUtils;
import com.empcraft.xpbank.text.Text;
import com.empcraft.xpbank.text.YamlLanguageProvider;

import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DataHelper {

  private YamlLanguageProvider ylp;
  private ExpBankConfig config;

  public DataHelper(final YamlLanguageProvider ylp, final ExpBankConfig config) {
    this.ylp = ylp;
    this.config = config;
  }

  private Connection getConnection() throws DatabaseConnectorException {
    if (config.isMySqlEnabled()) {
      return getMySqlConnection();
    }

    return getSqLiteConnection();
  }

  private Connection getMySqlConnection() throws DatabaseConnectorException {
    MySQL mySql = new MySQL(config.getMySqlHost(), config.getMySqlPort(), config.getMySqlDatabase(),
        config.getMySqlUsername(), config.getMySqlPassword());

    return mySql.openConnection();
  }

  private Connection getSqLiteConnection() throws DatabaseConnectorException {
    File sqliteFile = new File(config.getPlugin().getDataFolder(), "xp.db");
    SqLite sqlite = new SqLite(sqliteFile);

    return sqlite.openConnection();
  }

  private PlayerExperienceDao getDao(Connection connection) {
    if (config.isMySqlEnabled()) {
      return new MySqlPlayerExperienceDao(connection, config);
    }

    // TODO: Implement SQLite
    return null;
  }

  /**
   * Searches the bank storage yaml file. If players were found, insert them into the database.
   *
   * @param yamlentries
   *          the previously stored experience in a yaml file.
   * @throws DatabaseConnectorException
   */
  public void bulkSaveEntriesToDb(Map<UUID, Integer> yamlentries) throws DatabaseConnectorException {
    if (null == yamlentries || yamlentries.isEmpty()) {
      // nothing to do.
      return;
    }

    try (Connection connection = getConnection()) {
      MessageUtils.sendMessageToConsole(ylp.getMessage(Text.CONVERT));
      PlayerExperienceDao ped = getDao(connection);

      for (Map.Entry<UUID, Integer> player : yamlentries.entrySet()) {
        UUID uuid = player.getKey();
        int oldExperience = player.getValue();
        ped.insertPlayerAndExperience(uuid, oldExperience);
      }

      MessageUtils.sendMessageToConsole(ylp.getMessage(Text.DONE));
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not insert players into Database.", sqlEx);
    }

    return;
  }

  public int countPlayersInDatabase() throws DatabaseConnectorException {
    int playercount = 0;

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      playercount = ped.countPlayers();
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not count players in Database.", sqlEx);
    }

    return playercount;
  }

  public boolean updatePlayerExperience(UUID uuid, int newExperience)
      throws DatabaseConnectorException {
    boolean success = false;

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      success = ped.updatePlayerExperience(uuid, newExperience);
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not update player experience.", sqlEx);
    }

    return success;
  }

  public boolean createTableIfNotExists() throws DatabaseConnectorException {
    boolean success = false;

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      success = ped.createTable();
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not create Table in Database.", sqlEx);
    }

    return success;
  }

  public Map<UUID, Integer> getSavedExperience()
      throws DatabaseConnectorException {
    Map<UUID, Integer> results = new HashMap<>();

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      results.putAll(ped.getSavedExperience());
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not read existing saved exp from Database.", sqlEx);
      throw new DatabaseConnectorException(sqlEx);
    }

    return results;
  }

  public int getSavedExperience(UUID uuid)
      throws DatabaseConnectorException {
    int result = 0;

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      result = ped.getSavedExperience(uuid);
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not read existing saved exp from Database.",
          sqlEx);
      throw new DatabaseConnectorException(sqlEx);
    }

    return result;
  }

  public int getSavedExperience(Player player)
      throws DatabaseConnectorException {
    if (player == null) {
      return 0;
    }

    return getSavedExperience(player.getUniqueId());
  }

  public boolean updatePlayerExperienceDelta(UUID uuid, int delta)
      throws DatabaseConnectorException {
    boolean success = false;

    try (Connection connection = getConnection()) {
      PlayerExperienceDao ped = getDao(connection);
      success = ped.updatePlayerExperienceDelta(uuid, delta);
    } catch (SQLException sqlEx) {
      config.getLogger().log(Level.SEVERE, "Could not update player experience.", sqlEx);
      throw new DatabaseConnectorException(sqlEx);
    }

    return success;
  }

}
