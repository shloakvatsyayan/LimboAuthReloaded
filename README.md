# LimboAuthReloaded

[![Join our Discord](https://img.shields.io/discord/1133145134318497923.svg?logo=discord&label=Discord)](https://discord.gg/bonknetwork)

Auth System built in a virtual server (Limbo). This is a fork of [LimboAuth](https://github.com/Elytrium/LimboAuth), and is currently used on `play.bonkmc.net`. The goal of this fork is simply to add fixes, features, and future updates to a rarely maintained and useful project.

## See also
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

## Premium username changes

Premium players use the current name from their authenticated Minecraft profile. Older versions could replace it with a historical name saved in the auth database.

After upgrading the plugin and restarting the proxy, affected accounts are repaired automatically on their next successful online-mode connection. No database reset or config change is needed. The repair updates the existing account's name and lookup key together, preserving its password, 2FA settings, and account history. With `save-uuid: true`, the saved backend UUID remains stable across name changes, including when prefixes or offline UUIDs are configured. Other plugins that identify players by name may require their own updates.

Offline accounts retain their registered name casing, and Floodgate profiles are unchanged. By default, if the new name is already registered to another local account, login is stopped and the conflict is logged. An administrator must resolve the conflict before that player can reconnect.

### Overwriting unlinked offline accounts

To let verified premium players reclaim an occupied username, enable this option under `main` in `config.yml`, then restart the proxy or run `/lauth reload`:

```yaml
main:
  overwrite-unlinked-offline-accounts: true
```

The default is `false`. When enabled, this applies both to returning premium players who changed their name and to first-time premium players. Only accounts with an empty or null premium UUID can be replaced. Accounts already linked to a premium UUID remain protected, and separate offline-prefix accounts and Floodgate profiles are unchanged.

Replacement happens only after successful online-mode authentication. A returning premium account keeps its saved UUID, password, 2FA, and history. A first-time premium account starts fresh with the configured premium backend UUID; the replaced offline account's password, 2FA, saved UUID, and history are discarded. The replacement record is saved even when `save-premium-accounts` is disabled. Database changes are transactional, so a failed replacement restores the offline record. This does not migrate or delete player data managed by other plugins.

For an eligible username collision, the option checks the premium profile service even if the database or cache previously classified the name as offline. A confirmed premium name is sent through online-mode verification even when `online-mode-need-auth` is enabled. Failed verification never replaces an account. If the profile lookup fails or finds no premium owner, the existing offline account remains available. `force-offline-mode` still takes precedence.

## Upgrading a cracked account to premium

With `save-uuid: true`, `/premium <password> confirm` verifies the existing password, looks up the Minecraft name without the offline prefix, and links the registration to that premium UUID. The account moves into the configured premium namespace while keeping the current gameplay UUID, so UUID-based inventory, balances, and permissions remain attached. The old cracked login is reserved with an unretained random password so it cannot be re-registered to access the preserved UUID. The upgrade and reservation are transactional. The player is disconnected and must reconnect with a valid premium session.

Legacy unprefixed registrations are accepted only when both their stored offline UUID and the connected player's prefixed UUID match the registered name. An occupied destination or an existing premium binding is rejected instead of merging accounts. Changes made to the password or identity during the upgrade also cause it to fail without replacing the record.

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
