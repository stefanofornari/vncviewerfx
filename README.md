# VNCViewerFX

A reusable JavaFX component for embedding a VNC viewer into desktop applications. It displays a remote desktop streamed via the RFB (VNC) protocol and forwards local mouse/keyboard input back to the server.

## What it is

`VNCViewerFX` provides a single embeddable JavaFX control — `VNCViewer` — that you can drop into any JavaFX scene. The component handles the full VNC client lifecycle: connecting to a server, rendering framebuffer updates, relaying input events, synchronizing the clipboard, and showing a disconnection overlay when the link drops.

The current version targets **TigerVNC-compatible servers** with a fixed, known-good protocol configuration:
- Shared connection
- Server-rendered cursor (local cursor hidden)
- Desktop resize and extended desktop size support
- Client redirect and desktop rename support
- ZRLE encoding (with Tight, Hextile, Raw, CopyRect fallbacks)
- Compression level 0, no JPEG

Authentication is **not** yet supported; the component assumes an open VNC server (e.g. `x11vnc` with `-SecurityTypes None`).

## Prerequisites

- Java **21**
- JavaFX **21**
- A running VNC server reachable from the host machine

## Adding the dependency

```xml
<dependency>
    <groupId>com.github.stefanofornari</groupId>
    <artifactId>vncviewerfx</artifactId>
    <version>0.0.0-SNAPSHOT</version>
</dependency>
```

## Basic usage

### FXML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import ste.vnc.viewer.VNCViewer?>
<VNCViewer fx:id="viewer"
           uri="vnc://localhost:5901"
           connect="AUTO"/>
```

### Programmatic

```java
VNCViewer viewer = new VNCViewer();
viewer.setUri("vnc://localhost:5901");
viewer.setConnect(VNCViewer.ConnectionMode.AUTO);
root.getChildren().add(viewer);
```

## Connection modes

| Mode | Behavior |
|------|----------|
| `AUTO` | The component starts the connection as soon as the FXML controller finishes initializing. |
| `MANUAL` | The component stays disconnected until you call `connect()` explicitly. |

```java
// MANUAL mode — connect later
viewer.setConnect(VNCViewer.ConnectionMode.MANUAL);
// ...
viewer.connect("localhost", 5901);

// Or use the full URI
viewer.setUri("vnc://192.168.1.10:5902");
viewer.start();
```

## Connection URI

The `uri` property accepts a standard `vnc://host:port` URI. The protocol and path segments are currently ignored; only host and port are used.

```java
viewer.setUri("vnc://my-vnc-host.example.com:5901");
```

Default URI is `vnc://localhost:5900`.

## Properties

| Property | Type | Description |
|----------|------|-------------|
| `uri` | `ObjectProperty<URI>` | VNC server address. |
| `connect` | `ConnectionMode` (`AUTO` / `MANUAL`) | Whether the connection starts automatically. |
| `connected` | `BooleanProperty` | `true` while the VNC session is active. |

## Input forwarding

Mouse movement, clicks, drags, and scroll-wheel events are forwarded to the server automatically when the component has focus. Keyboard input is forwarded on press/release/typed events.

If you need custom handling, assign your own listeners:
The component hides the local OS cursor over the canvas so only the remote cursor is visible.

## Clipboard synchronization

Text copied on the remote desktop appears on the local system clipboard, and text copied locally is sent to the remote clipboard via the `ClientCutText` VNC message. The synchronization runs on a 500 ms poll interval and avoids echo loops.

## Disconnection handling

When the connection drops, the component:
- Stops rendering framebuffer updates
- Shows an animated scramble/noise background
- Displays a centered "Connection to VNC server lost" message

The `connected` property flips to `false`, and the `DisconnectionPane` becomes visible automatically.

## Rendering

The component renders the remote desktop into a `javafx.scene.image.ImageView` backed by a `PixelBuffer`. Framebuffer rectangles are accumulated and flushed to the JavaFX pulse thread, so updates are batched efficiently.

## Building the demo

```bash
mvn clean package
mvn javafx:run
```

The demo application (`ste.vnc.viewer.demo.VNCViewerDemo`) opens a 768×1024 window with the embedded viewer and a small control bar (connect, options, full-screen, exit). The options dialog exposes a subset of TigerVNC parameters (encoding, compression, scaling).

## Running tests

```bash
mvn test
```

Tests use **JUnit 5**, **TestFX**, and **AssertJ** and run headless by default (Monocle + Prism software renderer). For headful execution on a desktop:

```bash
mvn test -Pheadful
```

## Debugging

Attach a remote debugger to the JavaFX application thread:

```bash
mvn javafx:run \
  -Djavafx.exec.args="--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED"
```

Or uncomment the commented-out agent line in `pom.xml` under `javafx-maven-plugin`:

```xml
<option>-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000</option>
```

Then connect your IDE to port `8000`.

## Logging

The component uses `java.util.logging`. A default configuration is bundled at `src/main/resources/logging.properties`. Set the system property when launching:

```bash
mvn javafx:run -Djava.util.logging.config.file=src/main/resources/logging.properties
```

Use `logger.finest(...)` in the VNC service for low-level protocol tracing.

## License

GNU General Public License v2 or later.
