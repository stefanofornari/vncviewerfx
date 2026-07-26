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
import java.util.HashMap;
import java.util.Map;

/**
 * Handles mouse input events and sends them to VNC server.
 */
class MouseInputListener {

  private FxCConn connection;
  private EventBridge eventBridge;
  private int lastX = 0;
  private int lastY = 0;
  private int lastButtonState = 0;

  public MouseInputListener(FxCConn conn, EventBridge bridge) {
    this.connection = conn;
    this.eventBridge = bridge;
  }

  public void onMousePressed(MouseEvent e) {
    Point pos = eventBridge.mouseEventToVnc(e);
    int buttons = eventBridge.mouseButtonsFromEvent(e);
    lastButtonState = buttons;
    lastX = pos.x;
    lastY = pos.y;

    if (connection != null) {
      try {
        connection.pointerEvent(pos, buttons);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public void onMouseReleased(MouseEvent e) {
    Point pos = eventBridge.mouseEventToVnc(e);
    int buttons = eventBridge.mouseButtonsFromEvent(e);
    lastButtonState = buttons;
    lastX = pos.x;
    lastY = pos.y;

    if (connection != null) {
      try {
        connection.pointerEvent(pos, buttons);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public void onMouseMoved(MouseEvent e) {
    Point pos = eventBridge.mouseEventToVnc(e);
    int buttons = eventBridge.mouseButtonsFromEvent(e);

    int prevX = lastX;
    int prevY = lastY;
    int prevButtons = lastButtonState;

    lastX = pos.x;
    lastY = pos.y;

    if (connection != null && (pos.x != prevX || pos.y != prevY || buttons != prevButtons)) {
      lastButtonState = buttons;
      try {
        connection.pointerEvent(pos, buttons);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  public void onMouseWheel(int wheelDelta) {
    // Map wheel to VNC button 4 (up) and 5 (down): bits 3 and 4.
    Point pos = new Point(lastX, lastY);
    int baseButtons = lastButtonState;
    int wheelMask = wheelDelta > 0 ? (1 << 3) : (1 << 4);

    if (connection != null) {
      try {
        // Press wheel button
        connection.pointerEvent(pos, baseButtons | wheelMask);
        // Release wheel button
        connection.pointerEvent(pos, baseButtons);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }
}

/**
 * Handles keyboard input events and sends them to VNC server.
 */
class FxKeyboardInputListener {

  private FxCConn connection;
  private EventBridge eventBridge;
  private Map<javafx.scene.input.KeyCode, Integer> downKeySym = new HashMap<>();

  public FxKeyboardInputListener(FxCConn conn, EventBridge bridge) {
    this.connection = conn;
    this.eventBridge = bridge;
  }

  public void onKeyPressed(KeyEvent e) {
    // Prevent the scene/ScrollPane from treating space as a focus traversal
    // or scrolling shortcut when the canvas is acting as a remote desktop.
    if (e.getCode() == javafx.scene.input.KeyCode.SPACE) {
      e.consume();
    }
    int keysym = eventBridge.keyCodeToKeysym(e.getCode());
    if (keysym == 0) {
      // Fallback to text representation for keys we don't map explicitly.
      String text = e.getText();
      if (text == null || text.isEmpty())
        return;
      keysym = text.charAt(0);
    }

    downKeySym.put(e.getCode(), keysym);

    if (connection != null) {
      try {
        connection.keyEvent(keysym, true);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    e.consume();
  }

  public void onKeyReleased(KeyEvent e) {
    Integer keysym = downKeySym.remove(e.getCode());
    if (keysym == null)
      return;

    if (connection != null) {
      try {
        connection.keyEvent(keysym, false);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    e.consume();
  }

  public void onKeyTyped(KeyEvent e) {
    // KeyPressed/KeyReleased handle most keys. Some platforms or focus
    // configurations only generate KEY_TYPED for certain characters such
    // as space, so we special-case that here.
    String ch = e.getCharacter();
    if (ch == null || ch.isEmpty()) return;

    char c = ch.charAt(0);
    if (c != ' ') return;

    int keysym = 0x20; // XK_space
    if (connection != null) {
      try {
        connection.keyEvent(keysym, true);
        connection.keyEvent(keysym, false);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    e.consume();
  }

  /**
   * Release all pressed keys - called when focus is lost
   */
  public void releaseAllKeys() {
    if (connection != null) {
      for (int keysym : downKeySym.values()) {
        try {
          connection.keyEvent(keysym, false);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    }
    downKeySym.clear();
  }
}
