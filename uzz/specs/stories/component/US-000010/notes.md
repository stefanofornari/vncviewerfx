# Notes for US-000010 - Handle Connection Loss Gracefully

## Design Decisions
- Use a JavaFX BooleanProperty on CConnFX to represent connection status, named `connected`.
- VNCCanvas reacts to connection changes via a listener, entering a disconnected state when `connected` becomes false.
- When disconnected, VNCCanvas:
  - Stops applying framebuffer updates.
  - Renders a noise/static background.
  - Displays a centered error message "Connection to VNC server lost" in a big font inside a double-bordered square.
- Detailed error information is written to logs, not displayed inside the canvas.
- The error message is rendered via a JavaFX overlay node layered on top of the canvas, so that tests can assert visibility without relying on pixel-level checks.

## Implementation Notes
- CConnFX.connectionLost() and close() set `connected` to false on the JavaFX Application Thread.
- VNCCanvas exposes a `setDisconnected(boolean)` method responsible for switching rendering mode and triggering a redraw.
- VNCViewerFXController wires CConnFX.connectedProperty() to VNCCanvas by registering a listener.
- Framebuffer update methods in VNCCanvas short-circuit when in disconnected state to prevent further visual updates.

## Open Questions / Future Enhancements
- Add support for localized/customizable error messages.
- Add a reconnection flow that can clear the disconnected state and restore normal rendering.
