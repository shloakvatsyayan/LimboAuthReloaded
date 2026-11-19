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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.Player;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PremiumCommandTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID BACKEND_UUID = UUID.nameUUIDFromBytes("OfflinePlayer:*Player".getBytes(StandardCharsets.UTF_8));

  private PasswordCommandFixture fixture;
  private PremiumCommand command;

  @BeforeEach
  void prepareCommand() throws Exception {
    this.fixture = new PasswordCommandFixture();
    this.command = new PremiumCommand(this.fixture.plugin, this.fixture.playerDao);
    when(this.fixture.plugin.isPremiumExternal("player"))
        .thenReturn(new LimboAuth.PremiumResponse(LimboAuth.PremiumState.PREMIUM_USERNAME, PREMIUM_UUID));
  }

  @AfterEach
  void closeDatabase() throws Exception {
    this.fixture.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Player", "*Player"})
  void upgradesLegacyAndPrefixedRegistrationsWithoutChangingGameplayIdentity(String registeredName) throws Exception {
    RegisteredPlayer account = this.account(registeredName);
    Player player = this.player(account);

    this.command.execute(player, new String[]{"old-password", "confirm"});

    RegisteredPlayer upgraded = this.fixture.playerDao.queryForId("player");
    assertEquals(PREMIUM_UUID.toString(), upgraded.getPremiumUuid());
    assertEquals(BACKEND_UUID.toString(), upgraded.getUuid());
    assertEquals("", upgraded.getHash());
    assertEquals(account.getRegDate(), upgraded.getRegDate());
    assertTrue(upgraded.getTokenIssuedAt() > 1);
    RegisteredPlayer reserved = this.fixture.playerDao.queryForId("*player");
    assertFalse(AuthSessionHandler.checkPassword("old-password", reserved, this.fixture.playerDao));
    assertEquals("", reserved.getPremiumUuid());
    assertEquals(2, this.fixture.playerDao.countOf());
    verify(this.fixture.plugin).isPremiumExternal("player");
    verify(this.fixture.plugin).removePlayerFromCacheLowercased(account.getLowercaseNickname());
    verify(this.fixture.plugin).removePlayerFromCache("*Player");
    verify(this.fixture.plugin).removePlayerFromCache("Player");
    verify(player).disconnect(Component.text(Settings.IMP.MAIN.STRINGS.PREMIUM_SUCCESSFUL));
  }

  @Test
  void refusesToReplaceAnExistingPremiumNamespaceAccount() throws Exception {
    RegisteredPlayer cracked = this.account("*Player");
    RegisteredPlayer occupied = this.fixture.createAccount("Player", "another-password");
    Player player = this.player(cracked);

    assertThrows(SQLRuntimeException.class, () -> this.command.execute(player, new String[]{"old-password", "confirm"}));

    assertEquals(cracked.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    assertEquals(occupied.getHash(), this.fixture.playerDao.queryForId("player").getHash());
    verify(player, never()).disconnect(any(Component.class));
  }

  @Test
  void rejectsWrongPasswordBeforeLookingUpPremiumOwnership() throws Exception {
    RegisteredPlayer account = this.account("*Player");
    Player player = this.player(account);

    this.command.execute(player, new String[]{"wrong-password", "confirm"});

    assertEquals(account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    verify(this.fixture.plugin, never()).isPremiumExternal(anyString());
    verify(player).sendMessage(Component.text(Settings.IMP.MAIN.STRINGS.WRONG_PASSWORD));
  }

  @Test
  void rejectsLegacyFallbackWhenGameplayIdentityDoesNotMatch() throws Exception {
    RegisteredPlayer account = this.account("Player");
    Player player = this.player(account);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());

    this.command.execute(player, new String[]{"old-password", "confirm"});

    assertEquals(account.getHash(), this.fixture.playerDao.queryForId("player").getHash());
    verify(this.fixture.plugin, never()).isPremiumExternal(anyString());
    verify(player).sendMessage(Component.text(Settings.IMP.MAIN.STRINGS.NOT_REGISTERED));
  }

  @Test
  void rejectsPremiumResponseWithoutAnAccountUuid() throws Exception {
    RegisteredPlayer account = this.account("*Player");
    Player player = this.player(account);
    when(this.fixture.plugin.isPremiumExternal("player"))
        .thenReturn(new LimboAuth.PremiumResponse(LimboAuth.PremiumState.PREMIUM_USERNAME));

    this.command.execute(player, new String[]{"old-password", "confirm"});

    assertEquals(account.getHash(), this.fixture.playerDao.queryForId("*player").getHash());
    verify(player).sendMessage(Component.text(Settings.IMP.MAIN.STRINGS.NOT_PREMIUM));
  }

  @Test
  void respectsConfiguredPremiumPrefix() throws Exception {
    Settings.IMP.MAIN.ONLINE_MODE_PREFIX = "+";
    RegisteredPlayer account = this.account("*Player");

    this.command.execute(this.player(account), new String[]{"old-password", "confirm"});

    assertEquals(BACKEND_UUID.toString(), this.fixture.playerDao.queryForId("+player").getUuid());
    assertFalse(AuthSessionHandler.checkPassword("old-password", this.fixture.playerDao.queryForId("*player"), this.fixture.playerDao));
    verify(this.fixture.plugin).removePlayerFromCache("+Player");
  }

  private RegisteredPlayer account(String username) throws Exception {
    RegisteredPlayer account = this.fixture.createAccount(username, "old-password");
    account.setUuid(UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString());
    this.fixture.playerDao.update(account);
    return account;
  }

  private Player player(RegisteredPlayer account) {
    Player player = this.fixture.player("*Player", account, false);
    when(player.getUniqueId()).thenReturn(BACKEND_UUID);
    return player;
  }
}
