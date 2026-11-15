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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.j256.ormlite.dao.Dao;
import java.sql.SQLException;
import java.util.UUID;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class UnlinkedAccountsTest {

  private AccountDatabase database;
  private Dao<RegisteredPlayer, String> playerDao;
  private RegisteredPlayer offlineAccount;
  private RegisteredPlayer premiumAccount;

  @BeforeEach
  void openDatabase() throws Exception {
    this.database = new AccountDatabase();
    this.playerDao = this.database.getPlayerDao();
    this.offlineAccount = new RegisteredPlayer("CurrentName", UUID.randomUUID().toString(), "192.0.2.1")
        .setHash("offline-password").setTotpToken("offline-totp").setRegDate(1L).setTokenIssuedAt(2L);
    this.playerDao.create(this.offlineAccount);
    UUID premiumUuid = UUID.randomUUID();
    this.premiumAccount = new RegisteredPlayer("CurrentName", premiumUuid.toString(), "192.0.2.2").setPremiumUuid(premiumUuid);
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.database.close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void replacesOfflineCredentialsAndHistoryWithFreshPremiumAccount(String premiumUuid) throws Exception {
    this.playerDao.update(this.offlineAccount.setPremiumUuid(premiumUuid));
    assertTrue(UnlinkedAccounts.exists(this.playerDao, "CURRENTNAME", ""));

    UnlinkedAccounts.replace(this.playerDao, this.offlineAccount, this.premiumAccount);

    RegisteredPlayer storedAccount = this.playerDao.queryForId("currentname");
    assertEquals(this.premiumAccount.getPremiumUuid(), storedAccount.getPremiumUuid());
    assertEquals(this.premiumAccount.getUuid(), storedAccount.getUuid());
    assertEquals("", storedAccount.getHash());
    assertEquals("", storedAccount.getTotpToken());
    assertEquals(this.premiumAccount.getIP(), storedAccount.getIP());
    assertEquals(this.premiumAccount.getRegDate(), storedAccount.getRegDate());
    assertEquals(this.premiumAccount.getTokenIssuedAt(), storedAccount.getTokenIssuedAt());
    assertFalse(UnlinkedAccounts.exists(this.playerDao, "CurrentName", ""));
    assertEquals(1, this.playerDao.countOf());
  }

  @Test
  void restoresOfflineAccountWhenPremiumInsertFails() throws Exception {
    Dao<RegisteredPlayer, String> failingDao = spy(this.playerDao);
    doThrow(new SQLException("Simulated insert failure")).when(failingDao).create(this.premiumAccount);

    assertThrows(SQLRuntimeException.class, () -> UnlinkedAccounts.replace(failingDao, this.offlineAccount, this.premiumAccount));

    assertEquals("offline-password", this.playerDao.queryForId("currentname").getHash());
    assertEquals("offline-totp", this.playerDao.queryForId("currentname").getTotpToken());
    assertEquals(1, this.playerDao.countOf());
  }

  @Test
  void refusesAccountLinkedSinceItWasRead() throws Exception {
    UUID linkedUuid = UUID.randomUUID();
    this.playerDao.update(this.playerDao.queryForId("currentname").setPremiumUuid(linkedUuid));

    assertThrows(SQLRuntimeException.class, () -> UnlinkedAccounts.replace(this.playerDao, this.offlineAccount, this.premiumAccount));

    assertEquals(linkedUuid.toString(), this.playerDao.queryForId("currentname").getPremiumUuid());
    assertEquals("offline-password", this.playerDao.queryForId("currentname").getHash());
  }

  @Test
  void ignoresMissingAccountsAndSeparateOfflineNamespaces() throws Exception {
    this.playerDao.create(new RegisteredPlayer("*CurrentName", "offline-uuid", "192.0.2.3"));

    assertFalse(UnlinkedAccounts.exists(this.playerDao, "MissingName", ""));
    assertFalse(UnlinkedAccounts.exists(this.playerDao, "*CurrentName", "*"));
    assertTrue(UnlinkedAccounts.exists(this.playerDao, "CurrentName", "*"));
  }
}
