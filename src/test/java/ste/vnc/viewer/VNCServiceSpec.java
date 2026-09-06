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

import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.input.ScrollEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import static org.assertj.core.api.BDDAssertions.then;

import org.testfx.framework.junit5.ApplicationTest;

class VNCServiceSpec extends ApplicationTest {

    private VNCServiceStub vncService;

    @Override
    public void start(Stage stage) {
    }

    @BeforeEach
    void before_each() {
        vncService = new VNCServiceStub();
    }

    @Test
    @DisplayName("serverInit initializes desktop image and sends initial protocol requests")
    void serverInit_inizializes_image_and_protocol() {
        ste.vnc.rfb.ServerInit serverInit = new ste.vnc.rfb.ServerInit(
            100, 200, VNCService.NATIVE_PF, "test"
        );
        vncService.customServerInit(serverInit);
        vncService.serverInit(serverInit);

        Image img = vncService.image.get();
        then(img).isNotNull();
        then(img.getWidth()).isEqualTo(100);
        then(img.getHeight()).isEqualTo(200);
    }

    @Test
    @DisplayName("fillRect updates underlying pixel buffer")
    void fillRect_updates_pixel_buffer() {
        vncService.setDesktopSize(10, 10);

        ste.vnc.rfb.Rectangle rect = new ste.vnc.rfb.Rectangle(0, 0, 2, 2);
        int redPixel = 0x00FF0000;
        vncService.fillRect(rect, redPixel);

        Image img = vncService.image.get();
        then(img).isNotNull();
        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(0xFFFF0000);
        then(img.getPixelReader().getArgb(1, 1)).isEqualTo(0xFFFF0000);
    }

    @Test
    @DisplayName("fillRect and copyRect ignore out of bounds coordinates safely")
    void fillRect_and_copyRect_ignore_out_of_bound() {
        vncService.setDesktopSize(10, 10);
        Image img = vncService.image.get();
        then(img).isNotNull();

        int bluePixel = 0x000000FF;
        int expectedColor = 0xFF0000FF;
        vncService.fillRect(new ste.vnc.rfb.Rectangle(0, 0, 10, 10), bluePixel);

        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);
        then(img.getPixelReader().getArgb(9, 9)).isEqualTo(expectedColor);

        ste.vnc.rfb.Rectangle invalidFillRect = new ste.vnc.rfb.Rectangle(11, 11, 5, 5);
        vncService.fillRect(invalidFillRect, 0x00FF0000);

        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);

        ste.vnc.rfb.Rectangle copyDstRect = new ste.vnc.rfb.Rectangle(0, 0, 5, 5);
        vncService.copyRect(copyDstRect, 8, 8);

        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);
    }

    @Test
    @DisplayName("copyRect correctly shifts pixel regions")
    void copyRect_shifts_pixel_regions() {
        vncService.setDesktopSize(10, 10);

        vncService.fillRect(new ste.vnc.rfb.Rectangle(1, 1, 2, 2), 0x0000FF00);

        ste.vnc.rfb.Rectangle dstRect = new ste.vnc.rfb.Rectangle(5, 5, 2, 2);
        vncService.copyRect(dstRect, 1, 1);

        Image img = vncService.image.get();
        then(img.getPixelReader().getArgb(5, 5)).isEqualTo(0xFF00FF00);
    }

    @Test
    @DisplayName("pointerEvent and keyEvent forward data when connected")
    void pointerEvent_and_keyEvent_forward_data_when_connected() {
        vncService.connected.set(true);

        Point2D p = new Point2D(15, 25);
        vncService.pointerEvent(p, 1);

        vncService.keyEvent(0x61, true);
    }

    @Test
    @DisplayName("pointerEvent and keyEvent suppress data when not connected")
    void pointerEvent_and_keyEvent_supress_data_when_not_connected() {
        vncService.connected.set(false);

        Point2D p = new Point2D(15, 25);
        vncService.pointerEvent(p, 1);

        vncService.keyEvent(0x61, true);
    }

    @Test
    @DisplayName("writeWheelEvent translates ScrollEvent to VNC pointer button masks")
    void writeWheelEvent_translates_ScrollEvent_to_pointer_button_masks() {
        vncService.connected.set(true);

        ScrollEvent scrollUp = new ScrollEvent(
            ScrollEvent.SCROLL, 10.0, 10.0, 10.0, 10.0,
            false, false, false, false, false, false,
            0.0, 15.0, 0.0, 15.0,
            ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
            ScrollEvent.VerticalTextScrollUnits.NONE, 0.0, 0, null
        );

        vncService.writeWheelEvent(scrollUp);
    }

    @Test
    @DisplayName("serverCutText updates JavaFX clipboard property on FX thread")
    void serverCutText_updates_fx_clipboard() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        vncService.clipboard.addListener((obs, oldVal, newVal) -> {
            if ("Hello Remote Clipboard".equals(newVal)) {
                latch.countDown();
            }
        });

        vncService.serverCutText("Hello Remote Clipboard", 22);

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
        then(vncService.clipboard.get()).isEqualTo("Hello Remote Clipboard");
    }

    @Test
    @DisplayName("requestDesktopSize formats ScreenSet and sends writeSetDesktopSize request")
    void requestDesktopSize_formats_ScreenSet_and_sends_writeSetDesktopSize() {
        vncService.requestDesktopSize(1920, 1080);
    }

    @Test
    @DisplayName("connectionLost updates connected property to false and triggers shutdown")
    void connected_updated_on_connection_lost() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        vncService.connected.set(true);
        vncService.connected.addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                latch.countDown();
            }
        });

        vncService.connectionLost();

        then(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }
}
