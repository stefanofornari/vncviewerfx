# Instructions for Capturing Update Logs

## Current State

The code has been instrumented with comprehensive logging to capture framebuffer update sequences. However, the logging needs to be **explicitly started** to work.

## Quick Start (Recommended)

### 1. Rebuild the application

```bash
cd /home/ste/Projects/vncviewerfx
/opt/apache-maven-3.9.11/bin/mvn clean compile
```

### 2. Run with logging

The `VNCViewerDemo` has been modified to **auto-start logging** when the application starts. Simply run:

```bash
/opt/apache-maven-3.9.11/bin/mvn javafx:run
```

Logging will automatically start and save to `/tmp/vnc-update-logs/update_log_YYYYMMDD_HHMMSS.txt`

### 3. Reproduce the issue

Resize the window quickly, move windows around, or do whatever triggers the "lost updates" issue.

### 4. Close the application properly

Click the close button or press Alt+F4. **Do not kill the process** - this ensures the log file is properly flushed and closed.

### 5. Find the log file

```bash
ls -lt /tmp/vnc-update-logs/
```

The most recent file will be your log.

## Alternative: Manual Logging Control

If you want more control, you can start and stop logging manually from code:

```java
// Start logging
ste.vnc.viewer.UpdateLogger.startSession();

// ... do your operations ...

// Stop logging and flush to disk
ste.vnc.viewer.UpdateLogger.stopSession();
```

## What the Log Contains

Each line has the format:
```
[SEQUENCE] TIMESTAMP | EVENT_TYPE | details
```

Example events:
- `BEGIN_UPDATE` - Start of a VNC framebuffer update batch
- `MARK_DIRTY x=10 y=20 w=100 h=50` - A region was marked as dirty
- `DRAIN_DIRTY count=2 regions=[(10,20,100x50), (50,50,50x50)]` - Dirty regions were retrieved
- `DRAW_FRAMEBUFFER INCREMENTAL regions=2 fb=1024x768 canvas=1024x768` - Canvas was drawn incrementally
- `DRAW_FRAMEBUFFER FULL regions=0 fb=1024x768 canvas=1024x768` - Full redraw was performed
- `RESIZE 800x600` - Framebuffer or canvas was resized
- `FB_UPDATE_START update#1` - VNC server started sending an update
- `FB_UPDATE_END update#1` - VNC server finished sending an update

## What I Need From You

Once you've reproduced the issue and have a log file, please share it with me. I will:

1. Analyze the sequence of operations
2. Identify where dirty regions are being lost or not drawn
3. Create a deterministic unit test that reproduces the issue
4. Fix the bug

## Expected Patterns

**Normal operation:**
```
FB_UPDATE_START
BEGIN_UPDATE
MARK_DIRTY x=... (one or more)
FB_UPDATE_END
DRAIN_DIRTY count=N
DRAW_FRAMEBUFFER INCREMENTAL
```

**Problematic patterns to look for:**
- MARK_DIRTY without a corresponding DRAW_FRAMEBUFFER
- DRAIN_DIRTY returning empty when it shouldn't
- RESIZE followed by MARK_DIRTY but no DRAW_FRAMEBUFFER
- DRAW_FRAMEBUFFER FULL when you expected INCREMENTAL

## Troubleshooting

**No log file created?**
- Make sure you rebuilt with `mvn clean compile`
- Check that `/tmp/vnc-update-logs/` directory exists
- Verify the application printed "Update logging started: ..." on startup

**Log file is empty?**
- Make sure you closed the application properly (don't kill it)
- Check for exceptions in the console output

**Need more detail?**
- Let me know what additional information would help
- I can add more logging (pixel values, timing info, etc.)
