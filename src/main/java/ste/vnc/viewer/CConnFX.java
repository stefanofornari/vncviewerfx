package ste.vnc.viewer;

import java.awt.Toolkit;

import com.tigervnc.network.Socket;
import com.tigervnc.network.TcpSocket;
import com.tigervnc.rdr.FdInStreamBlockCallback;
import com.tigervnc.rdr.MemInStream;
import com.tigervnc.rdr.MemOutStream;
import com.tigervnc.rfb.CConnection;
import com.tigervnc.rfb.Encodings;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Point;
import com.tigervnc.rfb.Rect;
import com.tigervnc.rfb.Screen;
import com.tigervnc.rfb.ScreenSet;
import com.tigervnc.rfb.fenceTypes;
import java.awt.Dimension;
import java.util.Iterator;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Dimension2D;
import javafx.scene.input.ScrollEvent;
import static ste.lloop.Loop.on;


// TODO: remove dependency on viewer?
public class CConnFX extends CConnection implements FdInStreamBlockCallback {

    // Use the same native pixel format as the Swing viewer so the server
    // treats us the same way. ImageRender will convert from that format to
    // JavaFX INT_ARGB. The native format is computed from the AWT toolkit's
    // ColorModel, matching PlatformPixelBuffer.getNativePF().
    static final PixelFormat NATIVE_PF = computeNativePF();

    public final ObjectProperty<Dimension2D> canvasSize = new SimpleObjectProperty<>();
    public final StringProperty clipboard = new SimpleStringProperty();
    public final BooleanProperty connected = new SimpleBooleanProperty(false);

    private static final LogWriter vlog = new LogWriter("FxCConn");
    static final PixelFormat verylowColourPF = new PixelFormat(8, 3, false, true, 1, 1, 1, 2, 1, 0);
    static final PixelFormat lowColourPF = new PixelFormat(8, 6, false, true, 3, 3, 3, 4, 2, 0);
    static final PixelFormat mediumColourPF = new PixelFormat(8, 8, false, false, 7, 7, 3, 0, 3, 6);

    private final VNCViewer viewer;
    private final EventBridge eventBridge = new EventBridge();
    private final Dimension desktopSize = new Dimension();
    private Socket sock;
    private boolean shuttingDown;
    private boolean pendingPFChange;
    private PixelFormat pendingPF;
    private PixelFormat fullColourPF = new PixelFormat();
    private PixelFormat serverPF;
    private long lastDesktopSizeHash = 0;

    private final boolean fullColour = true;

    private int lowColourLevel;
    private boolean formatChange;
    private boolean encodingChange;
    private boolean firstUpdate = true;
    private boolean pendingUpdate;
    private boolean continuousUpdates;
    private boolean forceNonincremental = true;
    private boolean supportsSyncFence;
    private int lastRequestedDesktopWidth = -1;
    private int lastRequestedDesktopHeight = -1;
    private int pendingDesktopWidth = -1;
    private int pendingDesktopHeight = -1;
    private int updateCount = 0;
    private int currentEncoding = Encodings.encodingRaw;


    public CConnFX(final VNCViewer viewer) {
        this.viewer = viewer;

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

        // Mimic the Swing viewer: prefer ZRLE and advertise all supported
        // fallbacks (Tight, Hextile, Raw, CopyRect). This avoids the
        // standalone RawDecoder, which does not handle compact CPIXEL.
        currentEncoding = Encodings.encodingZRLE;
        vlog.info("Preferred encoding: " + Encodings.encodingName(currentEncoding));

        viewer.connected.bind(connected);
        try {
            sock = new TcpSocket("127.0.0.1", 5905);

            connected.set(true);
            vlog.debug("CConnFX.connected set to true in CConnFX()");
            vlog.info("Accepted connection from " + sock.getPeerEndpoint());

            sock.inStream().setBlockCallback(this);
            setStreams(sock.inStream(), sock.outStream());
            initialiseProtocol();

            connected.set(true);
            vlog.debug("CConnFX.connected set to true in CConnFX()");

        } catch (java.lang.Exception e) {
            vlog.error("Failed to establish VNC connection: " + e.toString());
            sock = null;
        }
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
        java.awt.image.ColorModel cm = Toolkit.getDefaultToolkit().getColorModel();
        if (cm.getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_RGB) {
            int depth = Math.min(cm.getPixelSize(), 24);
            int bpp = (depth > 16 ? 32 : (depth > 8 ? 16 : 8));
            java.nio.ByteOrder byteOrder = java.nio.ByteOrder.nativeOrder();
            boolean bigEndian = (byteOrder == java.nio.ByteOrder.BIG_ENDIAN);
            boolean trueColour = (depth > 8);
            int redShift   = cm.getComponentSize()[0] + cm.getComponentSize()[1];
            int greenShift = cm.getComponentSize()[0];
            int blueShift  = 0;
            return new PixelFormat(bpp, depth, bigEndian, trueColour,
                (depth > 8 ? 0xff : 0),
                (depth > 8 ? 0xff : 0),
                (depth > 8 ? 0xff : 0),
                (depth > 8 ? redShift : 0),
                (depth > 8 ? greenShift : 0),
                (depth > 8 ? blueShift : 0));
        }
        // Fallback to a safe 8-bit palette format if the display is not RGB.
        return new PixelFormat(8, 8, false, false, 7, 7, 3, 0, 3, 6);
    }

