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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.GameProfile;
import java.util.List;
import java.util.UUID;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.floodgate.FloodgateApiHolder;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackendProfileHandlerTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID SAVED_UUID = UUID.fromString("00000000-0000-3000-8000-000000000002");

  private AccountDatabase database;
  private LimboAuth plugin;
  private BackendProfileHandler handler;
  private Settings.MAIN previousSettings;

  @BeforeEach
  void openDatabase() throws Exception {
    this.database = new AccountDatabase();
    this.plugin = mock(LimboAuth.class);
    this.handler = new BackendProfileHandler(this.plugin, this.database.getPlayerDao(), null);
    this.previousSettings = Settings.IMP.MAIN;
    Settings.IMP.MAIN = new Settings.MAIN();
    Settings.IMP.MAIN.SAVE_UUID = true;
    Settings.IMP.MAIN.FORCE_OFFLINE_UUID = false;
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "";
    Settings.IMP.MAIN.ONLINE_MODE_PREFIX = "";
  }

  @AfterEach
  void closeDatabase() throws Exception {
    Settings.IMP.MAIN = this.previousSettings;
    this.database.close();
  }

  @Test
  void repairsReportedConfigurationOnFirstLoginAndPreservesSavedUuidOnReconnect() throws Exception {
    this.database.createAccount("OldName", PREMIUM_UUID, SAVED_UUID);
    GameProfileRequestEvent event = this.event("CurrentName", true);
    GameProfile.Property texture = new GameProfile.Property("textures", "skin", "signature");
    event.setGameProfile(new GameProfile(PREMIUM_UUID, "SkinPluginName", List.of(texture)));

    this.handler.handle(event);

    assertEquals("CurrentName", event.getGameProfile().getName());
    assertEquals(SAVED_UUID, event.getGameProfile().getId());
    assertEquals(List.of(texture), event.getGameProfile().getProperties());
    assertNull(this.database.getPlayerDao().queryForId("oldname"));
    verify(this.plugin).removePlayerFromCache("OldName");
    verify(this.plugin).removePlayerFromCache("CurrentName");
    verify(this.plugin).cacheAuthenticatedPremiumUuid("CurrentName", PREMIUM_UUID);

    GameProfileRequestEvent reconnect = this.event("CurrentName", true);
    this.handler.handle(reconnect);
    assertEquals("CurrentName", reconnect.getGameProfile().getName());
    assertEquals(SAVED_UUID, reconnect.getGameProfile().getId());
    assertEquals(1, this.database.getPlayerDao().countOf());
  }

  @Test
  void preservesSavedUuidAcrossPrefixedRenameAndReconnect() throws Exception {
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "*";
    Settings.IMP.MAIN.ONLINE_MODE_PREFIX = "+";
    Settings.IMP.MAIN.FORCE_OFFLINE_UUID = true;
    this.database.createAccount("+OldName", PREMIUM_UUID, SAVED_UUID);

    for (int attempt = 0; attempt < 2; attempt++) {
      GameProfileRequestEvent event = this.event("CurrentName", true);
      this.handler.handle(event);
      assertEquals("+CurrentName", event.getGameProfile().getName());
      assertEquals(SAVED_UUID, event.getGameProfile().getId());
    }
    assertEquals(SAVED_UUID.toString(), this.database.getPlayerDao().queryForId("+currentname").getUuid());
  }

  @Test
  void repairsStoredNameEvenWhenUuidSavingAndPremiumAccountCreationAreDisabled() throws Exception {
    Settings.IMP.MAIN.SAVE_UUID = false;
    Settings.IMP.MAIN.SAVE_PREMIUM_ACCOUNTS = false;
    this.database.createAccount("OldName", PREMIUM_UUID, SAVED_UUID);
    GameProfileRequestEvent event = this.event("CurrentName", true);

    this.handler.handle(event);

    assertEquals("CurrentName", event.getGameProfile().getName());
    assertEquals(PREMIUM_UUID, event.getGameProfile().getId());
    assertEquals("CurrentName", this.database.getPlayerDao().queryForId("currentname").getNickname());
  }

  @Test
  void preservesRegisteredCasingForOfflinePlayers() throws Exception {
    this.database.createAccount("CurrentName", PREMIUM_UUID, SAVED_UUID);
    GameProfileRequestEvent event = this.event("currentname", false);

    this.handler.handle(event);

    assertEquals("CurrentName", event.getGameProfile().getName());
    assertEquals(SAVED_UUID, event.getGameProfile().getId());
    verify(this.plugin, never()).removePlayerFromCache("CurrentName");
  }

  @Test
  void doesNotRenameAnOfflineAccountWithTheSamePremiumUuidInAnotherNamespace() throws Exception {
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "*";
    this.database.createAccount("*OldName", PREMIUM_UUID, SAVED_UUID);
    GameProfileRequestEvent event = this.event("CurrentName", true);

    this.handler.handle(event);

    assertEquals("CurrentName", event.getGameProfile().getName());
    assertEquals("*OldName", this.database.getPlayerDao().queryForId("*oldname").getNickname());
    assertNull(this.database.getPlayerDao().queryForId("currentname"));
  }

  @Test
  void failsBeforeChangingProfileWhenDestinationNameIsOccupied() throws Exception {
    this.database.createAccount("OldName", PREMIUM_UUID, SAVED_UUID);
    this.database.getPlayerDao().create(new RegisteredPlayer("CurrentName", "", ""));
    GameProfileRequestEvent event = this.event("CurrentName", true);

    assertThrows(IllegalStateException.class, () -> this.handler.handle(event));
    assertEquals(PREMIUM_UUID, event.getGameProfile().getId());
    assertEquals("OldName", this.database.getPlayerDao().queryForId("oldname").getNickname());
    verify(this.plugin, never()).cacheAuthenticatedPremiumUuid("CurrentName", PREMIUM_UUID);
  }

  @Test
  void leavesFloodgateProfilesAndAccountsUnchanged() throws Exception {
    FloodgateApiHolder floodgate = mock(FloodgateApiHolder.class);
    when(floodgate.isFloodgatePlayer(PREMIUM_UUID)).thenReturn(true);
    this.database.createAccount(".OldName", PREMIUM_UUID, SAVED_UUID);
    GameProfileRequestEvent event = this.event(".CurrentName", true);

    new BackendProfileHandler(this.plugin, this.database.getPlayerDao(), floodgate).handle(event);

    assertEquals(event.getOriginalProfile(), event.getGameProfile());
    assertEquals(".OldName", this.database.getPlayerDao().queryForId(".oldname").getNickname());
  }

  private GameProfileRequestEvent event(String username, boolean isOnlineMode) {
    return new GameProfileRequestEvent(mock(InboundConnection.class), new GameProfile(PREMIUM_UUID, username, List.of()), isOnlineMode);
  }
}
