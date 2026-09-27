# No Player Left Behind

> **Legacy version for Minecraft 1.21.11.** This branch only supports 1.21.11. For the latest Minecraft version, see the [`main` branch](https://github.com/Jc-42/no-player-left-behind).

A server-side Fabric mod that prevents any one from playing on your server until every required player is online.

Prevent anyone from playing on your SMP or co-op world until all players are online. When joining the server is frozen unless all required players are connected: no mobs, crops, redstone, day/night cycle, weather, or item progress. When the last required player joins, a short (configurable) countdown plays and the world starts up again for everyone.

## Features

- **Server freeze** while any required player is offline. Mobs, block ticks, redstone, crops, time and weather all stop.
- **Player freeze.** They can look around, and chat, but they can't move, break or place blocks, attack, use items, or move and drop inventory items. Anything the client tries to do is undone immediately.
- **No damage, hunger, or potion timers** tick for frozen players.
- **Boss bar** showing `Waiting for players connected/remaining`.
- **Countdown** (`Starting in 3`, `2`, `1`) once everyone is online. Configurable
- **Grace period** when a required player leaves: a configurable `Freezing in 30s` warning counts down, and the world freezes only if they don't rejoin in time.
- **Display choice.** The countdown and the warning can each show as big center text or small text above the hotbar.
- **Server-side only.** Players join with a normal vanilla client, no mod needed.

## How it works

1. Nobody online: the mod does nothing and vanilla behavior applies. Since 1.21.2, vanilla pauses an empty server after `pause-when-empty-seconds`.
2. A player joins and not every required player is online: the world and all players freeze.
3. Every required player is online: `Starting in 3, 2, 1`, then the world unfreezes.
4. A required player leaves: everyone sees `Freezing in 30s`. If they rejoin in time, the warning disappears. If not, the world freezes again and the cycle starts over.

Players who aren't on the required list are frozen along with everyone else, but don't count toward the total. While the world is running, they play normally.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/server/) on your server.
2. Put this mod and [Fabric API](https://modrinth.com/mod/fabric-api) in the server's `mods` folder.
3. Start the server once to generate the config file.
4. Add your players to the config and run `/leftbehind reload` (or restart the server).

## Configuration

`config/noplayerleftbehind.json`:

```jsonc
{
  // Minecraft usernames of every player who must be online before the world unfreezes.
  // Capitalization does not matter. Leave the list empty to turn the mod off.
  // Example: "requiredPlayers": ["Alex", "Steve"],
  "requiredPlayers": [],

  // Number of seconds everyone gets to rejoin after a required player leaves, before the world freezes.
  "gracePeriodSeconds": 30,
  // Where the freeze warning appears: "title" for big text in the center of the screen, "actionbar" for small text above the hotbar.
  "gracePeriodDisplay": "actionbar",

  // Length of the starting countdown.
  "countdownSeconds": 3,
  // Where the starting countdown appears: "title" or "actionbar".
  "countdownDisplay": "title"
}
```

## Commands

| Command | Who can use it | What it does |
|---|---|---|
| `/leftbehind missing` | Everyone | Lists the required players who aren't online, or `None` |
| `/leftbehind reload` | Operators | Reloads the config file. If the file has an error, the previous settings stay active. |

## Known limitations

- The survival inventory screen can still be opened, since the client opens it without asking the server, but any change made in it is undone anyways.

## Building

Requires Java 21.

```sh
./gradlew build
```

The mod jar is written to `build/libs/`.

## Store page copy

Text for the Modrinth and CurseForge project pages.

**Summary:**

> Prevents anyone from playing on your SMP or co-op world until all required players are online.

**Description:**

> ## Are you tired of joining your server and seeing that someone beat the whole game while you were offline?
>
> Or are you *that* friend, and you just can't help yourself?
>
> Then this mod is for you.
>
> No Player Left Behind completly prevents any player from doing anything until the whole group is online. Mobs freeze, crops stop growing, redstone stops, the day night cycle stops, and nobody can mine, build, fight, or change their inventory. When the last friend joins, a countdown plays and everyone can play like normal.
>
> If someone leaves mid-session, everyone gets a 30 second warning after which the world freezes again, when all players are offline the mod has no effect.
>
> ### Why you'll like it
> - **Nobody gets ahead.** It forces you all to progress together which makes it perfect if you have people with more free time than others.
> - **Keeps everyone accountable** You don't have to resist the urge of late night grinding, or hassle anyone, just install the mod and rest easy.
> - **Server-side only.** Your friends join with a normal vanilla client which means they have nothing to install.
> - **Simple setup.** List your group's usernames in one config file and you're done.
> - **Leave the server running.** You don't have to manually start and stop the server when you want to play, just leave it running and this mod ensures that you all must play together 
>
> ### Perfect for
> SMP friend groups, duo and trio survival worlds, co-op hardcore runs, and any server where you want to play together.
> ### Commands and Configs
> **Commands**
> - `/leftbehind missing` (everyone can run): lists the required players who aren't online yet, or `None`.
> - `/leftbehind reload` (operators only): reloads the config file without restarting the server.
>
> **Config**
>
> The config file is created the first time the server starts, at `config/noplayerleftbehind.json` inside your server folder.
> - `requiredPlayers`: the usernames of everyone who must be online, for example `["Alex", "Steve"]`. Capitalization does not matter. Leave it empty to turn the mod off.
> - `gracePeriodSeconds`: how many seconds everyone gets to rejoin after a required player leaves, before the world freezes. Default `30`.
> - `countdownSeconds`: length of the starting countdown once everyone is online. Default `3`.
> - `gracePeriodDisplay` and `countdownDisplay`: `"title"` for big text in the center of the screen, or `"actionbar"` for small text above the hotbar. Defaults `"actionbar"` and `"title"`.

## License

This project is licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). See [LICENSE](LICENSE).
