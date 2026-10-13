# HTML HUD

Author: uint32.

HUD > Watermark displays Xing 0.1a in regular-weight Inter. Web UI enables
alongside the widget. The renderer supports nine screen anchors and uses
Minecraft GUI scale for widget positions and font size.

## Editor

Press Right Alt or open HUD > Watermark > Layout > Open HUD editor.
The editor uses the ClickGUI's Jost font, square panels, current pink/blue colors,
and panel opacity. It has one small Hud Modules window. Drag its title to move
it; use the title's collapse button to fold it.

Left-click a module to toggle it. Right-click its row, or click its arrow, to
expand settings beneath the module. Anchor changes preserve screen position.
Numeric X/Y offsets and font size update the selected widget. Drag a widget in
the world to position it. Arrow keys nudge one GUI pixel; Shift + arrows move
ten. Hold Shift while dragging to bypass snapping. Grid and snap controls are
inside the window.

Background blur ranges from zero to twelve and blurs the full Minecraft
background. Its blur registration is released when the editor closes. The
slight black background tint lives in #xing-hud-editor-shade in hud-editor.css.

Save commits widget visibility, placement, window position, blur and snapping
options. Cancel, Escape, or Right Alt restores the session's initial settings.
Undo/redo includes widget and window changes, with up to 100 history entries.
Ctrl Z undoes; Ctrl Y or Ctrl Shift Z redoes outside text fields.

## Templates and future widgets

HudManager owns widget registrations and data suppliers. Disabling a widget
releases its registration and removes its DOM node on the next browser frame.
Web UI stays available for other widgets and Nametags.

Future widgets register their ID, template, style supplier and data supplier.
Provide a HudManager.define entry with a style getter, setter and defaults to
include the module in the editor. Templates use data-hud-template; elements
with data-hud-text bind to public display data through dotted property paths.
Values are assigned through textContent.

Editable assets live in config/xingclient/web:

- watermark.html: widget markup.
- hud.css and hud.js: widget styling and rendering.
- hud-editor.css and hud-editor.js: editor styling and elements.

Assets are copied on first use and remain editable. Web UI > Assets > Reload
page reloads CSS and JS. For template edits, toggle Watermark off/on before
reloading the page. Native HudLayout owns screen anchoring and watermark text.

Compilation does not validate the in-game appearance.
