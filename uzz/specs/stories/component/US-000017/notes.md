# Notes for US-000017 - Display Animated Disconnection Pane

## Design Decisions

### Component Architecture
- Created `DisconnectionPane` as a reusable JavaFX component extending `Pane`
- Created `DisconnectionPaneController` as the FXML controller to separate UI logic from the component
- Uses FXML for layout definition (`disconnection-pane.fxml`)
- Animation implemented using JavaFX `AnimationTimer` for frame-by-frame updates
- Animation starts/stops automatically based on the root pane's visibility

### Integration with VNCCanvas
- `VNCCanvas` creates a `DisconnectionPane` instance and layers it on top of the canvas
- `VNCCanvas.connected` property is bound to `DisconnectionPane.visible` property (inverted via `connected.not()`)
- Removed old `disconnectedOverlay` Text node and related methods (`createDisconnectedOverlay`, `centerOverlay`, `drawDisconnectedOverlay`) from VNCCanvas
- DisconnectionPane is resized when VNCCanvas is resized
- Changed `VNCCanvas.connected` default value from `true` to `false` to match expected disconnected state

### Scramble Animation
- Uses `AnimationTimer` for frame-by-frame updates
- Refreshes with new random gray pixels every 0.75 seconds (750_000_000 nanoseconds)
- Cell size of 4x4 pixels for the noise grid
- Alpha of 0.4 for the gray cells to create a semi-transparent effect
- Animation automatically starts when the pane becomes visible and stops when hidden

### Message Styling
- Message text: "Connection to VNC server lost" (hardcoded in FXML)
- Font: System Bold 28pt
- Color: White
- Centered in the pane using layout listeners

## Implementation Details

### Files Created
- `src/main/java/ste/vnc/viewer/DisconnectionPane.java` - Main component class
- `src/main/java/ste/vnc/viewer/DisconnectionPaneController.java` - FXML controller
- `src/main/resources/ste/vnc/viewer/disconnection-pane.fxml` - FXML layout
- `src/test/java/ste/vnc/viewer/DisconnectionPaneTest.java` - Component tests

### Files Modified
- `src/main/java/ste/vnc/viewer/VNCCanvas.java` - Integrated DisconnectionPane, removed old overlay logic
- `src/test/java/ste/vnc/viewer/VNCCanvasTest.java` - Updated tests to work with new DisconnectionPane

## Trade-offs
- Used `AnimationTimer` instead of `Timeline` for smoother animation that automatically pauses when pane is not visible
- Kept the scramble effect simple (coarse grid) for performance
- Message is hardcoded in FXML for now; could be made configurable in future
- Split logic into controller class to follow MVC pattern for FXML-based components

## Open Questions
- Should the scramble effect be configurable (cell size, refresh rate, colors)?
- Should the message text be localizable/internationalizable?