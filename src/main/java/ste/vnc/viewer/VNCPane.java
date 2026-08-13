package ste.vnc.viewer;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import com.tigervnc.rfb.LogWriter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

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
public class VNCPane extends Pane {

    public MouseInputListener mouseListener;
    public KeyboardInputListener keyboardListener;
    private final Canvas canvas;
    private final DisconnectionPane disconnectionPane;
    private final ImageRender imageRender;
    private int desktopWidth = 1;
    private int desktopHeight = 1;

    private final LogWriter vlog = new LogWriter("DesktopCanvasFx");

    private boolean loggedFirstDraw = false;
    private boolean desktopSizeReady = false;
    private final AtomicBoolean redrawPending = new AtomicBoolean();

    private int overlayX;
    private int overlayY;
    private int overlayWidth;
    private int overlayHeight;
    private boolean overlayVisible;

    public final BooleanProperty connected = new SimpleBooleanProperty(false);


    public VNCPane(int width, int height) {
        this.imageRender = new ImageRender(width, height);
        this.canvas = new Canvas(width, height);
        this.disconnectionPane = new DisconnectionPane();
        this.disconnectionPane.resize(width, height);

        setPrefSize(width, height);
        getChildren().addAll(canvas, disconnectionPane);

        // Bind disconnection pane visibility to connected property (inverted)
        disconnectionPane.visibleProperty().bind(connected.not());

        canvas.setFocusTraversable(true);
        // Hide the local OS pointer over the canvas so only the remote
        // cursor rendered by the server is visible.
        canvas.setCursor(Cursor.NONE);
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnScroll(this::handleScroll);
        canvas.setOnKeyPressed(this::handleKeyPressed);
        canvas.setOnKeyReleased(this::handleKeyReleased);
        canvas.setOnKeyTyped(this::handleKeyTyped);
        canvas.setOnMouseEntered(e -> requestFocus());

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && keyboardListener != null) {
                keyboardListener.releaseAllKeys();
            }
        });

        // Visibility is now handled by the binding to disconnectionPane.visibleProperty()
        connected.addListener((o, ov, nv) -> {
            // When disconnected, request a redraw to ensure clean state
            if (!nv) {
                redraw();
            }
        });

        redraw();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        resizeDesktop((int)width, (int)height);
    }

    public void resizeDesktop(int width, int height) {
        this.desktopWidth = width;
        this.desktopHeight = height;
        canvas.setWidth(width);
        canvas.setHeight(height);
        disconnectionPane.resize(width, height);
        setPrefSize(width, height);
        // Reset diagnostics so we log the new size on next redraw.
        loggedFirstDraw = false;
        desktopSizeReady = true;
        redraw();
    }

    public void updateFramebuffer(int x, int y, int w, int h, int[] pixels) {
        if (!connected.get()) {
            return;
        }
        imageRender.updatePixels(x, y, w, h, pixels);
    }

    public void redraw() {
        if (!Platform.isFxApplicationThread()) {
            requestRedrawOnFxThread();
            return;
        }

        redrawPending.set(false);
        drawFramebuffer();
    }

    private void requestRedrawOnFxThread() {
        if (!redrawPending.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(this::redraw);
    }

    private void drawFramebuffer() {
        if (!desktopSizeReady) {
            return;
        }

        final int w = desktopWidth, h = desktopHeight;
        if (w <= 0 || h <= 0) {
            return;
        }

        final GraphicsContext gc = canvas.getGraphicsContext2D();

        final int[] fb = imageRender.getFramebuffer();
        if (fb == null || fb.length < w * h) {
            return;
        }

        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            int sample = fb.length > 0 ? fb[0] : 0;
            int center = fb.length > 0 ? fb[fb.length / 2] : 0;
            vlog.debug("redraw " + w + "x" + h + " sample=0x" + Integer.toHexString(sample)
                + " center=0x" + Integer.toHexString(center));
        }

        final PixelWriter pw = gc.getPixelWriter();
        pw.setPixels(
            0, 0, w, h,
            PixelFormat.getIntArgbInstance(),
            fb, 0, w
        );

        if (overlayVisible) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.strokeRect(overlayX, overlayY, overlayWidth, overlayHeight);
        }
    }

    public void setSelectionOverlay(int x, int y, int width, int height) {
        this.overlayX = x;
        this.overlayY = y;
        this.overlayWidth = Math.max(0, width);
        this.overlayHeight = Math.max(0, height);
        this.overlayVisible = overlayWidth > 0 && overlayHeight > 0;
        drawFramebuffer();
    }

    public void clearSelectionOverlay() {
        this.overlayVisible = false;
        drawFramebuffer();
    }

    public ImageRender getImageRender() {
        return imageRender;
    }

    public int getDesktopWidth() {
        return desktopWidth;
    }

    public int getDesktopHeight() {
        return desktopHeight;
    }

    public void setRemoteCursor(int width, int height, com.tigervnc.rfb.Point hotspot,
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
        ImageCursor cursor = new ImageCursor(img, hx, hy);
        canvas.setCursor(cursor);
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
