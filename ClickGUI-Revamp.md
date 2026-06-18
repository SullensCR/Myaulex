UI Overview
The image shows a Minecraft Utility Client ClickGUI overlaying a blurred in-game background. The interface consists of 5 floating, rounded rectangular panels (windows) arranged horizontally.

Each panel represents a specific module category, featuring a color-coded accent, a title, and a vertical list of toggles, sliders, or dropdowns.

Technical Layout Breakdown
1. Global Window Properties
   Background: Dark semi-transparent grey/black (#18181B or similar with ~85% opacity).

Corners: Rounded corners (approx. 8px to 12px border-radius).

Layout Behavior: Panels act as independent vertical containers, potentially draggable.

2. Panel Components (Top-to-Bottom Hierarchy)
   Header: * A colored vertical indicator bar on the far left.

The Category Title in bold white text.

Module Rows: * Disabled State: Light grey text (#A1A1AA), toggle switch is off (white circle on the left, dark track).

Enabled State: Bold white text prefixed by a colored dot matching the category accent. Toggle switch is on (white circle on the right, colored track).

Sub-Settings (Expanded Modules): Nested elements appearing directly below a module header, containing sliders, dropdowns, or sub-toggles.

Left clicking a module header toggles its enabled state. Right-clicking may expand/collapse module settings and keybind button if available, if not, only keybind button will be shown.

Fade Animation: Smooth fade-in and fade-out transitions for panels and module toggle buttons when tapped.