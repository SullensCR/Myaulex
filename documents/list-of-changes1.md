# List of changes for Myaulex

1. When opening a dropdown menu the ClickGUI doesn't register inputs to the dropdown menu, my suggestion for this is to have like an overlay or layer when opening a dropdown, so the inputs go to the dropdown instead of whatever elements are behind the dropdown, clicking outside of the dropdown menu will just close the dropdown.

2. Make the default scaling x1.0, and saved to the `client` internal module settings

3. I'm noticing that no modules have icons, the circle there was meant for a placeholder, I may have not mentioned it.
   I suggest using Google Material Icons for that (Rounded and Filled!)

4. I notice there are no animations, which is fine since I told you to not do them yet. But I also noticed that there may be a scale in when opening the ClickGUI, but I can't see it as it buffers/freezed a bit when opening the ClickGUI, maybe trying to keep the ClickGUI rendered or loaded in the background may solve this small freeze, but that also puts some load on the renderer/gpu because of the blur, so I thought, what if anything that uses blur on the ClickGUI stays solid with no blur while the ClickGUI is not loaded, and when its loaded it smoothly transitions from solid to translucent + blur.

5. Search changes:
- Pressing Escape while focused on the text box/while typing must exit the ClickGUI. Basically the search should do a two in one `exit the search box` and `exit the ClickGUI`

- Search should include all modules (except `Client`) regardless of the current category.

- When searching, deselect the current category, when the search is cleared, return to the previous category.


6. The icon for "no keybind set" isn't showing up,

7. The icon for "hidden in arraylist" is off-center, not properly aligned.

> Important to mention: Previously, you fixed an issue with the category icons where these had their sizes changed due to an issue on how OpenGL renders stuff, etc. Was this fix applied to the category icons or globally to whatever logic or system handles icons/vectors.

8. Search and logo are not in "in-line", search is a bit more high than the logo
   This kind of applies to all text in the ClickGUI, seems to be a bit more high up, might be an issue with the font or the way fonts are rendered?

9. As I mentioned, there are no animations and I want animations ofc so here is a list.
- When opening the ClickGUI smoothly fade the entire background/screen and the ClickGUI Menu (Not Category bar!) should either slide or scale by randomness
  What I mean by this is that the ClickGUI open and close animation will be randomized to either be
  `Slide In from the top`
  `Slide In from the bottom`
  `Scale in or out (out when closing it)`
  
  Expanding or collapsing a module's settings must also have a smooth animation

- Pill shaped toggles must have a smooth animation aswell

- Separate the logo into individual letters and present a smooth slightly slow sequential animation that moves the letters from the top of the menu (hidden) to the original position (now visible). A similar animation for the icons on the category bar applied too but from the bottom of the menu (hidden) to the original position (now visible)

- Switching categories should smoothly fade the bottom indicator 

> The top indicator isn't showing up either, the icon itself should transition from the gray (unselected) to the proper color (selected)

![](/home/dragon/.config/marktext/images/2026-08-03-21-02-42-image.png)

- Smooth scrolling

- Category icons should hava a smooth sequential slide-in transition from the bottom (not visible) to their normal position (now visible)

And anything else in the client such as notifications and pause screen should have animations too, you are free to choose the values for delays etc.