package ste.vnc.viewer;

import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
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
    public Canvas canvas;

    @FXML
    public DisconnectionPane disconnectionPane;

    @FXML
    public VNCViewer viewer;

    private CConnFX connection;

    @FXML
    public void initialize() {
        connection = new CConnFX(viewer);

        final EventBridge eventBridge = new EventBridge();

        viewer.mouseListener = new MouseInputListener(connection, eventBridge);
        viewer.keyboardListener = new KeyboardInputListener(connection, eventBridge);

        // Start the RFB processing loop on a background thread so that
        // incoming framebuffer updates are handled continuously.
        Thread rfbThread = new Thread(() -> {
            try {
                while (connection.connected.get()) {
                    connection.processMsg();
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.warning("RFB loop terminated: " + e.toString());
                connection.connected.set(false);
            }
        }, "VncViewerFx-RFB");
        rfbThread.setDaemon(true);
        rfbThread.start();

        connection.canvasSize.addListener((o, ov, nv) -> {
            Platform.runLater(() -> {
                viewer.resizeDesktop((int) nv.getWidth(), (int) nv.getHeight());
            });
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
        viewer.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            if (connection != null && connection.connected.get() && newVal != null) {
                int w = (int) Math.round(newVal.getWidth());
                int h = (int) Math.round(newVal.getHeight());
                connection.requestDesktopSize(w, h);
            }
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

        // Bind disconnection pane visibility to connected property (inverted)
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