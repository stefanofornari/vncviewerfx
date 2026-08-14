package ste.vnc.viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.Rect;

/**
 * Minimal framebuffer that can be used to actually render the incoming rectangles
 */
public class ImageRender {

  private static final LogWriter vlog = new LogWriter("ImageRenderFx");

  private int width;
  private int height;
  private int[] framebuffer;
  private com.tigervnc.rfb.PixelFormat serverPF;
  private final List<Rect> dirty = new ArrayList<>();

  public ImageRender(int width, int height) {
    resize(width, height);
  }

  public synchronized void resize(int width, int height) {
    this.width = width;
    this.height = height;
    this.framebuffer = new int[width * height];
    // VNC has no alpha channel; the 32-bit wire format leaves the high byte
    // as padding. Initialise the framebuffer as opaque so that JavaFX's
    // INT_ARGB pixel writer treats unset pixels as solid black rather than
    // transparent (which would show the canvas background).
    Arrays.fill(framebuffer, 0xff000000);
    // Mark the entire framebuffer as dirty on resize
    dirty.clear();
    if (width > 0 && height > 0) {
      dirty.add(new Rect(0, 0, width, height));
    }
  }

  public synchronized void beginUpdate() {
    dirty.clear();
  }

  public synchronized void markDirty(int x, int y, int w, int h) {
    dirty.add(new Rect(x, y, x + w, y + h));
  }

  public synchronized List<Rect> drainDirty() {
    List<Rect> result = new ArrayList<>(dirty);
    dirty.clear();
    return result;
  }

  public void setServerPF(com.tigervnc.rfb.PixelFormat pf) {
    this.serverPF = pf;
    vlog.debug("setServerPF: " + pf.print());
  }

  public void setColourMapEntries(int firstColour, int nColours, int[] rgbs) {
    // Palette modes not supported in this minimal viewer.
  }

  public synchronized void fillRect(int x, int y, int w, int h, int p) {
    if (framebuffer == null) return;
    // Bounds check
    if (x < 0 || y < 0 || x + w > width || y + h > height) {
      vlog.info("fillRect out of bounds: x=" + x + " y=" + y + " w=" + w + " h=" + h + " (framebuffer=" + width + "x" + height + ")");
      return;
    }
    int pixel = toJavaFxPixel(p);
    for (int ry = y; ry < y + h; ry++) {
      int base = ry * width + x;
      for (int rx = 0; rx < w; rx++) {
        framebuffer[base + rx] = pixel;
      }
    }
    markDirty(x, y, w, h);
  }

  public synchronized void imageRect(int x, int y, int w, int h, Object p) {
    if (framebuffer == null || !(p instanceof int[])) return;
    int[] src = (int[]) p;
    // Bounds check
    if (x < 0 || y < 0 || x + w > width || y + h > height) {
      vlog.info("imageRect out of bounds: x=" + x + " y=" + y + " w=" + w + " h=" + h + " (framebuffer=" + width + "x" + height + ")");
      return;
    }
    // Check that src has enough data
    if (src.length < w * h) {
      vlog.info("imageRect src too small: " + src.length + " < " + (w * h));
      return;
    }
    for (int row = 0; row < h; row++) {
      int dstOffset = (y + row) * width + x;
      int srcOffset = row * w;
      for (int col = 0; col < w; col++) {
        framebuffer[dstOffset + col] = toJavaFxPixel(src[srcOffset + col]);
      }
    }
    markDirty(x, y, w, h);
  }

  public synchronized void copyRect(int x, int y, int w, int h, int sx, int sy) {
    if (framebuffer == null) return;
    // Bounds check
    if (x < 0 || y < 0 || x + w > width || y + h > height) {
      vlog.info("copyRect dest out of bounds: x=" + x + " y=" + y + " w=" + w + " h=" + h);
      return;
    }
    if (sx < 0 || sy < 0 || sx + w > width || sy + h > height) {
      vlog.info("copyRect src out of bounds: sx=" + sx + " sy=" + sy + " w=" + w + " h=" + h);
      return;
    }

    int dest = (y * width) + x;
    int src = (sy * width) + sx;
    int inc = width;

    if (y > sy) {
      src += (h - 1) * inc;
      dest += (h - 1) * inc;
      inc = -inc;
    }
    int destEnd = dest + h * inc;

    while (dest != destEnd) {
      System.arraycopy(framebuffer, src, framebuffer, dest, w);
      src += inc;
      dest += inc;
    }
    markDirty(x, y, w, h);
  }

  public void setCursor(int width, int height, com.tigervnc.rfb.Point hotspot, int[] data, byte[] mask) {
    // Local cursor rendering is not implemented in the minimal FX viewer.
  }

  public synchronized void updatePixels(int x, int y, int w, int h, int[] pixels) {
    if (framebuffer == null) return;
    for (int row = 0; row < h; row++) {
      int dstOffset = (y + row) * width + x;
      int srcOffset = row * w;
      for (int col = 0; col < w; col++) {
        framebuffer[dstOffset + col] = toJavaFxPixel(pixels[srcOffset + col]);
      }
    }
    markDirty(x, y, w, h);
  }

  public synchronized int[] getFramebuffer() {
    // Return a copy to avoid concurrent modification during rendering
    // Must hold the lock during copy to ensure consistency
    int[] copy = new int[framebuffer.length];
    System.arraycopy(framebuffer, 0, copy, 0, framebuffer.length);
    return copy;
  }

  public synchronized int[] copyRegion(int x, int y, int w, int h) {
    if (x < 0 || y < 0 || w < 0 || h < 0 || x + w > width || y + h > height) {
      return new int[0];
    }
    int[] region = new int[w * h];
    for (int row = 0; row < h; row++) {
      System.arraycopy(framebuffer, (y + row) * width + x, region, row * w, w);
    }
    return region;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  /**
   * Converts a pixel from the server-declared pixel format to JavaFX's
   * {@code INT_ARGB}, forcing full opacity. This lets the viewer request the
   * native display format (matching the Swing viewer) and correct for it here.
   */
  private int toJavaFxPixel(int p) {
    if (serverPF == null || !serverPF.trueColour || serverPF.depth <= 8) {
      // Fallback for missing or palette formats: just force opaque.
      return p | 0xff000000;
    }

    int r = (p >>> serverPF.redShift)   & serverPF.redMax;
    int g = (p >>> serverPF.greenShift) & serverPF.greenMax;
    int b = (p >>> serverPF.blueShift)  & serverPF.blueMax;

    // Scale component ranges up to 8 bits when the server uses fewer bits.
    if (serverPF.redMax   != 255) r = r * 255 / serverPF.redMax;
    if (serverPF.greenMax != 255) g = g * 255 / serverPF.greenMax;
    if (serverPF.blueMax  != 255) b = b * 255 / serverPF.blueMax;

    return 0xff000000 | (r << 16) | (g << 8) | b;
  }
}
