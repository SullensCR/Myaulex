10. If the settings of a module are open, clicking Escape will close the settings, not the ClickGUI, this is not the intended behavior. Clicking Escape should instead discard the changes and close the ClickGUI. To only discard the changes the user is espected to tap outside of the text box.

Enter: commit valid input.
Clicking outside: discard edit
Escape: discard and close ClickGUI.
Invalid input: discard and retain the previous value.
Clicking another setting while editing: discards and performs the action for that setting
Empty numeric input: invalid when committed, but allowed while typing.


11. Dropdown menu must be on top of all the ClickGUI elements

12. The ClickGUI doesn't save the last scroll position and current category when exiting

13. Closing the ClickGUI saves to the default config for some reason

14. When toggling a module on or off, the indicator on the name and at the module bar itself should change color smoothly but fast

15. Allow for numeric values in text boxes to be deleted with direct input on the text box, even to something invalid, the client must parse it to check if its a valid input (eg. a numeric value where only numbers are expected) or discard if invalid, this means the value must not be "locked" if there is already a value in it.

> Example: trying to set 1 in MinCPS is not possible because if the value is 6 by example, I can't delete that 6 to type a 1

