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

import com.tigervnc.rfb.CMsgWriterV3;
import com.tigervnc.rfb.Encodings;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Point;
import com.tigervnc.rfb.Rect;
import com.tigervnc.rfb.Screen;
import com.tigervnc.rfb.ScreenSet;
import javafx.scene.image.Image;
import javafx.scene.input.ScrollEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import static org.assertj.core.api.BDDAssertions.then;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.testfx.framework.junit5.ApplicationTest;

class VNCServiceSpec extends ApplicationTest {

    private VNCServiceStub vncService;
    private CMsgWriterV3 mockWriter;

    @Override
    public void start(Stage stage) {
        // No UI needed for these tests; JavaFX platform is initialised by TestFX.
    }

    @BeforeEach
    void before_each() {
        mockWriter = mock(CMsgWriterV3.class);
        vncService = new VNCServiceStub();
        vncService.customWriter(mockWriter);

        // Configure default server parameters for CConnection
        vncService.cp.width = 100;
        vncService.cp.height = 200;
        vncService.cp.setPF(VNCService.NATIVE_PF);
    }

    @Test
    @DisplayName("serverInit initializes desktop image and sends initial protocol requests")
    void serverInit_inizializes_image_and_protocol() {
        vncService.serverInit();

        // Image should be created with server dimensions (100x200)
        Image img = vncService.image.get();
        then(img).isNotNull();
        then(img.getWidth()).isEqualTo(100);
        then(img.getHeight()).isEqualTo(200);

        // Verify initial protocol messages were sent
        verify(mockWriter).writeSetPixelFormat(any(PixelFormat.class));
        verify(mockWriter).writeSetEncodings(eq(Encodings.encodingZRLE), eq(true));
        verify(mockWriter).writeFramebufferUpdateRequest(any(Rect.class), eq(false));
    }

    @Test
    @DisplayName("fillRect updates underlying pixel buffer")
    void fillRect_updates_pixel_buffer() {
        vncService.setDesktopSize(10, 10);

        // Fill a 2x2 area at (0,0) with solid red (0x00FF0000 in RGB)
        Rect rect = new Rect(0, 0, 2, 2);
        int redPixel = 0x00FF0000;
        vncService.fillRect(rect, redPixel);

        Image img = vncService.image.get();
        then(img).isNotNull();
        // Verify pixel turned opaque ARGB: 0xFFFF0000
        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(0xFFFF0000);
        then(img.getPixelReader().getArgb(1, 1)).isEqualTo(0xFFFF0000);
    }

    @Test
    @DisplayName("fillRect and copyRect ignore out of bounds coordinates safely")
    void fillRect_and_copyRect_ignore_out_of_bound() {
        vncService.setDesktopSize(10, 10);
        Image img = vncService.image.get();
        then(img).isNotNull();

        // 1. Establish a baseline color (Blue: 0x000000FF -> 0xFF0000FF ARGB) across the image
        int bluePixel = 0x000000FF;
        int expectedColor = 0xFF0000FF;
        vncService.fillRect(new Rect(0, 0, 10, 10), bluePixel);

        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);
        then(img.getPixelReader().getArgb(9, 9)).isEqualTo(expectedColor);

        // 2. Out-of-bounds fillRect (negative top-left) - attempting to paint Red (0x00FF0000)
        Rect invalidFillRect = new Rect(-5, -5, 5, 5);
        vncService.fillRect(invalidFillRect, 0x00FF0000);

        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);

        // 3. Out-of-bounds copyRect (source coordinates 8+5=13 exceed width of 10)
        Rect copyDstRect = new Rect(0, 0, 5, 5);
        vncService.copyRect(copyDstRect, 8, 8);

        // Assert pixel buffer was NOT modified
        then(img.getPixelReader().getArgb(0, 0)).isEqualTo(expectedColor);
    }

    @Test
    @DisplayName("copyRect correctly shifts pixel regions")
    void copyRect_shifts_pixel_regions() {
        vncService.setDesktopSize(10, 10);

        // Draw a distinct pixel at (1, 1)
        vncService.fillRect(new Rect(1, 1, 2, 2), 0x0000FF00); // Green

        // Copy (1,1)-(2,2) region to (5,5)
        Rect dstRect = new Rect(5, 5, 6, 6);
        vncService.copyRect(dstRect, 1, 1);

        Image img = vncService.image.get();
        then(img.getPixelReader().getArgb(5, 5)).isEqualTo(0xFF00FF00);
    }

    @Test
    @DisplayName("pointerEvent and keyEvent forward data to CMsgWriter when state is RFBSTATE_NORMAL")
    void pointerEvent_and_keyEvent_forward_data_when_state_is_normal() {
        vncService.forcedState(VNCServiceStub.RFBSTATE_NORMAL);

        Point p = new Point(15, 25);
        vncService.pointerEvent(p, 1);
        verify(mockWriter).writePointerEvent(p, 1);

        vncService.keyEvent(0x61, true); // 'a' key down
        verify(mockWriter).writeKeyEvent(0x61, true);
    }

    @Test
    @DisplayName("pointerEvent and keyEvent suppress data when state is not RFBSTATE_NORMAL")
    void pointerEvent_and_keyEvent_supress_data_when_state_is_not_normal() {
        vncService.forcedState(0); // Non-normal state

        Point p = new Point(15, 25);
        vncService.pointerEvent(p, 1);
        verify(mockWriter, never()).writePointerEvent(any(), anyInt());

        vncService.keyEvent(0x61, true);
        verify(mockWriter, never()).writeKeyEvent(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("writeWheelEvent translates ScrollEvent to VNC pointer button masks")
    void writeWheelEvent_translates_ScrollEvent_to_pointer_button_masks() {
        vncService.forcedState(VNCServiceStub.RFBSTATE_NORMAL);

        ScrollEvent scrollUp = new ScrollEvent(
            ScrollEvent.SCROLL, 10.0, 10.0, 10.0, 10.0,
            false, false, false, false, false, false,
            0.0, 15.0, 0.0, 15.0,
            ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
            ScrollEvent.VerticalTextScrollUnits.NONE, 0.0, 0, null
        );

        vncService.writeWheelEvent(scrollUp);

        // Scroll Up (positive deltaY) maps to button mask 8, followed by release (mask 0)
        verify(mockWriter).writePointerEvent(any(Point.class), eq(8));
        verify(mockWriter).writePointerEvent(any(Point.class), eq(0));
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
        vncService.cp.supportsSetDesktopSize = true;

        vncService.requestDesktopSize(1920, 1080);

        ArgumentCaptor<ScreenSet> screenSetCaptor = ArgumentCaptor.forClass(ScreenSet.class);
        verify(mockWriter).writeSetDesktopSize(eq(1920), eq(1080), screenSetCaptor.capture());

        ScreenSet set = screenSetCaptor.getValue();
        then(set.num_screens()).isEqualTo(1);
        Screen screen = set.screens.iterator().next();
        then(screen.dimensions.width()).isEqualTo(1920);
        then(screen.dimensions.height()).isEqualTo(1080);
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
        then(vncService.connected.get()).isFalse();
    }
}