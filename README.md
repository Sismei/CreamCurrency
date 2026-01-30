# CreamCurrency 🍦

CreamCurrency is an advanced multi-currency economy plugin for Minecraft servers. Built with performance and flexibility in mind, it allows server administrators to create and manage multiple custom currencies (e.g., Money, Gems, Credits, Gold) with ease.

## ✨ Features

* **Multi-Currency Support:** Create unlimited custom currencies, each with its own formatting, symbols, and settings.
* **Vault Integration:** Fully compatible with the Vault API. You can link your primary currency to Vault to ensure compatibility with other economy-dependent plugins.
* **Dynamic Command System:** Automatically generates commands for each currency based on your configuration (e.g., `/money`, `/gems`, `/credits`).
* **Database Flexibility:** Supports both **SQLite** for local storage and **MySQL** for cross-server synchronization.
* **PlaceholderAPI Support:** Use placeholders like `%creamcurrency_balance_<currency>%` to display balances in chat, scoreboards, or menus.
* **Transaction Logging:** Every transaction is logged into date-based files for security and auditing purposes.
* **Advanced Notifications:** Customizable chat messages, action bar notifications, and sound effects for transfers.
* **Rich API:** Easy-to-use API for developers to integrate CreamCurrency into their own plugins.

## Super Lightweight

We got this spark report from over 150 players!
<img width="331" height="83" alt="image" src="https://github.com/user-attachments/assets/92f87f01-e575-4160-88ac-ed3764539ad7" />


## 🛠 Installation

1.  Download the latest version of **CreamCurrency**.
2.  Place the `.jar` file into your server's `plugins` folder.
3.  Restart your server to generate the configuration files.
4.  Configure your database settings in `config.yml`.
5.  Define your custom currencies in the `currencies/` folder.
6.  Restart the server or use `/creamcurrency reload`.

## 📋 Commands & Permissions

### User Commands
* `/<currency>` - Check your current balance.
* `/<currency> pay <player> <amount>` - Send money to another player.
* `/<currency> top` - View the richest players ranking.

### Admin Commands
* `/creamcurrency reload` - Reloads the configuration and currencies.
* `/<currency> give <player> <amount>` - Add balance to a player.
* `/<currency> set <player> <amount>` - Set a player's balance.
* `/<currency> remove <player> <amount>` - Deduct balance from a player.

### Permissions
* `creamcurrency.use` - Allows basic command usage (Default: true).
* `creamcurrency.admin` - Access to all administrative commands.



👨‍💻 Developer API
You can easily access balances and manage economies via the CreamCurrencyAPI:

`// Get balance
double balance = CreamCurrencyAPI.getBalance(uuid, "money");`

`// Deposit money
CreamCurrencyAPI.deposit(uuid, "gems", 500);`

