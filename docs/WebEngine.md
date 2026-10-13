# HTML/CSS rendering engine

Author: uint32.

Visuals > Web UI enables a transparent Chromium Embedded Framework surface over
the game. The starter page is intentionally empty: this is the engine rather
than an ESP or HUD design. Java supplies Minecraft render state and uploads the
surface; C++ owns browser lifecycle, paint buffering and world projection.

## Authoring

On first enable the engine installs index.html, style.css and engine.js into
config/xingclient/web beneath Minecraft's game directory. Edit those files and
use Web UI > Assets > Reload page. CSS layout, custom fonts, SVG, gradients,
shadows and animations are provided by Chromium. Existing user files are kept.
All coordinates are physical framebuffer pixels, independent of GUI scale.
The engine remains passive: it does not consume game mouse or keyboard input.

A world-anchored element uses an ID supplied by a module:

```html
<div class="label" data-world-anchor="target">Target</div>
```

```css
.label {
  padding: 6px 10px;
  border: 1px solid #ffffff28;
  border-radius: 8px;
  background: #171923cc;
  color: #eeeaf8;
  box-shadow: 0 4px 16px #00000030;
}
```

engine.js updates --world-x, --world-y and --world-depth. Its default transform
centers the element above the anchor. You can change that transform in CSS.
Anchors behind the camera, outside the viewport, or outside the projection's
depth range are hidden. Visibility here does not mean visibility through blocks:
this overlay currently has no terrain occlusion or in-world depth testing.

## Module bridge

Register an anchor on the client thread and close the registration on disable:

```java
anchor = XingClient.INSTANCE.webOverlay.track("target", () -> targetPosition);
XingClient.INSTANCE.webOverlay.publish("combat", statusSnapshot);
```

The position supplier returns an absolute Vec3d, or null to hide the anchor.
Use an interpolated render position for moving entities. The engine subtracts
the camera position in double precision before projecting with the captured
Minecraft matrices, including the current FOV, camera orientation and bobbing.
Game changes still require modules to release stale anchors and channels.

Read data from the page:

```js
xing.onFrame(frame => {
  const combat = xing.data("combat");
  // Update your existing DOM using the module's public display data.
});
```

xing.frame exposes width, height, worldReady, anchors and data. onFrame returns
an unsubscribe function; the page also receives a xing-frame CustomEvent.
The bridge uses Minecraft render frames when Follow VSync and game VSync are enabled.
and can lag the game frame; this is not a synchronous world geometry renderer.

## Runtime and compilation

The Windows x64 runtime is native/web/runtime. Keep the complete directory,
including xing-web.dll, xing-web-process.exe, CEF DLLs, resources and locales.
Set -Dxing.web.runtime=/absolute/path/to/runtime for a packaged installation.
CEF is a separate native library so the existing xing.dll need not be replaced.

The SDK version and archive checksum are pinned in native/web/cef-sdk.json.
Run native/web/build.ps1 to compile only the engine and helper process.
Use -DownloadSdk on a checkout without the SDK. The downloaded archive, SDK,
compiled runtime and generated CMake files are ignored by Git. Redistribute
CEF-LICENSE.txt and Chromium's accompanying license resources with the runtime.

The initial path uses CEF's BGRA paint buffer and a premultiplied alpha OpenGL
composite. It copies the browser surface through CPU memory; shared GPU textures,
input routing and DOM developer tools are possible future extensions.

Java and native compilation do not validate browser initialization, transparency,
projection alignment, shutdown or performance inside Minecraft. No game launch
or automated tests are part of this implementation task.

## Extensions and world backdrops

installExtension(id, scriptFile, stylesheetFile) installs bundled page assets and
loads them after browser startup and reload. Existing editable assets are kept.
requestBackdrop(id, radiusSupplier) returns a Registration; close it when the
module is disabled. Radius is in GUI units, clamped to 0?12. The largest active
request applies to the overlay surface. Mark an HTML panel with data-world-backdrop to request blur beneath its bounds.
The engine reports its DOM rectangle and computed corner radius through the
CEF render-process bridge. Blur samples the Minecraft frame inside those
rounded bounds before compositing the page, preserving sharp text and images.
Background tint alpha and backdrop coverage are independent.

## Frame pacing

Web UI > Follow VSync is enabled by default. With Minecraft VSync enabled, CEF
uses the current monitor refresh rate (up to 1000 Hz) and receives external frame
requests from Minecraft's render loop. Monitor changes and fullscreen refresh
changes are picked up while rendering. Disabling either VSync setting uses the
manual 15-360 FPS target instead. Web UI settings show the active target.

Pending frame publications are coalesced to the latest data. Browser painting
remains asynchronous, so display synchronization does not guarantee that CEF
produces a fresh bitmap for every game frame. The compositor always draws the
latest available bitmap during Minecraft rendering.
