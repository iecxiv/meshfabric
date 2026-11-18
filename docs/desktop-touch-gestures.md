# Desktop touch gestures

English | [Español](es/desktop-touch-gestures.md)

## Using the controls

Select Left mode for normal pointing. Tap for a left click. While zoomed in, swipe with one finger to pan the local view without clicking the remote desktop. Tap twice quickly for a double click. To drag a window or select text, tap and then touch again nearby, keeping the second touch down while moving; lift the finger to release.

Pinch with two fingers to zoom and move both fingers to pan. Right mode sends a right click when the desktop is tapped. Scroll mode uses vertical swipes for remote scrolling. The separate floating right-click button arms the next desktop tap as a right click. A long press alone does not produce a right click. Physical mouse dragging uses the held left button; right click and the wheel retain their usual roles.

The floating toolbar information button opens the in-app gesture guide in English or Spanish. The smaller gray information icon does not reduce the existing IconButton layout.

## Click coordination and limits

The left-mode touch path defers a single click while considering a second tap and coordinates the double-click pair. Pending clicks are bound to their original connection and can be cancelled when the session or input context changes. This is not a remote execution acknowledgement or a guarantee of reliable double clicking on every host or network. The remote application and OS still decide how received clicks are interpreted.

## Validation

Build and run the relevant JUnit tests, including the 13 tests added for the click coordinator. Their presence does not establish that they have run successfully. Verify single and double taps, second-touch dragging and release, zoomed one-finger panning, two-finger zoom/pan, right-click assist, rotation and dialog transitions on real devices. Repeat with latency, coverage loss, disconnects and reconnections, checking that old input is not replayed into a new session.

The display selector and the proposed visual changes to left/right mode controls remain outside these changes. See [Device sessions](device-sessions.md) for the configurable Disconnect all confirmation.
