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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.j256.ormlite.dao.Dao;
import java.util.UUID;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PremiumAccountRenamerTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID BACKEND_UUID = UUID.fromString("00000000-0000-3000-8000-000000000002");

  private AccountDatabase database;
  private Dao<RegisteredPlayer, String> playerDao;
  private PremiumAccountRenamer renamer;

  @BeforeEach
  void openDatabase() throws Exception {
    this.database = new AccountDatabase();
    this.playerDao = this.database.getPlayerDao();
    this.renamer = new PremiumAccountRenamer(this.playerDao);
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.database.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repairsExistingRowWithoutChangingCredentialsOrAccountHistory(boolean shouldOverwrite) throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID)
        .setHash("existing-password-hash").setTotpToken("existing-totp-secret")
        .setRegDate(123L).setLoginDate(456L).setLoginIp("192.0.2.1").setTokenIssuedAt(789L);
    this.playerDao.update(account);
    if (shouldOverwrite) {
      this.playerDao.create(new RegisteredPlayer("CurrentName", "occupied-uuid", "192.0.2.2").setHash("offline-password"));
    }

    assertTrue(this.renamer.rename(account, PREMIUM_UUID, "CurrentName", shouldOverwrite));

    RegisteredPlayer repaired = this.playerDao.queryForId("currentname");
    assertNull(this.playerDao.queryForId("oldname"));
    assertEquals(1, this.playerDao.countOf());
    assertEquals("CurrentName", repaired.getNickname());
    assertEquals("currentname", account.getLowercaseNickname());
    assertEquals(BACKEND_UUID.toString(), repaired.getUuid());
    assertEquals(PREMIUM_UUID.toString(), repaired.getPremiumUuid());
    assertEquals("existing-password-hash", repaired.getHash());
    assertEquals("existing-totp-secret", repaired.getTotpToken());
    assertEquals("127.0.0.1", repaired.getIP());
    assertEquals("192.0.2.1", repaired.getLoginIp());
    assertEquals(123L, repaired.getRegDate());
    assertEquals(456L, repaired.getLoginDate());
    assertEquals(789L, repaired.getTokenIssuedAt());
    assertFalse(this.renamer.rename(repaired, PREMIUM_UUID, "CurrentName", false));
  }

  @Test
  void updatesCaseOnlyNameChanges() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OLDNAME", PREMIUM_UUID, BACKEND_UUID);

    assertTrue(this.renamer.rename(account, PREMIUM_UUID, "OldName", false));
    assertEquals("OldName", this.playerDao.queryForId("oldname").getNickname());
    assertEquals(1, this.playerDao.countOf());
  }

  @Test
  void keepsConfiguredPrefixOnRepairedName() throws Exception {
    RegisteredPlayer account = this.database.createAccount("+OldName", PREMIUM_UUID, BACKEND_UUID);

    assertTrue(this.renamer.rename(account, PREMIUM_UUID, "+CurrentName", false));
    assertNull(this.playerDao.queryForId("+oldname"));
    assertEquals("+CurrentName", this.playerDao.queryForId("+currentname").getNickname());
  }

  @Test
  void refusesOccupiedNamesWithoutModifyingEitherAccount() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);
    RegisteredPlayer occupied = new RegisteredPlayer("CurrentName", "occupied-uuid", "192.0.2.2").setHash("password");
    this.playerDao.create(occupied);

    assertThrows(IllegalStateException.class, () -> this.renamer.rename(account, PREMIUM_UUID, "CurrentName", false));
    assertEquals("OldName", account.getNickname());
    assertEquals("OldName", this.playerDao.queryForId("oldname").getNickname());
    assertEquals("password", this.playerDao.queryForId("currentname").getHash());
    assertEquals("occupied-uuid", this.playerDao.queryForId("currentname").getUuid());
    assertEquals(2, this.playerDao.countOf());
  }

  @Test
  void refusesAnAccountLinkedToAnotherPremiumUuid() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);

    assertThrows(IllegalStateException.class, () -> this.renamer.rename(account, UUID.randomUUID(), "OldName", false));
    assertEquals(PREMIUM_UUID.toString(), this.playerDao.queryForId("oldname").getPremiumUuid());
  }

  @Test
  void leavesUnlinkedOfflineAccountsUnchanged() throws Exception {
    RegisteredPlayer account = new RegisteredPlayer("OldName", BACKEND_UUID.toString(), "127.0.0.1");
    this.playerDao.create(account);

    assertFalse(this.renamer.rename(account, PREMIUM_UUID, "CurrentName", false));
    assertNull(this.playerDao.queryForId("currentname"));
    assertEquals("OldName", this.playerDao.queryForId("oldname").getNickname());
  }

  @Test
  void refusesStaleAccountAfterConcurrentRename() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);
    RegisteredPlayer staleAccount = this.playerDao.queryForId("oldname");
    this.renamer.rename(account, PREMIUM_UUID, "CurrentName", false);

    assertThrows(IllegalStateException.class, () -> this.renamer.rename(staleAccount, PREMIUM_UUID, "AnotherName", false));
    assertNull(this.playerDao.queryForId("anothername"));
    assertEquals("OldName", staleAccount.getNickname());
    assertEquals("CurrentName", this.playerDao.queryForId("currentname").getNickname());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void overwritesOnlyTheMatchingUnlinkedDestination(String premiumUuid) throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);
    this.playerDao.create(new RegisteredPlayer("CURRENTNAME", "occupied-uuid", "192.0.2.2").setPremiumUuid(premiumUuid));
    this.playerDao.create(new RegisteredPlayer("Unrelated", "other-uuid", "192.0.2.3").setPremiumUuid(premiumUuid));

    assertTrue(this.renamer.rename(account, PREMIUM_UUID, "CurrentName", true));

    assertEquals(PREMIUM_UUID.toString(), this.playerDao.queryForId("currentname").getPremiumUuid());
    assertEquals("other-uuid", this.playerDao.queryForId("unrelated").getUuid());
    assertEquals(2, this.playerDao.countOf());
  }

  @Test
  void refusesLinkedDestinationEvenWhenOverwriteIsEnabled() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);
    UUID otherPremiumUuid = UUID.randomUUID();
    this.database.createAccount("CurrentName", otherPremiumUuid, UUID.randomUUID());

    assertThrows(IllegalStateException.class, () -> this.renamer.rename(account, PREMIUM_UUID, "CurrentName", true));

    assertEquals(otherPremiumUuid.toString(), this.playerDao.queryForId("currentname").getPremiumUuid());
    assertEquals("OldName", this.playerDao.queryForId("oldname").getNickname());
    assertEquals(2, this.playerDao.countOf());
  }

  @Test
  void restoresOfflineDestinationWhenSourceChangedBeforeRename() throws Exception {
    RegisteredPlayer account = this.database.createAccount("OldName", PREMIUM_UUID, BACKEND_UUID);
    this.playerDao.create(new RegisteredPlayer("CurrentName", "occupied-uuid", "192.0.2.2").setHash("password"));
    this.playerDao.update(this.playerDao.queryForId("oldname").setPremiumUuid(UUID.randomUUID()));

    assertThrows(IllegalStateException.class, () -> this.renamer.rename(account, PREMIUM_UUID, "CurrentName", true));

    assertEquals("OldName", account.getNickname());
    assertEquals("password", this.playerDao.queryForId("currentname").getHash());
    assertEquals("occupied-uuid", this.playerDao.queryForId("currentname").getUuid());
    assertEquals(2, this.playerDao.countOf());
  }
}
