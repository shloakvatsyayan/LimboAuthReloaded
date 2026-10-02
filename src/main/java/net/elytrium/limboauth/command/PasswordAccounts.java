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

package net.elytrium.limboauth.command;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.model.RegisteredPlayer;

final class PasswordAccounts {

  private PasswordAccounts() {
  }

  static RegisteredPlayer findPlayer(Dao<RegisteredPlayer, String> playerDao, Player player) throws SQLException {
    RegisteredPlayer account = playerDao.queryForId(player.getUsername().toLowerCase(Locale.ROOT));
    if (account != null) {
      return account;
    }

    List<RegisteredPlayer> accounts = playerDao.queryForEq(RegisteredPlayer.UUID_FIELD, player.getUniqueId().toString());
    if (accounts.size() != 1) {
      return null;
    }

    account = accounts.getFirst();
    String offlinePrefix = Settings.IMP.MAIN.OFFLINE_MODE_PREFIX;
    boolean isOfflineAccount = !offlinePrefix.isEmpty() && account.getNickname().startsWith(offlinePrefix);
    if (player.isOnlineMode()) {
      return isOfflineAccount ? null : account;
    }
    return account.getPremiumUuid().isEmpty() && !account.getHash().isEmpty() ? account : null;
  }

  static RegisteredPlayer findTarget(Dao<RegisteredPlayer, String> playerDao, String nickname) throws SQLException {
    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    RegisteredPlayer account = playerDao.queryForId(lowercaseNickname);
    String offlinePrefix = Settings.IMP.MAIN.OFFLINE_MODE_PREFIX.toLowerCase(Locale.ROOT);
    if (account != null || offlinePrefix.isEmpty() || lowercaseNickname.startsWith(offlinePrefix)) {
      return account;
    }
    return playerDao.queryForId(offlinePrefix + lowercaseNickname);
  }

  static void changePassword(Dao<RegisteredPlayer, String> playerDao, RegisteredPlayer account, String newHash) throws SQLException {
    long issuedAt = System.currentTimeMillis();
    UpdateBuilder<RegisteredPlayer, String> update = playerDao.updateBuilder();
    update.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, account.getLowercaseNickname())
        .and().eq(RegisteredPlayer.HASH_FIELD, account.getHash());
    update.updateColumnValue(RegisteredPlayer.HASH_FIELD, newHash);
    update.updateColumnValue(RegisteredPlayer.TOKEN_ISSUED_AT_FIELD, issuedAt);
    if (update.update() != 1) {
      throw new SQLException("Password update did not match exactly one registered account");
    }
    account.setHash(newHash).setTokenIssuedAt(issuedAt);
  }
}
