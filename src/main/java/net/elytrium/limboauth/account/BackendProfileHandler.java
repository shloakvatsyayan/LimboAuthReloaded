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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.floodgate.FloodgateApiHolder;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;

public final class BackendProfileHandler {

  private final LimboAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;
  private final FloodgateApiHolder floodgateApi;

  public BackendProfileHandler(
      LimboAuth plugin, Dao<RegisteredPlayer, String> playerDao, FloodgateApiHolder floodgateApi) {
    this.plugin = plugin;
    this.playerDao = playerDao;
    this.floodgateApi = floodgateApi;
  }

  public void handle(GameProfileRequestEvent event) {
    GameProfile originalProfile = event.getOriginalProfile();
    boolean isFloodgatePlayer = this.floodgateApi != null && this.floodgateApi.isFloodgatePlayer(originalProfile.getId());
    BackendAccountIdentity backendIdentity = BackendAccountIdentity.resolve(
        event.getUsername(),
        originalProfile.getId(),
        event.isOnlineMode(),
        isFloodgatePlayer,
        Settings.IMP.MAIN.FORCE_OFFLINE_UUID,
        Settings.IMP.MAIN.OFFLINE_MODE_PREFIX,
        Settings.IMP.MAIN.ONLINE_MODE_PREFIX
    );

    if (event.isOnlineMode() && !isFloodgatePlayer) {
      this.cachePremiumUuid(event.getUsername(), backendIdentity.getUsername(), originalProfile.getId());
    }

    UUID backendUuid = backendIdentity.getUuid();
    if (Settings.IMP.MAIN.SAVE_UUID && !isFloodgatePlayer) {
      RegisteredPlayer registeredPlayer = this.findRegisteredPlayer(event, backendIdentity.getUsername());
      if (registeredPlayer != null) {
        backendUuid = this.resolveSavedUuid(registeredPlayer, backendIdentity);
      }
    } else if (event.isOnlineMode()) {
      this.clearPasswordHash(event.getUsername());
    }

    event.setGameProfile(originalProfile.withName(backendIdentity.getUsername()).withId(backendUuid));
  }

  private void cachePremiumUuid(String connectionUsername, String backendUsername, UUID premiumUuid) {
    this.plugin.cacheAuthenticatedPremiumUuid(connectionUsername, premiumUuid);
    if (!backendUsername.equals(connectionUsername)) {
      this.plugin.cacheAuthenticatedPremiumUuid(backendUsername, premiumUuid);
    }
  }

  private RegisteredPlayer findRegisteredPlayer(GameProfileRequestEvent event, String backendUsername) {
    RegisteredPlayer registeredPlayer = null;
    if (event.isOnlineMode()) {
      registeredPlayer = AccountNamespace.selectForConnection(
          AuthSessionHandler.fetchInfo(this.playerDao, event.getOriginalProfile().getId()),
          true,
          Settings.IMP.MAIN.OFFLINE_MODE_PREFIX
      );
    }

    if (registeredPlayer == null) {
      registeredPlayer = AccountNamespace.selectForConnection(
          AuthSessionHandler.fetchInfo(this.playerDao, backendUsername),
          event.isOnlineMode(),
          Settings.IMP.MAIN.OFFLINE_MODE_PREFIX
      );
    }

    return registeredPlayer;
  }

  private UUID resolveSavedUuid(RegisteredPlayer registeredPlayer, BackendAccountIdentity backendIdentity) {
    String savedUuid = registeredPlayer.getUuid();
    if (!backendIdentity.isOfflineUuid() && !savedUuid.isEmpty()) {
      return UUID.fromString(savedUuid);
    }

    String backendUuid = backendIdentity.getUuid().toString();
    if (!backendUuid.equals(savedUuid)) {
      registeredPlayer.setUuid(backendUuid);
      try {
        this.playerDao.update(registeredPlayer);
      } catch (SQLException e) {
        throw new SQLRuntimeException(e);
      }
    }

    return backendIdentity.getUuid();
  }

  private void clearPasswordHash(String username) {
    try {
      UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
      updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, username.toLowerCase(Locale.ROOT));
      updateBuilder.updateColumnValue(RegisteredPlayer.HASH_FIELD, "");
      updateBuilder.update();
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }
  }
}
