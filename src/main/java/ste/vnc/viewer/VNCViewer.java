package ste.vnc.viewer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import com.tigervnc.rfb.LogWriter;
import java.io.IOException;
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
    private final ImageRender imageRender;
    private final VNCViewerController controller;
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


    public VNCViewer() {
        this.imageRender = new ImageRender(0, 0);

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("VNCViewer.fxml"));
        fxmlLoader.setRoot(this);
        // Do NOT set controller manually here; FXML instantiates it via fx:controller

        // Visibility is now handled by the binding to disconnectionPane.visibleProperty()
        connected.addListener((o, was, is) -> {
            // When disconnected, request a redraw to ensure clean state
            if (is) {
                redraw();
            }
        });

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

    public void resizeDesktop(int width, int height) {
        this.desktopWidth = width;
        this.desktopHeight = height;
        controller.canvas.setWidth(width);
        controller.canvas.setHeight(height);
        controller.disconnectionPane.resize(width, height);
        setPrefSize(width, height);
        // Also resize the image render to ensure it matches the canvas
        if (imageRender.getWidth() != width || imageRender.getHeight() != height) {
            imageRender.resize(width, height);
        }
        // Reset diagnostics so we log the new size on next redraw.
        loggedFirstDraw = false;
        desktopSizeReady = true;
        UpdateLogger.logResize(width, height);
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

        final GraphicsContext gc = controller.canvas.getGraphicsContext2D();
        final PixelWriter pw = gc.getPixelWriter();

        // Draw dirty regions incrementally
        List<com.tigervnc.rfb.Rect> dirtyRegions = imageRender.drainDirty();
        
        // Log the draw operation
        UpdateLogger.logDrawFramebuffer(!dirtyRegions.isEmpty(), dirtyRegions.size(),
            imageRender.getWidth(), imageRender.getHeight(), w, h);
        
        // If there are no dirty regions, it means we need a full redraw
        // (e.g., called from resize, overlay change, or window exposure)
        if (dirtyRegions.isEmpty()) {
            // Full redraw - get entire framebuffer
            final int[] fb = imageRender.getFramebuffer();
            final int fbWidth = imageRender.getWidth();
            final int fbHeight = imageRender.getHeight();
            
            if (fb == null || fb.length < fbWidth * fbHeight) {
                if (overlayVisible) {
                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2);
                    gc.strokeRect(overlayX, overlayY, overlayWidth, overlayHeight);
                }
                return;
            }

            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                int sample = fb.length > 0 ? fb[0] : 0;
                int center = fb.length > 0 ? fb[fb.length / 2] : 0;
                vlog.debug("redraw " + w + "x" + h + " sample=0x" + Integer.toHexString(sample)
                    + " center=0x" + Integer.toHexString(center));
            }

            // Draw the framebuffer at its actual size
            pw.setPixels(0, 0, fbWidth, fbHeight,
                PixelFormat.getIntArgbInstance(), fb, 0, fbWidth);
            
            if (overlayVisible) {
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.strokeRect(overlayX, overlayY, overlayWidth, overlayHeight);
            }
            return;
        }

        // Log first draw with the first dirty region's data
        if (!loggedFirstDraw && !dirtyRegions.isEmpty()) {
            loggedFirstDraw = true;
            com.tigervnc.rfb.Rect first = dirtyRegions.get(0);
            int[] fb = imageRender.getFramebuffer();
            int sample = fb.length > 0 ? fb[0] : 0;
            int center = fb.length > 0 ? fb[fb.length / 2] : 0;
            vlog.debug("redraw " + w + "x" + h + " sample=0x" + Integer.toHexString(sample)
                + " center=0x" + Integer.toHexString(center));
        }

        // Draw each dirty region
        for (com.tigervnc.rfb.Rect r : dirtyRegions) {
            int rx = r.tl.x;
            int ry = r.tl.y;
            int rw = r.width();
            int rh = r.height();
            
            // Bounds check against the canvas size
            if (rx < 0 || ry < 0 || rx + rw > w || ry + rh > h) {
                vlog.info("Skipping dirty region out of canvas bounds: x=" + rx + " y=" + ry + " w=" + rw + " h=" + rh + " (canvas=" + w + "x" + h + ")");
                continue;
            }
            
            // Also check against the framebuffer size
            int fbWidth = imageRender.getWidth();
            int fbHeight = imageRender.getHeight();
            if (rx < 0 || ry < 0 || rx + rw > fbWidth || ry + rh > fbHeight) {
                vlog.info("Skipping dirty region out of framebuffer bounds: x=" + rx + " y=" + ry + " w=" + rw + " h=" + rh + " (framebuffer=" + fbWidth + "x" + fbHeight + ")");
                continue;
            }
            
            int[] region = imageRender.copyRegion(rx, ry, rw, rh);
            if (region != null && region.length >= rw * rh) {
                pw.setPixels(rx, ry, rw, rh,
                    PixelFormat.getIntArgbInstance(), region, 0, rw);
            }
        }

        if (overlayVisible) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.strokeRect(overlayX, overlayY, overlayWidth, overlayHeight);
        }
    }

    public void selectionOverlay(int x, int y, int width, int height) {
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

    public ImageRender imageRender() {
        return imageRender;
    }

    public int desktopWidth() {
        return desktopWidth;
    }

    public int desktopHeight() {
        return desktopHeight;
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
