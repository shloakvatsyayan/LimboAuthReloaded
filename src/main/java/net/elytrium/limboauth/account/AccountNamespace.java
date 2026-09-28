/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth.account;

import java.util.Objects;
import net.elytrium.limboauth.model.RegisteredPlayer;

public final class AccountNamespace {

  private AccountNamespace() {
  }

  public static RegisteredPlayer selectForConnection(
      RegisteredPlayer registeredPlayer, boolean isOnlineMode, String offlineModePrefix) {
    Objects.requireNonNull(offlineModePrefix, "offlineModePrefix");
    if (registeredPlayer == null || !isOnlineMode || offlineModePrefix.isEmpty()) {
      return registeredPlayer;
    }

    return registeredPlayer.getNickname().startsWith(offlineModePrefix) ? null : registeredPlayer;
  }

  public static String connectionUsername(
      String backendUsername, boolean isOnlineMode, String offlineModePrefix, String onlineModePrefix) {
    Objects.requireNonNull(backendUsername, "backendUsername");
    Objects.requireNonNull(offlineModePrefix, "offlineModePrefix");
    Objects.requireNonNull(onlineModePrefix, "onlineModePrefix");

    String configuredPrefix = isOnlineMode ? onlineModePrefix : offlineModePrefix;
    if (configuredPrefix.isEmpty() || !backendUsername.startsWith(configuredPrefix)) {
      return backendUsername;
    }

    return backendUsername.substring(configuredPrefix.length());
  }

  public static String canonicalConnectionUsername(
      String connectionUsername,
      RegisteredPlayer registeredPlayer,
      boolean isOnlineMode,
      String offlineModePrefix,
      String onlineModePrefix) {
    Objects.requireNonNull(connectionUsername, "connectionUsername");

    if (registeredPlayer == null || isOnlineMode) {
      return connectionUsername;
    }

    return connectionUsername(
        registeredPlayer.getNickname(), isOnlineMode, offlineModePrefix, onlineModePrefix);
  }
}
