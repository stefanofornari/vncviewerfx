package ste.vnc.viewer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.ImageCursor;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;

import com.tigervnc.rfb.LogWriter;

class DesktopCanvas extends Canvas {

    private final ImageRenderFx imageRender;
    private final EventBridge eventBridge;
    private FxCConn connection;
    private MouseInputListener mouseListener;
    private FxKeyboardInputListener keyboardListener;
    private int desktopWidth = 800;
    private int desktopHeight = 600;

    private static final LogWriter vlog = new LogWriter("DesktopCanvasFx");
    private boolean loggedFirstDraw = false;

    public DesktopCanvas(int width, int height) {
        this.desktopWidth = width;
        this.desktopHeight = height;
        this.imageRender = new ImageRenderFx(width, height);
        this.eventBridge = new EventBridge();
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
        redraw();
    }

    public void setConnection(FxCConn conn) {
        this.connection = conn;
        this.mouseListener = new MouseInputListener(connection, eventBridge);
        this.keyboardListener = new FxKeyboardInputListener(connection, eventBridge);
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
        redraw();
    }

    public void updateFramebuffer(int x, int y, int w, int h, int[] pixels) {
        imageRender.updatePixels(x, y, w, h, pixels);
    }

    public void redraw() {
        final GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, getWidth(), getHeight());

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
            vlog.info("First redraw " + w + "x" + h + " sample pixel=0x" + Integer.toHexString(sample));
        }

        final PixelWriter pw = gc.getPixelWriter();
        pw.setPixels(
            0, 0, w, h,
            PixelFormat.getIntArgbInstance(),
            fb, 0, w
        );
    }

    public void setScale(double scaleX, double scaleY) {
        eventBridge.setScale(scaleX, scaleY);
    }

    public ImageRenderFx getImageRender() {
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

    void handleMousePressed(javafx.scene.input.MouseEvent e) {
        requestFocus();
        mouseListener.onMousePressed(e);
    }

    void handleMouseReleased(javafx.scene.input.MouseEvent e) {
        mouseListener.onMouseReleased(e);
    }

    void handleMouseMoved(javafx.scene.input.MouseEvent e) {
        mouseListener.onMouseMoved(e);
    }

    void handleMouseDragged(javafx.scene.input.MouseEvent e) {
        mouseListener.onMouseMoved(e);
    }

    void handleScroll(javafx.scene.input.ScrollEvent e) {
        if (connection != null) {
            connection.writeWheelEvent(e);
        }
    }

    void handleKeyPressed(javafx.scene.input.KeyEvent e) {
        keyboardListener.onKeyPressed(e);
    }

    void handleKeyReleased(javafx.scene.input.KeyEvent e) {
        keyboardListener.onKeyReleased(e);
    }

    void handleKeyTyped(javafx.scene.input.KeyEvent e) {
        keyboardListener.onKeyTyped(e);
    }
}
