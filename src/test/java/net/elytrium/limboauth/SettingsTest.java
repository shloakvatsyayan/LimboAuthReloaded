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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

  @TempDir
  private Path configDirectory;

  @Test
  void generatesDisabledOverwriteOptionAndLoadsExplicitOptIn() throws Exception {
    Settings settings = new Settings();
    Path configFile = this.configDirectory.resolve("config.yml");
    settings.reload(configFile);

    assertFalse(settings.MAIN.OVERWRITE_UNLINKED_OFFLINE_ACCOUNTS);
    String configuration = Files.readString(configFile);
    assertTrue(configuration.contains("overwrite-unlinked-offline-accounts: false"));

    Files.writeString(configFile, configuration.replace(
        "overwrite-unlinked-offline-accounts: false", "overwrite-unlinked-offline-accounts: true"));
    settings.reload(configFile);

    assertTrue(settings.MAIN.OVERWRITE_UNLINKED_OFFLINE_ACCOUNTS);
  }
}
