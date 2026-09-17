# Oh My Minecraft Client Forge 1.20.1

This mod is migrated to [ommc](https://github.com/plusls/oh-my-minecraft-client)

> This is a **Forge 1.20.1 port** of [Oh My Minecraft Client](https://github.com/plusls/oh-my-minecraft-client).
> The original is a Fabric mod by plusls. This port is maintained independently and does not
> cover exactly the same set of features (see "Not Implemented Yet" and "Differences from the Fabric Version" below).

Make Minecraft Client Great Again!

The default hotkey to open the in-game config GUI is **O + C**.

# Dependencies

| Dependency | Download |
|----|----|
| MaFgLib | [GitHub](https://github.com/ThinkingStudio/MaFgLib) |

MaFgLib is the Forge port of malilib. The entire config system, config GUI and hotkey system of this
mod come from it, so it is a **hard dependency and must be installed**.

# Features

## Persist Chat History (dontClearChatHistory)

Saves the messages in the chat box to a file when you leave a world, and restores them the next time
you join the same world.

- Category: `Generic`
- Type: `boolean`
- Default value: `false`

**Where it is saved**

```
<game directory>/config/ommc/chat-history/<SHA-1 fingerprint>.json
```

**Isolated per world**

Chat history is stored separately for each "world", so different saves and different servers
**never mix**:

- Singleplayer: the absolute path of the save root directory (`saves/<level name>`) is used as the identity
- Multiplayer: the server address (`host:port`) is used as the identity

The identity is hashed with SHA-1 to form the file name, so it can neither collide nor fail to write
because a world name contains illegal characters. The readable world name is also recorded inside the
file for manual inspection, and the stored identity is verified again on load — so even a manually
renamed or copied file will not cause histories to mix.

**When it is saved**

On "leaving the world". At that moment the chat history has not been cleared yet, so the content is complete.

**Behaviour when restoring**

- Restored messages are shown all at once and fade out after about 10 seconds (the vanilla mechanism)
- Click / hover events are stripped from restored messages — those events originally belonged to the
  server-side message; keeping them would make a click send a command to the current server, which is
  both confusing and risky
- A separator line `── chat history from the previous session ──` is inserted before them
- At most the latest 100 messages are kept

**Differences from the Fabric version**

The original `dontClearChatHistory` prevents the chat box from being cleared *within a session*.
This port instead persists it *across sessions* by saving on exit and restoring on join, which also
makes it immune to clearing the chat with F3+D. In addition, the original semantics cover the input
history (the lines you scroll through with the up arrow), whereas this port **only saves the messages**,
not the input history.

# Not Implemented Yet

The following features exist in the Fabric version but are **not implemented** in this port.
In the in-game config GUI they are marked in red with "(Not Implemented)":

## Generic

- `clearWaypoint` Clear Highlighted Waypoint
- `parseWaypointFromChat` Parse Waypoints from Chat
- `forceParseWaypointFromChat` Force Parsing Waypoints from Chat

## Feature Toggles

- `disableBlocklistCheck` Disable Player Blocklist Check
- `disablePistonPushEntity` Disable Piston Pushing Entities
- `highlightPersistentMob` Highlight Persistent Mobs
- `highlightPersistentMobClientMode` Highlight Persistent Mobs Client Mode
- `worldEaterMineHelper` World Eater Mine Helper

## Lists

- `highlightEntityBlackList` Highlight Entity List Blacklist
- `highlightEntityListType` Highlight Entity List Type
- `highlightEntityWhiteList` Highlight Entity List Whitelist
- `blockModelNoOffsetBlackList` Block Model No-Offset List Blacklist
- `blockModelNoOffsetListType` Block Model No-Offset List Type
- `blockModelNoOffsetWhiteList` Block Model No-Offset List Whitelist
- `worldEaterMineHelperWhitelist` World Eater Mine Helper Whitelist

# Building

JDK 17 is required. Put the MaFgLib jar into a Maven-layout directory under `libs/repo`, then build
with Gradle:

```bash
./gradlew clean build
```

The output is placed under `build/libs/`.

# License

This project is available under the LGPL-3.0 license. The original
[Oh My Minecraft Client](https://github.com/plusls/oh-my-minecraft-client) is also licensed under LGPL-3.0.
