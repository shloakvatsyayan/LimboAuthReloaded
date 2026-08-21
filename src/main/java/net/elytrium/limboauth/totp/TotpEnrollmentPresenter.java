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

import com.velocitypowered.api.command.CommandSource;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

public final class TotpEnrollmentPresenter {

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
  private final String issuer = Settings.IMP.MAIN.TOTP_ISSUER;
  private final String qrGeneratorUrl = Settings.IMP.MAIN.QR_GENERATOR_URL;
  private final Component qr = LimboAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.TOTP_QR);
  private final String token = Settings.IMP.MAIN.STRINGS.TOTP_TOKEN;
  private final int recoveryCodesAmount = Settings.IMP.MAIN.TOTP_RECOVERY_CODES_AMOUNT;
  private final String recovery = Settings.IMP.MAIN.STRINGS.TOTP_RECOVERY;
  private final Component verify = LimboAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.TOTP_VERIFY);

  public String createSecret() {
    return this.secretGenerator.generate();
  }

  public void sendSetup(CommandSource source, String username, String secret) {
    QrData qrData = new QrData.Builder()
        .label(username)
        .secret(secret)
        .issuer(this.issuer)
        .build();
    String qrUrl = this.qrGeneratorUrl.replace("{data}", URLEncoder.encode(qrData.getUri(), StandardCharsets.UTF_8));
    source.sendMessage(this.qr.clickEvent(ClickEvent.openUrl(qrUrl)));

    Serializer serializer = LimboAuth.getSerializer();
    source.sendMessage(serializer.deserialize(MessageFormat.format(this.token, secret))
        .clickEvent(ClickEvent.copyToClipboard(secret)));
    String recoveryCodes = String.join(", ", this.recoveryCodeGenerator.generateCodes(this.recoveryCodesAmount));
    source.sendMessage(serializer.deserialize(MessageFormat.format(this.recovery, recoveryCodes))
        .clickEvent(ClickEvent.copyToClipboard(recoveryCodes)));
    source.sendMessage(this.verify);
  }
}
