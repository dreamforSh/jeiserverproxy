# JEIServerProxy

JEIServerProxy is a Paper plugin that helps JEI clients on a Paper server discover server recipes and use JEI's recipe-transfer bridge.

This branch targets Minecraft/Paper `26.1.2` and the current JEI `29.6.x` channel layout.

## Features

- Sends server recipe discoveries to players when they join.
- Lets admins blacklist recipe keys from automatic discovery.
- Registers the JEI `26.1.2` custom payload channels used for recipe transfer and cheat-permission checks.
- Keeps the older JEI/REI bridge channels registered for clients that still use them.
- Supports English and Chinese message files, plus custom language files in the plugin folder.

## Commands

- `/jeiproxy reload`: Reloads the plugin config and recipe cache.
- `/jeiproxy handshake <player>`: Sends the compatibility handshake and cheat-permission packets again.

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
- `recipe-blacklist`: Recipe keys to skip, using `namespace:key` format.

## Building

You need JDK 25 or newer for Paper `26.1+`.

```powershell
cmd /c gradlew.bat build --console plain
```

The shaded plugin jar is written to `build/libs`.
