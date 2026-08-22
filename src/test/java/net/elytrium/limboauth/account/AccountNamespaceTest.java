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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.elytrium.limboauth.model.RegisteredPlayer;
import org.junit.jupiter.api.Test;

class AccountNamespaceTest {

  @Test
  void excludesPrefixedCrackedAccountFromPremiumConnection() {
    RegisteredPlayer crackedAccount = this.account("-PyroEdged");

    assertNull(AccountNamespace.selectForConnection(crackedAccount, true, "-"));
  }

  @Test
  void retainsPremiumAccountForPremiumConnection() {
    RegisteredPlayer premiumAccount = this.account("PyroEdged");

    assertSame(premiumAccount, AccountNamespace.selectForConnection(premiumAccount, true, "-"));
  }

  @Test
  void retainsPrefixedAccountForCrackedConnection() {
    RegisteredPlayer crackedAccount = this.account("-PyroEdged");

    assertSame(crackedAccount, AccountNamespace.selectForConnection(crackedAccount, false, "-"));
  }

  @Test
  void retainsAccountWhenOfflinePrefixIsDisabled() {
    RegisteredPlayer account = this.account("-PyroEdged");

    assertSame(account, AccountNamespace.selectForConnection(account, true, ""));
  }

  @Test
  void retainsMissingAccount() {
    assertNull(AccountNamespace.selectForConnection(null, true, "-"));
  }

  private RegisteredPlayer account(String nickname) {
    return new RegisteredPlayer(nickname, "", "");
  }
}
