package ste.vnc.viewer;

import java.awt.Toolkit;

import com.tigervnc.network.Socket;
import com.tigervnc.network.TcpSocket;
import com.tigervnc.rdr.FdInStreamBlockCallback;
import com.tigervnc.rdr.MemInStream;
import com.tigervnc.rdr.MemOutStream;
import com.tigervnc.rfb.CConnection;
import com.tigervnc.rfb.Encodings;
import com.tigervnc.rfb.Exception;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Point;
import com.tigervnc.rfb.Rect;
import com.tigervnc.rfb.Screen;
import com.tigervnc.rfb.ScreenSet;
import com.tigervnc.rfb.fenceTypes;
import java.util.Iterator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Dimension2D;
import javafx.scene.input.ScrollEvent;

public class CConnFX extends CConnection implements FdInStreamBlockCallback {

    public final ObjectProperty<Dimension2D> canvasSize = new SimpleObjectProperty<>();
    public final StringProperty clipboard = new SimpleStringProperty();

    private static final LogWriter vlog = new LogWriter("FxCConn");
    static final PixelFormat verylowColourPF = new PixelFormat(8, 3, false, true, 1, 1, 1, 2, 1, 0);
    static final PixelFormat lowColourPF = new PixelFormat(8, 6, false, true, 3, 3, 3, 4, 2, 0);
    static final PixelFormat mediumColourPF = new PixelFormat(8, 8, false, false, 7, 7, 3, 0, 3, 6);

    private final ImageRender imageRender;
    private final Runnable redraw;
    private final EventBridge eventBridge = new EventBridge();
    private final Socket sock;
    private boolean shuttingDown;
    private boolean pendingPFChange;
    private PixelFormat pendingPF;
    private PixelFormat fullColourPF = new PixelFormat();
    private PixelFormat serverPF;

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

    public CConnFX(ImageRender imageRender, Runnable redraw) throws Exception {
        this.imageRender = imageRender; // TODO: remove
        this.redraw = redraw;

        //
        // Hard-wire protocol options that we comfortable they work
        // - No local cursor rendering (server renders cursor in framebuffer)
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

        sock = new TcpSocket("127.0.0.1", 5905);

        vlog.info("Accepted connection from " + sock.getPeerEndpoint());

        sock.inStream().setBlockCallback(this);
        setStreams(sock.inStream(), sock.outStream());
        initialiseProtocol();
    }

    @Override
    public PixelFormat getPreferredPF() {
        return fullColourPF;
    }
    public void connectionReady() {
    }

    public void connectionLost() {
        shuttingDown = true;
    }

