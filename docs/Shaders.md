# Shaders

Author: uint32.

Visuals > Shaders uses simple pastel materials: Pastel, Solid, Soft Gradient,
and Pearl. Entities, crystals, and hands can select their own material and colors.
Settings pages: Targets, Style, Colors, and Sky.

Cotton, Lavender, Mint, Peach, and Custom palettes are available. Style controls
fill opacity, outline opacity/width, soft glow, and gentle motion. Defaults use
low opacity, a thin outline, subtle glow, and no animation. Optional sky uses a
pastel or night gradient, with vanilla celestial rendering retained by default.

Existing experimental shader settings migrate once to the pastel defaults.
Target selection and range are preserved. Legacy enum names remain readable
for saved-config compatibility but are not offered in the UI or rendered.

Effect sources and target policy live in native/xing/ShaderEffects.cpp. Java
adapts Minecraft capture and GPU resources. Capture is through terrain and uses
Minecraft's outlined model layers. No Chams module is registered.

Compile only; appearance and GPU compilation need an in-game check.

The sky gradient is fixed in screen coordinates and does not follow camera
yaw, pitch, or FOV. Scanline rendering and its configuration field are removed.
Restart Minecraft after updating the DLL; an already loaded DLL stays in memory.

Pastel fill, outline, and glow are composited as separate opacity layers rather
than adding glow brightness to the fill color. Linear silhouette sampling keeps
thin outlines smoother; category decoding accounts for partial edge coverage.
Saved colors and opacity controls remain intact. New defaults use 0.22 fill,
0.55 outline, and 0.15 glow strength.

## Supplied pastel and liquid chrome

Pastel now uses the supplied six-color Easter shader for entities, crystals,
hands and sky. Liquid Chrome adds the supplied spectral chrome pattern to the
same target selectors. Both use their own procedural palettes; global
brightness/saturation and per-target angle, scale, scrolling and speed still
apply. Motion zero pauses animation. Chrome's reversed smoothstep vignette is
expressed as one minus an ordered smoothstep to avoid undefined GLSL behavior.

With global Fill and the hand target Fill at one, a nonzero hand strength now
produces an opaque hand interior. A separate alpha pass makes this hold even
when the main effects use additive or screen blending. Mask edges retain their
coverage for antialiasing. Partial fill retains the previous transparency.

Sky settings include separate Vanilla sun and Vanilla moon toggles, which also
work when Custom sky is disabled. Existing sun/moon visibility is preserved
when settings migrate. Vanilla stars/glow retains the existing custom sky
control separately from the sun and moon.
