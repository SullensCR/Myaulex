# Straight-only Legit Scaffold acceptance

## Controls and supported setup

Enable **Legit Scaffold** in the Player category. Movement correction is always built in;
Client's Move Fix setting is not read. The only setting is **Initial Sneak**, stored as
2–20 ticks and displayed with milliseconds (default `6t (300 ms)`).

Start grounded on a level full-block row, with usable full building blocks in the hotbar.
Any camera heading is accepted. The camera chooses the nearest north/south/east/west
block row; W/A/S/D only signal that movement is held and never change that direction.
Holding a movement key before enabling is supported. Changing or combining those
keys does not restart the segment while at least one remains held.

Movement correction keeps travel exactly along the chosen row. The character aims
silently to one side while the camera remains mouse-controlled. Turning the camera
into another row's sector starts a new segment, preserving the selected aim side.
A 5-degree margin at the sector boundary prevents tiny camera movements from
repeatedly restarting setup. Releasing all movement keys ends the segment; the next
start picks the nearest row afresh.

The module does not center your position within the block row. The approach across
supported ground does not require a placement ray. At an edge, positions without a
valid sideways ray window pause. Local chat and the module HUD suffix explain the
stop reason. Non-full supports, falling blocks, ladders, water, lava, riding, flight
and item-use are outside this version's supported setup.

The first edge holds sneak for at least six full ticks before placement. Later edges
use one sneak cycle per block, releasing only on a later tick after local support is
present. This is local world confirmation, not an acknowledgement from the server.
Manual sneak is never released automatically. Jump pauses, suppresses jump, and holds
sneak. Release jump to restart setup, even while W remains held. Stopping and resuming
movement repeats the initial hold. Disabling restores the original hotbar slot.

Placement uses the pre-movement action phase, matching regular Scaffold and vanilla
right-click ordering. The slot change, placement and swing occur before the next
movement packet. Placement waits for the fixed look to have been reported on a
previous tick, and uses the player's position and sneak state before movement.
Yaw remains continuous across 180/360-degree boundaries; only angle differences
are wrapped, so the locked direction does not introduce a full-turn numeric snap.

## Automated evidence

The test suite covers startup/repeat timing, turn caps, side retention, movement
correction, support-dependent release, inventory exhaustion, world/disable resets,
rotation conflict detection, setting persistence/bounds, all 360 camera headings,
held-key startup/resume, and stability around row-direction boundaries. Geometry tests trace
30 translated block faces for each of four cardinal directions and both sides.
A separate headless fixture combines the controller with level-cube walking,
sneaking and ray intersections for 30 placements per direction/side, with placement
before movement. Regression tests cover raw yaw deltas across full turns, repeated
direction changes/restarts, and waiting for previously reported aim before placement.

These fixtures do not run the Minecraft client, mixins, renderer or multiplayer
server and are not live gameplay or anticheat verification.

## Live checklist — pending

Use a disposable local creative world first, without other movement modules.

- [ ] For each cardinal direction, complete 30 consecutive placements with each
      chosen aim side. To select a side, disable/re-enable with the camera angled
      slightly to the desired side of the row's direction.
- [ ] Observe one longer initial edge sneak followed by one normal cycle per block.
- [ ] Check third-person head pitch/yaw remain fixed throughout the segment and the
      body presents sideways. Check mouse-controlled first-person camera separately.
- [ ] Hold manual sneak across placements; it must never be released.
- [ ] Hold W before enabling: setup must finish and movement start without releasing W.
- [ ] Switch between W/A/S/D or combine them while holding at least one: the camera,
      not the key combination, chooses the row; there should be no setup restart.
- [ ] Sweep the camera through 360 degrees. Every angle must select a straight row;
      small movements around a row boundary must not cause repeated turns.
- [ ] On the local server, retest the reported Post and AimModulo360 failures.
      Include several full camera turns before enabling, row changes crossing
      180/360-degree boundaries, pause/resume and hotbar stack exhaustion.
- [ ] Press jump: no jump/placement, sneak and pause. Release jump while continuing
      to hold W; setup must resume automatically with the same aim side.
- [ ] Stop/resume, turn to another cardinal direction, and open/close a menu.
- [ ] Start near either lateral edge of a row: an invalid fixed-ray position must
      pause without turning, changing sides, or moving the player to the center.
- [ ] Exhaust a hotbar stack, then all usable stacks. Verify the next usable slot is
      selected visibly, exhaustion pauses, and disabling restores the original slot.
- [ ] Enable regular Scaffold while Legit Scaffold is active and vice versa; only
      the newly enabled module should remain active.
- [ ] Remove/reject a placed block, take external knockback, respawn, change worlds,
      disconnect, and disable. No stale placement/input/slot ownership should carry
      into the next player/world. Sneak cannot prevent an already airborne fall.

Towering, diagonal bridging, and timing randomization remain deferred.
