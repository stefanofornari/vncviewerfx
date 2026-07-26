package ste.vnc.viewer;

import com.tigervnc.rfb.LogWriter;

/**
 * Minimal framebuffer that can be used to actually render the incoming rectangles
 */
public class ImageRender {

  private static final LogWriter vlog = new LogWriter("ImageRenderFx");

  private int width;
  private int height;
  private int[] framebuffer;

  public ImageRender(int width, int height) {
    resize(width, height);
  }

  public synchronized void resize(int width, int height) {
    this.width = width;
    this.height = height;
    this.framebuffer = new int[width * height];
  }

  public void setServerPF(com.tigervnc.rfb.PixelFormat pf) {
    // Ignored for now; decoders already produce ARGB ints in their output buffers.
    // But log it for debugging
    vlog.info("setServerPF: " + pf.print());
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
    for (int ry = y; ry < y + h; ry++) {
      int base = ry * width + x;
      for (int rx = 0; rx < w; rx++) {
        framebuffer[base + rx] = p;
      }
    }
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
      System.arraycopy(src, row * w, framebuffer, (y + row) * width + x, w);
    }
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
  }

  public void setCursor(int width, int height, com.tigervnc.rfb.Point hotspot, int[] data, byte[] mask) {
    // Local cursor rendering is not implemented in the minimal FX viewer.
  }

  public synchronized void updatePixels(int x, int y, int w, int h, int[] pixels) {
    if (framebuffer == null) return;
    for (int row = 0; row < h; row++) {
      System.arraycopy(pixels, row * w, framebuffer, (y + row) * width + x, w);
    }
  }

  public synchronized int[] getFramebuffer() {
    // Return a copy to avoid concurrent modification during rendering
    // Must hold the lock during copy to ensure consistency
    int[] copy = new int[framebuffer.length];
    System.arraycopy(framebuffer, 0, copy, 0, framebuffer.length);
    return copy;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
