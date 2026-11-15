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
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.UpdateBuilder;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;

final class PremiumAccountRenamer {

  private final Dao<RegisteredPlayer, String> playerDao;

  PremiumAccountRenamer(Dao<RegisteredPlayer, String> playerDao) {
    this.playerDao = Objects.requireNonNull(playerDao, "playerDao");
  }

  boolean rename(RegisteredPlayer registeredPlayer, UUID authenticatedUuid, String username, boolean shouldOverwriteUnlinked) {
    Objects.requireNonNull(registeredPlayer, "registeredPlayer");
    Objects.requireNonNull(authenticatedUuid, "authenticatedUuid");
    Objects.requireNonNull(username, "username");
    String premiumUuid = registeredPlayer.getPremiumUuid();
    if (premiumUuid.isEmpty()) {
      return false;
    }
    if (!premiumUuid.equals(authenticatedUuid.toString())) {
      throw new IllegalStateException("Username " + username + " is linked to a different premium account");
    }
    if (registeredPlayer.getNickname().equals(username)) {
      return false;
    }

    String previousKey = registeredPlayer.getLowercaseNickname();
    String usernameKey = username.toLowerCase(Locale.ROOT);
    try {
      TransactionManager.callInTransaction(this.playerDao.getConnectionSource(), () -> {
        if (!previousKey.equals(usernameKey)) {
          this.clearDestination(previousKey, usernameKey, shouldOverwriteUnlinked);
        }
        this.updateUsername(previousKey, premiumUuid, username, usernameKey);
        return null;
      });
    } catch (SQLException exception) {
      if (exception.getCause() instanceof IllegalStateException conflict) {
        throw conflict;
      }
      throw new SQLRuntimeException(exception);
    }

    registeredPlayer.setNickname(username);
    return true;
  }

  private void clearDestination(String previousKey, String usernameKey, boolean shouldOverwriteUnlinked) throws SQLException {
    if (shouldOverwriteUnlinked) {
      UnlinkedAccounts.delete(this.playerDao, usernameKey);
    }
    if (this.playerDao.idExists(usernameKey)) {
      throw new IllegalStateException("Cannot rename premium account " + previousKey
          + " to " + usernameKey + ": the destination account already exists");
    }
  }

  private void updateUsername(String previousKey, String premiumUuid, String username, String usernameKey) throws SQLException {
    UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
    updateBuilder.updateColumnValue(RegisteredPlayer.NICKNAME_FIELD, username);
    updateBuilder.updateColumnValue(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, usernameKey);
    updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, previousKey)
        .and().eq(RegisteredPlayer.PREMIUM_UUID_FIELD, premiumUuid);
    if (updateBuilder.update() != 1) {
      throw new IllegalStateException("Premium account changed while renaming " + previousKey + " to " + username);
    }
  }
}
