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

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CachedPremiumUserTest {

  private static final UUID PREMIUM_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID OFFLINE_UUID = UUID.fromString("00000000-0000-4000-8000-000000000002");

  @Test
  void usesOnlineModeWhenConnectionUuidMatchesPremiumAccount() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true, PREMIUM_UUID);

    assertTrue(premiumUser.isPremium(PREMIUM_UUID));
  }

  @Test
  void exposesAuthenticatedUuidForDatabasePersistence() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true, PREMIUM_UUID);

    assertEquals(PREMIUM_UUID, premiumUser.getPremiumUuid());
  }

  @Test
  void usesOfflineModeWhenConnectionUuidDoesNotMatchPremiumAccount() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true, PREMIUM_UUID);

    assertFalse(premiumUser.isPremium(OFFLINE_UUID));
  }

  @Test
  void preservesPremiumRoutingWhenAccountUuidIsUnavailable() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true);

    assertTrue(premiumUser.isPremium(OFFLINE_UUID));
  }

  @Test
  void preservesLegacyRoutingWhenConnectionUuidIsUnavailable() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true, PREMIUM_UUID);

    assertTrue(premiumUser.isPremium(null));
  }

  @Test
  void keepsCrackedAccountsOfflineForEveryConnectionUuid() {
    LimboAuth.CachedPremiumUser crackedUser = new LimboAuth.CachedPremiumUser(0, false, PREMIUM_UUID);

    assertFalse(crackedUser.isPremium(PREMIUM_UUID));
    assertFalse(crackedUser.isPremium(OFFLINE_UUID));
    assertFalse(crackedUser.isPremium(null));
  }

  @Test
  void keepsDifferentConnectionUuidOfflineAfterPremiumVerification() {
    LimboAuth.CachedPremiumUser premiumUser = new LimboAuth.CachedPremiumUser(0, true, PREMIUM_UUID);
    premiumUser.setForcePremium(true);

    assertTrue(premiumUser.isForcePremium());
    assertFalse(premiumUser.isPremium(OFFLINE_UUID));
  }
}
