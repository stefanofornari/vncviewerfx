package ste.vnc.viewer;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.IOException;
import java.util.logging.Logger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;

/**
 * A JavaFX Canvas for displaying and interacting with a remote desktop via VNC.
 * <p>
 * This class is designed to be reusable as a pure JavaFX component and can be
 * embedded in your own applications. It exposes {@code public} fields for mouse
 * and keyboard listener assignment, allowing flexible integration.
 * </p>
 * <h3>Usage Example</h3>
 * <pre>{@code
 *   DesktopCanvas canvas = new DesktopCanvas(800, 600);
 *   // Assign listeners as needed, example:
 *   canvas.mouseListener = new MyMouseInputListener(...);
 *   canvas.keyboardListener = new MyKeyboardInputListener(...);
 *   // Add to your JavaFX scene:
 *   root.getChildren().add(canvas);
 * }
 * </pre>
 *
 * See {@link ste.vnc.viewer.demo.VNCViewerFX} for a ready-to-run demo
 * application.
 */
public class VNCViewer extends ScrollPane {

    public MouseInputListener mouseListener;
    public KeyboardInputListener keyboardListener;
    private final VNCViewerController controller;

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final AtomicBoolean redrawPending = new AtomicBoolean();

    public final BooleanProperty connected = new SimpleBooleanProperty(false);


    public VNCViewer() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("VNCViewer.fxml"));
        fxmlLoader.setRoot(this);

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && keyboardListener != null) {
                keyboardListener.releaseAllKeys();
            }
        });

        try {
            fxmlLoader.load();
            // Retrieve the controller instance created by FXMLLoader
            this.controller = fxmlLoader.getController();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load VNCViewer.fxml", exception);
        }

        controller.canvas.setFocusTraversable(true);
        // Hide the local OS pointer over the canvas so only the remote
        // cursor rendered by the server is visible.
        controller.canvas.setCursor(Cursor.NONE);
        controller.canvas.setOnMousePressed(this::handleMousePressed);
        controller.canvas.setOnMouseReleased(this::handleMouseReleased);
        controller.canvas.setOnMouseMoved(this::handleMouseMoved);
        controller.canvas.setOnMouseDragged(this::handleMouseDragged);
        controller.canvas.setOnScroll(this::handleScroll);
        controller.canvas.setOnKeyPressed(this::handleKeyPressed);
        controller.canvas.setOnKeyReleased(this::handleKeyReleased);
        controller.canvas.setOnKeyTyped(this::handleKeyTyped);
        controller.canvas.setOnMouseEntered(e -> requestFocus());
    }

    public void remoteCursor(int width, int height, com.tigervnc.rfb.Point hotspot,
        int[] data, byte[] mask) {
        if (width <= 0 || height <= 0 || data == null || mask == null) {
            return;
        }

        WritableImage img = new WritableImage(width, height);
        PixelWriter pw = img.getPixelWriter();

        int maskBytesPerRow = (width + 7) / 8;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int byteIndex = y * maskBytesPerRow + x / 8;
                int bit = 7 - x % 8;
                boolean visible = (mask[byteIndex] & (1 << bit)) != 0;
                int argb = visible ? data[y * width + x] : 0x00000000;
                pw.setArgb(x, y, argb);
            }
        }

        double hx = hotspot != null ? hotspot.x : 0;
        double hy = hotspot != null ? hotspot.y : 0;

        controller.canvas.setCursor(new ImageCursor(img, hx, hy));
    }

    public void handleMousePressed(javafx.scene.input.MouseEvent e) {
        requestFocus();
        if ((mouseListener != null) && (connected.get())) {
            mouseListener.onMousePressed(e);
        }
    }

    public void handleMouseReleased(javafx.scene.input.MouseEvent e) {
        if ((mouseListener != null) && (connected.get())) {
            mouseListener.onMouseReleased(e);
        }
    }

    public void handleMouseMoved(javafx.scene.input.MouseEvent e) {
        if ((mouseListener != null) && (connected.get())) {
            mouseListener.onMouseMoved(e);
        }
    }

    public void handleMouseDragged(javafx.scene.input.MouseEvent e) {
        if ((mouseListener != null) && (connected.get())) {
            mouseListener.onMouseMoved(e);
        }
    }

    public void handleScroll(javafx.scene.input.ScrollEvent e) {
        if ((mouseListener != null) && (connected.get())) {
            mouseListener.onMouseScroll(e);
        }
    }

    public void handleKeyPressed(javafx.scene.input.KeyEvent e) {
        if ((keyboardListener != null) && (connected.get())) {
            keyboardListener.onKeyPressed(e);
        }
    }

    public void handleKeyReleased(javafx.scene.input.KeyEvent e) {
        if ((keyboardListener != null) && (connected.get())) {
            keyboardListener.onKeyReleased(e);
        }
    }

    public void handleKeyTyped(javafx.scene.input.KeyEvent e) {
        if ((keyboardListener != null) && (connected.get())) {
            keyboardListener.onKeyTyped(e);
        }
    }
}
