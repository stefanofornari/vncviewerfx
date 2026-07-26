package ste.vnc.viewer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import com.tigervnc.network.Socket;
import com.tigervnc.network.TcpSocket;
import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.Security;
import com.tigervnc.rfb.SecurityClient;

public class VNCViewerFX extends Application {

    public final Param shared = new Param(true); // TODO:

    private Viewport viewport;
    private FxCConn connection;
    private DesktopCanvas canvas;

    // Last clipboard contents that originated from the server, used to
    // avoid echoing clipboard updates straight back to the server.
    private volatile String lastClipboardFromServer = "";
    // Last clipboard contents we actually sent to the server from the
    // local system clipboard.
    private volatile String lastClipboardSent = "";

    private static final LogWriter vlog = new LogWriter("VncViewerFx");

    public Viewport createViewport(String title, DesktopCanvas canvas, FxCConn connection) {
        Viewport v = new Viewport(title, canvas, connection);
        this.viewport = v;
        return v;
    }

    public void setServerClipboardText(String text) {
        if (text == null) {
            text = "";
        }
        lastClipboardFromServer = text;

        final String clipText = text;
        Runnable r = () -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(clipText);
            Clipboard.getSystemClipboard().setContent(content);
        };

        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    public String getClipboard() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb != null && cb.hasString()) {
            return cb.getString();
        }
        return "";
    }

    @Override
    public void start(Stage primaryStage) {

        // Enable viewer parameters so Configuration.setParam() can see them
        Configuration.enableViewerParams();


        // Restrict security to None only
        SecurityClient.setDefaults();
        Security.enabledSecTypes.clear();
        Security.EnableSecType(Security.secTypeNone);

        try {
            // Establish TCP socket to the server
            Socket sock = new TcpSocket("127.0.0.1", 5905);

            // Create canvas and connection
            canvas = new DesktopCanvas(800, 600);
            connection = new FxCConn(this, sock, "127.0.0.1:5905");
            canvas.setConnection(connection);
            connection.setCanvas(canvas);

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
                    vlog.error("RFB loop terminated: " + e.toString());
                }
            }, "VncViewerFx-RFB");
            rfbThread.setDaemon(true);
            rfbThread.start();

            // Create and show viewport window
            viewport = new Viewport("127.0.0.1:5905", canvas, connection);
            connection.setViewport(viewport);
            viewport.setOnClose(connection::close);
            viewport.show();

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

                            int max = VNCConfiguration.maxCutText.getValue();
                            String toSend = current;
                            if (max > 0 && toSend.length() > max) {
                                toSend = toSend.substring(0, max);
                            }

                            vlog.debug("Sending ClientCutText, length=" + toSend.length());
                            connection.writeClientCutText(toSend, toSend.length());
                        }
                    } catch (Exception ex) {
                        vlog.error("Clipboard sync failed: " + ex.toString());
                    }
                })
            );
            clipboardTimeline.setCycleCount(Timeline.INDEFINITE);
            clipboardTimeline.setDelay(Duration.millis(500));
            clipboardTimeline.play();
        } catch (Exception e) {
            vlog.error("Failed to start FX viewer: " + e.getMessage());
            // If startup fails, just exit the application.
            primaryStage.close();
        }
    }

    public static class Param {

        private boolean b;
        private int i;
        private String s;

        Param(boolean b) {
            this.b = b;
        }

        Param(int i) {
            this.i = i;
        }

        Param(String s) {
            this.s = s;
        }

        boolean getValue() {
            return b;
        }

        int getValueInt() {
            return i;
        }

        String getValueStr() {
            return s;
        }

        void setParam(boolean b) {
            this.b = b;
        }

        void setParam(String s) {
            this.s = s;
        }

        String getDefaultStr() {
            return s == null ? "" : s;
        }
    }
}
