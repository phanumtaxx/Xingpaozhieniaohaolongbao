# Xingpaozhieniaohaolongbao Client

Independent ClickGUI for **Minecraft Java 1.21.5 / Fabric**, ported from the approved HTML design. Includes six cotton-candy category panels, 91 placeholder module entries, Jost text, Hazel at the bottom right, and animated cherry blossoms in front of the interface.

## Install

For a direct development launch, double-click **`launch-client.bat`**. It uses the installed JDK 21 and starts this client with [DevAuth](https://github.com/DJtheRedstoner/DevAuth). On your first authenticated launch, follow the Microsoft sign-in link shown in the console. DevAuth reuses the saved login on later launches. Its configuration and account cache live in `run/devauth`, which is excluded from version control. The first launch can take longer while Gradle downloads dependencies.

The batch file enables authentication with `-PdevAuth`; normal `runClient` and automated smoke checks keep it disabled. DevAuth is a development runtime dependency and is not bundled into the release mod JAR.

To use your regular Minecraft launcher instead:

1. Copy `build/libs/xingpaozhieniaohaolongbao-1.21.5-0.1.0.jar` into the `mods` folder of your Fabric 1.21.5 installation. The default launcher location is `%APPDATA%\.minecraft\mods`.
2. Launch **Fabric 1.21.5** with Java 21 or newer. The small Fabric API components needed by this mod are bundled; a separate Fabric API download is not required.
3. Press **Right Shift** on the title screen or while playing to open/close the GUI. Escape also closes it after dismissing the search overlay.

Use the regular JAR, not the `-sources.jar`. The mod is client-only. This is an interface port: toggles, sample settings and binds work, but the module entries do not implement combat, movement, or other gameplay features. Hazel remains a visual mascot; no chatbot is planned.

## Controls

- Left click a module to toggle it; right click or use its arrow to edit sample settings. Click the native mode control to cycle normal / strict / custom.
- Drag category headers or collapse with the minus button. The native top-right search, layout and appearance controls have been removed.
- **Ctrl+F** opens a smoothly animated search box over a darkened GUI, ready for typing. All matching modules appear in a scrollable list. Click a result, or use Up/Down and Enter, to dismiss search and automatically expand and reveal that module's settings. Ctrl+A selects the query; Ctrl+V pastes. Escape dismisses search first, then closes the GUI.
- **P**, while the ClickGUI is open, opens **Player List**. Compact cards show other loaded, living players within your configured render-distance radius, sorted by distance. Scroll to see more cards; double-click a card to open its live front-facing model, current equipment/pose and actions. This is a list of tracked nearby players, not everyone on the server's tab list. P does not open this window during normal gameplay or while entering a module bind/search query.
- **Target** stores that player's UUID and name for the current server (or singleplayer world), without adding a world marker or triggering combat. `/hazel` is handled locally and reports the saved target; autopilot is not implemented. **Follow**, **Kill**, **Mace (Once)** and **Mace (Loop)** are disabled placeholders.
- **Spectate** temporarily attaches a local third-person camera to the selected loaded player. Your actual player remains in place and can still be attacked; no server spectator mode or movement is requested. Escape, P or Right Shift returns to the selected player's details and restores your previous camera/perspective. If the target dies or unloads, the view returns automatically. Only client-loaded terrain/entities are available. Escape in player details returns to the list, then closes the list on a second press.
- Jost module labels use 16px on desktop and 15px in smaller windows; category headers keep their compact size.
- Shaded sakura petals and occasional five-petal flowers drift over the GUI without intercepting clicks. Sizes vary, with a few slightly larger, slower foreground blossoms, gentle rotation and smooth occasional gusts. Motion pauses when the menu is hidden or unfocused. Existing saved appearance preferences are retained.
- **Hold left click on Hazel to pick her up**, move her, then release to drop her with gentle gravity. You can catch her mid-fall. She stays within the window and settles at the bottom; her horizontal position lasts for the current launch. Her existing image is used throughout. Transparent pixels pass clicks through, and she rises above panels while held or falling. Closing the GUI or losing focus releases the drag and pauses the motion. This interaction is implemented in the Minecraft client.
- Click a module's keybind field and press a key or mouse button. Escape cancels; Delete/Backspace clears. Right Shift is reserved for the menu. Binds toggle the corresponding preview state during gameplay.
- Native preferences, module settings, binds and panel positions save to `config/xingclient/settings.json` in the game directory. The HTML and Minecraft configurations are separate.

The native GUI uses the browser design's pixel dimensions independently of Minecraft's GUI Scale, fitting down for narrow windows. Scroll inside tall panels to reach the remaining entries. The world remains visible and keeps running behind it during gameplay, with a subtle background blur applied before the sharp panels, Hazel and blossoms are drawn. This ClickGUI blur is independent of the vanilla menu-blur slider. Opening it from the title screen uses a black background, where blur has no visible effect.

Only the main menu's panorama is replaced with the supplied `background.jpg`, centered and cropped to fill the window. Minecraft's title logo, splash text, buttons and footer retain their normal behavior and layout.

## Menu soundtrack

The supplied `Stay With Me ~ Mayonaka no Door.mp3` is bundled as `assets/xingclient/audio/menu.mp3`. It loops across title, Singleplayer, Multiplayer, options and other screens, including the ClickGUI and in-game menus. It pauses during normal gameplay and resumes from its previous position when a screen opens. Moving between menus does not restart the track. Vanilla background music is suppressed while a menu is open.

Playback uses a dedicated streaming audio line at fixed 100% (unity gain), independent of Minecraft's Master/Music sliders, with no client volume control. Windows/device volume still applies. The original MP3 is preserved without transcoding; JLayer is bundled to decode it.

## HTML prototype

Open `index.html` directly. No server or build step is needed. `app.js`, `style.css` and `blossoms.js` remain the earlier independent browser prototype, with preferences saved in browser local storage. The new Ctrl+F overlay and menu soundtrack are implemented in the Minecraft client.

The current Hazel image is `hazel-new-source-removebg-preview.png`, copied to `mascot.png`; it preserves her head and ears and places the thigh crop at the bottom edge. Jost Regular and its OFL license are under `assets/fonts`. Blossom artwork is generated locally by `blossoms.js`.

## Build and checks

Use JDK 21 and the included Gradle wrapper:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat build
.\gradlew.bat runClient
```

`build` runs the settings persistence/validation tests and creates the installable remapped JAR under `build/libs`. Development Minecraft runs in this project's `run` directory, separate from your regular installation.

Optional automated native checks:

```powershell
.\gradlew.bat runClient -PguiSmoke
.\gradlew.bat runClient -PworldSmoke
```

The GUI check exercises module settings, keyboard/mouse binds, Ctrl+F focus, result clicks, keyboard selection, scrolling to settings, empty results, drag, persistence, GUI scale and resizing. It also decodes the menu MP3 and checks music stays active between menus. The world check creates a new flat test world under `run/saves`, verifies gameplay continues behind the GUI, checks music pauses/resumes with menu visibility, and uses client-only fixture players to check the player grid, live previews, saved target, disabled actions, spectate and camera restoration. Both use separate development preferences and silent audio output, save screenshots under `run/screenshots`, print an explicit PASS/FAIL marker, and exit. These checks only activate in the development environment.

Native assets are bundled under `src/main/resources/assets/xingclient`. `tools/export-native.cjs` exports the original browser blossom sprites and antialiased Jost atlases using the local Playwright setup under `.qa`; this exporter is not needed to build or run the mod. The bundled Jost atlas is intended for the interface's ASCII labels and search, not general multilingual chat.
