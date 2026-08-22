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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackendAccountIdentityTest {

  private static final UUID AUTHENTICATED_UUID = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID PREMIUM_OFFLINE_UUID = UUID.fromString("560845de-b124-3b05-a9de-9cccb531473a");
  private static final UUID CRACKED_OFFLINE_UUID = UUID.fromString("0e702b72-cf33-3f64-8e9e-da89a97a6f9e");

  @Test
  void usesOfflineUuidForPremiumPlayerWhenCrackedNamespaceIsEnabled() {
    BackendAccountIdentity identity = this.resolve(true, false, false, "-", "");

    assertEquals("PyroEdged", identity.getUsername());
    assertEquals(PREMIUM_OFFLINE_UUID, identity.getUuid());
    assertTrue(identity.isOfflineUuid());
  }

  @Test
  void includesCrackedPrefixInOfflineUuid() {
    BackendAccountIdentity identity = this.resolve(false, false, false, "-", "");

    assertEquals("-PyroEdged", identity.getUsername());
    assertEquals(CRACKED_OFFLINE_UUID, identity.getUuid());
    assertNotEquals(PREMIUM_OFFLINE_UUID, identity.getUuid());
    assertTrue(identity.isOfflineUuid());
  }

  @Test
  void includesOnlinePrefixInOfflineUuid() {
    BackendAccountIdentity identity = this.resolve(true, false, false, "", "+");

    assertEquals("+PyroEdged", identity.getUsername());
    assertEquals(UUID.fromString("fe823df0-360c-3eeb-9aa4-308b53ee0c72"), identity.getUuid());
  }

  @Test
  void preservesAuthenticatedUuidWhenNamespaceAndForceSettingAreDisabled() {
    BackendAccountIdentity identity = this.resolve(true, false, false, "", "");

    assertEquals(AUTHENTICATED_UUID, identity.getUuid());
    assertFalse(identity.isOfflineUuid());
  }

  @Test
  void forceSettingUsesOfflineUuidWithoutNamespacePrefixes() {
    BackendAccountIdentity identity = this.resolve(true, false, true, "", "");

    assertEquals(PREMIUM_OFFLINE_UUID, identity.getUuid());
    assertTrue(identity.isOfflineUuid());
  }

  @Test
  void preservesFloodgateIdentity() {
    BackendAccountIdentity identity = this.resolve(false, true, true, "-", "+");

    assertEquals("PyroEdged", identity.getUsername());
    assertEquals(AUTHENTICATED_UUID, identity.getUuid());
    assertFalse(identity.isOfflineUuid());
  }

  private BackendAccountIdentity resolve(
      boolean isOnlineMode,
      boolean isFloodgatePlayer,
      boolean shouldForceOfflineUuid,
      String offlineModePrefix,
      String onlineModePrefix) {
    return BackendAccountIdentity.resolve(
        "PyroEdged",
        AUTHENTICATED_UUID,
        isOnlineMode,
        isFloodgatePlayer,
        shouldForceOfflineUuid,
        offlineModePrefix,
        onlineModePrefix
    );
  }
}
