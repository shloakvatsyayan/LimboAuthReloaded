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
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.Where;
import java.sql.SQLException;
import java.util.Locale;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;

public final class UnlinkedAccounts {

  private UnlinkedAccounts() {
  }

  public static boolean exists(Dao<RegisteredPlayer, String> playerDao, String username, String offlinePrefix) {
    try {
      RegisteredPlayer account = AccountNamespace.selectForConnection(
          playerDao.queryForId(username.toLowerCase(Locale.ROOT)), true, offlinePrefix);
      return account != null && account.getPremiumUuid().isEmpty();
    } catch (SQLException exception) {
      throw new SQLRuntimeException(exception);
    }
  }

  static RegisteredPlayer replace(
      Dao<RegisteredPlayer, String> playerDao, RegisteredPlayer offlineAccount, RegisteredPlayer premiumAccount) {
    if (!offlineAccount.getPremiumUuid().isEmpty() || premiumAccount.getPremiumUuid().isEmpty()
        || !offlineAccount.getLowercaseNickname().equals(premiumAccount.getLowercaseNickname())) {
      throw new IllegalArgumentException("Replacement requires an unlinked account and a premium account with the same username");
    }
    try {
      return TransactionManager.callInTransaction(playerDao.getConnectionSource(), () -> {
        if (delete(playerDao, offlineAccount.getLowercaseNickname()) != 1) {
          throw new SQLException("Unlinked account changed while replacing " + offlineAccount.getNickname());
        }
        if (playerDao.create(premiumAccount) != 1) {
          throw new SQLException("Could not save premium replacement for " + premiumAccount.getNickname());
        }
        return premiumAccount;
      });
    } catch (SQLException exception) {
      throw new SQLRuntimeException(exception);
    }
  }

  static int delete(Dao<RegisteredPlayer, String> playerDao, String usernameKey) throws SQLException {
    DeleteBuilder<RegisteredPlayer, String> deleteBuilder = playerDao.deleteBuilder();
    Where<RegisteredPlayer, String> where = deleteBuilder.where();
    where.and(where.eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, usernameKey),
        where.or(where.eq(RegisteredPlayer.PREMIUM_UUID_FIELD, ""), where.isNull(RegisteredPlayer.PREMIUM_UUID_FIELD)));
    return deleteBuilder.delete();
  }
}
