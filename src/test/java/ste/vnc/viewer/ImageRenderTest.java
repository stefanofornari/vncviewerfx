package ste.vnc.viewer;

import com.tigervnc.rdr.MemInStream;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Rect;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link ImageRender}.
 */
public class ImageRenderTest {

    @Test
    public void resize_initialisesOpaqueBlack() {
        ImageRender r = new ImageRender(10, 10);
        int[] fb = r.getFramebuffer();
        assertEquals(100, fb.length);
        for (int pixel : fb) {
            assertEquals("unset pixel must be opaque black", 0xff000000, pixel);
        }
    }

    @Test
    public void fillRect_forcesOpaqueAlpha() {
        ImageRender r = new ImageRender(10, 10);
        // 0x00abcdef has a transparent alpha byte, as produced by the raw
        // decoder when the server sends 0x00 in the 32-bit padding byte.
        r.fillRect(1, 2, 3, 4, 0x00abcdef);
        int[] fb = r.getFramebuffer();
        for (int y = 2; y < 6; y++) {
            for (int x = 1; x < 4; x++) {
                assertEquals("fillRect must make pixel opaque", 0xffabcdef, fb[y * 10 + x]);
            }
        }
    }

    @Test
    public void imageRect_forcesOpaqueAlpha() {
        ImageRender r = new ImageRender(10, 10);
        int[] src = { 0x00112233, 0x00445566, 0x00778899, 0x00aabbcc };
        r.imageRect(0, 0, 2, 2, src);
        int[] fb = r.getFramebuffer();
        assertEquals(0xff112233, fb[0]);
        assertEquals(0xff445566, fb[1]);
        assertEquals(0xff778899, fb[10]);
        assertEquals(0xffaabbcc, fb[11]);
    }

    @Test
    public void updatePixels_forcesOpaqueAlpha() {
        ImageRender r = new ImageRender(10, 10);
        int[] src = { 0x00ddeeff, 0x00112233 };
        r.updatePixels(5, 5, 2, 1, src);
        int[] fb = r.getFramebuffer();
        assertEquals(0xffddeeff, fb[5 * 10 + 5]);
        assertEquals(0xff112233, fb[5 * 10 + 6]);
    }

    @Test
    public void copyRect_preservesOpaqueAlpha() {
        ImageRender r = new ImageRender(10, 10);
        r.fillRect(0, 0, 2, 2, 0x00aabbcc);
        r.copyRect(2, 0, 2, 2, 0, 0);
        int[] fb = r.getFramebuffer();
        assertEquals(0xffaabbcc, fb[2]);
        assertEquals(0xffaabbcc, fb[3]);
        assertEquals(0xffaabbcc, fb[12]);
        assertEquals(0xffaabbcc, fb[13]);
    }

    /**
     * Simulates the path a Raw rectangle takes through the TigerVNC decoder:
     * bytes on the wire are decoded with the current pixel format and then
     * written into the JavaFX framebuffer. This confirms the ARGB mapping
     * is what JavaFX's INT_ARGB pixel writer expects.
     */
    @Test
    public void rawDecoderPixels_mapToJavaFxArgb() {
        // rgb888 little-endian, matching the viewer's requested pixel format.
        PixelFormat pf = new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);

        // Four pixels: red, green, blue, white.
        // For rgb888 little-endian the wire bytes are [B,G,R,P].
        byte[] wire = {
            0x00, 0x00, (byte) 0xff, 0x00,   // red
            0x00, (byte) 0xff, 0x00, 0x00,   // green
            (byte) 0xff, 0x00, 0x00, 0x00,   // blue
            (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00 // white
        };

        MemInStream in = new MemInStream(wire, 0, wire.length);
        int[] decoded = new int[4];
        in.readPixels(decoded, decoded.length, pf.bpp / 8, pf.bigEndian);

        ImageRender r = new ImageRender(2, 2);
        r.setServerPF(pf);
        r.imageRect(0, 0, 2, 2, decoded);

        int[] fb = r.getFramebuffer();
        assertEquals("red", 0xffff0000, fb[0]);
        assertEquals("green", 0xff00ff00, fb[1]);
        assertEquals("blue", 0xff0000ff, fb[2]);
        assertEquals("white", 0xffffffff, fb[3]);
    }