    public boolean isConnected() {
        return !shuttingDown;
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
        vlog.info("Server pixel format: " + serverPF.print());
        // Resize the underlying pixel buffer immediately on the RFB thread so
        // that subsequent decoder calls (fillRect/imageRect) never overrun it.
        imageRender.setServerPF(serverPF);
        imageRender.resize(cp.width, cp.height);

        // Update the JavaFX canvas and window geometry on the FX thread.
        Platform.runLater(() -> canvasSize.set(new Dimension2D(cp.width, cp.height)));

        fullColourPF = serverPF;
        formatChange = true;
        encodingChange = true;

        requestNewUpdate();
        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }
    }

    @Override
    public void setDesktopSize(int w, int h) {
        super.setDesktopSize(w, h);
        resizeFramebuffer();
    }

    @Override
    public void setExtendedDesktopSize(int reason, int result, int w, int h, com.tigervnc.rfb.ScreenSet layout) {
        super.setExtendedDesktopSize(reason, result, w, h, layout);
        resizeFramebuffer();

        // If we attempted to request a specific desktop size before the
        // server signalled support, retry now that supportsSetDesktopSize
        // has been enabled by the ExtendedDesktopSize message.
        if (cp.supportsSetDesktopSize && pendingDesktopWidth > 0 && pendingDesktopHeight > 0) {
            requestDesktopSize(pendingDesktopWidth, pendingDesktopHeight);
            pendingDesktopWidth = pendingDesktopHeight = -1;
        }
    }

    @Override
    public void framebufferUpdateStart() {
        pendingUpdate = false;
        requestNewUpdate();
    }

    @Override
    public void framebufferUpdateEnd() {
        redraw.run();

        if (firstUpdate) {
            vlog.info("First framebuffer update " + cp.width + "x" + cp.height);

            // We need fences to make extra update requests and continuous
            // updates "safe". See fence() for the next step.
            if (cp.supportsFence) {
                writer().writeFence(fenceTypes.fenceFlagRequest | fenceTypes.fenceFlagSyncNext, 0, null);
            }

            firstUpdate = false;
        }
        if (pendingPFChange) {
            cp.setPF(pendingPF);
            pendingPFChange = false;
        }
    }

    @Override
    public void setColourMapEntries(int firstColour, int nColours, int[] rgbs) {
        imageRender.setColourMapEntries(firstColour, nColours, rgbs);
    }

    @Override
    public void bell() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void serverCutText(String str, int len) {
        vlog.debug("Received ServerCutText of length " + len);
        Platform.runLater( () -> clipboard.set(str));
    }

    @Override
    public void beginRect(Rect r, int encoding) {
        sock.inStream().startTiming();
    }

    @Override
    public void endRect(Rect r, int encoding) {
        sock.inStream().stopTiming();
    }

    @Override
    public void fillRect(Rect r, int p) {
        imageRender.fillRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
    }

    @Override
    public void imageRect(Rect r, Object p) {
        imageRender.imageRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
    }

    @Override
    public void copyRect(Rect r, int sx, int sy) {
        imageRender.copyRect(r.tl.x, r.tl.y, r.width(), r.height(), sx, sy);
    }

    /* CHECK
    @Override
    public void setCursor(int width, int height, Point hotspot, int[] data, byte[] mask) {
        runOnFxThread(() -> canvas.setRemoteCursor(width, height, hotspot, data, mask));
    }
    */

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

                if (cp.supportsContinuousUpdates) {
                    vlog.info("Enabling continuous updates");
                    continuousUpdates = true;
                    writer().writeEnableContinuousUpdates(true, 0, 0, cp.width, cp.height);
                }
            }
        } else {
            MemInStream memStream = new MemInStream(data, 0, len);
            PixelFormat pf = new PixelFormat();

            pf.read(memStream);
            vlog.info("Fence: new pixel format: " + pf.print());

            imageRender.setServerPF(pf);
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
            throw new Exception(e.getMessage());
        }
    }

    public void close() {
        shuttingDown = true;
        try {
            if (sock != null) {
                sock.shutdown();
            }
        } catch (java.lang.Exception e) {
            throw new Exception(e.getMessage());
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

        // Map JavaFX scroll to VNC wheel buttons. We treat each event as at
        // least one "click" in the appropriate direction so that both
        // directions behave symmetrically.
        int clicks = (int) Math.signum(dy);  // +1 or -1

        int buttonMask = clicks < 0 ? 16 : 8; // down vs up

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
            // Collapse to a single screen, mirroring the Swing viewer logic.
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
            // Resize the underlying pixel buffer on the RFB thread first
            imageRender.resize(cp.width, cp.height);

            // Update the JavaFX canvas on the FX thread
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

            /* Catch incorrect requestNewUpdate calls */
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

        checkEncodings();

        if (forceNonincremental || !continuousUpdates) {
            vlog.info("Sending update request (incremental=" + !forceNonincremental + ")");
            pendingUpdate = true;
            writer().writeFramebufferUpdateRequest(new Rect(0, 0, cp.width, cp.height),
                !forceNonincremental);
        }

        forceNonincremental = false;
    }

    private void checkEncodings() {
        if (encodingChange && writer() != null) {
            int[] encodings = new int[10];
            int nEncodings = 0;

            // Pseudo-encodings - only the essential ones we rely on
            encodings[nEncodings++] = Encodings.pseudoEncodingContinuousUpdates;
            encodings[nEncodings++] = Encodings.pseudoEncodingFence;
            encodings[nEncodings++] = Encodings.pseudoEncodingLastRect;

            // Real encodings: prefer ZRLE, fall back to Raw/CopyRect
            encodings[nEncodings++] = Encodings.encodingZRLE;
            encodings[nEncodings++] = Encodings.encodingRaw;
            encodings[nEncodings++] = Encodings.encodingCopyRect;

            // Compression level if custom
            if (cp.customCompressLevel && cp.compressLevel >= 0 && cp.compressLevel <= 9) {
                encodings[nEncodings++] = Encodings.pseudoEncodingCompressLevel0 + cp.compressLevel;
            }

            vlog.info("Sending encodings: " + nEncodings + " encodings");
            writer().writeSetEncodings(nEncodings, encodings);
            encodingChange = false;
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
        if (state() != RFBSTATE_NORMAL || shuttingDown) {
            return;
        }
        writer().writeClientCutText(str, len);
    }
}
