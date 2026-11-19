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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.account.AccountDatabase;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PremiumCollisionLoginTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID SAVED_UUID = UUID.fromString("00000000-0000-3000-8000-000000000002");
  private static final UUID CRACKED_UUID = UUID.fromString("00000000-0000-3000-8000-000000000003");

  private AccountDatabase database;
  private Settings.MAIN previousSettings;
  private MockedStatic<LimboAuth> pluginStatics;
  private LimboAuth plugin;
  private AuthListener listener;
  private InboundConnection connection;

  @BeforeEach
  void prepareCollision() throws Exception {
    this.previousSettings = Settings.IMP.MAIN;
    Settings.IMP.MAIN = new Settings.MAIN();
    Settings.IMP.MAIN.STRINGS = new Settings.MAIN.STRINGS();
    Settings.IMP.MAIN.ONLINE_MODE_NEED_AUTH = false;
    Settings.IMP.MAIN.ONLINE_MODE_NEED_AUTH_STRICT = false;
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "*";
    Settings.IMP.MAIN.OVERWRITE_UNLINKED_OFFLINE_ACCOUNTS = true;
    this.database = new AccountDatabase();
    this.database.getPlayerDao().create(new RegisteredPlayer("PreviousName", SAVED_UUID.toString(), "192.0.2.1")
        .setPremiumUuid(PREMIUM_UUID));
    this.database.getPlayerDao().create(new RegisteredPlayer("CurrentName", UUID.randomUUID().toString(), "192.0.2.2")
        .setHash("occupied-password"));
    this.database.getPlayerDao().create(new RegisteredPlayer("*CurrentName", CRACKED_UUID.toString(), "192.0.2.3")
        .setHash("cracked-password"));
    this.plugin = mock(LimboAuth.class, CALLS_REAL_METHODS);
    this.setPluginField("playerDao", this.database.getPlayerDao());
    this.setPluginField("premiumCache", new ConcurrentHashMap<>());
    this.setPluginField("cachedAuthChecks", new ConcurrentHashMap<>());
    this.plugin.setPremiumCacheLowercased("currentname", false);
    doReturn(new LimboAuth.PremiumResponse(LimboAuth.PremiumState.PREMIUM_USERNAME, PREMIUM_UUID))
        .when(this.plugin).isPremiumExternal("CurrentName");
    Serializer serializer = mock(Serializer.class);
    when(serializer.deserialize(anyString())).thenReturn(Component.text("Authentication failed"));
    this.pluginStatics = mockStatic(LimboAuth.class);
    this.pluginStatics.when(LimboAuth::getSerializer).thenReturn(serializer);
    this.listener = new AuthListener(this.plugin, this.database.getPlayerDao(), null);
    this.connection = mock(InboundConnection.class);
    when(this.connection.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.4", 25565));
  }

  @AfterEach
  void restoreSettings() throws Exception {
    Settings.IMP.MAIN = this.previousSettings;
    this.pluginStatics.close();
    this.database.close();
  }

  @Test
  void requiresSessionVerificationBeforeChangingAnyCollisionAccount() throws Exception {
    PreLoginEvent login = new PreLoginEvent(this.connection, "CurrentName", PREMIUM_UUID);

    this.listener.onPreLoginEvent(login);

    assertTrue(login.getResult().isOnlineModeAllowed());
    assertFalse(login.getResult().isForceOfflineMode());
    assertEquals(3, this.database.getPlayerDao().countOf());
    assertEquals("occupied-password", this.database.getPlayerDao().queryForId("currentname").getHash());
    assertEquals("", this.database.getPlayerDao().queryForId("currentname").getPremiumUuid());
  }

  @Test
  void restoresReturningPremiumIdentityAcrossRenameCollisionAndReconnect() throws Exception {
    PreLoginEvent login = new PreLoginEvent(this.connection, "CurrentName", PREMIUM_UUID);
    this.listener.onPreLoginEvent(login);
    assertTrue(login.getResult().isOnlineModeAllowed());

    for (int attempt = 0; attempt < 2; attempt++) {
      GameProfileRequestEvent profile = this.profile(PREMIUM_UUID, true);
      this.listener.onGameProfileRequest(profile);
      assertEquals("CurrentName", profile.getGameProfile().getName());
      assertEquals(SAVED_UUID, profile.getGameProfile().getId());
    }

    RegisteredPlayer premium = this.database.getPlayerDao().queryForId("currentname");
    assertEquals(PREMIUM_UUID.toString(), premium.getPremiumUuid());
    assertEquals(SAVED_UUID.toString(), premium.getUuid());
    assertEquals("", premium.getHash());
    assertNull(this.database.getPlayerDao().queryForId("previousname"));
    assertEquals(2, this.database.getPlayerDao().countOf());
    this.assertCrackedAccountPreserved();
  }

  @Test
  void routesCrackedClientToSeparateAccountWithoutReplacingPremiumCollision() throws Exception {
    PreLoginEvent login = new PreLoginEvent(this.connection, "CurrentName", CRACKED_UUID);

    this.listener.onPreLoginEvent(login);
    GameProfileRequestEvent profile = this.profile(CRACKED_UUID, false);
    this.listener.onGameProfileRequest(profile);

    assertTrue(login.getResult().isForceOfflineMode());
    assertEquals("*CurrentName", profile.getGameProfile().getName());
    assertEquals(3, this.database.getPlayerDao().countOf());
    assertEquals("occupied-password", this.database.getPlayerDao().queryForId("currentname").getHash());
    assertEquals(PREMIUM_UUID.toString(), this.database.getPlayerDao().queryForId("previousname").getPremiumUuid());
    assertEquals("cracked-password", this.database.getPlayerDao().queryForId("*currentname").getHash());
  }

  private void assertCrackedAccountPreserved() throws Exception {
    RegisteredPlayer cracked = this.database.getPlayerDao().queryForId("*currentname");
    assertEquals("cracked-password", cracked.getHash());
    assertEquals(CRACKED_UUID.toString(), cracked.getUuid());
    assertEquals("", cracked.getPremiumUuid());
  }

  private GameProfileRequestEvent profile(UUID connectionUuid, boolean isOnlineMode) {
    return new GameProfileRequestEvent(this.connection, new GameProfile(connectionUuid, "CurrentName", List.of()), isOnlineMode);
  }

  private void setPluginField(String fieldName, Object fieldContents) throws Exception {
    Field field = LimboAuth.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(this.plugin, fieldContents);
  }
}
