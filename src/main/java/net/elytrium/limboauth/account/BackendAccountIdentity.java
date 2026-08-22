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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class BackendAccountIdentity {

  private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";

  private final String username;
  private final UUID uuid;
  private final boolean isOfflineUuid;

  private BackendAccountIdentity(String username, UUID uuid, boolean isOfflineUuid) {
    this.username = username;
    this.uuid = uuid;
    this.isOfflineUuid = isOfflineUuid;
  }

  public static BackendAccountIdentity resolve(
      String connectionUsername,
      UUID authenticatedUuid,
      boolean isOnlineMode,
      boolean isFloodgatePlayer,
      boolean shouldForceOfflineUuid,
      String offlineModePrefix,
      String onlineModePrefix) {
    Objects.requireNonNull(connectionUsername, "connectionUsername");
    Objects.requireNonNull(authenticatedUuid, "authenticatedUuid");
    Objects.requireNonNull(offlineModePrefix, "offlineModePrefix");
    Objects.requireNonNull(onlineModePrefix, "onlineModePrefix");

    if (isFloodgatePlayer) {
      return new BackendAccountIdentity(connectionUsername, authenticatedUuid, false);
    }

    String usernamePrefix = isOnlineMode ? onlineModePrefix : offlineModePrefix;
    String backendUsername = usernamePrefix + connectionUsername;
    boolean isOfflineUuid = shouldForceOfflineUuid || !offlineModePrefix.isEmpty() || !onlineModePrefix.isEmpty();
    UUID backendUuid = isOfflineUuid ? generateOfflineUuid(backendUsername) : authenticatedUuid;
    return new BackendAccountIdentity(backendUsername, backendUuid, isOfflineUuid);
  }

  private static UUID generateOfflineUuid(String username) {
    return UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + username).getBytes(StandardCharsets.UTF_8));
  }

  public String getUsername() {
    return this.username;
  }

  public UUID getUuid() {
    return this.uuid;
  }

  public boolean isOfflineUuid() {
    return this.isOfflineUuid;
  }
}
