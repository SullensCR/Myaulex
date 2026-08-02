# ClickGUI Implementation Specification

## Goal

Replace the existing ClickGUI with the design provided by the SVG and image references.

The final result should visually match the design as closely as possible while preserving the existing module system, setting system, event system and client architecture.

This is a VISUAL REWORK ONLY.

Gameplay logic must remain untouched.

---

# Phase 1 - Analyze

Before writing code:

* Analyze every SVG group and object.
* Generate a hierarchy of UI components.
* Determine padding, spacing, corner radii and dimensions.
* Reuse common components instead of duplicating rendering code.

Do NOT begin implementation until the hierarchy is complete.

---

# Phase 2 - Rendering System

If the current rendering utilities are insufficient:

Create new rendering utilities.

Allowed utilities include:

* Rounded rectangles
* Circle rendering
* Gradient rectangles
* Horizontal gradients
* Vertical gradients
* Scissor clipping
* Blur helper
* Stencil helper
* Framebuffer helper
* Glow helper if needed
* Animation helper
* Color interpolation helper

Do NOT modify existing rendering utilities unless absolutely necessary.

Prefer creating new helper methods.

---

# Phase 3 - Component System

Implement reusable components:

* Category Header
* Module Button
* Toggle Switch
* Dropdown
* Slider
* Text Setting
* Container
* Scroll Area

Each component should be independently renderable.

No duplicated drawing code.

---

# Phase 4 - Layout

Follow SVG positions exactly.

Do not estimate spacing.

Do not approximate proportions.

Do not redesign anything.

Padding and margins should match the SVG.

Use consistent spacing throughout.

---

# Phase 5 - Colors

Extract colors directly from the SVG.

Do not substitute colors.

Do not change saturation.

Do not add shadows unless present.

Do not add outlines unless present.

---

# Phase 6 - Animations

Animations should be smooth.

Recommended:

* Toggle: 180ms
* Dropdown: 200ms
* Slider interpolation: smooth
* Hover fade: 120ms

Do not use abrupt transitions.

---

# Phase 7 - Existing Client Integration

Do NOT modify:

* ModuleManager
* SettingManager
* EventBus
* Combat modules
* Movement modules
* HUD modules
* Configuration system

Reuse existing APIs whenever possible.

The new GUI should act as a renderer over the existing architecture.

---

# Phase 8 - Forbidden Changes

Do NOT:

* Rewrite modules
* Copy modules from another client
* Copy settings
* Add duplicate settings
* Change module logic
* Change setting logic
* Change packet logic
* Change rotations
* Change combat code
* Change scaffold code
* Change KillAura
* Change event handling

Visual changes only.

---

# Phase 9 - Before Every Commit

Verify:

* Visual appearance matches SVG.
* Existing modules still function.
* Existing settings still function.
* No duplicate code was added.
* No gameplay logic changed.
* Rendering utilities remain reusable.

If any gameplay logic changes, revert and retry.

The implementation should be indistinguishable from the provided design while preserving the existing client functionality.
