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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Optional;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.ChangePasswordEvent;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ForceChangePasswordCommandTest {

  private PasswordCommandFixture fixture;

  @BeforeEach
  void prepareCommand() throws Exception {
    this.fixture = new PasswordCommandFixture();
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.fixture.close();
  }

  @Test
  void resolvesBareNameToCrackedAccountAndNotifiesCanonicalPlayer() throws Exception {
    RegisteredPlayer account = this.fixture.createAccount("*Player", "old-password");
    Player player = this.fixture.player("*Player", account, false);
    when(this.fixture.server.getPlayer("*Player")).thenReturn(Optional.of(player));

    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"pLaYeR", "new-password"});

    RegisteredPlayer updated = this.fixture.playerDao.queryForId("*player");
    assertTrue(AuthSessionHandler.checkPassword("new-password", updated, this.fixture.playerDao));
    assertFalse(AuthSessionHandler.checkPassword("old-password", updated, this.fixture.playerDao));
    assertTrue(updated.getTokenIssuedAt() > 1);
    verify(player).sendMessage(Component.text(MessageFormat.format(Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_MESSAGE, "new-password")));
    verify(this.fixture.admin).sendMessage(Component.text(MessageFormat.format(
        Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_SUCCESSFUL, "*Player")));
    verify(this.fixture.plugin).removePlayerFromCacheLowercased("*player");
    ArgumentCaptor<ChangePasswordEvent> event = ArgumentCaptor.forClass(ChangePasswordEvent.class);
    verify(this.fixture.events).fireAndForget(event.capture());
    assertEquals(account.getHash(), event.getValue().getOldHash());
    assertEquals(updated.getHash(), event.getValue().getPlayerInfo().getHash());
  }

  @Test
  void explicitCrackedNameNeverChangesSameNamedPremiumAccount() throws Exception {
    final RegisteredPlayer premium = this.fixture.createAccount("Player", "premium-password");
    this.fixture.createAccount("*Player", "old-password");

    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"*PLAYER", "new-password"});

    assertTrue(AuthSessionHandler.checkPassword("new-password", this.fixture.playerDao.queryForId("*player"), this.fixture.playerDao));
    assertEquals(premium.getHash(), this.fixture.playerDao.queryForId("player").getHash());
  }

  @Test
  void exactNameTakesPrecedenceOverPrefixedFallback() throws Exception {
    this.fixture.createAccount("Player", "premium-password");
    RegisteredPlayer cracked = this.fixture.createAccount("*Player", "old-password");

    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"Player", "new-password"});

    assertTrue(AuthSessionHandler.checkPassword("new-password", this.fixture.playerDao.queryForId("player"), this.fixture.playerDao));
    assertEquals(cracked.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
  }

  @Test
  void reportsMissingAccountWithoutSuccessEvent() {
    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"Missing", "new-password"});

    verify(this.fixture.admin).sendMessage(Component.text(MessageFormat.format(
        Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_REGISTERED, "Missing")));
    verifyNoInteractions(this.fixture.events);
  }

  @Test
  void worksWithPrefixesDisabled() throws Exception {
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "";
    this.fixture.createAccount("Player", "old-password");

    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"Player", "new-password"});

    assertTrue(AuthSessionHandler.checkPassword("new-password", this.fixture.playerDao.queryForId("player"), this.fixture.playerDao));
  }

  @Test
  void doesNotStripExplicitPrefixToTargetDifferentAccount() throws Exception {
    final RegisteredPlayer premium = this.fixture.createAccount("Player", "premium-password");

    this.fixture.adminCommand().execute(this.fixture.admin, new String[]{"*Player", "new-password"});

    assertEquals(premium.getHash(), this.fixture.playerDao.queryForId("player").getHash());
    verifyNoInteractions(this.fixture.events);
  }

  @Test
  void reportsDatabaseLookupFailureWithoutSuccess() throws Exception {
    Dao<RegisteredPlayer, String> failingDao = spy(this.fixture.playerDao);
    doThrow(new SQLException("Account lookup unavailable")).when(failingDao).queryForId("*player");
    ForceChangePasswordCommand command = new ForceChangePasswordCommand(this.fixture.plugin, this.fixture.server, failingDao);

    assertThrows(SQLRuntimeException.class, () -> command.execute(this.fixture.admin, new String[]{"*Player", "new-password"}));

    verify(this.fixture.admin).sendMessage(Component.text(MessageFormat.format(
        Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL, "*Player")));
    verifyNoInteractions(this.fixture.events);
  }
}
