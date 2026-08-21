# LimboAuthReloaded

[![Join our Discord](https://img.shields.io/discord/775778822334709780.svg?logo=discord&label=Discord)](https://discord.gg/bonknetwork)

Auth System built in a virtual server (Limbo). This is a fork of [LimboAuth](https://github.com/Elytrium/LimboAuth), and is currently used on `play.bonkmc.net`. The goal of this fork is simply to add fixes, features, and future updates to a rarely maintained and useful project.

## See also

- [LimboFilter](https://github.com/Elytrium/LimboFilter) - Most powerful bot filtering solution for Minecraft proxies. Built with [LimboAPI](https://github.com/Elytrium/LimboAPI).
- [LimboAPI](https://github.com/Elytrium/LimboAPI) - Library for sending players to virtual servers (called limbo)

## Features of LimboAuth

- Supports [H2](https://www.h2database.com/html/main.html), [MySQL](https://www.mysql.com/about/), [PostgreSQL](https://www.postgresql.org/about/) [databases](https://en.wikipedia.org/wiki/Database);
- [Geyser](https://wiki.geysermc.org) [Floodgate](https://wiki.geysermc.org/floodgate/) support;
- Hybrid ([Floodgate](https://wiki.geysermc.org/floodgate/)/Online/Offline) mode support;
- Uses [BCrypt](https://en.wikipedia.org/wiki/Bcrypt) - the best [hashing algorithm](https://en.wikipedia.org/wiki/Cryptographic_hash_function) for password;
- Ability to migrate from [AuthMe](https://www.spigotmc.org/resources/authmereloaded.6269/)-alike plugins;
- Ability to block weak passwords;
- [TOTP](https://en.wikipedia.org/wiki/Time-based_one-time_password) [2FA](https://en.wikipedia.org/wiki/Help:Two-factor_authentication) support;
- Ability to set [UUID](https://minecraft.wiki/w/Universally_unique_identifier) from [database](https://en.wikipedia.org/wiki/Database);
- Highly customisable config - you can change all the messages the plugin sends, or just disable them;
- [MCEdit](https://www.mcedit.net/about.html) schematic world loading;
- And more...

## Commands and permissions

### Player

- ***limboauth.commands.destroysession* | /destroysession** - Destroy Account Auth Session Command
- ***limboauth.commands.premium* | /license or /premium** - Command Makes Account Premium
- ***limboauth.commands.unregister* | /unregister** - Unregister Account Command
- ***limboauth.commands.changepassword* | /changepassword** - Change Account Password Command
- ***limboauth.commands.totp* | /totp** - 2FA Management Command
- ***limboauth.commands.***\* - Gives All Player Permissions

### Admin

- ***limboauth.admin.forceunregister* | /forceunregister** - Force Unregister Account Command
- ***limboauth.admin.forcechangepassword* | /forcechangepassword** - Force Change Account Password Command
- ***limboauth.admin.forceregister* | /forceregister** - Force Registration Account Command
- ***limboauth.admin.forcelogin* | /forcelogin** - Force Login Account Command
- ***limboauth.admin.reload* | /lauth reload** - Reload Plugin Command
- ***limboauth.admin.***\* - Gives All Admin Permissions

## Donate to the original author

Your donations are really appreciated by the original author. Donation wallets/links/cards:

- MasterCard Debit Card (Tinkoff Bank): ``5536 9140 0599 1975``
- Qiwi Wallet: ``PFORG`` or [this link](https://my.qiwi.com/form/Petr-YSpyiLt9c6)
- YooMoney Wallet: ``4100 1721 8467 044`` or [this link](https://yoomoney.ru/quickpay/shop-widget?writer=seller&targets=Donation&targets-hint=&default-sum=&button-text=11&payment-type-choice=on&mobile-payment-type-choice=on&hint=&successURL=&quickpay=shop&account=410017218467044)
- Monero (XMR): 86VQyCz68ApebfFgrzRFAuYLdvd3qG8iT9RHcru9moQkJR9W2Q89Gt3ecFQcFu6wncGwGJsMS9E8Bfr9brztBNbX7Q2rfYS
