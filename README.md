# JEIServerProxy

JEIServerProxy is a Bukkit plugin for Minecraft servers that enhances the experience for players using the Just Enough Items (JEI) mod. It ensures that clients have the most up-to-date recipe information from the server, providing a seamless and accurate crafting experience.

## Features

- **Recipe Synchronization**: Automatically sends all server-side recipes to the client upon joining, ensuring that JEI displays the correct and available recipes.
- **Recipe Blacklist**: Allows server administrators to hide specific recipes from players, providing greater control over the gameplay experience.
- **Configuration Reload**: Supports reloading the plugin's configuration without requiring a server restart.
- **Multi-language Support**: Comes with built-in support for English and Chinese, with the ability to add more languages.

## Commands

- `/jeiproxy reload`: Reloads the plugin's configuration.
- `/jeiproxy handshake`: Manually triggers the recipe synchronization process for the player who executes the command.

## Permissions

- `jeiserverproxy.admin`: Grants access to the administrative commands of JEIServerProxy. Default is OP.

## Installation

1.  Download the latest version of the plugin from the releases page.
2.  Place the downloaded `.jar` file into the `plugins` folder of your PaperMC server.
3.  Restart the server.

## Configuration

The main configuration file is `config.yml`, located in the `plugins/JEIServerProxy` directory.

- `send-recipes-on-join`: (Default: `true`) If set to `true`, the plugin will automatically send all recipes to players when they join the server.
- `recipe-blacklist`: A list of recipes to be hidden from players. Recipes should be specified in the format `namespace:key`. For example: `minecraft:tnt`.

## For Developers

This project is built with Kotlin and Gradle. To compile the project, you will need JDK 21 or higher.

- Clone the repository: `git clone https://github.com/your-username/JEIServerProxy.git`
- Build the project: `./gradlew build`

The compiled plugin will be located in the `build/libs` directory.
