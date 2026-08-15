package ste.vnc.viewer;

import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

public class VNCViewerController {

    final Logger logger = Logger.getLogger(getClass().getName());

    // Last clipboard contents that originated from the server, used to
    // avoid echoing clipboard updates straight back to the server.
    private volatile String lastClipboardFromServer = "";

    // Last clipboard contents we actually sent to the server from the
    // local system clipboard.
    private volatile String lastClipboardSent = "";

    @FXML
    public ImageView canvas;

    @FXML
    public DisconnectionPane disconnectionPane;

    @FXML
    public VNCViewer viewer;

    private VNCService connection;

    @FXML
    public void initialize() {
        connection = new VNCService();

        canvas.imageProperty().bind(connection.image);

        final EventBridge eventBridge = new EventBridge();

        viewer.mouseListener = new MouseInputListener(connection, eventBridge);
        viewer.keyboardListener = new KeyboardInputListener(connection, eventBridge);

        // Start the RFB processing loop on a background thread so that
        // incoming framebuffer updates are handled continuously.
        // Use a virtual thread for blocking socket I/O loops
        Thread rfbThread = Thread.ofVirtual()
            .name("VncViewerFx-RFB")
            .start(() -> {
                try {
                    while (connection.connected.get()) {
                        connection.processMsg();
                    }
                } catch (Exception e) {
                    logger.warning("RFB loop terminated: " + e.getMessage());
                } finally {
                    // Ensure connection cleanup and thread-safe UI update
                    Platform.runLater(() -> {
                        connection.connected.set(false);
                    });
                    connection.close();
                }
            });

        connection.clipboard.addListener((o, ov, nv) -> {
            logger.finest("received clipboard content from %s: %s".formatted(o, nv));
            setServerClipboardText(nv);
        });

        // Start clipboard synchronization on the JavaFX application thread:
        // poll the local clipboard periodically and push changes to the
        // server as ClientCutText messages.
        Timeline clipboardTimeline = new Timeline(
            new KeyFrame(Duration.millis(500), ev -> {
                if (connection == null || !connection.connected.get()) {
                    return;
                }

                try {
                    String current = getClipboard();
                    if (current == null) {
                        current = "";
                    }

                    // Skip if unchanged or if this value just came from the server.
                    if (!current.equals(lastClipboardSent)
                        && !current.equals(lastClipboardFromServer)) {
                        lastClipboardSent = current;

                        String toSend = current;

                        logger.finest("Sending ClientCutText, length=" + toSend.length());
                        connection.writeClientCutText(toSend, toSend.length());
                    }
                } catch (Exception ex) {
                    logger.info("Clipboard sync failed: " + ex.toString());
                }
            })
        );
        clipboardTimeline.setCycleCount(Timeline.INDEFINITE);
        clipboardTimeline.setDelay(Duration.millis(500));
        clipboardTimeline.play();

        // Set up scroll pane viewport bounds listener for desktop resize
        viewer.viewportBoundsProperty().addListener((o, was, is) -> {
            if (connection != null && connection.connected.get() && is != null) {
                int w = (int)is.getWidth();
                int h = (int)is.getHeight();
                if (w != (int)was.getWidth() || h != (int)was.getHeight()) {
                    connection.requestDesktopSize(w, h);
                }
            }
            disconnectionPane.setPrefSize(is.getWidth(), is.getHeight());
        });
        viewer.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            viewer.requestFocus();
            viewer.handleKeyPressed(e);
            e.consume();
        });
        viewer.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            viewer.handleKeyReleased(e);
            e.consume();
        });
        viewer.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            viewer.handleKeyTyped(e);
            e.consume();
        });

        viewer.connected.bind(connection.connected);

        disconnectionPane.visibleProperty().bind(viewer.connected.not());
        disconnectionPane.managedProperty().bind(disconnectionPane.visibleProperty());

        canvas.visibleProperty().bind(viewer.connected);
        canvas.managedProperty().bind(canvas.visibleProperty());

        //
        // cleanup when the component is removed from the scene
        //
        viewer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                logger.info("closing vnc connection");
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }

    public void setServerClipboardText(String text) {
        if (text == null) {
            text = "";
        }
        lastClipboardFromServer = text;

        final String clipText = text;
        Platform.runLater(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(clipText);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    public String getClipboard() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb != null && cb.hasString()) {
            return cb.getString();
        }
        return "";
    }

}