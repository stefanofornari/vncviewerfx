/* Copyright (C) 2026 VNC Viewer Contributors
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */
package ste.vnc.viewer;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ScrollEvent;
import ste.vnc.rfb.Encodings;
import ste.vnc.rfb.EncodingType;
import ste.vnc.rfb.FramebufferUpdateMessage;
import ste.vnc.rfb.FramebufferUpdateRectangle;
import ste.vnc.rfb.PixelFormat;
import ste.vnc.rfb.RFBStream;
import ste.vnc.rfb.Rectangle;
import ste.vnc.rfb.Screen;
import ste.vnc.rfb.ScreenSet;
import ste.vnc.rfb.ServerCutTextMessage;
import ste.vnc.rfb.ServerInit;
import ste.vnc.rfb.VNCClient;
import ste.vnc.rfb.FenceType;
import static ste.lloop.Loop.on;
import ste.vnc.rfb.ServerMessage;


public class VNCService implements AutoCloseable {
    static final PixelFormat NATIVE_PF = computeNativePF();
    static final PixelFormat verylowColourPF = new PixelFormat(8, 3, false, true, 1, 1, 1, 2, 1, 0);
    static final PixelFormat lowColourPF = new PixelFormat(8, 6, false, true, 3, 3, 3, 4, 2, 0);
    static final PixelFormat mediumColourPF = new PixelFormat(8, 8, false, false, 7, 7, 3, 0, 3, 6);

    private final Logger logger = Logger.getLogger(getClass().getName());

    public final StringProperty clipboard = new SimpleStringProperty();
    public final BooleanProperty connected = new SimpleBooleanProperty(false);

