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
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.stmt.Where;
import com.velocitypowered.api.proxy.Player;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.account.AccountNamespace;
import net.elytrium.limboauth.model.RegisteredPlayer;

final class PremiumAccounts {

  private PremiumAccounts() {
  }

  static RegisteredPlayer findPlayer(Dao<RegisteredPlayer, String> playerDao, Player player) throws SQLException {
    RegisteredPlayer account = PasswordAccounts.findPlayer(playerDao, player);
    if (account != null || player.isOnlineMode() || Settings.IMP.MAIN.OFFLINE_MODE_PREFIX.isEmpty()) {
      return account;
    }

    String username = connectionUsername(player);
    account = playerDao.queryForId(username.toLowerCase(Locale.ROOT));
    if (account == null || account.getHash().isEmpty() || !account.getPremiumUuid().isEmpty()) {
      return null;
    }

    UUID legacyUuid = offlineUuid(account.getNickname());
    UUID backendUuid = offlineUuid(Settings.IMP.MAIN.OFFLINE_MODE_PREFIX + account.getNickname());
    return legacyUuid.toString().equals(account.getUuid()) && backendUuid.equals(player.getUniqueId()) ? account : null;
  }

  static String connectionUsername(Player player) {
    return AccountNamespace.connectionUsername(player.getUsername(), player.isOnlineMode(),
        Settings.IMP.MAIN.OFFLINE_MODE_PREFIX, Settings.IMP.MAIN.ONLINE_MODE_PREFIX);
  }

  static String upgrade(Dao<RegisteredPlayer, String> playerDao, RegisteredPlayer account,
      Player player, UUID premiumUuid) throws SQLException {
    if (!Settings.IMP.MAIN.SAVE_UUID) {
      throw new SQLException("Premium upgrades require save-uuid to preserve gameplay identity");
    }
    if (premiumUuid == null || account.getHash().isEmpty()) {
      throw new SQLException("Premium upgrade requires a registered password and a premium UUID");
    }
    String previousPremiumUuid = account.getPremiumUuid();
    if (!previousPremiumUuid.isEmpty() && !previousPremiumUuid.equals(premiumUuid.toString())) {
      throw new SQLException("Cannot upgrade an account linked to a different premium UUID");
    }

    for (RegisteredPlayer linked : playerDao.queryForEq(RegisteredPlayer.PREMIUM_UUID_FIELD, premiumUuid.toString())) {
      if (!linked.getLowercaseNickname().equals(account.getLowercaseNickname())) {
        throw new SQLException("Premium UUID is already linked to " + linked.getNickname());
      }
    }

    String username = Settings.IMP.MAIN.ONLINE_MODE_PREFIX + connectionUsername(player);
    RegisteredPlayer reservation = !player.isOnlineMode() && !Settings.IMP.MAIN.OFFLINE_MODE_PREFIX.isEmpty()
        && !player.getUsername().equalsIgnoreCase(username)
        ? new RegisteredPlayer(player.getUsername(), player.getUniqueId().toString(), "")
            .setPassword(UUID.randomUUID().toString()) : null;
    TransactionManager.callInTransaction(playerDao.getConnectionSource(), () -> {
      updateAccount(playerDao, account, player.getUniqueId(), premiumUuid, username);
      if (reservation != null && playerDao.create(reservation) != 1) {
        throw new SQLException("Could not reserve the former offline login " + player.getUsername());
      }
      return null;
    });
    return username;
  }

  private static void updateAccount(Dao<RegisteredPlayer, String> playerDao, RegisteredPlayer account,
      UUID backendUuid, UUID premiumUuid, String username) throws SQLException {
    UpdateBuilder<RegisteredPlayer, String> update = playerDao.updateBuilder();
    update.updateColumnValue(RegisteredPlayer.NICKNAME_FIELD, username);
    update.updateColumnValue(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, username.toLowerCase(Locale.ROOT));
    update.updateColumnValue(RegisteredPlayer.UUID_FIELD, backendUuid.toString());
    update.updateColumnValue(RegisteredPlayer.PREMIUM_UUID_FIELD, premiumUuid.toString());
    update.updateColumnValue(RegisteredPlayer.HASH_FIELD, "");
    update.updateColumnValue(RegisteredPlayer.TOKEN_ISSUED_AT_FIELD, System.currentTimeMillis());
    Where<RegisteredPlayer, String> where = update.where();
    where.eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, account.getLowercaseNickname())
        .and().eq(RegisteredPlayer.HASH_FIELD, account.getHash())
        .and().eq(RegisteredPlayer.UUID_FIELD, account.getUuid());
    String previousPremiumUuid = account.getPremiumUuid();
    if (previousPremiumUuid.isEmpty()) {
      where.and().or(where.eq(RegisteredPlayer.PREMIUM_UUID_FIELD, ""), where.isNull(RegisteredPlayer.PREMIUM_UUID_FIELD));
    } else {
      where.and().eq(RegisteredPlayer.PREMIUM_UUID_FIELD, previousPremiumUuid);
    }
    if (update.update() != 1) {
      throw new SQLException("Account changed while upgrading " + account.getNickname() + " to premium");
    }
  }

  private static UUID offlineUuid(String username) {
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
  }
}
