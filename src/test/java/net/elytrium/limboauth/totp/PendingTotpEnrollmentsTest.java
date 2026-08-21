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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PendingTotpEnrollmentsTest {

  private final PendingTotpEnrollments enrollments = new PendingTotpEnrollments();

  @Test
  void storesPendingSecretByLowercaseUsername() {
    this.enrollments.begin("player", "first-secret");

    assertEquals("first-secret", this.enrollments.find("player"));
    assertNull(this.enrollments.find("Player"));
  }

  @Test
  void replacesSecretWhenSetupRestarts() {
    this.enrollments.begin("player", "first-secret");
    this.enrollments.begin("player", "second-secret");

    assertEquals("second-secret", this.enrollments.find("player"));
  }

  @Test
  void completesOnlyMatchingEnrollment() {
    this.enrollments.begin("player", "current-secret");

    assertFalse(this.enrollments.complete("player", "stale-secret"));
    assertEquals("current-secret", this.enrollments.find("player"));
    assertTrue(this.enrollments.complete("player", "current-secret"));
    assertNull(this.enrollments.find("player"));
  }

  @Test
  void cancelsPendingEnrollment() {
    this.enrollments.begin("player", "current-secret");

    this.enrollments.cancel("player");

    assertNull(this.enrollments.find("player"));
  }
}
