package ste.vnc.viewer;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.ImageCursor;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.Cursor;
import javafx.application.Platform;

import com.tigervnc.rfb.LogWriter;

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
public class VNCCanvas extends Canvas {

    public MouseInputListener mouseListener;
    public KeyboardInputListener keyboardListener;
    private final ImageRender imageRender;
    private int desktopWidth = 1;
    private int desktopHeight = 1;

    private static final LogWriter vlog = new LogWriter("DesktopCanvasFx");
    private boolean loggedFirstDraw = false;
    private boolean desktopSizeReady = false;
    private final AtomicBoolean redrawPending = new AtomicBoolean();

    public VNCCanvas(int width, int height) {
        this.imageRender = new ImageRender(width, height);
        setWidth(width);
        setHeight(height);
        setFocusTraversable(true);
        // Hide the local OS pointer over the canvas so only the remote
        // cursor rendered by the server is visible.
        setCursor(Cursor.NONE);
        setOnMousePressed(this::handleMousePressed);
        setOnMouseReleased(this::handleMouseReleased);
        setOnMouseMoved(this::handleMouseMoved);
        setOnMouseDragged(this::handleMouseDragged);
        setOnScroll(this::handleScroll);
        setOnKeyPressed(this::handleKeyPressed);
        setOnKeyReleased(this::handleKeyReleased);
        setOnKeyTyped(this::handleKeyTyped);
        setOnMouseEntered(e -> requestFocus());

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && keyboardListener != null) {
                keyboardListener.releaseAllKeys();
            }
        });

        redraw();
    }

    public void resizeDesktop(int width, int height) {
        this.desktopWidth = width;
        this.desktopHeight = height;
        // Framebuffer size is managed by ImageRenderFx.resize() on the RFB thread;
        // here we only adjust the visible canvas.
        setWidth(width);
        setHeight(height);
        // Reset diagnostics so we log the new size on next redraw.
        loggedFirstDraw = false;
        desktopSizeReady = true;
        redraw();
    }

    public void updateFramebuffer(int x, int y, int w, int h, int[] pixels) {
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

        final int[] fb = imageRender.getFramebuffer();
        if (fb == null || fb.length < w * h) {
            return;
        }

        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            int sample = fb.length > 0 ? fb[0] : 0;
            int center = fb.length > 0 ? fb[fb.length / 2] : 0;
            vlog.info("redraw " + w + "x" + h + " sample=0x" + Integer.toHexString(sample)
                + " center=0x" + Integer.toHexString(center));
        }

        final GraphicsContext gc = getGraphicsContext2D();
        final PixelWriter pw = gc.getPixelWriter();
        pw.setPixels(
            0, 0, w, h,
            PixelFormat.getIntArgbInstance(),
            fb, 0, w
        );
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

    // Renders the server-provided cursor shape as a JavaFX custom cursor.
    public void setRemoteCursor(int width, int height, com.tigervnc.rfb.Point hotspot,
        int[] data, byte[] mask) {
        if (width <= 0 || height <= 0 || data == null || mask == null) {
            return;
        }

        // Build ARGB image from cursor pixels and mask.
        // The cursor data from readPixels is already in ARGB format (0xAARRGGBB).
        javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(width, height);
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
        setCursor(cursor);
    }

    public void handleMousePressed(javafx.scene.input.MouseEvent e) {
        requestFocus();
        if (mouseListener != null) {
            mouseListener.onMousePressed(e);
        }
    }

    public void handleMouseReleased(javafx.scene.input.MouseEvent e) {
        if (mouseListener != null) {
            mouseListener.onMouseReleased(e);
        }
    }

    public void handleMouseMoved(javafx.scene.input.MouseEvent e) {
        if (mouseListener != null) {
            mouseListener.onMouseMoved(e);
        }
    }

    public void handleMouseDragged(javafx.scene.input.MouseEvent e) {
        if (mouseListener != null) {
            mouseListener.onMouseMoved(e);
        }
    }

    public void handleScroll(javafx.scene.input.ScrollEvent e) {
        if (mouseListener != null) {
            mouseListener.onMouseScroll(e);
        }
    }

    public void handleKeyPressed(javafx.scene.input.KeyEvent e) {
        if (keyboardListener != null) {
            keyboardListener.onKeyPressed(e);
        }
    }

    public void handleKeyReleased(javafx.scene.input.KeyEvent e) {
        if (keyboardListener != null) {
            keyboardListener.onKeyReleased(e);
        }
    }

    public void handleKeyTyped(javafx.scene.input.KeyEvent e) {
        if (keyboardListener != null) {
            keyboardListener.onKeyTyped(e);
        }
    }
}
