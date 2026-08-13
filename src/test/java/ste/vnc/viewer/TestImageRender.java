package ste.vnc.viewer;

/**
 * Simple test double for {@link ImageRender} that records calls made by the
 * production code without performing any real rendering.
 */
class TestImageRender extends ImageRender {

    int[] capturedPixels;

    TestImageRender() {
        super(1, 1);
    }

    @Override
    public void updatePixels(int x, int y, int w, int h, int[] src) {
        // do not mutate src, just keep a reference so tests can verify if it
        // was invoked or not.
        this.capturedPixels = src;
    }
}
