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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.UUID;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordAccountsTest {

  private PasswordCommandFixture fixture;

  @BeforeEach
  void prepareDatabase() throws Exception {
    this.fixture = new PasswordCommandFixture();
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.fixture.close();
  }

  @Test
  void doesNotResolveCrackedPlayerThroughPremiumUuid() throws Exception {
    RegisteredPlayer premium = this.fixture.createAccount("Player", "premium-password");
    RegisteredPlayer cracked = new RegisteredPlayer("*Player", UUID.randomUUID().toString(), "127.0.0.1");
    premium.setPremiumUuid(cracked.getUuid());
    this.fixture.playerDao.update(premium);
    Player player = this.fixture.player("*Player", cracked, false);

    assertNull(PasswordAccounts.findPlayer(this.fixture.playerDao, player));
  }

  @Test
  void refusesAmbiguousSavedUuid() throws Exception {
    RegisteredPlayer first = this.fixture.createAccount("*First", "password");
    RegisteredPlayer second = this.fixture.createAccount("*Second", "password").setUuid(first.getUuid());
    this.fixture.playerDao.update(second);
    Player player = this.fixture.player("*Missing", first, false);

    assertNull(PasswordAccounts.findPlayer(this.fixture.playerDao, player));
  }

  @Test
  void doesNotResolvePremiumConnectionToCrackedAccount() throws Exception {
    RegisteredPlayer cracked = this.fixture.createAccount("*Player", "password");
    Player player = this.fixture.player("Player", cracked, true);

    assertNull(PasswordAccounts.findPlayer(this.fixture.playerDao, player));
  }

  @Test
  void doesNotResolveCrackedConnectionToLinkedPremiumAccount() throws Exception {
    RegisteredPlayer premium = this.fixture.createAccount("Player", "password").setPremiumUuid(UUID.randomUUID());
    this.fixture.playerDao.update(premium);
    Player player = this.fixture.player("*Player", premium, false);

    assertNull(PasswordAccounts.findPlayer(this.fixture.playerDao, player));
  }

  @Test
  void preservesExactNameLookupBeforeUuidFallback() throws Exception {
    RegisteredPlayer cracked = this.fixture.createAccount("*Player", "cracked-password");
    this.fixture.createAccount("Player", "premium-password");
    Player player = this.fixture.player("Player", cracked, false);

    assertEquals("player", PasswordAccounts.findPlayer(this.fixture.playerDao, player).getLowercaseNickname());
  }

  @Test
  void refusesMissingNameWithUnrelatedUuid() throws Exception {
    RegisteredPlayer account = this.fixture.createAccount("*Player", "password");
    Player player = this.fixture.player("*Missing", new RegisteredPlayer("*Missing", UUID.randomUUID().toString(), "127.0.0.1"), false);

    assertNull(PasswordAccounts.findPlayer(this.fixture.playerDao, player));
    assertEquals(account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
  }

  @Test
  void supportsLegacyAccountWithoutSavedUuid() throws Exception {
    RegisteredPlayer account = this.fixture.createAccount("*Player", "password");
    Player player = this.fixture.player("*Player", account, false);
    account.setUuid("");
    this.fixture.playerDao.update(account);

    assertEquals("*player", PasswordAccounts.findPlayer(this.fixture.playerDao, player).getLowercaseNickname());
  }

  @Test
  void rejectsMissingRowInsteadOfReportingSuccessfulWrite() throws Exception {
    RegisteredPlayer account = this.fixture.createAccount("*Player", "password");
    this.fixture.playerDao.delete(account);

    assertThrows(SQLException.class, () -> PasswordAccounts.changePassword(this.fixture.playerDao, account, "new-hash"));
    assertEquals(1L, account.getTokenIssuedAt());
  }

  @Test
  void rejectsStalePasswordChangeAfterAnotherReset() throws Exception {
    RegisteredPlayer account = this.fixture.createAccount("*Player", "old-password");
    RegisteredPlayer concurrentAccount = this.fixture.playerDao.queryForId("*player");
    PasswordAccounts.changePassword(this.fixture.playerDao, concurrentAccount, "admin-reset-hash");

    assertThrows(SQLException.class, () -> PasswordAccounts.changePassword(this.fixture.playerDao, account, "stale-hash"));

    assertEquals("admin-reset-hash", this.fixture.playerDao.queryForId("*player").getHash());
  }
}
