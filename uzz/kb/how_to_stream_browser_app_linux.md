Here is the complete, self-contained `instructions.md` file. It consolidates all the troubleshooting, environmental overrides, and automation steps we designed to bypass the global system browser and profile-locking issues.

You can save this directly to your system to easily reproduce the environment whenever needed.

---

# Isolated VNC Browser Sandbox Environment Instructions

This guide outlines how to configure a headless VNC instance on display slot `:5` (port `5905`) running an isolated instance of the system's default web browser (whether installed natively, via Snap, or via Flatpak) inside the Openbox window manager.

It completely bypasses Host/Wayland session conflicts, Xauthority locks, and browser profile collision issues (`"Firefox is already running..."`).

---

## 1. System Dependencies

Before starting, ensure the necessary X11 components, window manager, and clipboard tools are installed on your host system.

```bash
sudo apt update
sudo apt install tigervnc-standalone-server openbox autocutsel dbus-x11 xdg-utils

```

---

## 2. Configuration Setup

### Step A: The Core Environment Script (`~/.vnc/xstartup`)

This script sanitizes the display environment, hooks up the window manager and clipboard sync, isolates the browser user profile to a temporary space, and runs the default system handler in a decoupled D-Bus container.

Create or update your `~/.vnc/xstartup` file:

```bash
nano ~/.vnc/xstartup

```

Paste the following configuration:

```bash
#!/bin/sh
# 1. Clean out host display, Wayland, and session authority leakage
unset XDG_RUNTIME_DIR
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export DISPLAY=:5
export GDK_BACKEND=x11
unset WAYLAND_DISPLAY

# 2. Universal Profile Isolation Sandbox
# Re-routes the HOME directory to an isolated space in /tmp so ANY browser
# engine can spin up concurrently without touching or locking your display :0 profile.
VNC_HOME="/tmp/vnc_sandbox_user_$USER"
mkdir -p "$VNC_HOME"
export HOME="$VNC_HOME"

# 3. Start clipboard synchronization utilities
autocutsel -s CLIPBOARD -fork
autocutsel -s PRIMARY -fork

# 4. Start the lightweight Openbox window manager
openbox &

# 5. Launch the default system web browser via the generic XDG handler
exec dbus-run-session xdg-open "[http://www.google.com](http://www.google.com)"

```

Make the script executable:

```bash
chmod +x ~/.vnc/xstartup

```

### Step B: Clean Start of the VNC server

```bash
#!/bin/sh
# 1. Terminate any existing instances on display :5 to prevent lock conflicts
killall -9 Xtigervnc openbox openbox-session 2>/dev/null

# 2. Launch the raw X server in the background with access control disabled
Xtigervnc :5 -geometry 1280x720 -depth 24 -SecurityTypes None -ac &

# 3. Wait a brief second for the X server socket to initialize properly
sleep 1

# 4. Trigger the core startup application pipeline against the display
DISPLAY=:5 ~/.vnc/xstartup &

```
