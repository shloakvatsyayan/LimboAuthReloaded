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

package net.elytrium.limboauth.totp;

import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class PendingTotpEnrollments {

  private final Map<String, String> secrets = new HashMap<>();

  public synchronized void begin(String lowercaseUsername, String secret) {
    this.secrets.put(lowercaseUsername, secret);
  }

  @Nullable
  public synchronized String find(String lowercaseUsername) {
    return this.secrets.get(lowercaseUsername);
  }

  public synchronized boolean complete(String lowercaseUsername, String secret) {
    return this.secrets.remove(lowercaseUsername, secret);
  }

  public synchronized void cancel(String lowercaseUsername) {
    this.secrets.remove(lowercaseUsername);
  }
}
