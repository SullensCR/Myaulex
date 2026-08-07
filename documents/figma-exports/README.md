# Myaulex Figma export handoff

The Figma connector cannot read the complete free-tier board, so exported
assets should be placed in this directory using the stable names below.

Preferred formats:

- SVG for icons, logos, vector ornaments, masks, and simple gradients.
- PNG at 2× resolution for raster effects, complex blur/glow compositions,
  Minecraft item renders, and reference screenshots.
- PDF only for complete boards or multi-state visual reference. Also include
  the individual SVG/PNG assets used by that board.

Keep transparent backgrounds where applicable and do not flatten shadows into
icons unless the shadow is intentionally part of the asset.

## Full-screen references

- `clickgui-reference.png`
- `client-settings-reference.png`
- `pause-screen-reference.png`
- `hud-reference.png`

Export these at their exact Figma frame dimensions.

## Component states

- `toggle-off.svg`
- `toggle-on.svg`
- `dropdown-closed.svg`
- `dropdown-open.svg`
- `keybind-empty.svg`
- `keybind-bound.svg`
- `hidden-off.svg`
- `hidden-on.svg`
- `client-gear.svg`
- `search.svg`

## Designed surfaces

- `targethud-myaulex.png`
- `nametag-myaulex.png`
- `bedplates-collapsed.png`
- `bedplates-expanded.png`
- `media-player-playing.png`
- `media-player-paused.png`
- `media-player-stopped.png`
- `notification-info.png`
- `notification-warning.png`
- `notification-error.png`
- `notification-analysis.png`
- `notification-config-success.png`
- `notification-config-error.png`
- `notification-config-edit.png`
- `notification-enabled.png`
- `notification-disabled.png`

For any component with hover, pressed, disabled, expanded, or error states,
append `-hover`, `-pressed`, `-disabled`, `-expanded`, or `-error` before the
extension.

## Module icons

Put module icons in `module-icons/` as lowercase kebab-case SVG files, for
example:

- `module-icons/aim-assist.svg`
- `module-icons/inventory-move.svg`
- `module-icons/transaction-analyzer.svg`

Use the same viewBox and optical size for all module icons. Material Rounded or
Material Filled exports are preferred.
