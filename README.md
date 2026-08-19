# LootDrop Plugin
**The LootDrop Plugin is a server plugin that alllows admins to create loot drops that fall from the sky at specific coordinates (or spawn automatically on their own at random coordinates!)**
## Features
- **Big Landing:** The crate drops from the sky with a customizable server broadcast (optionally showing landing coordinates)
- **Health Bar:** The crate has a visible health bar which can be customized
- **Custom Loot:** You choose exactly what drops when the crate is destroyed (diamonds, gear, etc.) and how rare each item is (in percent), and even randomize amounts (_e.g. _5-10__)
- **Break the Box:** Loot drops as soon as the health bar reaches 0 HP
- **Auto-Despawn:** Set a timer for undestroyed crates to clean themselves up automatically

## Admin Commands
- ```/lootdrop spawn <x> <y> <z> [world]``` Spawns a loot drop at the specified coordinates.
- ```/lootdrop reload``` Quickly update your config settings after changing the ```config.yml``` file.
- ```/lootdrop list``` See where all the active loot drops are right now.
- ```/lootdrop auto <on|off|status>``` Toggle automatic loot drop spawning in-game.

> You can customize everything (spawn behavior, messages, loot tables, fall speed, and more) in the ```config.yml``` file in your plugin's folder.

**Get the LootDrop Plugin and start your next big event today!**
