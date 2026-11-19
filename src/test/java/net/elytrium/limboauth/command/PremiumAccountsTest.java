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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.account.BackendProfileHandler;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class PremiumAccountsTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");

  private PasswordCommandFixture fixture;
  private RegisteredPlayer account;
  private Player player;

  @BeforeEach
  void prepareAccounts() throws Exception {
    this.fixture = new PasswordCommandFixture();
    this.account = this.fixture.createAccount("*Player", "old-password");
    this.player = this.fixture.player("*Player", this.account, false);
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.fixture.close();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void preservesGameplayUuidWhenTheNextConnectionIsAuthenticated(String previousPremiumUuid) throws Exception {
    this.fixture.playerDao.update(this.account.setPremiumUuid(previousPremiumUuid));

    PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID);
    GameProfileRequestEvent login = new GameProfileRequestEvent(mock(InboundConnection.class),
        new GameProfile(PREMIUM_UUID, "Player", List.of()), true);
    new BackendProfileHandler(this.fixture.plugin, this.fixture.playerDao, null).handle(login);

    assertEquals("Player", login.getGameProfile().getName());
    assertEquals(this.player.getUniqueId(), login.getGameProfile().getId());
    assertEquals(PREMIUM_UUID.toString(), this.fixture.playerDao.queryForId("player").getPremiumUuid());
    RegisteredPlayer reserved = this.fixture.playerDao.queryForId("*player");
    assertEquals("", reserved.getPremiumUuid());
    assertFalse(AuthSessionHandler.checkPassword("old-password", reserved, this.fixture.playerDao));
  }

  @Test
  void refusesAnAccountLinkedToAnotherPremiumIdentity() throws Exception {
    UUID otherUuid = UUID.randomUUID();
    this.fixture.playerDao.update(this.account.setPremiumUuid(otherUuid));

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(otherUuid.toString(), this.fixture.playerDao.queryForId("*player").getPremiumUuid());
    assertEquals(this.account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
  }

  @Test
  void refusesPremiumIdentityAlreadyLinkedUnderAnotherName() throws Exception {
    RegisteredPlayer existing = this.fixture.createAccount("PreviousName", "").setPremiumUuid(PREMIUM_UUID);
    this.fixture.playerDao.update(existing);

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(this.account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    assertEquals(existing.getUuid(), this.fixture.playerDao.queryForId("previousname").getUuid());
  }

  @Test
  void refusesToOverwriteCredentialsChangedSincePasswordVerification() throws Exception {
    RegisteredPlayer changed = this.fixture.playerDao.queryForId("*player").setPassword("new-password");
    this.fixture.playerDao.update(changed);

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(changed.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    assertNull(this.fixture.playerDao.queryForId("player"));
  }

  @Test
  void refusesToOverwritePremiumBindingChangedSincePasswordVerification() throws Exception {
    UUID otherUuid = UUID.randomUUID();
    this.fixture.playerDao.update(this.fixture.playerDao.queryForId("*player").setPremiumUuid(otherUuid));

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(otherUuid.toString(), this.fixture.playerDao.queryForId("*player").getPremiumUuid());
    assertNull(this.fixture.playerDao.queryForId("player"));
  }

  @Test
  void restoresOriginalAccountWhenOfflineReservationFails() throws Exception {
    Dao<RegisteredPlayer, String> failingDao = spy(this.fixture.playerDao);
    doThrow(new SQLException("Reservation insert failed")).when(failingDao).create(any(RegisteredPlayer.class));

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(failingDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(this.account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    assertEquals(this.account.getUuid(), this.fixture.playerDao.queryForId("*player").getUuid());
    assertNull(this.fixture.playerDao.queryForId("player"));
    assertEquals(1, this.fixture.playerDao.countOf());
  }

  @Test
  void refusesUpgradeWhenUuidPreservationIsDisabled() throws Exception {
    Settings.IMP.MAIN.SAVE_UUID = false;

    assertThrows(SQLException.class, () -> PremiumAccounts.upgrade(this.fixture.playerDao, this.account, this.player, PREMIUM_UUID));

    assertEquals(this.account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    assertNull(this.fixture.playerDao.queryForId("player"));
  }
}
