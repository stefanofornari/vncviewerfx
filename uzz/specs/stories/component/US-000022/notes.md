# Notes for US-000022 - Set retry timeout for automatic reconnection

## Design Decisions

### Component Architecture
- Added `reconnectTimeout` property (`ObjectProperty<Duration>`) to `VNCViewer` as the public API.
- Default value is `Duration.seconds(10)` to preserve existing behavior.
- `VNCViewerController` propagates `viewer.reconnectTimeout` to `DisconnectionPane` both at initialization and via a property listener for runtime changes.
- `DisconnectionPane` exposes `setReconnectTimeout(Duration)` / `getReconnectTimeout()` to decouple the controller from the viewer.
- `DisconnectionPaneController` owns its own `ObjectProperty<Duration> reconnectTimeout` and uses it in `startCountdown()` instead of a hardcoded constant.

### API Shape
- Followed story requirements for both legacy and modern JavaFX-style aliases:
  - `getReconnectTimeout()` / `setReconnectTimeout(Duration)`
  - `reconnectTimeout()` / `reconnectTimeout(Duration)`
  - `reconnectTimeoutProperty()`
- Validation rejects `null` and non-positive durations with `IllegalArgumentException`.

### Integration
- The countdown displayed on the reconnect button reflects the configured timeout (e.g. `"Retry connection (30s)"`).
- Changing `viewer.reconnectTimeout` at runtime updates the disconnection pane countdown immediately.

## Implementation Details

### Files Modified
- `src/main/java/ste/vnc/viewer/VNCViewer.java` - Added `reconnectTimeout` property and accessors
- `src/main/java/ste/vnc/viewer/DisconnectionPane.java` - Added `setReconnectTimeout` / `getReconnectTimeout`
- `src/main/java/ste/vnc/viewer/DisconnectionPaneController.java` - Replaced hardcoded `COUNTDOWN_SECONDS` with configurable property
- `src/main/java/ste/vnc/viewer/VNCViewerController.java` - Wired viewer property to disconnection pane

### Files Modified (Tests)
- `src/test/java/ste/vnc/viewer/VNCViewerSpec.java` - Added property, binding, validation, and propagation tests
- `src/test/java/ste/vnc/viewer/DisconnectionPaneTest.java` - Added custom timeout and invalid-value tests
- `src/test/resources/ste/vnc/viewer/VNCViewer.fxml` - Added `reconnectTimeout="30s"` to verify FXML parsing

## Trade-offs
- Kept `DisconnectionPaneController` owning its own `reconnectTimeout` property rather than reaching into the viewer directly, preserving separation of concerns.
- Used `(int) reconnectTimeout.get().toSeconds()` for the integer countdown display, matching the existing `remainingSeconds` semantics.

## Open Questions
- None
