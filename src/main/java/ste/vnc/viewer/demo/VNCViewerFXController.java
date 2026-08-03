package ste.vnc.viewer.demo;

import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.Security;
import com.tigervnc.rfb.SecurityClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;
import ste.vnc.viewer.CConnFX;
import ste.vnc.viewer.EventBridge;
import ste.vnc.viewer.KeyboardInputListener;
import ste.vnc.viewer.MouseInputListener;
import ste.vnc.viewer.VNCCanvas;

public class VNCViewerFXController {

    @FXML
    private TextArea infoText;
    @FXML
    private ScrollPane canvasScrollPane;
    @FXML
    private RectangleTracePane rectangleTracePane;

    private VNCCanvas canvas;
    private CConnFX connection;
    private Runnable onClose;

    public Stage stage;

    // Last clipboard contents that originated from the server, used to
    // avoid echoing clipboard updates straight back to the server.
    private volatile String lastClipboardFromServer = "";
    // Last clipboard contents we actually sent to the server from the
    // local system clipboard.
    private volatile String lastClipboardSent = "";

    private static final LogWriter logger = new LogWriter(VNCViewerFXController.class.getName());

    static {
        /* TODO
        final Level l = Logger.getLogger(VNCViewerFXController.class.getName()).getLevel();
        logger.setLevel(
            switch(l.getName()) {
                case "INFO" -> 30;
                case "ERROR" -> 0;
                default -> 100;
            }
        );*/
        logger.setLevel(100);
    }

    @FXML
    public void initialize() {
        // Set up scroll pane viewport bounds listener for desktop resize
        canvasScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            if (connection != null && newVal != null) {
                int w = (int) Math.round(newVal.getWidth());
                int h = (int) Math.round(newVal.getHeight());
                connection.requestDesktopSize(w, h);
            }
        });
        canvasScrollPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            canvas.requestFocus();
            canvas.handleKeyPressed(e);
            e.consume();
        });
        canvasScrollPane.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            canvas.handleKeyReleased(e);
            e.consume();
        });
        canvasScrollPane.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            canvas.handleKeyTyped(e);
            e.consume();
        });

        // Enable viewer parameters so Configuration.setParam() can see them
        Configuration.enableViewerParams();

        // Restrict security to None only
        SecurityClient.setDefaults();
        Security.enabledSecTypes.clear();
        Security.EnableSecType(Security.secTypeNone);

        final EventBridge eventBridge = new EventBridge();

        // Create canvas and connection
        canvas = new VNCCanvas(600, 800);

        connection = new CConnFX(canvas, canvas::redraw, rectangleTracePane);

        canvas.mouseListener = new MouseInputListener(connection, eventBridge);
        canvas.keyboardListener = new KeyboardInputListener(connection, eventBridge);

        // Start the RFB processing loop on a background thread so that
        // incoming framebuffer updates are handled continuously.
        Thread rfbThread = new Thread(() -> {
            try {
                while (connection.isConnected()) {
                    connection.processMsg();
                }
            } catch (Exception e) {
                // Print full stack trace to help diagnose rendering/decoding issues.
                e.printStackTrace();
                logger.error("RFB loop terminated: " + e.toString());
            }
        }, "VncViewerFx-RFB");
        rfbThread.setDaemon(true);
        rfbThread.start();

        connection.canvasSize.addListener((o, ov, nv) -> {
            Platform.runLater(() -> {
                canvas.resizeDesktop((int) nv.getWidth(), (int) nv.getHeight());
            });
        });
        connection.clipboard.addListener((o, ov, nv) -> {
            logger.debug("received clipboard content from %s: %s".formatted(o, nv));
            setServerClipboardText(nv);
        });

        // Start clipboard synchronization on the JavaFX application thread:
        // poll the local clipboard periodically and push changes to the
        // server as ClientCutText messages.
        Timeline clipboardTimeline = new Timeline(
            new KeyFrame(Duration.millis(500), ev -> {
                if (connection == null || !connection.isConnected()) {
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

                        logger.debug("Sending ClientCutText, length=" + toSend.length());
                        connection.writeClientCutText(toSend, toSend.length());
                    }
                } catch (Exception ex) {
                    logger.error("Clipboard sync failed: " + ex.toString());
                }
            })
        );
        clipboardTimeline.setCycleCount(Timeline.INDEFINITE);
        clipboardTimeline.setDelay(Duration.millis(500));
        clipboardTimeline.play();

        canvasScrollPane.setContent(canvas);

        rectangleTracePane.setOnSelectionChanged(selected -> {
            if (selected == null) {
                canvas.clearSelectionOverlay();
            } else {
                canvas.setSelectionOverlay(selected.x(), selected.y(), selected.width(), selected.height());
            }
        });
    }

    @FXML
    private void onConnect() {
        // TODO
    }

    @FXML
    private void onCloseWindow() {
        if (stage != null) {
            stage.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @FXML
    private void onExit() {
        System.exit(0);
    }

    @FXML
    private void onToggleFullScreen() {
        if (stage != null) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    @FXML
    private void onOptions() {
        if (stage != null) {
            new OptionsDialogFX(stage).show();
        }
    }

    @FXML
    private void onAbout() {
        // TODO
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
