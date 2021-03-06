package com.empcraft.xpbank.logic;

public final class ExperienceLevelCalculator {

  /**
   * This table represents the experience points you need to gain a specific level. For example: If
   * you have 7-16 points, you have level 1.
   */
  private static final int[] expList = { 0, 7, 16, 27, 40, 55, 72, 91, 112, 135, 160, 187, 216,
      247, 280, 315, 352, 394, 441, 493, 550, 612, 679, 751, 828, 910, 997, 1089, 1186, 1288, 1395,
      1507, 1628, 1758, 2048, 2202, 2368, 2543, 2727, 2920 };

  /**
   * Hidden private utility constructor for tool class.
   */
  private ExperienceLevelCalculator() {}

  /**
   * Returns the player's level for the given experience points.
   * @param experience The experience points.
   * @return the level a player had with these points.
   */
  public static int getLevel(int experience) {
    /*
     * We need to count down to get the maximum possible level.
     */
    int level = expList.length - 1;

    if (experience < 0) {
      return 0;
    }

    while (true) {
      if (experience >= expList[level]) {
        return level;
      }

      level--;
    }
  }

  /**
   * Returns the minimum amount of experience needed for the given level.
   * @param level the level to check.
   * @return 0 on invalid inputs, otherwise the experience needed for the given level.
   */
  public static int getMinExperienceForLevel(int level) {
    if (level <= 0) {
      return 0;
    }

    if (level > expList.length - 1) {
      return expList[expList.length - 1];
    }

    return expList[level];
  }

  /**
   * Returns the maximum available level.
   * @return the level as int.
   */
  public static int getMaxLevel() {
    return expList.length - 1;
  }

  /**
   * Calculates the amount of Xp the player can deposit for one level.
   * @param playerExperience the current player's experience.
   * @return the delta to the lower level.
   */
  public static int getExperienceDelteToLowerLevel(int playerExperience) {
    int currentLevel = getLevel(playerExperience);
    int experienceForLowerLevel = getMinExperienceForLevel(currentLevel);

    if (experienceForLowerLevel == playerExperience) {
      // the player is currently at the bottom of the current level.
      // he needs to give away a whole level.
      experienceForLowerLevel = getMinExperienceForLevel(currentLevel - 1);
    }

    return playerExperience - experienceForLowerLevel;
  }
}
