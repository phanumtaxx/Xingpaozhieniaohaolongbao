# Break ESP

Author: uint32.

Enable Visuals > Break ESP. Web UI starts automatically. The default Grow
animation begins at the center of the mined block and expands toward all eight
corners as breaking progress increases. Native code smooths progress every
browser frame, applies easing, builds cube faces and fades inactive markers.
The HTML renderer draws those projected faces and edges as SVG.

## Controls

Targets: local mining, other players' server-reported mining, range, maximum
markers, through-wall visibility and back faces. The marker budget selects the
nearest blocks. With Through walls off, a ray to the block center determines
visibility; this is a whole-block visibility check, not per-pixel depth testing.

Animation: Grow, Rise, Pulse, Twist, Bounce and Shrink; linear, smooth, cubic,
sine and quint easing; smoothing/fade duration; minimum size; inset; rotation
speed; pulse amount and frequency. Grow expands symmetrically from the center.
Rise lifts the growing box from the bottom, Twist rotates it toward alignment,
and Shrink reverses the progression. Pulse and Bounce modulate its size.

Style: independent fill/edge opacity, line width, glow, start/end RGB colors,
progress color blending, percentage labels/font size and animated dashed edges.
An alpha of zero hides that part. Set glow to zero for crisp edges.

## Rendering and lifecycle

The Java wrapper reads Minecraft breaking progress and block state. Local
progress comes from the interaction manager; remote progress comes from the
world renderer's server-fed breaking records. Instant breaks may complete
between rendered frames and therefore have no visible progression. Mining
interruptions fade the last silhouette. World changes reset native state.

The reusable WorldProjection.projectPolygons API clips faces against the near
plane and viewport before perspective division. It supports back-face culling.
This avoids huge coordinates and disappearing entire cubes at screen edges.
SVG faces draw from far to near; duplicate shared edges are removed. Cube
geometry updates with the captured camera matrices rather than screen-space
CSS transitions. Browser painting remains asynchronous.

Native logic: native/xing/BreakEspModule.cpp.
Java Minecraft wrapper: NativeBreakEspModule.java.
Editable rendering: config/xingclient/web/break-esp.js and break-esp.css.

Compilation does not confirm in-game rendering or visual smoothness.
