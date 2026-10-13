# Nametags

Author: uint32.

Enable Visuals > Nametags. It enables Web UI and installs nametags.js and
nametags.css into run/config/xingclient/web in the development game directory.
The labels use bundled Inter, a configurable translucent background, small
3 GUI-unit corners and a soft shadow, with no outline. Health is shown as a
number, green above 66% health, amber above 33%, and red otherwise. Absorption
is included in the displayed health. Armor slots (head, chest, legs, feet),
mainhand and offhand items use actual Minecraft inventory models, captured
once and cached as PNGs for the HTML page. Stack counts are shown when above one. The compositor samples a copy of the Minecraft image to blur the
world beneath the browser surface; CSS backdrop-filter alone cannot do that.

C++ selects eligible players and interpolates their head anchors each render
frame. Java supplies Minecraft snapshots and publishes the display names to the
HTML engine. Local players and spectators are excluded; invisible players are
optional. Range defaults to 96 blocks, head offset to 0.3 blocks, font size to 11
GUI units and blur radius to 6 GUI units. GUI scale is applied to visual sizes.
Background presets, RGB sliders and a 0?1 alpha slider are exposed in settings.
Inter and its OFL license are bundled locally; no font network request is needed.

The module replaces selected players' vanilla name labels while Web UI is
enabled, and releases anchors when players disappear or Nametags is disabled.
Disabling Web UI restores vanilla labels. Web UI stays enabled when Nametags is
disabled so other web visuals can keep using it.

Edit nametags.css for the appearance and reload through Web UI settings. The
extension is reinstalled after page reload without rewriting existing custom
assets. Native interpolation, range checks and world projection stay in C++.
The engine's requestBackdrop API lets other modules reuse the world blur.

Projected names can be shown through walls; terrain occlusion is not implemented.
Chromium paints asynchronously, so labels may lag camera motion by a frame.
Compilation succeeded; no Minecraft run or automated tests were performed.

Equipment captures are limited to two new icons per game frame and PNG encoding
runs on a background worker. An icon appears once its cached image is ready.
The icon cache is bounded to 512 stack/component combinations. Captured icons
are static snapshots, so enchanted glints and animated item textures do not
animate continuously inside the HTML tag.

The equipment row is positioned above the name panel and does not contribute
to the panel's width or height. Inter is registered from the bundled font bytes
through FontFace; tags wait for that font rather than drawing a fallback face.
Item capture uses Minecraft's shared entity buffer provider so item and glint
layers can retain their separate builders until the render finishes.

Horizontal padding, vertical padding and corner radius are configurable in GUI
units. Padding X ranges from 0?24, padding Y from 0?16, and corners from 0?16.
Blur uses the actual rounded heading geometry, reported by the browser and
matched to its paint buffer. It renders before the HTML tint, independently of
background alpha. At alpha zero the panel can remain blurred without a tint;
at alpha one an opaque background naturally hides the scene underneath.

## Saved defaults

Defaults match the user's saved preset: range 256, head offset 0.35, font size 6,
blur 12, white background at 20% alpha, horizontal and vertical padding 3.25,
corner radius 2.25, and invisible players included. Existing saved settings
continue to take precedence over these defaults.