    public void connectionLost() {
        vlog.error("Connection to VNC server lost");
        shuttingDown = true;
        runOnFxThread(() -> connected.set(false));
    }

    public void pointerEvent(Point position, int buttonMask) {
        if (state() != RFBSTATE_NORMAL || shuttingDown) {
            return;
        }
        writer().writePointerEvent(position, buttonMask);
    }

    public void keyEvent(int keysym, boolean keyDown) {
        if (state() != RFBSTATE_NORMAL || shuttingDown) {
            return;
        }
        vlog.debug("Key event: keysym=0x" + Integer.toHexString(keysym)
            + (keyDown ? " down" : " up"));
        writer().writeKeyEvent(keysym, keyDown);
    }

    @Override
    public void serverInit() {
        super.serverInit();

        serverPF = cp.pf();
        fullColourPF = NATIVE_PF;

        vlog.info("serverInit: serverPF=" + serverPF.print() + " fullColourPF=" + fullColourPF.print());

        viewer.imageRender().setServerPF(fullColourPF);
        viewer.imageRender().resize(cp.width, cp.height);

        Platform.runLater(() -> canvasSize.set(new Dimension2D(cp.width, cp.height)));

        formatChange = true;
        encodingChange = true;
        vlog.debug("serverInit done, formatChange=" + formatChange + " encodingChange=" + encodingChange);

        requestNewUpdate();
        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }
    }

    @Override
    public void setDesktopSize(int w, int h) {
        if ((desktopSize.width != w) || (desktopSize.height != h)) {
            vlog.info("Setting desktop size to %dx%d".formatted(w, h));

            super.setDesktopSize(w, h);
            desktopSize.width = w; desktopSize.height = h;
            resizeFramebuffer();
        }
    }

    @Override
    public void setExtendedDesktopSize(int reason, int result, int w, int h, ScreenSet layout) {
        final long desktopSizeHash = desktopSizeHash(reason, result, w, h, layout);
        if (desktopSizeHash != lastDesktopSizeHash) {
            vlog.info("Setting desktop size (extended) to %d, %d, %dx%d, %s".formatted(reason, result, w, h, toString(layout)));
            super.setExtendedDesktopSize(reason, result, w, h, layout);
            resizeFramebuffer();

            if (cp.supportsSetDesktopSize && pendingDesktopWidth > 0 && pendingDesktopHeight > 0) {
                requestDesktopSize(pendingDesktopWidth, pendingDesktopHeight);
                pendingDesktopWidth = pendingDesktopHeight = -1;
            }

            lastDesktopSizeHash = desktopSizeHash;
        }
    }

    @Override
    public void framebufferUpdateStart() {
        updateCount++;
        vlog.debug("framebufferUpdateStart #" + updateCount);
        pendingUpdate = false;
        requestNewUpdate();
    }

    @Override
    public void framebufferUpdateEnd() {
        vlog.debug("framebufferUpdateEnd #" + updateCount);
        viewer.redraw();

        if (firstUpdate) {
            vlog.info("First framebuffer update " + cp.width + "x" + cp.height);
            firstUpdate = false;
        }
        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }
    }

    @Override
    public void setColourMapEntries(int firstColour, int nColours, int[] rgbs) {
        viewer.imageRender().setColourMapEntries(firstColour, nColours, rgbs);
    }

    @Override
    public void bell() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void serverCutText(String str, int len) {
        vlog.debug("Received ServerCutText of length " + len);
        Platform.runLater(() -> clipboard.set(str));
    }

    @Override
    public void beginRect(Rect r, int encoding) {
        vlog.debug("beginRect #" + updateCount + ": " + r.tl.x + "," + r.tl.y + " " + r.width() + "x" + r.height()
            + " encoding=" + encodingName(encoding));
        if (sock != null) {
            sock.inStream().startTiming();
        }
    }

    @Override
    public void endRect(Rect r, int encoding) {
        vlog.debug("endRect #" + updateCount + ": " + r.tl.x + "," + r.tl.y + " " + r.width() + "x" + r.height());
        if (sock != null) {
            sock.inStream().stopTiming();
        }
    }

    @Override
    public void fillRect(Rect r, int p) {
        vlog.debug("fillRect #" + updateCount + ": " + r.tl.x + "," + r.tl.y + " " + r.width() + "x" + r.height());
        viewer.imageRender().fillRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
    }

    @Override
    public void imageRect(Rect r, Object p) {
        int[] src = (p instanceof int[]) ? (int[]) p : null;
        vlog.debug("imageRect #" + updateCount + ": " + r.tl.x + "," + r.tl.y + " "
            + r.width() + "x" + r.height() + " len=" + (src != null ? src.length : 0));
        viewer.imageRender().imageRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
    }

    @Override
    public void copyRect(Rect r, int sx, int sy) {
        viewer.imageRender().copyRect(r.tl.x, r.tl.y, r.width(), r.height(), sx, sy);
    }

    @Override
    public void fence(int flags, int len, byte[] data) {
        cp.supportsFence = true;

        if ((flags & fenceTypes.fenceFlagRequest) != 0) {
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
            vlog.info("Fence: new pixel format: " + pf.print());

            viewer.imageRender().setServerPF(pf);
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

    public void close() {
        shuttingDown = true;
        runOnFxThread(() -> connected.set(false));
        try {
            if (sock != null) {
                sock.shutdown();
            }
        } catch (java.lang.Exception e) {
            vlog.error("Error while closing VNC socket: " + e.toString());
            throw new com.tigervnc.rfb.Exception(e.getMessage());
        }
    }

    public void writeWheelEvent(ScrollEvent ev) {
        if (state() != RFBSTATE_NORMAL || shuttingDown) {
            return;
        }

        double dy = ev.getDeltaY();
        vlog.debug("Scroll event: deltaY=" + dy);
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
    public void requestDesktopSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        // Cache the last requested size so that we can send it once the
        // server indicates support for SetDesktopSize.
        pendingDesktopWidth = width;
        pendingDesktopHeight = height;

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

        vlog.debug("Requesting desktop size " + width + "x" + height);
        writer().writeSetDesktopSize(width, height, layout);
    }

    private void resizeFramebuffer() {
        if (cp.width > 0 && cp.height > 0) {
            viewer.imageRender().resize(cp.width, cp.height);
            Platform.runLater(() -> canvasSize.set(new Dimension2D(cp.width, cp.height)));
        }

        if (continuousUpdates) {
            writer().writeEnableContinuousUpdates(true, 0, 0, cp.width, cp.height);
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
                    case 0 -> verylowColourPF;
                    case 1 -> lowColourPF;
                    default -> mediumColourPF;
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
            vlog.info("Using pixel format " + str);
            writer().writeSetPixelFormat(pf);

            formatChange = false;
        }

        if (encodingChange) {
            vlog.info("Requesting encoding");
            requestEncodings(); encodingChange = false;
        }

        if (forceNonincremental || !continuousUpdates) {
            vlog.debug("Sending update request (incremental=" + !forceNonincremental + ")");
            pendingUpdate = true;
            writer().writeFramebufferUpdateRequest(new Rect(0, 0, cp.width, cp.height),
                !forceNonincremental);
        }

        forceNonincremental = false;
    }

    private void requestEncodings() {
        // Use the same encoding negotiation as the Swing viewer: advertise
        // the preferred encoding plus all supported fallbacks (CopyRect,
        // ZRLE, Hextile, Tight, etc.). This lets the server choose the
        // encoding it handles best, which in practice is ZRLE.
        vlog.info("Requesting " + Encodings.encodingName(currentEncoding)
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
        if (state() != RFBSTATE_NORMAL || shuttingDown) {
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
        final StringBuffer sb = new StringBuffer();
        on(ss.screens).loop((screen) -> sb.append(toString(screen)).append(" "));
        return sb.toString();
    }

    private String toString(final Screen s) {
        return "(%d/%s/%d)".formatted(s.id, toString(s.dimensions), s.flags);
    }

    private String toString(final Rect r) {
        return "[%s-%s]".formatted(toString(r.tl), toString(r.br));
    }

    private String toString(final Point p) {
        return "(%d,%d)".formatted(p.x, p.y);
    }
}
