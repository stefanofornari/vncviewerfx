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

import java.awt.Toolkit;

import com.tigervnc.network.Socket;
import com.tigervnc.network.TcpSocket;
import com.tigervnc.rdr.FdInStreamBlockCallback;
import com.tigervnc.rdr.MemInStream;
import com.tigervnc.rdr.MemOutStream;
import com.tigervnc.rfb.CConnection;
import com.tigervnc.rfb.Encodings;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Point;
import com.tigervnc.rfb.Rect;
import com.tigervnc.rfb.Screen;
import com.tigervnc.rfb.ScreenSet;
import com.tigervnc.rfb.fenceTypes;
import java.awt.Dimension;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ScrollEvent;
import static ste.lloop.Loop.on;


public class VNCService extends CConnection implements FdInStreamBlockCallback, AutoCloseable {
    // Use the same native pixel format as the Swing viewer so the server
    // treats us the same way. ImageRender will convert from that format to
    // JavaFX INT_ARGB. The native format is computed from the AWT toolkit's
    // ColorModel, matching PlatformPixelBuffer.getNativePF().
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
    protected Future vncProcessing = null;

    private final EventBridge eventBridge = new EventBridge();
    private final Dimension desktopSize = new Dimension();

    private Socket sock;
    private boolean pendingPFChange;
    private PixelFormat pendingPF;
    private PixelFormat fullColourPF = new PixelFormat();
    private PixelFormat serverPF;
    private long lastDesktopSizeHash = 0;

    /*
     * Rectangle of dirty content within a buffer update
     */
    private Rect dirtyRect = null;

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
    private int currentEncoding = Encodings.encodingRaw;

    private int[] imageBuffer;
    private PixelBuffer pixelBuffer;

    public final ObjectProperty<WritableImage> image = new SimpleObjectProperty();

    public VNCService() {
        //
        // Hard-wire protocol options that we comfortable they work
        // - Server-rendered cursor in the framebuffer
        // - ZRLE encoding with lossless (no-JPEG) compression
        // - Compression level 0
        //
        setShared(true);
        cp.supportsLocalCursor = false;
        cp.supportsDesktopResize = true;
        cp.supportsExtendedDesktopSize = true;
        cp.supportsClientRedirect = true;
        cp.supportsDesktopRename = true;
        cp.customCompressLevel = true;
        cp.compressLevel = 0;
        cp.noJpeg = true;

        //
        // Prefer ZRLE and advertise all supported fallbacks (Tight, Hextile, Raw,
        // CopyRect). This avoids the standalone RawDecoder, which does not
        // handle compact CPIXEL.
        //
        currentEncoding = Encodings.encodingZRLE;
    }

    @Override
    public PixelFormat getPreferredPF() {
        return NATIVE_PF;
    }

    /**
     * Compute the native pixel format from the AWT toolkit's ColorModel,
     * matching the logic in TigerVNC's PlatformPixelBuffer.getNativePF().
     */
    private static PixelFormat computeNativePF() {
        // JavaFX Prism pipeline normalizes rendering to standard 32-bit TrueColor (24-bit color depth)
        int depth = 24;
        int bpp = 32;
        boolean bigEndian = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
        boolean trueColour = true;

        // 8 bits per channel max values (0xff)
        int redMax = 0xff;
        int greenMax = 0xff;
        int blueMax = 0xff;

        // Standard 32-bit RGB bit shifts matching JavaFX ARGB pixel format
        int redShift = 16;
        int greenShift = 8;
        int blueShift = 0;

        return new PixelFormat(
            bpp, depth, bigEndian, trueColour,
            redMax, greenMax, blueMax,
            redShift, greenShift, blueShift
        );
    }

    public void connectionLost() {
        logger.info("Connection to VNC server lost");
        runOnFxThread(() -> connected.set(false));
    }

    public void pointerEvent(Point position, int buttonMask) {
        if (state() != RFBSTATE_NORMAL || !connected.get()) {
            return;
        }
        writer().writePointerEvent(position, buttonMask);
    }

    public void keyEvent(int keysym, boolean keyDown) {
        if (state() != RFBSTATE_NORMAL || !connected.get()) {
            return;
        }
        logger.finest("Key event: keysym=0x" + Integer.toHexString(keysym)
            + (keyDown ? " down" : " up"));
        writer().writeKeyEvent(keysym, keyDown);
    }

