# Fabric-TPA

[中文](README_CN.md) | English

Fabric-TPA is a server-side Fabric teleportation mod designed for survival and community servers.
It allows regular players to use `/tpa` as a practical replacement for `/tp` without granting operator-level teleport permissions.

Author: `Qixishunzi`

## Overview

Vanilla `/tp` is normally restricted to administrators.
Fabric-TPA provides a direct teleport command for regular players while keeping administrative permissions under control.

The mod focuses on a simple workflow:

- Players can teleport directly to another player
- Players can teleport directly to coordinates
- Teleport requests do not require acceptance
- Administrators can define a shared global home for the server
- All in-game teleport broadcasts include coordinates and dimension information

## Commands

- `/tpa <player>`
- `/tpa <x> <y> <z>`
- `/tpa home`
- `/tpa set home`

## Behavior

- `/tpa <player>` teleports the executing player directly to the target player
- `/tpa <x> <y> <z>` teleports the executing player directly to the specified coordinates
- `/tpa home` teleports the executing player to the shared server home
- `/tpa set home` sets the shared server home at the administrator's current location

## Permissions

- Regular players can use `/tpa <player>`
- Regular players can use `/tpa <x> <y> <z>`
- Regular players can use `/tpa home`
- Only server administrators can use `/tpa set home`

## Messaging

- In-game teleport broadcasts are shown in Chinese with colored formatting
- Console output is written in English for better compatibility with Windows server consoles

## Requirements

- - Minecraft (compatible with specified Fabric Loader versions)
- Fabric Loader
- Fabric API
- Java `21` or newer

## Build

```powershell
.\gradlew.bat build --no-daemon
```

## Notes

- This is a server-side mod and does not need to be installed on clients
- The shared home is stored in the world save folder
