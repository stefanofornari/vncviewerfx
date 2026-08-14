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
import com.tigervnc.rfb.Point;
import javafx.scene.input.ScrollEvent;

/**
 * Handles mouse input events and sends them to VNC server.
 */
public class MouseInputListener {

    private final CConnFX connection;
    private final EventBridge eventBridge;
    private int lastX = 0;
    private int lastY = 0;
    private int lastButtonState = 0;

    public MouseInputListener(CConnFX conn, EventBridge bridge) {
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

    public void onMouseScroll(ScrollEvent e) {
        connection.writeWheelEvent(e);
    }
}
