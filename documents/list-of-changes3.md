16. I need a solution for module settings that have their own options, settings available by other settings must be more "noticeable", my idea for that is simple: X Offset
    Let's call it child settings, coming from parent settings:

> Example: 

`  Scaffold:` <- Module
`    Keep-Y: "telly"` <- Parent setting
`      Keep Y On Press: false` <- Child Setting
`      No Keep Y On Jump Potion: false` <- Another Child Setting

` ` <- Separator/Blank space

`Multi Place: true`

The idea here is that the Child Settings get properly separated, this can be done by:

- X offset of a couple of pixels (or equivalent)

- 10% background brightness decrease

- Separator space/line between Child Settings and other settings in the module.


17. Extensive module changes, check modules.yml
18. Save keybinds under a new file. `keybinds.json`
19. Rework the module status and setting changes to a new file: `latest.json`, discard `default.json` entirely. `latest.json` should be loaded at start-up.
20. With the introduction of the internal `Client` module, save those settings to a new file. `client.json`
On top of that the `Client` module shouldn't be included on the normal ClickGUI modules list, a separate menu in the ClickGUI will be implemented
21. When the ClickGUI is closed the module settings should remain open, only closed if switching categories or opening other module's settings, only across closing and reopening the ClickGUI in the current game session, not across game restarts.
22. Modules currently have limits on allowed values. (eg. AutoBlock CPS allows from 1-10CPS), change this logic to instead be "recommended" values, if the parsed value of a setting is outside the "recommended" value, show a notification instead warning the user that the value is not recommended and "could trigger anticheat flags".
23. Folowing the introduction onf .json files for configuration and keybind saving, add "versions" to configs starting from `config-version 1`, when the client tries to load a config with a different config version, show a notification, create a backup and attempt to parse what is currently valid for the client
latest.json: ordinary module enabled state, hidden state, and property values.
keybinds.json: ordinary module keybinds.
client.json: Client properties and persistent ClickGUI state.
- Configs can be saved and loaded using the already built-in commands, just following the new logic now, but existing reserved files like latest, keybinds and client cannot be used to save configs
- Configs do not change keybinds
- Configs can't have empty names

Files are saved when:
- Disconnecting/Quitting the server or worls
- Closing the game (Either by using Minecraft's exit game button or closing it directly)

24. The figma URL I provided also contains reworks of nametags, pause screen (+ MusicPlayer, use MPRIS for this integration.) and TargetHUD, the old TargetHUDs should still be available as is, but the one I designed should be the default by the name "Myaulex" in the TargetHUD settingss
The pause screen is a full replacement for the current Minecraft pause screen, I suggest building a whole system to replace Minecraft's menus and UIs for future uses, but for now just for the pause screen. The goal of this is that the code is there ready for future replacements such as main menu, server list etc.