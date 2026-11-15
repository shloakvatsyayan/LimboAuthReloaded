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

package net.elytrium.limboauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.elytrium.limboauth.LimboAuth.PremiumResponse;
import net.elytrium.limboauth.LimboAuth.PremiumState;
import net.elytrium.limboauth.account.AccountDatabase;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PremiumAccountPriorityTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");

  private AccountDatabase database;
  private Settings.MAIN previousSettings;
  private LimboAuth plugin;

  @BeforeEach
  void preparePlugin() throws Exception {
    this.database = new AccountDatabase();
    this.previousSettings = Settings.IMP.MAIN;
    Settings.IMP.MAIN = new Settings.MAIN();
    Settings.IMP.MAIN.OVERWRITE_UNLINKED_OFFLINE_ACCOUNTS = true;
    this.plugin = mock(LimboAuth.class, CALLS_REAL_METHODS);
    Field playerDao = LimboAuth.class.getDeclaredField("playerDao");
    playerDao.setAccessible(true);
    playerDao.set(this.plugin, this.database.getPlayerDao());
    Field premiumCache = LimboAuth.class.getDeclaredField("premiumCache");
    premiumCache.setAccessible(true);
    premiumCache.set(this.plugin, new ConcurrentHashMap<>());
    this.database.getPlayerDao().create(new RegisteredPlayer("CurrentName", "offline-uuid", "192.0.2.1").setHash("password"));
    doReturn(new PremiumResponse(PremiumState.PREMIUM_USERNAME, PREMIUM_UUID)).when(this.plugin).isPremiumExternal("CurrentName");
  }

  @AfterEach
  void closeDatabase() throws Exception {
    Settings.IMP.MAIN = this.previousSettings;
    this.database.close();
  }

  @Test
  void verifiesPremiumOwnershipDespiteOfflineRegistrationAndCachedOfflineStatus() throws Exception {
    this.plugin.setPremiumCacheLowercased("currentname", false);

    assertTrue(this.plugin.isPremium("CurrentName"));

    assertEquals(PREMIUM_UUID, this.plugin.getPremiumCache("CurrentName").getPremiumUuid());
    assertTrue(this.plugin.getPremiumCache("CurrentName").isForcePremium());
    assertEquals("password", this.database.getPlayerDao().queryForId("currentname").getHash());
    assertEquals("", this.database.getPlayerDao().queryForId("currentname").getPremiumUuid());
  }

  @Test
  void retainsExistingOfflineRoutingWhenOptionIsDisabled() {
    Settings.IMP.MAIN.OVERWRITE_UNLINKED_OFFLINE_ACCOUNTS = false;

    assertFalse(this.plugin.isPremium("CurrentName"));

    verify(this.plugin, never()).isPremiumExternal("CurrentName");
  }

  @Test
  void neverOverridesForceOfflineMode() {
    Settings.IMP.MAIN.FORCE_OFFLINE_MODE = true;

    assertFalse(this.plugin.isPremium("CurrentName"));

    verify(this.plugin, never()).isPremiumExternal("CurrentName");
  }

  @ParameterizedTest
  @EnumSource(value = PremiumState.class, names = {"CRACKED", "UNKNOWN", "RATE_LIMIT", "ERROR"})
  void leavesUnverifiedAccountsOfflineAndUnmodified(PremiumState state) throws Exception {
    doReturn(new PremiumResponse(state)).when(this.plugin).isPremiumExternal("CurrentName");

    assertFalse(this.plugin.isPremium("CurrentName"));

    assertEquals("password", this.database.getPlayerDao().queryForId("currentname").getHash());
    assertEquals("", this.database.getPlayerDao().queryForId("currentname").getPremiumUuid());
  }

  @Test
  void retainsLinkedOfflineAccountRouting() throws Exception {
    RegisteredPlayer account = this.database.getPlayerDao().queryForId("currentname").setPremiumUuid(UUID.randomUUID());
    this.database.getPlayerDao().update(account);

    assertFalse(this.plugin.isPremium("CurrentName"));

    verify(this.plugin, never()).isPremiumExternal("CurrentName");
  }

  @Test
  void keepsMismatchedClientUuidOffline() {
    assertFalse(this.plugin.isPremium("CurrentName", UUID.randomUUID()));
    assertTrue(this.plugin.isPremium("CurrentName", PREMIUM_UUID));
  }

  @Test
  void looksUpTheConfiguredPremiumNamespace() throws Exception {
    Settings.IMP.MAIN.ONLINE_MODE_PREFIX = "+";
    Settings.IMP.MAIN.OFFLINE_MODE_PREFIX = "*";
    this.database.getPlayerDao().create(new RegisteredPlayer("+CurrentName", "offline-uuid", "192.0.2.1").setHash("password"));

    assertTrue(this.plugin.isPremium("CurrentName", PREMIUM_UUID));
  }
}
