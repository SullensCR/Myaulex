# List of changes for Myaulex

1. When opening a dropdown menu the ClickGUI doesn't register inputs to the dropdown menu, my suggestion for this is to have like an overlay or layer when opening a dropdown, so the inputs go to the dropdown instead of whatever elements are behind the dropdown, clicking outside of the dropdown menu will just close the dropdown.

2. Make the default scaling x1.0, and saved to the `client` internal module settings

3. I'm noticing that no modules have icons, the circle there was meant for a placeholder, I may have not mentioned it.
   I suggest using Google Material Icons for that (Rounded and Filled!)

4. I notice there are no animations, which is fine since I told you to not do them yet. But I also noticed that there may be a scale in when opening the ClickGUI, but I can't see it as it buffers/freezed a bit when opening the ClickGUI, maybe trying to keep the ClickGUI rendered or loaded in the background may solve this small freeze, but that also puts some load on the renderer/gpu because of the blur, so I thought, what if anything that uses blur on the ClickGUI stays solid with no blur while the ClickGUI is not loaded, and when its loaded it smoothly transitions from solid to translucent + blur.

5. Search changes:
- Pressing Escape while focused on the text box/while typing must exit the ClickGUI. Basically the search should do a two in one `exit the search box` and `exit the ClickGUI`

- Search should include all modules (except `Client`) regardless of the current category.

- When searching, deselect the current category.
1. The icon for "no keybind set" isn't showing up,

2. The icon for "hidden in arraylist" is off-center, not properly aligned.

> Important to mention: Previously, you fixed an issue with the category icons where these had their sizes changed due to an issue on how OpenGL renders stuff, etc. Was this fix applied to the category icons or globally to whatever logic or system handles icons/vectors.

1. Search and logo are not in "in-line", search is a bit more high than the logo
   This kind of applies to all text in the ClickGUI, seems to be a bit more high up, might be an issue with the font? If that was the case we could switch to a Monospaced font, as mono fonts have the same size on every character.

2. As I mentioned, there are no animations and I want animations ofc so here is a list.
- When opening the ClickGUI smoothly fade the entire background/screen and the ClickGUI Menu (Not Category bar!) should either slide or scale by randomness
  What I mean by this is that the ClickGUI open and close animation will be randomized to either be
  `Slide In from the top`
  `Slide In from the bottom`
  `Scale in or out`
  
  Expanding or collapsing a module's settings must also have a smooth animation

- Pill shaped toggles must have a smooth animation aswell

- Separate the logo into separated letters and present a smooth slightly slow animation that moves the letters from the top of the menu (hidden) to the original position (now visible). A similar animation for the icons on the category bar applied too but from the bottom of the menu (hidden) to the original position (now visible)

- Switching categories should smoothly fade the bottom indicator 

> The top indicator isn't showing up either, the icon itself should transition from the gray (unselected) to the proper color (selected)

![](/home/dragon/.config/marktext/images/2026-08-03-21-02-42-image.png)

- Smooth scrolling

- The first time
10. If the settings of a module are open, clicking Escape will close the settings, not the ClickGUI, this is not the intended behavior

11. Dropdown menu must be on top of all the ClickGUI elements

12. The ClickGUI doesn't save the last scroll position and current category when exiting

13. Closing the ClickGUI saves to the default config for some reason

14. When toggling a module on or off, the indicator on the name and at the module bar itself should change color smoothly but fast

15. Allow for numeric values in text boxes to be deleted with direct input, this means the value must not be "locked" if there is already a value, allow for un-allowed values, but show a notification when the maximum or minimum value are outside of the suggested limits

> Example: trying to set 1 in MinCPS is not possible because if the value is 6 by example, I can't delete that 6 to type a 1

17. I need a solution for module settings that have their own options, settings available by other settings must be more "noticeable", my idea for that is simple: X Offset
    Let's call it child settings, coming from parent settings:

> Example: 

`  Scaffold:` <- Module
`    Keep-Y: "telly"` <- Parent setting
`      Keep Y On Press: false` <- Child Setting
`      No Keep Y On Jump Potion: false` <- Another Child Setting

` ` <- Separator/Blank space

`Multi Place: true`

The idea here is that the Child Settings get properly separated, this can be done by:

- X offset of a couple of pixels (or equivalent)

- 10% background brightness decrease

- Separator space/line between Child Settings and other settings in the module.
18. Extensive module changes, check module-changes.yml
19. Save keybinds under a new file. `keybinds.json`
20. Rework the module status and setting changes to a new file: `latest.json`
21. With the introduction of the internal "Client" module, save those settings to a new file. `client.json`
