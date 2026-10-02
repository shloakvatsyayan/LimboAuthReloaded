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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.sql.SQLException;
import java.util.UUID;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.account.AccountDatabase;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.kyori.adventure.text.Component;
import org.mockito.MockedStatic;

final class PasswordCommandFixture implements AutoCloseable {

  final AccountDatabase database;
  final Dao<RegisteredPlayer, String> playerDao;
  final LimboAuth plugin = mock(LimboAuth.class);
  final ProxyServer server = mock(ProxyServer.class);
  final EventManager events = mock(EventManager.class);
  final CommandSource admin = mock(CommandSource.class);
  private final Settings.MAIN previousSettings;
  private final MockedStatic<LimboAuth> pluginStatics;

  PasswordCommandFixture() throws SQLException {
    this.database = new AccountDatabase();
    this.playerDao = this.database.getPlayerDao();
    this.previousSettings = Settings.IMP.MAIN;
    Settings.IMP.MAIN = new Settings.MAIN();
    Settings.IMP.MAIN.STRINGS = new Settings.MAIN.STRINGS();
    Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_MESSAGE = "Password changed to {0}";
    Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_SUCCESSFUL = "Password changed for {0}";
    Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL = "Unable to change password for {0}";
    Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_REGISTERED = "Account {0} is not registered";
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "*";
    Settings.IMP.MAIN.BCRYPT_COST = 4;
    Serializer serializer = mock(Serializer.class);
    when(serializer.deserialize(anyString())).thenAnswer(invocation -> Component.text(invocation.getArgument(0, String.class)));
    this.pluginStatics = mockStatic(LimboAuth.class);
    this.pluginStatics.when(LimboAuth::getSerializer).thenReturn(serializer);
    when(this.plugin.getServer()).thenReturn(this.server);
    when(this.server.getEventManager()).thenReturn(this.events);
  }

  RegisteredPlayer createAccount(String nickname, String password) throws SQLException {
    RegisteredPlayer account = new RegisteredPlayer(nickname, UUID.randomUUID().toString(), "127.0.0.1");
    if (!password.isEmpty()) {
      account.setPassword(password);
    }
    account.setTokenIssuedAt(1L);
    this.playerDao.create(account);
    return account;
  }

  Player player(String nickname, RegisteredPlayer account, boolean isOnlineMode) {
    Player player = mock(Player.class);
    when(player.getUsername()).thenReturn(nickname);
    when(player.getUniqueId()).thenReturn(UUID.fromString(account.getUuid()));
    when(player.isOnlineMode()).thenReturn(isOnlineMode);
    return player;
  }

  ChangePasswordCommand playerCommand() {
    return new ChangePasswordCommand(this.plugin, this.playerDao);
  }

  ForceChangePasswordCommand adminCommand() {
    return new ForceChangePasswordCommand(this.plugin, this.server, this.playerDao);
  }

  @Override
  public void close() throws Exception {
    this.pluginStatics.close();
    Settings.IMP.MAIN = this.previousSettings;
    this.database.close();
  }
}
