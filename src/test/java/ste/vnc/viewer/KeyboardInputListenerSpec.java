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

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KeyboardInputListenerSpec {

    private VNCService vnc;
    private EventBridge bridge;
    private KeyboardInputListener listener;

    @BeforeEach
    void setUp() {
        vnc = mock(VNCService.class);
        bridge = new EventBridge();
        listener = new KeyboardInputListener(vnc, bridge);
    }

    // --- EventBridge keyCodeToKeysym ---

    @Test
    @DisplayName("keyCodeToKeysym returns unshifted keysym for letters A-Z")
    void keyCodeToKeysym_returns_unshifted_keysym_for_letters() {
        then(bridge.keyCodeToKeysym(KeyCode.A)).isEqualTo(0x61);
        then(bridge.keyCodeToKeysym(KeyCode.M)).isEqualTo(0x6D);
        then(bridge.keyCodeToKeysym(KeyCode.Z)).isEqualTo(0x7A);
    }

    @Test
    @DisplayName("keyCodeToKeysym returns unshifted keysym for digits 0-9")
    void keyCodeToKeysym_returns_unshifted_keysym_for_digits() {
        then(bridge.keyCodeToKeysym(KeyCode.DIGIT0)).isEqualTo(0x30);
        then(bridge.keyCodeToKeysym(KeyCode.DIGIT5)).isEqualTo(0x35);
        then(bridge.keyCodeToKeysym(KeyCode.DIGIT9)).isEqualTo(0x39);
    }

    @Test
    @DisplayName("keyCodeToKeysym returns 0 for unmapped/undefined keys")
    void keyCodeToKeysym_returns_zero_for_unknown_keys() {
        then(bridge.keyCodeToKeysym(KeyCode.UNDEFINED)).isEqualTo(0);
    }

    // --- KeyboardInputListener key combination handling ---

    @Test
    @DisplayName("onKeyPressed sends key-down even when getText() is empty (e.g. Ctrl+V)")
    void onKeyPressed_sends_key_down_when_text_is_empty() {
        // Simulate Ctrl+V: getText() is empty for KEY_PRESSED when control is held.
        KeyEvent vPressed = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.V,
            false, true, false, false
        );

        listener.onKeyPressed(vPressed);

        verify(vnc).keyEvent(0x76, true); // XK_v
    }

    @Test
    @DisplayName("Ctrl+V press/release sequence sends correct ordered key events")
    void ctrl_v_sequence_sends_correct_ordered_key_events() {
        KeyEvent ctrlDown = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.CONTROL,
            false, true, false, false
        );
        KeyEvent vDown = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.V,
            false, true, false, false
        );
        KeyEvent vUp = new KeyEvent(
            KeyEvent.KEY_RELEASED, "", "", KeyCode.V,
            false, true, false, false
        );
        KeyEvent ctrlUp = new KeyEvent(
            KeyEvent.KEY_RELEASED, "", "", KeyCode.CONTROL,
            false, true, false, false
        );

        listener.onKeyPressed(ctrlDown);
        listener.onKeyPressed(vDown);
        listener.onKeyReleased(vUp);
        listener.onKeyReleased(ctrlUp);

        InOrder order = inOrder(vnc);
        order.verify(vnc).keyEvent(0xFFE3, true);  // Control down
        order.verify(vnc).keyEvent(0x76, true);    // V down
        order.verify(vnc).keyEvent(0x76, false);   // V up
        order.verify(vnc).keyEvent(0xFFE3, false); // Control up
    }

    @Test
    @DisplayName("onKeyReleased ignores release for key that was never pressed")
    void onKeyReleased_ignores_release_for_unpressed_key() {
        KeyEvent orphanRelease = new KeyEvent(
            KeyEvent.KEY_RELEASED, "", "", KeyCode.V,
            false, false, false, false
        );

        listener.onKeyReleased(orphanRelease);

        verify(vnc, never()).keyEvent(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("releaseAllKeys releases every pressed key")
    void release_all_keys_releases_every_pressed_key() {
        KeyEvent ctrlDown = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.CONTROL,
            false, false, false, false
        );
        KeyEvent aDown = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.A,
            false, false, false, false
        );

        listener.onKeyPressed(ctrlDown);
        listener.onKeyPressed(aDown);
        listener.releaseAllKeys();

        // HashMap iteration order is not guaranteed; verify both key-up events fired.
        verify(vnc).keyEvent(0xFFE3, true);
        verify(vnc).keyEvent(0x61, true);
        verify(vnc).keyEvent(0x61, false);
        verify(vnc).keyEvent(0xFFE3, false);
    }
}