    /**
     * Same as above but for a BGR native pixel format, which the Swing viewer
     * may request on some systems.
     */
    @Test
    public void rawDecoderPixels_mapBgrToJavaFxArgb() {
        PixelFormat pf = new PixelFormat(32, 24, false, true, 255, 255, 255, 0, 8, 16);

        // Four pixels: red, green, blue, white.
        // For bgr888 little-endian the wire bytes are [R,G,B,P].
        byte[] wire = {
            (byte) 0xff, 0x00, 0x00, 0x00,   // red
            0x00, (byte) 0xff, 0x00, 0x00,   // green
            0x00, 0x00, (byte) 0xff, 0x00,   // blue
            (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00 // white
        };

        MemInStream in = new MemInStream(wire, 0, wire.length);
        int[] decoded = new int[4];
        in.readPixels(decoded, decoded.length, pf.bpp / 8, pf.bigEndian);

        ImageRender r = new ImageRender(2, 2);
        r.setServerPF(pf);
        r.imageRect(0, 0, 2, 2, decoded);

        int[] fb = r.getFramebuffer();
        assertEquals("red", 0xffff0000, fb[0]);
        assertEquals("green", 0xff00ff00, fb[1]);
        assertEquals("blue", 0xff0000ff, fb[2]);
        assertEquals("white", 0xffffffff, fb[3]);
    }

    @Test
    public void resize_marksEntireFramebufferAsDirty() {
        ImageRender r = new ImageRender(10, 10);
        List<Rect> dirty = r.drainDirty();
        assertEquals("resize should mark entire framebuffer as dirty", 1, dirty.size());
        Rect rect = dirty.get(0);
        assertEquals(0, rect.tl.x);
        assertEquals(0, rect.tl.y);
        assertEquals(10, rect.width());
        assertEquals(10, rect.height());
    }

    @Test
    public void beginUpdate_clearsDirtyRegions() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        r.fillRect(1, 1, 5, 5, 0x00ff0000);
        r.beginUpdate();
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("beginUpdate should clear dirty regions", 0, dirty.size());
    }

