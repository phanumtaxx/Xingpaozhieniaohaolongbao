# ViewModel and Swing

Author: uint32. Ported from Synthetic's Java modules to native C++ policies.

Visuals > ViewModel controls first-person translation, rotation and scale.
Offsets range from -2 to 2, rotations from -180 to 180 degrees, and scales from
0.05 to 3. Translation defaults to zero and scale to one. X translation and
Y/Z rotation mirror with the rendered arm, including left-handed players.
Sync Hands copies mainhand offsets to offhand and hides the offhand sliders.
Render Arms hides skin and sleeves, including map arms, when disabled.

Disable Vanilla Items to hide textured held items. With Shaders enabled and
Hands and items selected, the item silhouette still feeds the shader fill,
outline and glow. This affects first-person rendering only.

Visuals > Swing overrides the local player's swing hand, duration and smoothing.
Defaults match Synthetic: hand override off, main hand selected, speed override
on, speed 12, old animation off. Duration is max(2, 20 - speed). Old animation
uses current swing progress rather than the interpolated progress.

Compilation does not establish that the mixins behave correctly in-game.
