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

import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import com.tigervnc.rfb.Point;

/**
 * Bridges JavaFX input events to VNC server coordinates.
 * Handles coordinate translation for scaled/scrolled views.
 */
public class EventBridge {

  private double scaleX = 1.0;
  private double scaleY = 1.0;
  private double offsetX = 0.0;
  private double offsetY = 0.0;

  public void setScale(double scaleX, double scaleY) {
    this.scaleX = scaleX;
    this.scaleY = scaleY;
  }

  public void setOffset(double offsetX, double offsetY) {
    this.offsetX = offsetX;
    this.offsetY = offsetY;
  }

  /**
   * Converts JavaFX screen coordinates to VNC server coordinates
   */
  public Point screenToVnc(double screenX, double screenY) {
    double vncX = (screenX - offsetX) / scaleX;
    double vncY = (screenY - offsetY) / scaleY;
    return new Point((int)vncX, (int)vncY);
  }

  /**
   * Converts JavaFX screen coordinates to VNC for mouse event
   */
  public Point mouseEventToVnc(MouseEvent e) {
    return screenToVnc(e.getX(), e.getY());
  }

  /**
   * Extracts button state from JavaFX MouseEvent
   * Returns bitmask compatible with VNC button format:
   *   bit 0: left button
   *   bit 1: middle button
   *   bit 2: right button
   */
  public int mouseButtonsFromEvent(MouseEvent e) {
    int buttons = 0;

    if (e.isPrimaryButtonDown())
      buttons |= 1;  // Left button
    if (e.isMiddleButtonDown())
      buttons |= 2;  // Middle button
    if (e.isSecondaryButtonDown())
      buttons |= 4;  // Right button

    return buttons;
  }

  /**
   * Extracts keyboard modifiers from JavaFX KeyEvent
   * Returns bitmask compatible with VNC:
   *   bit 0: Shift
   *   bit 1: Ctrl
   *   bit 2: Alt
   */
  public int keyModifiersFromEvent(KeyEvent e) {
    int mods = 0;

    if (e.isShiftDown())
      mods |= 1;
    if (e.isControlDown())
      mods |= 2;
    if (e.isAltDown())
      mods |= 4;

    return mods;
  }

  /**
   * Converts JavaFX KeyCode to VNC keysym
   */
  public int keyCodeToKeysym(javafx.scene.input.KeyCode keyCode) {
    // Map JavaFX KeyCode to X11 keysyms.
    //
    // Printable characters (letters A-Z, digits 0-9, punctuation, etc.) are
    // intentionally NOT mapped here. KeyboardInputListener.onKeyPressed() falls
    // back to KeyEvent.getText(), which JavaFX resolves with full modifier and
    // keyboard-layout awareness (e.g. Shift+1 → "!" → keysym 0x21). Mapping
    // them explicitly here would always return the unshifted keysym (e.g. XK_1
    // for Shift+1), causing TigerVNC's "temp-shift" fixup to silently undo the
    // Shift modifier and type the wrong character.
    switch (keyCode) {
      case SPACE: return 0x20;
      case BACK_SPACE: return 0xFF08;
      case TAB: return 0xFF09;
      case ENTER: return 0xFF0D;
      case ESCAPE: return 0xFF1B;
      case DELETE: return 0xFFFF;
      case HOME: return 0xFF50;
      case LEFT: return 0xFF51;
      case UP: return 0xFF52;
      case RIGHT: return 0xFF53;
      case DOWN: return 0xFF54;
      case PAGE_UP: return 0xFF55;
      case PAGE_DOWN: return 0xFF56;
      case END: return 0xFF57;
      case INSERT: return 0xFF63;
      case F1: return 0xFFBE;
      case F2: return 0xFFBF;
      case F3: return 0xFFC0;
      case F4: return 0xFFC1;
      case F5: return 0xFFC2;
      case F6: return 0xFFC3;
      case F7: return 0xFFC4;
      case F8: return 0xFFC5;
      case F9: return 0xFFC6;
      case F10: return 0xFFC7;
      case F11: return 0xFFC8;
      case F12: return 0xFFC9;
      case SHIFT: return 0xFFE1;
      case CONTROL: return 0xFFE3;
      case ALT: return 0xFFE9;
      case CAPS: return 0xFFE5;
      case NUM_LOCK: return 0xFF7F;
      case SCROLL_LOCK: return 0xFF14;
      case PAUSE: return 0xFF13;
//      case SUPER_L: return 0xFFEB;
//      case SUPER_R: return 0xFFEC;
      case CONTEXT_MENU: return 0xFF67;
      default: return 0;
    }
  }

  /**
   * Checks if a KeyEvent is a modifier key (Shift, Ctrl, Alt, etc.)
   */
  public static boolean isModifierKey(KeyEvent e) {
    switch (e.getCode()) {
      case SHIFT:
      case CONTROL:
      case ALT:
      case ALT_GRAPH:
      case CAPS:
      case NUM_LOCK:
      case SCROLL_LOCK:
//      case SUPER_L:
//      case SUPER_R:
        return true;
      default:
        return false;
    }
  }
}
