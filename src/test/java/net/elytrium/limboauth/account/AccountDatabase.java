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
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.table.TableUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.SQLException;
import java.util.UUID;
import net.elytrium.limboauth.model.RegisteredPlayer;

public final class AccountDatabase implements AutoCloseable {

  private final JdbcConnectionSource connection;
  private final Dao<RegisteredPlayer, String> playerDao;

  public AccountDatabase() throws SQLException {
    this.connection = new JdbcConnectionSource("jdbc:h2:mem:" + UUID.randomUUID());
    TableUtils.createTable(this.connection, RegisteredPlayer.class);
    this.playerDao = DaoManager.createDao(this.connection, RegisteredPlayer.class);
  }

  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Tests exercise persistence through the shared fixture DAO")
  public Dao<RegisteredPlayer, String> getPlayerDao() {
    return this.playerDao;
  }

  RegisteredPlayer createAccount(String username, UUID premiumUuid, UUID backendUuid) throws SQLException {
    RegisteredPlayer account = new RegisteredPlayer(username, backendUuid.toString(), "127.0.0.1")
        .setPremiumUuid(premiumUuid);
    this.playerDao.create(account);
    return account;
  }

  @Override
  public void close() throws Exception {
    this.connection.close();
  }
}
