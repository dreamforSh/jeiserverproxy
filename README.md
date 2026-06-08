# JEIServerProxy

JEIServerProxy is a Paper plugin that helps JEI clients on a Paper server discover server recipes and use JEI's recipe-transfer bridge without JEI showing missing server recipe warnings.

This branch targets Minecraft/Paper `26.1.2` and the current JEI `29.6.x` channel layout.

## Features

- Sends server recipe discoveries to players when they join.
- Sends NeoForge's `neoforge:recipe_content` payload to modded clients that register the channel, which fills JEI's synced recipe map on NeoForge clients.
- Lets admins blacklist recipe keys from automatic discovery.
- Registers the JEI `26.1.2` custom payload channels used for recipe transfer and cheat-permission checks.
- Keeps the older JEI/REI bridge channels registered for clients that still use them.
- Delays join sync slightly so JEI has time to finish its client channel setup.
- Keeps JEI cheat-mode item actions disabled unless the server owner enables them.
- Supports English and Chinese message files, plus custom language files in the plugin folder.

## Commands

- `/jeiproxy reload`: Reloads the plugin config and recipe cache.
- `/jeiproxy sync <player>`: Sends JEI sync data again.
- `/jeiproxy status`: Shows cached recipe and bridge settings.

`/jeiproxy handshake <player>` still works as an older alias for `sync`.

## Permissions

- `jeiserverproxy.admin`: Allows use of the admin command. Defaults to OP.
- `jeiserverproxy.cheat`: Allows JEI cheat-mode bridge actions. Defaults to OP.

## Installation

1. Download the plugin jar from `build/libs` or the releases page.
2. Put the jar in your Paper server's `plugins` folder.
3. Restart the server.

## Configuration

The main config is `plugins/JEIServerProxy/config.yml`.

- `language`: Message language file to load. Defaults to `en`.
- `send-recipes-on-join`: Sends recipe discovery data when players join. Defaults to `true`.
- `send-neoforge-recipe-content`: Sends NeoForge recipe content to clients that support it. Defaults to `true`.
- `send-compatibility-packets-on-join`: Sends JEI compatibility packets shortly after join. Defaults to `true`.
- `recipe-sync-delay-ticks`: Delay before join sync runs. Defaults to `20`.
- `recipe-transfer-enabled`: Allows JEI's recipe transfer button to move matching items into crafting menus. Defaults to `true`.
- `max-transfer-sets`: Caps shift-click/max-transfer work per request. Defaults to `64`.
- `cheat-bridge-enabled`: Allows JEI cheat-mode bridge actions when the player also has `jeiserverproxy.cheat`. Defaults to `false`.
- `recipe-blacklist`: Recipe keys to skip, using `namespace:key` format.

## Building

You need JDK 25 or newer for Paper `26.1+`.

```powershell
cmd /c gradlew.bat build --console plain
```

The shaded plugin jar is written to `build/libs`.