    @Override
    public void serverInit() {
        super.serverInit();

        serverPF = cp.pf();
        fullColourPF = NATIVE_PF;

        logger.info("serverInit: serverPF=" + serverPF.print() + " fullColourPF=" + fullColourPF.print());

        resizeImage(cp.width, cp.height);

        formatChange = true;
        encodingChange = true;
        logger.finest("serverInit done, formatChange=" + formatChange + " encodingChange=" + encodingChange);

        if (continuousUpdates) {
            writer().writeEnableContinuousUpdates(true, 0, 0, cp.width, cp.height);
        }

        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }

        requestNewUpdate();
    }

    @Override
    public void setDesktopSize(int w, int h) {
        if ((desktopSize.width != w) || (desktopSize.height != h)) {
            logger.info("Setting desktop size to %dx%d".formatted(w, h));

            super.setDesktopSize(w, h);
            desktopSize.width = w; desktopSize.height = h;
            resizeImage(w, h);
        }
    }

    @Override
    public void setExtendedDesktopSize(int reason, int result, int w, int h, ScreenSet layout) {
        super.setExtendedDesktopSize(reason, result, w, h, layout);

        final long desktopSizeHash = desktopSizeHash(reason, result, w, h, layout);
        if (desktopSizeHash != lastDesktopSizeHash) {
            logger.info("Setting desktop size (extended) to %d, %d, %dx%d, %s".formatted(reason, result, w, h, toString(layout)));


            resizeImage(w, h);

            lastDesktopSizeHash = desktopSizeHash;
        }
    }

    @Override
    public void framebufferUpdateStart() {
        updateCount++;
        logger.finest("framebufferUpdateStart #" + updateCount);
        pendingUpdate = false;
        requestNewUpdate();
    }

    @Override
    public void framebufferUpdateEnd() {
        logger.finest("framebufferUpdateEnd #" + updateCount + ", dirty: " + toString(dirtyRect));

        if (dirtyRect != null) {
            final int x = dirtyRect.tl.x;
            final int y = dirtyRect.tl.y;
            final int w = dirtyRect.width();
            final int h = dirtyRect.height();

            // Reset dirtyRect for the next frame
            dirtyRect = null;

            // Single JavaFX pulse for all accumulated updates
            Platform.runLater(() -> {
                if (pixelBuffer != null) {
                    pixelBuffer.updateBuffer(pb -> new Rectangle2D(x, y, w, h));
                }
            });
        }

        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }
    }

    @Override
    public void setColourMapEntries(int firstColour, int nColours, int[] rgbs) {
        // Palette modes not supported in this minimal viewer.
    }

    @Override
    public void bell() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void serverCutText(String str, int len) {
        logger.finest("Received ServerCutText of length " + len);
        Platform.runLater(() -> clipboard.set(str));
    }

    @Override
    public void beginRect(Rect r, int encoding) {
        logger.finest("beginRect #" + updateCount + ": " + toString(r) + " encoding=" + encodingName(encoding));
        if (sock != null) {
            sock.inStream().startTiming();
        }
    }

    @Override
    public  void endRect(final Rect r, final int encoding) {
        logger.finest("endRect #" + updateCount + ": " + toString(r));
        if (sock != null) {
            sock.inStream().stopTiming();
        }
    }

    @Override
    public void fillRect(final Rect r, final int p) {
        logger.finest("fillRect #%d: %s with %h".formatted(updateCount, toString(r), p));
        if (image.get() == null) {
            return;
        }

        final WritableImage I = image.get();

        // buond check
        if (r.tl.x < 0 || r.tl.y < 0 || r.width() > I.getWidth() || r.height() > I.getHeight()) {
          logger.info("fillRect out of bounds: x=" + r.tl.x + " y=" + r.tl.y + " w=" + r.width() + " h=" + r.height() + " (image=" + I.getWidth() + "x" + I.getHeight() + ")");
          return;
        }

        // update buffers
        final int pixel = toJavaFxPixel(p);
        final int startY = r.tl.y, endY = r.br.y;
        for (int y = startY; y < endY; ++y) {
            final int offset = y * (int)I.getWidth() + r.tl.x;
            Arrays.fill(imageBuffer, offset, offset + r.width(), pixel);
        }

        markDirty(r);
    }

    @Override
    public void imageRect(final Rect r, final Object p) {
        final Rectangle2D R = new Rectangle2D(r.tl.x, r.tl.y, r.width(), r.height());

        int[] src = (p instanceof int[]) ? (int[]) p : null;
        logger.finest("imageRect #" + updateCount + ": " + R + " len=" + (src != null ? String.valueOf(src.length) : "null"));

        if (src == null) {
            return;
        }

        final WritableImage I = image.get();

        // buond check
        if (R.getMinX() < 0 || R.getMinY() < 0 || R.getWidth() > I.getWidth() || R.getHeight() > I.getHeight()) {
          logger.info("fillRect out of bounds: x=" + R + " (image=" + I.getWidth() + "x" + I.getHeight() + ")");
          return;
        }

        if (src.length < R.getWidth() * R.getHeight() ) {
          logger.info("imageRect src too small: " + src.length + " < " + R.getWidth() * R.getHeight());
          return;
        }

        final int rectWidth  = (int)R.getWidth();
        final int rectHeight = (int)R.getHeight();

        for (int y = 0; y < rectHeight; y++) {
            int dstRowOffset = (int)((R.getMinY() + y) * I.getWidth() + R.getMinX());
            int srcRowOffset = y * rectWidth;

            for (int x = 0; x < rectWidth; x++) {
                imageBuffer[dstRowOffset + x] = toJavaFxPixel(src[srcRowOffset + x]);
            }
        }

        markDirty(r);
    }

    @Override
    public void copyRect(final Rect r, final int sx, final int sy) {
        logger.finest("copyRect #" + updateCount + ": " + toString(r) + " from (" + sx + "," + sy + ")");

        final WritableImage i = image.get();
        // Bounds check
        if (r.tl.x < 0 || r.tl.y < 0 || r.width() > i.getWidth() || r.height() > i.getHeight()) {
          logger.info("copyRect out of bounds: x=" + r.tl.x + " y=" + r.tl.y + " w=" + r.width() + " h=" + r.height() + " (image=" + i.getWidth() + "x" + i.getHeight() + ")");
          return;
        }
        if (sx < 0 || sy < 0 || sx + r.width() > i.getWidth() || sy + r.height() > i.getHeight()) {
          logger.info("copyRect src out of bounds: sx=" + sx + " sy=" + sy + " w=" + r.width() + " h=" + r.height());
          return;
        }

        final int stride = (int) i.getWidth();
        final int width = r.width();
        final int height = r.height();

        if (r.tl.y > sy) {
            // Copy bottom-to-top when moving down to avoid overwriting overlapping memory
            for (int y = height - 1; y >= 0; y--) {
                int srcOffset = (sy + y) * stride + sx;
                int dstOffset = (r.tl.y + y) * stride + r.tl.x;
                System.arraycopy(imageBuffer, srcOffset, imageBuffer, dstOffset, width);
            }
        } else {
            // Copy top-to-bottom
            for (int y = 0; y < height; y++) {
                int srcOffset = (sy + y) * stride + sx;
                int dstOffset = (r.tl.y + y) * stride + r.tl.x;
                System.arraycopy(imageBuffer, srcOffset, imageBuffer, dstOffset, width);
            }
        }

        markDirty(r);
    }

    @Override
    public void fence(int flags, int len, byte[] data) {
        logger.finest("fence with flags: " + flags + ", len: " + len);
        cp.supportsFence = true;

        if ((flags & fenceTypes.fenceFlagRequest) != 0) {
            logger.finest("fenceTypes.fenceFlagRequest");
            flags = flags & (fenceTypes.fenceFlagBlockBefore | fenceTypes.fenceFlagBlockAfter);
            writer().writeFence(flags, len, data);
            return;
        }

        if (len == 0) {
            if ((flags & fenceTypes.fenceFlagSyncNext) != 0) {
                supportsSyncFence = true;
                continuousUpdates = false;
            }
        } else {
            MemInStream memStream = new MemInStream(data, 0, len);
            PixelFormat pf = new PixelFormat();

            pf.read(memStream);
            logger.info("fence: new pixel format: " + pf.print());

            cp.setPF(pf);
        }
    }

    @Override
    public void blockCallback() {
        try {
            synchronized (this) {
                wait(1);
            }
        } catch (InterruptedException e) {
            throw new com.tigervnc.rfb.Exception(e.getMessage());
        }
    }

    /**
     * Shuts down the network socket and stops background executor threads.
     * Idempotent and safe to call multiple times.
     */
    @Override
    public void close() {
        logger.info(() -> "closing VNC service and releasing resources");
        runOnFxThread(() -> connected.set(false));

        // 1. Close socket to unblock read/write operations
        if (sock != null) {
            try {
                Socket s = sock;
                sock = null; // Prevents re-entry
                s.shutdown();
            } catch (Exception e) {
                logger.warning("error closing VNC socket: " + e.getMessage());
            }
        }

        // 2. Cancel the running task if any
        if (vncProcessing != null && !vncProcessing.isCancelled()) {
            vncProcessing.cancel(true);
        }
    }

    public void writeWheelEvent(ScrollEvent ev) {
        if (state() != RFBSTATE_NORMAL || !connected.get()) {
            return;
        }

        double dy = ev.getDeltaY();
        logger.finest("Scroll event: deltaY=" + dy);
        if (dy == 0.0) {
            return;
        }

        int clicks = (int) Math.signum(dy);
        int buttonMask = clicks < 0 ? 16 : 8;

        Point p = eventBridge.screenToVnc(ev.getX(), ev.getY());
        writer().writePointerEvent(p, buttonMask);
        writer().writePointerEvent(p, 0);
    }

    /**
     * Requests the server to resize the remote desktop to the given size, if
     * the server supports the SetDesktopSize extension.
     */
    public void requestDesktopSize(final int width, final int height) {
        logger.finest(() ->"requesting new desktop size %dx%d".formatted(width, height));

        if (width <= 0 || height <= 0) {
            return;
        }

        if (!cp.supportsSetDesktopSize) {
            return;
        }

        if (width == lastRequestedDesktopWidth && height == lastRequestedDesktopHeight) {
            return;
        }

        lastRequestedDesktopWidth = width;
        lastRequestedDesktopHeight = height;

        ScreenSet layout = cp.screenLayout;

        if (layout.num_screens() == 0) {
            layout.add_screen(new Screen());
        } else if (layout.num_screens() != 1) {
            while (layout.num_screens() > 1) {
                Iterator<Screen> iter = layout.screens.iterator();
                Screen screen = iter.next();
                if (!iter.hasNext()) {
                    break;
                }
                layout.remove_screen(screen.id);
            }
        }

        Screen screen0 = layout.screens.iterator().next();
        screen0.dimensions.tl.x = 0;
        screen0.dimensions.tl.y = 0;
        screen0.dimensions.br.x = width;
        screen0.dimensions.br.y = height;

        writer().writeSetDesktopSize(width, height, layout);
        incremental = false;
        requestNewUpdate();
    }

    // --------------------------------------------------------------- main loop

    // Start the RFB processing loop on a background thread so that
    // incoming framebuffer updates are handled continuously.
    // Use a virtual thread for blocking socket I/O loops
    public void connect(final String host, final int port) {
        vncProcessing = executor.submit(() -> {
            try {
                incremental = false;
                logger.info(() -> "connecting to %s:%d".formatted(host, port));
                sock = createSocket(host, port);
                sock.inStream().setBlockCallback(this);
                setStreams(sock.inStream(), sock.outStream());
                doInitialiseProtocol();

                runOnFxThread(() -> connected.set(true));
                logger.info(() -> "connected to " + sock.getPeerEndpoint());

                while (!Thread.currentThread().isInterrupted()) {
                    processMsg();
                }
            } catch (Exception e) {
                logger.info("RFB loop terminated: " + e.getMessage());
            } finally {
                close();
            }
        });
    }

    /**
     * Alias for {@link #close()} for lifecycle symmetry with {@link #connect()}.
     */
    public void disconnect() {
        close();
    }

    protected Socket createSocket(String host, int port) throws Exception {
        return new TcpSocket(host, port);
    }

    protected void doInitialiseProtocol() {
        initialiseProtocol(); // Mainly for testing, it calls the final parent method
    }

    // ---------------------------------------------------------------------

    private void resizeImage(final int w, final int h) {
        if (cp.width > 0 && cp.height > 0) {
            imageBuffer = new int[w * h];
            pixelBuffer = new PixelBuffer(
                w, h,
                IntBuffer.wrap(imageBuffer),
                javafx.scene.image.PixelFormat.getIntArgbPreInstance()
            );
            image.set(new WritableImage(pixelBuffer));
        }
    }

    private void requestNewUpdate() {
        if (writer() == null) {
            return;
        }

        if (formatChange) {
            PixelFormat pf;

            assert (!pendingUpdate || supportsSyncFence);

            if (fullColour) {
                pf = fullColourPF;
            } else {
                pf = switch (lowColourLevel) {
                    case 0 ->
                        verylowColourPF;
                    case 1 ->
                        lowColourPF;
                    default ->
                        mediumColourPF;
                };
            }

            if (supportsSyncFence) {
                MemOutStream memStream = new MemOutStream();
                pf.write(memStream);
                writer().writeFence(fenceTypes.fenceFlagRequest | fenceTypes.fenceFlagSyncNext,
                    memStream.length(), (byte[]) memStream.data());
            } else {
                pendingPFChange = true;
                pendingPF = pf;
            }

            String str = pf.print();
            logger.info("Using pixel format " + str);
            writer().writeSetPixelFormat(pf);

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

            // Fix: Pass the incremental variable instead of hardcoded 'false'
            writer().writeFramebufferUpdateRequest(new Rect(0, 0, cp.width, cp.height), incremental);
        }

        // Fix: Only mark incremental = true AFTER sending the initial update request
        if (!incremental) {
            incremental = true;
        }
    }

    private void requestEncodings() {
        // Use the same encoding negotiation as the Swing viewer: advertise
        // the preferred encoding plus all supported fallbacks (CopyRect,
        // ZRLE, Hextile, Tight, etc.). This lets the server choose the
        // encoding it handles best, which in practice is ZRLE.
        logger.info("Requesting " + Encodings.encodingName(currentEncoding)
            + " encoding with fallbacks (Swing-style)");
        writer().writeSetEncodings(currentEncoding, true);
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void writeClientCutText(String str, int len) {
        if (state() != RFBSTATE_NORMAL || !connected.get()) {
            return;
        }
        writer().writeClientCutText(str, len);
    }

    private String encodingName(int encoding) {
        switch (encoding) {
            case Encodings.encodingRaw: return "Raw";
            case Encodings.encodingZRLE: return "ZRLE";
            case Encodings.encodingCopyRect: return "CopyRect";
            default: return String.valueOf(encoding);
        }
    }

    private long desktopSizeHash(int reason, int result, int w, int h, ScreenSet layout) {
        return "%d%d%d%d%s".formatted(reason, result, w, h, toString(layout)).hashCode();
    }

    private String toString(final ScreenSet ss) {
        if (ss == null) {
            return null;
        }
        final StringBuffer sb = new StringBuffer();
        on(ss.screens).loop((screen) -> sb.append(toString(screen)).append(" "));
        return sb.toString();
    }

    private String toString(final Screen s) {
        return "(%d/%s/%d)".formatted(s.id, toString(s.dimensions), s.flags);
    }

    private String toString(final Rect r) {
        return (r == null) ? "null" : "[%s-%s]".formatted(toString(r.tl), toString(r.br));
    }

    private String toString(final Point p) {
        return (p == null) ? "null" : "(%d,%d)".formatted(p.x, p.y);
    }

   /*
   * Converts a pixel from the server-declared pixel format to JavaFX's
   * {@code INT_ARGB}, forcing full opacity. This lets the viewer request the
   * native display format (matching the Swing viewer) and correct for it here.
   */
    private int toJavaFxPixel(int p) {
        if (serverPF == null || !serverPF.trueColour || serverPF.depth <= 8) {
            // Fallback for missing or palette formats: just force opaque.
            return p | 0xff000000;
        }

        if (serverPF.trueColour && serverPF.depth >= 24 && serverPF.redShift == 16) {
            return p | 0xff000000;
        }

        int r = (p >>> serverPF.redShift) & serverPF.redMax;
        int g = (p >>> serverPF.greenShift) & serverPF.greenMax;
        int b = (p >>> serverPF.blueShift) & serverPF.blueMax;

        // Scale component ranges up to 8 bits when the server uses fewer bits.
        if (serverPF.redMax != 255) {
            r = r * 255 / serverPF.redMax;
        }
        if (serverPF.greenMax != 255) {
            g = g * 255 / serverPF.greenMax;
        }
        if (serverPF.blueMax != 255) {
            b = b * 255 / serverPF.blueMax;
        }

        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private void markDirty(final Rect r) {
        if (dirtyRect == null) {
            dirtyRect = new Rect(r.tl, r.br);
            return;
        }

        dirtyRect.tl.x = Math.min(dirtyRect.tl.x, r.tl.x);
        dirtyRect.tl.y = Math.min(dirtyRect.tl.y, r.tl.y);
        dirtyRect.br.x = Math.max(dirtyRect.br.x, r.br.x);
        dirtyRect.br.y = Math.max(dirtyRect.br.y, r.br.y);
    }
}
