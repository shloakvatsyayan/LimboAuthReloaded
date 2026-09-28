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

package net.elytrium.limboauth.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AuthListenerTest {

  private static final Component FAILURE_MESSAGE = Component.text("Authentication failed");

  private Settings.MAIN previousSettings;
  private MockedStatic<LimboAuth> pluginStatics;
  private Dao<RegisteredPlayer, String> playerDao;
  private AuthListener listener;
  private GameProfile profile;
  private SQLException databaseFailure;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void prepareListener() throws Exception {
    this.previousSettings = Settings.IMP.MAIN;
    Settings.IMP.MAIN = new Settings.MAIN();
    Settings.IMP.MAIN.STRINGS = new Settings.MAIN.STRINGS();
    Serializer serializer = mock(Serializer.class);
    when(serializer.deserialize(anyString())).thenReturn(FAILURE_MESSAGE);
    this.playerDao = mock(Dao.class);
    UUID premiumUuid = UUID.randomUUID();
    this.databaseFailure = new SQLException("Database unavailable");
    when(this.playerDao.queryForEq(RegisteredPlayer.PREMIUM_UUID_FIELD, premiumUuid.toString()))
        .thenThrow(this.databaseFailure);
    this.profile = new GameProfile(premiumUuid, "CurrentName", List.of());
    this.pluginStatics = mockStatic(LimboAuth.class);
    this.pluginStatics.when(LimboAuth::getSerializer).thenReturn(serializer);
    this.listener = new AuthListener(mock(LimboAuth.class), this.playerDao, null);
  }

  @AfterEach
  void restoreSettings() {
    Settings.IMP.MAIN = this.previousSettings;
    if (this.pluginStatics != null) {
      this.pluginStatics.close();
    }
  }

  @Test
  void disconnectsLoginConnectionWhenAccountLookupFails() {
    LoginInboundConnection connection = mock(LoginInboundConnection.class);

    this.assertDatabaseFailure(connection);

    verify(connection).disconnect(FAILURE_MESSAGE);
  }

  @Test
  void disconnectsLimboInitialConnectionWithoutMaskingDatabaseFailure() {
    InitialInboundConnection connection = mock(InitialInboundConnection.class);

    this.assertDatabaseFailure(connection);

    verify(connection).disconnect(FAILURE_MESSAGE);
  }

  @Test
  void preservesOriginalFailureIfDisconnectAlsoFails() {
    InitialInboundConnection connection = mock(InitialInboundConnection.class);
    IllegalStateException disconnectFailure = new IllegalStateException("Connection closed during disconnect");
    doThrow(disconnectFailure).when(connection).disconnect(FAILURE_MESSAGE);

    SQLRuntimeException failure = this.assertDatabaseFailure(connection);

    assertEquals(1, failure.getSuppressed().length);
    assertSame(disconnectFailure, failure.getSuppressed()[0]);
  }

  @Test
  void reportsUnsupportedConnectionTypeAlongsideOriginalFailure() {
    SQLRuntimeException failure = this.assertDatabaseFailure(mock(InboundConnection.class));

    assertEquals(1, failure.getSuppressed().length);
    assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[0]);
  }

  @Test
  void disconnectsInitialConnectionWhenPremiumIdentityConflicts() throws Exception {
    UUID differentUuid = UUID.randomUUID();
    when(this.playerDao.queryForEq(RegisteredPlayer.PREMIUM_UUID_FIELD, differentUuid.toString())).thenReturn(List.of());
    RegisteredPlayer occupied = new RegisteredPlayer("CurrentName", "", "").setPremiumUuid(this.profile.getId());
    when(this.playerDao.queryForEq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, "currentname")).thenReturn(List.of(occupied));
    InitialInboundConnection connection = mock(InitialInboundConnection.class);
    GameProfileRequestEvent event = new GameProfileRequestEvent(connection, new GameProfile(differentUuid, "CurrentName", List.of()), true);

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> this.listener.onGameProfileRequest(event));

    assertEquals("Username CurrentName is linked to a different premium account", failure.getMessage());
    verify(connection).disconnect(FAILURE_MESSAGE);
  }

  @Test
  void leavesHealthyInitialConnectionsOpen() throws Exception {
    Settings.IMP.MAIN.SAVE_UUID = true;
    InitialInboundConnection connection = mock(InitialInboundConnection.class);
    GameProfileRequestEvent event = new GameProfileRequestEvent(connection, this.profile, false);

    this.listener.onGameProfileRequest(event);

    verifyNoInteractions(connection);
    assertEquals(this.profile.getName(), event.getGameProfile().getName());
  }

  private SQLRuntimeException assertDatabaseFailure(InboundConnection connection) {
    GameProfileRequestEvent event = new GameProfileRequestEvent(connection, this.profile, true);
    SQLRuntimeException failure = assertThrows(SQLRuntimeException.class, () -> this.listener.onGameProfileRequest(event));
    assertSame(this.databaseFailure, failure.getCause());
    assertEquals(event.getOriginalProfile(), event.getGameProfile());
    return failure;
  }
}
