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

package net.elytrium.limboauth.command;

import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;

public class PremiumCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final String confirmKeyword;
  private final Component notRegistered;
  private final Component alreadyPremium;
  private final Component successful;
  private final Component errorOccurred;
  private final Component notPremium;
  private final Component wrongPassword;
  private final Component usage;
  private final Component notPlayer;

  public PremiumCommand(LimboAuth plugin, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.playerDao = playerDao;

    Serializer serializer = LimboAuth.getSerializer();
    this.confirmKeyword = Settings.IMP.MAIN.CONFIRM_KEYWORD;
    this.notRegistered = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_REGISTERED);
    this.alreadyPremium = serializer.deserialize(Settings.IMP.MAIN.STRINGS.ALREADY_PREMIUM);
    this.successful = serializer.deserialize(Settings.IMP.MAIN.STRINGS.PREMIUM_SUCCESSFUL);
    this.errorOccurred = serializer.deserialize(Settings.IMP.MAIN.STRINGS.ERROR_OCCURRED);
    this.notPremium = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PREMIUM);
    this.wrongPassword = serializer.deserialize(Settings.IMP.MAIN.STRINGS.WRONG_PASSWORD);
    this.usage = serializer.deserialize(Settings.IMP.MAIN.STRINGS.PREMIUM_USAGE);
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (!(source instanceof Player player)) {
      source.sendMessage(this.notPlayer);
      return;
    }
    if (args.length != 2 || !this.confirmKeyword.equalsIgnoreCase(args[1])) {
      source.sendMessage(this.usage);
      return;
    }

    try {
      RegisteredPlayer account = PremiumAccounts.findPlayer(this.playerDao, player);
      if (account == null) {
        source.sendMessage(this.notRegistered);
        return;
      }
      if (account.getHash().isEmpty()) {
        source.sendMessage(this.alreadyPremium);
        return;
      }
      if (!AuthSessionHandler.checkPassword(args[0], account, this.playerDao)) {
        source.sendMessage(this.wrongPassword);
        return;
      }

      String username = PremiumAccounts.connectionUsername(player);
      LimboAuth.PremiumResponse premium = this.plugin.isPremiumExternal(username.toLowerCase(Locale.ROOT));
      if (premium.getState() != LimboAuth.PremiumState.PREMIUM_USERNAME || premium.getUuid() == null) {
        source.sendMessage(this.notPremium);
        return;
      }

      final String premiumUsername = PremiumAccounts.upgrade(this.playerDao, account, player, premium.getUuid());
      this.plugin.removePlayerFromCacheLowercased(account.getLowercaseNickname());
      this.plugin.removePlayerFromCache(player.getUsername());
      this.plugin.removePlayerFromCache(username);
      if (!premiumUsername.equals(username)) {
        this.plugin.removePlayerFromCache(premiumUsername);
      }
      player.disconnect(this.successful);
    } catch (SQLException exception) {
      source.sendMessage(this.errorOccurred);
      throw new SQLRuntimeException(exception);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.PREMIUM
        .hasPermission(invocation.source(), "limboauth.commands.premium");
  }
}