    protected ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("VNC RFB processing").factory()
    );
    protected Future<?> vncProcessing = null;

    private final EventBridge eventBridge = new EventBridge();
    private final Dimension desktopSize = new Dimension();

    private Socket socket;
    private VNCClient client;
    private RFBStream rfb;
    private boolean pendingPFChange;
    private PixelFormat pendingPF;
    private PixelFormat fullColourPF = new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);
    private PixelFormat serverPF;
    private long lastDesktopSizeHash = 0;

    private Rectangle dirtyRect = null;
    private final boolean fullColour = true;
    private int lowColourLevel;
    private boolean formatChange;
    private boolean encodingChange;
    private boolean pendingUpdate;
    private boolean continuousUpdates = false;
    private boolean incremental = false;
    private boolean supportsSyncFence;
    private int lastRequestedDesktopWidth = -1;
    private int lastRequestedDesktopHeight = -1;
    private int updateCount = 0;
    private int currentEncoding = Encodings.encodingHextile;

    private int[] imageBuffer;
    private PixelBuffer pixelBuffer;
    private int imageWidth;
    private int imageHeight;

    public final ObjectProperty<WritableImage> image = new SimpleObjectProperty<>();

    public VNCService() {
        formatChange = true;
        encodingChange = true;
    }

    private static PixelFormat computeNativePF() {
        int depth = 24;
        int bpp = 32;
        boolean bigEndian = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
        boolean trueColour = true;
        int redMax = 0xff;
        int greenMax = 0xff;
        int blueMax = 0xff;
        int redShift = 16;
        int greenShift = 8;
        int blueShift = 0;

        return new PixelFormat(
            bpp, depth, bigEndian, trueColour,
            redMax, greenMax, blueMax,
            redShift, greenShift, blueShift
        );
    }

    // TODO: is it still used?
    public void connectionLost() {
        logger.info("Connection to VNC server lost");
        runOnFxThread(() -> connected.set(false));
    }

    public void pointerEvent(Point2D position, int buttonMask) {
        if (!connected.get() || rfb == null) {
            return;
        }
        try {
            rfb.sendPointerEvent(buttonMask, (int) position.getX(), (int) position.getY());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to send pointer event", e);
        }
    }

    public void keyEvent(int keysym, boolean keyDown) {
        if (!connected.get() || rfb == null) {
            return;
        }
        try {
            rfb.sendKeyEvent(keyDown, keysym);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to send key event", e);
        }
    }

    public void serverInit(ServerInit serverInit) {
        this.serverPF = serverInit.getPixelFormat();
        fullColourPF = NATIVE_PF;

        logger.info("serverInit: serverPF=" + serverPF + " fullColourPF=" + fullColourPF);

        resizeImage(serverInit.getFramebufferWidth(), serverInit.getFramebufferHeight());

        formatChange = true;
        encodingChange = true;
        logger.finest("serverInit done, formatChange=" + formatChange + " encodingChange=" + encodingChange);

        if (continuousUpdates) {
            try {
                rfb.requestFramebufferUpdate(true,
                    new Rectangle(0, 0, serverInit.getFramebufferWidth(), serverInit.getFramebufferHeight()));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to enable continuous updates", e);
            }
        }

        if (pendingPFChange) {
            try {
                rfb.setPixelFormat(pendingPF);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to set pending pixel format", e);
            }
            pendingPFChange = false;
        }

        requestNewUpdate();
    }

    public void setDesktopSize(int w, int h) {
        if ((desktopSize.width != w) || (desktopSize.height != h)) {
            logger.info("Setting desktop size to %dx%d".formatted(w, h));
            desktopSize.width = w; desktopSize.height = h;
            resizeImage(w, h);
        }
    }

    public void setExtendedDesktopSize(int reason, int result, int w, int h, ScreenSet layout) {
        final long desktopSizeHash = desktopSizeHash(reason, result, w, h, layout);
        if (desktopSizeHash != lastDesktopSizeHash) {
            logger.info("Setting desktop size (extended) to %d, %d, %dx%d, %s".formatted(reason, result, w, h, toString(layout)));
            resizeImage(w, h);
            lastDesktopSizeHash = desktopSizeHash;
        }
    }

    public void framebufferUpdateStart() {
        updateCount++;
        logger.finest("framebufferUpdateStart #" + updateCount);
        pendingUpdate = false;
        requestNewUpdate();
    }

    public void framebufferUpdateEnd() {
        logger.finest("framebufferUpdateEnd #" + updateCount + ", dirty: " + toString(dirtyRect));

        if (dirtyRect != null) {
            final int x = dirtyRect.x();
            final int y = dirtyRect.y();
            final int w = dirtyRect.width();
            final int h = dirtyRect.height();

            dirtyRect = null;

            Platform.runLater(() -> {
                if (pixelBuffer != null) {
                    pixelBuffer.updateBuffer(pb -> new Rectangle2D(x, y, w, h));
                }
            });
        }

        if (pendingPFChange) {
            try {
                rfb.setPixelFormat(pendingPF);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to set pending pixel format", e);
            }
            pendingPFChange = false;
        }
    }

    public void setColourMapEntries(int firstColour, int nColours, int[] rgbs) {
    }

    public void bell() {
        Toolkit.getDefaultToolkit().beep();
    }

    public void serverCutText(String str, int len) {
        logger.finest("Received ServerCutText of length " + len);
        Platform.runLater(() -> clipboard.set(str));
    }

    public void beginRect(Rectangle r, int encoding) {
        logger.finest("beginRect #" + updateCount + ": " + toString(r) + " encoding=" + encodingName(encoding));
    }

    public void endRect(final Rectangle r, final int encoding) {
        logger.finest("endRect #" + updateCount + ": " + toString(r));
    }

    public void fillRect(final Rectangle r, final int p) {
        logger.finest("fillRect #%d: %s with %h".formatted(updateCount, toString(r), p));
        if (imageBuffer == null) {
            return;
        }

        if (r.x() < 0 || r.y() < 0 || r.x() + r.width() > imageWidth || r.y() + r.height() > imageHeight) {
            logger.info("fillRect out of bounds: x=" + r.x() + " y=" + r.y() + " w=" + r.width() + " h=" + r.height() + " (image=" + imageWidth + "x" + imageHeight + ")");
            return;
        }

        final int pixel = toJavaFxPixel(p);
        final int startY = r.y(), endY = r.y() + r.height();
        final int w = r.width();
        for (int y = startY; y < endY; ++y) {
            final int offset = y * imageWidth + r.x();
            Arrays.fill(imageBuffer, offset, offset + w, pixel);
        }

        markDirty(r);
    }

    public void imageRect(final Rectangle r, final Object p) {
        final Rectangle2D R = new Rectangle2D(r.x(), r.y(), r.width(), r.height());

        int[] src = (p instanceof int[]) ? (int[]) p : null;
        logger.finest("imageRect #" + updateCount + ": " + R + " len=" + (src != null ? String.valueOf(src.length) : "null"));

        if (src == null || imageBuffer == null) {
            return;
        }

        if (r.x() < 0 || r.y() < 0 || r.x() + r.width() > imageWidth || r.y() + r.height() > imageHeight) {
            logger.info("imageRect out of bounds: " + R + " (image=" + imageWidth + "x" + imageHeight + ")");
            return;
        }

        if (src.length < R.getWidth() * R.getHeight()) {
            logger.info("imageRect src too small: " + src.length + " < " + R.getWidth() * R.getHeight());
            return;
        }

        final int rectWidth  = r.width();
        final int rectHeight = r.height();

        for (int y = 0; y < rectHeight; y++) {
            int dstRowOffset = (r.y() + y) * imageWidth + r.x();
            int srcRowOffset = y * rectWidth;

            for (int x = 0; x < rectWidth; x++) {
                imageBuffer[dstRowOffset + x] = toJavaFxPixel(src[srcRowOffset + x]);
            }
        }

        markDirty(r);
    }

    public void copyRect(final Rectangle r, final int sx, final int sy) {
        logger.finest("copyRect #" + updateCount + ": " + toString(r) + " from (" + sx + "," + sy + ")");

        if (imageBuffer == null) {
            return;
        }

        if (r.x() < 0 || r.y() < 0 || r.x() + r.width() > imageWidth || r.y() + r.height() > imageHeight) {
            logger.info("copyRect out of bounds: x=" + r.x() + " y=" + r.y() + " w=" + r.width() + " h=" + r.height() + " (image=" + imageWidth + "x" + imageHeight + ")");
            return;
        }
        if (sx < 0 || sy < 0 || sx + r.width() > imageWidth || sy + r.height() > imageHeight) {
            logger.info("copyRect src out of bounds: sx=" + sx + " sy=" + sy + " w=" + r.width() + " h=" + r.height());
            return;
        }

        final int stride = imageWidth;
        final int width = r.width();
        final int height = r.height();

        if (r.y() > sy) {
            for (int y = height - 1; y >= 0; y--) {
                int srcOffset = (sy + y) * stride + sx;
                int dstOffset = (r.y() + y) * stride + r.x();
                System.arraycopy(imageBuffer, srcOffset, imageBuffer, dstOffset, width);
            }
        } else {
            for (int y = 0; y < height; y++) {
                int srcOffset = (sy + y) * stride + sx;
                int dstOffset = (r.y() + y) * stride + r.x();
                System.arraycopy(imageBuffer, srcOffset, imageBuffer, dstOffset, width);
            }
        }

        markDirty(r);
    }

    public void fence(int flags, int len, byte[] data) {
        logger.finest(() -> "fence with flags: %d, len: %d".formatted(flags, len));

        if (len == 0) {
            if ((flags & FenceType.SYNC_NEXT.code) != 0) {
                supportsSyncFence = true;
                continuousUpdates = false;
            }
        }
    }

    @Override
    public void close() {
        logger.info(() -> "closing VNC service and releasing resources");
        runOnFxThread(() -> connected.set(false));

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "error closing VNC client", e);
            }
            client = null;
        }

        if (socket != null) {
            try {
                Socket s = socket;
                socket = null;
                s.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "error closing VNC socket", e);
            }
        }

        if (vncProcessing != null && !vncProcessing.isCancelled()) {
            vncProcessing.cancel(true);
        }
    }

    public void writeWheelEvent(ScrollEvent ev) {
        if (!connected.get()) {
            return;
        }

        double dy = ev.getDeltaY();
        logger.finest(() -> "Scroll event: deltaY=%".formatted(dy));
        if (dy == 0.0) {
            return;
        }

        int clicks = (int) Math.signum(dy);
        int buttonMask = clicks < 0 ? RFBStream.POINTER_WHEEL_UP : RFBStream.POINTER_WHEEL_DOWN;

        Point2D p = eventBridge.screenToVnc(ev.getX(), ev.getY());
        pointerEvent(p, buttonMask);
        pointerEvent(p, 0);
    }

    public void requestDesktopSize(final int width, final int height) {
        logger.finest(() -> "requesting new desktop size %dx%d".formatted(width, height));

        if (width <= 0 || height <= 0 || rfb == null) {
            return;
        }

        if (lastRequestedDesktopWidth == width && lastRequestedDesktopHeight == height) {
            return;
        }

        lastRequestedDesktopWidth = width;
        lastRequestedDesktopHeight = height;

        ScreenSet layout = new ScreenSet();
        layout.addScreen(new Screen(0, new Rectangle(0, 0, width, height), 0));

        try {
            rfb.setDesktopSize(width, height, layout);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to request desktop size", e);
        }
        incremental = false;
        requestNewUpdate();
    }

    protected Socket createSocket(String host, int port) throws Exception {
        return new Socket(host, port);
    }

    protected VNCClient createVNCClient(Socket sock, boolean shared) throws IOException {
        final RFBStream rfb = new RFBStream(sock.getInputStream(), sock.getOutputStream());
        rfb.handshake(shared);
        return new VNCClient(sock, rfb);
    }

    public void connect(final String host, final int port) {
        vncProcessing = executor.submit(() -> {
            try {
                incremental = false;
                logger.info(() -> "connecting to %s:%d".formatted(host, port));
                socket = createSocket(host, port);
                client = createVNCClient(socket, true);
                this.rfb = client != null ? new RFBStream(client.getInputStream(), client.getOutputStream()) : null;

                if (client != null) {
                    ServerInit serverInit = client.getServerInit();
                    serverInit(serverInit);
                }

                connected.set(true);
                logger.info(() -> "connected");

                while (!Thread.currentThread().isInterrupted()) {
                    processMsg();
                }
            } catch (Exception e) {
                logger.info("RFB loop terminated: " + e.getMessage());
                connectionLost();
            } finally {
                close();
            }
        });
    }

    public void disconnect() {
        close();
    }

    private void processMsg() throws IOException {
        ServerMessage msg = client.readMessage();
        if (msg instanceof FramebufferUpdateMessage fum) {
            processFramebufferUpdate(fum);
        } else if (msg instanceof ste.vnc.rfb.BellMessage) {
            bell();
        } else if (msg instanceof ServerCutTextMessage scm) {
            serverCutText(scm.getText(), scm.getText().length());
        } else if (msg instanceof ste.vnc.rfb.SetColourMapEntriesMessage) {
            setColourMapEntries(0, 0, new int[0]);
        }
    }

    private void processFramebufferUpdate(FramebufferUpdateMessage fum) {
        framebufferUpdateStart();
        for (FramebufferUpdateRectangle updateRect : fum.getRectangles()) {
            final Rectangle r = updateRect.getRectangle();
            beginRect(r, updateRect.getEncodingType());
            if (updateRect.isCopyRect()) {
                copyRect(r, updateRect.getSourceX(), updateRect.getSourceY());
            } else if (updateRect.getPixels() != null) {
                imageRect(r, updateRect.getPixels());
            }
            endRect(r, updateRect.getEncodingType());
        }
        framebufferUpdateEnd();
    }

    private void resizeImage(final int w, final int h) {
        if (w > 0 && h > 0) {
            imageBuffer = new int[w * h];
            imageWidth = w;
            imageHeight = h;
            pixelBuffer = new PixelBuffer(
                w, h,
                IntBuffer.wrap(imageBuffer),
                javafx.scene.image.PixelFormat.getIntArgbPreInstance()
            );
            image.set(new WritableImage(pixelBuffer));
        }
    }

    private void requestNewUpdate() {
        if (rfb == null) {
            return;
        }

        if (formatChange) {
            PixelFormat pf;

            if (fullColour) {
                pf = fullColourPF;
            } else {
                pf = switch (lowColourLevel) {
                    case 0 -> verylowColourPF;
                    case 1 -> lowColourPF;
                    default -> mediumColourPF;
                };
            }

            if (supportsSyncFence) {
                pendingPFChange = true;
                pendingPF = pf;
            }

            String str = pf.toString();
            logger.info("Using pixel format " + str);
            try {
                rfb.setPixelFormat(pf);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to set pixel format", e);
            }

            formatChange = false;
        }

        if (encodingChange) {
            logger.info("Requesting encoding");
            requestEncodings();
            encodingChange = false;
        }

        if (!incremental || !continuousUpdates) {
            logger.finest("Sending update request (incremental=" + incremental + ")");
            pendingUpdate = true;

            try {
                rfb.requestFramebufferUpdate(incremental,
                    new Rectangle(0, 0, imageWidth, imageHeight));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to request framebuffer update", e);
            }
        }

        if (!incremental) {
            incremental = true;
        }
    }

    private void requestEncodings() {
        logger.info("Requesting " + encodingName(currentEncoding) + " encoding");
        try {
            rfb.setEncodings(EncodingType.fromCode(currentEncoding));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to set encodings", e);
        }
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void writeClientCutText(String str, int len) {
        if (!connected.get() || rfb == null) {
            return;
        }
        try {
            rfb.sendClipboardText(str);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to send clipboard text", e);
        }
    }

    private String encodingName(int encoding) {
        return Encodings.encodingName(encoding);
    }

    private long desktopSizeHash(int reason, int result, int w, int h, ScreenSet layout) {
        return "%d%d%d%d%s".formatted(reason, result, w, h, toString(layout)).hashCode();
    }

    private String toString(final ScreenSet ss) {
        if (ss == null) {
            return null;
        }
        final StringBuffer sb = new StringBuffer();
        on(ss.screens()).loop((screen) -> sb.append(toString(screen)).append(" "));
        return sb.toString();
    }

    private String toString(final Screen s) {
        return "(%d/%s/%d)".formatted(s.id, toString(s.dimensions), s.flags);
    }

    private String toString(final Rectangle r) {
        return (r == null) ? "null" : "[%d,%d-%d,%d]".formatted(r.x(), r.y(), r.x() + r.width(), r.y() + r.height());
    }

    private int toJavaFxPixel(int p) {
        if (serverPF == null || !serverPF.isTrueColor() || serverPF.getDepth() <= 8) {
            return p | 0xff000000;
        }

        if (serverPF.isTrueColor() && serverPF.getDepth() >= 24 && serverPF.getRedShift() == 16) {
            return p | 0xff000000;
        }

        int r = (p >>> serverPF.getRedShift()) & serverPF.getRedMax();
        int g = (p >>> serverPF.getGreenShift()) & serverPF.getGreenMax();
        int b = (p >>> serverPF.getBlueShift()) & serverPF.getBlueMax();

        if (serverPF.getRedMax() != 255) {
            r = r * 255 / serverPF.getRedMax();
        }
        if (serverPF.getGreenMax() != 255) {
            g = g * 255 / serverPF.getGreenMax();
        }
        if (serverPF.getBlueMax() != 255) {
            b = b * 255 / serverPF.getBlueMax();
        }

        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private void markDirty(final Rectangle r) {
        if (dirtyRect == null) {
            dirtyRect = new Rectangle(r.x(), r.y(), r.width(), r.height());
            return;
        }

        final int newX = Math.min(dirtyRect.x(), r.x());
        final int newY = Math.min(dirtyRect.y(), r.y());
        final int newRight = Math.max(dirtyRect.x() + dirtyRect.width(), r.x() + r.width());
        final int newBottom = Math.max(dirtyRect.y() + dirtyRect.height(), r.y() + r.height());
        dirtyRect = new Rectangle(newX, newY, newRight - newX, newBottom - newY);
    }
}