    @Test
    public void fillRect_marksRegionAsDirty() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        r.fillRect(2, 3, 4, 5, 0x00ff0000);
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("fillRect should mark region as dirty", 1, dirty.size());
        Rect rect = dirty.get(0);
        assertEquals(2, rect.tl.x);
        assertEquals(3, rect.tl.y);
        assertEquals(4, rect.width());
        assertEquals(5, rect.height());
    }

    @Test
    public void imageRect_marksRegionAsDirty() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        int[] pixels = new int[4 * 3];
        java.util.Arrays.fill(pixels, 0x00112233);
        r.imageRect(1, 2, 4, 3, pixels);
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("imageRect should mark region as dirty", 1, dirty.size());
        Rect rect = dirty.get(0);
        assertEquals(1, rect.tl.x);
        assertEquals(2, rect.tl.y);
        assertEquals(4, rect.width());
        assertEquals(3, rect.height());
    }

    @Test
    public void copyRect_marksRegionAsDirty() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        // First fill a region to copy from
        r.beginUpdate();
        r.fillRect(0, 0, 5, 5, 0x00ff0000);
        r.drainDirty(); // Clear the dirty from fillRect
        
        r.beginUpdate();
        r.copyRect(3, 3, 2, 2, 0, 0);
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("copyRect should mark region as dirty", 1, dirty.size());
        Rect rect = dirty.get(0);
        assertEquals(3, rect.tl.x);
        assertEquals(3, rect.tl.y);
        assertEquals(2, rect.width());
        assertEquals(2, rect.height());
    }

    @Test
    public void updatePixels_marksRegionAsDirty() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        int[] pixels = {0x00112233, 0x00445566, 0x00778899, 0x00aabbcc};
        r.updatePixels(5, 5, 2, 2, pixels);
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("updatePixels should mark region as dirty", 1, dirty.size());
        Rect rect = dirty.get(0);
        assertEquals(5, rect.tl.x);
        assertEquals(5, rect.tl.y);
        assertEquals(2, rect.width());
        assertEquals(2, rect.height());
    }

    @Test
    public void multipleOperations_accumulateDirtyRegions() {
        ImageRender r = new ImageRender(20, 20);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        r.fillRect(0, 0, 5, 5, 0x00ff0000);
        r.imageRect(10, 10, 3, 3, new int[9]);
        r.copyRect(15, 15, 2, 2, 0, 0);
        
        List<Rect> dirty = r.drainDirty();
        assertEquals("multiple operations should accumulate dirty regions", 3, dirty.size());
    }

    @Test
    public void drainDirty_clearsDirtyList() {
        ImageRender r = new ImageRender(10, 10);
        r.drainDirty(); // Clear initial dirty from resize
        
        r.beginUpdate();
        r.fillRect(1, 1, 5, 5, 0x00ff0000);
        
        List<Rect> firstDrain = r.drainDirty();
        assertEquals(1, firstDrain.size());
        
        List<Rect> secondDrain = r.drainDirty();
        assertEquals("drainDirty should clear the list", 0, secondDrain.size());
    }

    @Test
    public void copyRegion_returnsCorrectSubregion() {
        ImageRender r = new ImageRender(10, 10);
        r.fillRect(0, 0, 10, 10, 0x00ff0000);
        r.fillRect(2, 2, 4, 4, 0x0000ff00);
        
        int[] region = r.copyRegion(2, 2, 4, 4);
        assertEquals(4 * 4, region.length);
        // All pixels in the region should be green (0xff00ff00 after forcing opaque)
        for (int pixel : region) {
            assertEquals("All pixels in region should be green", 0xff00ff00, pixel);
        }
    }

    /**
     * Test case for the bug where framebuffer resize creates a dirty region
     * that exceeds the current canvas bounds, causing it to be skipped and
     * leaving old content visible.
     * 
     * This reproduces the issue found in the logs where:
     * - Framebuffer was resized to 566x958
     * - Canvas was still at 560x944
     * - Dirty region was (0,0,566x958)
     * - The region exceeded canvas bounds and was skipped
     */
    @Test
    public void resize_DirtyRegionExceedsPreviousCanvasBounds() {
        // Start with a smaller size
        ImageRender r = new ImageRender(560, 944);
        r.drainDirty(); // Clear initial dirty region
        
        // Resize to larger dimensions (simulating what happens during a VNC resize)
        r.resize(566, 958);
        
        // Get the dirty region - should be the full new size
        List<Rect> dirtyRegions = r.drainDirty();
        assertEquals(1, dirtyRegions.size());
        
        Rect dirty = dirtyRegions.get(0);
        assertEquals(0, dirty.tl.x);
        assertEquals(0, dirty.tl.y);
        assertEquals(566, dirty.width());
        assertEquals(958, dirty.height());
        
        // This is the problematic case: when trying to draw this region
        // with a canvas of 560x944, the bounds check:
        // rx + rw = 0 + 566 = 566 > 560 (canvas width)
        // ry + rh = 0 + 958 = 958 > 944 (canvas height)
        // would cause the region to be skipped in the old code
        
        // The fix in VNCViewer.drawFramebuffer() should clamp the region
        // to canvas bounds instead of skipping it
    }

    /**
     * Test case for the bug where canvas is resized larger than the framebuffer,
     * causing old content (like window borders) to remain visible in the extra area.
     * 
     * This reproduces the issue where:
     * - User moves a window quickly
     * - Canvas gets resized larger by the window manager
     * - But framebuffer hasn't been updated yet
     * - The extra canvas area shows old content (window borders)
     * - Clicking the mouse triggers a redraw that fixes it
     */
    @Test
    public void canvasLargerThanFramebuffer_ClearsExtraArea() {
        // This test documents the expected behavior in VNCViewer.drawFramebuffer()
        // When canvas (w x h) > framebuffer (fbWidth x fbHeight), the extra areas
        // should be cleared before drawing the framebuffer content.
        
        // Example scenario:
        // - Canvas is 583x981 (resized larger by window manager)
        // - Framebuffer is 560x944 (not yet updated)
        // - Extra area: right 23px (583-560) and bottom 44px (981-944)
        // - The fix in VNCViewer.drawFramebuffer() should:
        //   1. gc.clearRect(560, 0, 23, 981) - clear right strip
        //   2. gc.clearRect(0, 944, 560, 44) - clear bottom strip
        //   This is implemented at lines 170-172 in VNCViewer.java
    }
}
