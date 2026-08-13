#!/usr/bin/env bash

# Default variables
URL=""
DRY_RUN=false

# Usage / Help message
show_help() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS] <URL>

Detects the user's default browser and launches it in kiosk mode.

Arguments:
  <URL>          The web address to open (REQUIRED).

Options:
  --dry-run      Print the command that would be executed without running it.
  -h, --help     Display this help message and exit.

Examples:
  $(basename "$0") https://github.com
  $(basename "$0") --dry-run https://news.ycombinator.com
EOF
}

# 1. Parse command-line options and positional arguments
POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        -*)
            echo "Error: Unknown option '$1'" >&2
            echo "" >&2
            show_help >&2
            exit 1
            ;;
        *)
            POSITIONAL_ARGS+=("$1")
            shift
            ;;
    esac
done

# Restore positional arguments
set -- "${POSITIONAL_ARGS[@]}"

# Validate that a URL was provided
if [[ $# -eq 0 ]]; then
    echo "Error: Missing mandatory argument <URL>." >&2
    echo "" >&2
    show_help >&2
    exit 1
fi

URL="$1"

# 2. Detect default browser desktop entry
DEFAULT_DESKTOP=$(xdg-settings get default-web-browser 2>/dev/null || xdg-mime query default x-scheme-handler/https 2>/dev/null)

if [[ -z "$DEFAULT_DESKTOP" ]]; then
    echo "Error: Could not detect default web browser (XDG settings/mime query returned empty)." >&2
    exit 1
fi

# 3. Locate .desktop file across standard XDG locations
DESKTOP_PATH=""

# Build search list including user home and system XDG data paths
XDG_DATA_DIRS="${XDG_DATA_HOME:-$HOME/.local/share}:${XDG_DATA_DIRS:-/usr/local/share:/usr/share}"

IFS=':' read -ra DIRS <<< "$XDG_DATA_DIRS"
for dir in "${DIRS[@]}"; do
    if [[ -f "$dir/applications/$DEFAULT_DESKTOP" ]]; then
        DESKTOP_PATH="$dir/applications/$DEFAULT_DESKTOP"
        break
    fi
done

if [[ -z "$DESKTOP_PATH" ]]; then
    DESKTOP_PATH=$(which "$DEFAULT_DESKTOP" 2>/dev/null)
fi

# 4. Extract command or handle Flatpak
EXEC_CMD=""
IS_FLATPAK=false
FLATPAK_ID=""

if [[ -n "$DESKTOP_PATH" && -f "$DESKTOP_PATH" ]]; then
    # Check if this is a Flatpak desktop entry
    if grep -q '^X-Flatpak=' "$DESKTOP_PATH" 2>/dev/null; then
        IS_FLATPAK=true
        # Extract Flatpak App ID (e.g., com.google.Chrome)
        FLATPAK_ID=$(grep -m1 '^X-Flatpak=' "$DESKTOP_PATH" | cut -d'=' -f2)
        EXEC_CMD="/usr/bin/flatpak"
    else
        # Standard desktop entry: get Exec line and remove XDG field codes (%u, %f, @@, etc.)
        EXEC_LINE=$(grep -m1 '^Exec=' "$DESKTOP_PATH" 2>/dev/null | cut -d'=' -f2-)
        # Strip field codes (%u, %U, %f, %F, @@u, @@) and trim
        EXEC_CMD=$(echo "$EXEC_LINE" | sed -E 's/@@u?|%[fFuUnNiImM]//g' | awk '{print $1}')
    fi
fi

# Emergency parser: If .desktop file was unreadable/missing
if [[ -z "$EXEC_CMD" ]]; then
    if [[ "$DEFAULT_DESKTOP" =~ [Ff]irefox ]]; then
        EXEC_CMD="firefox"
    elif [[ "$DEFAULT_DESKTOP" =~ [Cc]hrome ]]; then
        EXEC_CMD="google-chrome"
    elif [[ "$DEFAULT_DESKTOP" =~ [Cc]hromium ]]; then
        EXEC_CMD="chromium"
    fi
fi

if [[ -z "$EXEC_CMD" ]]; then
    echo "Error: Found default desktop entry '$DEFAULT_DESKTOP', but could not extract an executable binary." >&2
    exit 1
fi

# 5. Construct final command array based on browser engine
CMD=("$EXEC_CMD")

# Insert 'run <APP_ID>' arguments if running under Flatpak
if [ "$IS_FLATPAK" = true ]; then
    CMD+=("run" "$FLATPAK_ID")
fi

case "$DEFAULT_DESKTOP" in
    *chrome*|*chromium*|*brave*|*edge*|*vivaldi*)
        # Uses native --app mode for frameless window
        CMD+=("--app=$URL" "--start-maximized")
        ;;
    *firefox*|*zen*|*waterfox*)
        # Uses isolated new instance in kiosk mode
        CMD+=("--new-instance" "--kiosk" "$URL")
        ;;
    *)
        CMD+=("--kiosk" "$URL")
        ;;
esac

# 6. Handle Dry-Run or Execution
if [ "$DRY_RUN" = true ]; then
    echo "[DRY-RUN] Command to execute:"
    printf '%q ' "${CMD[@]}"
    echo ""
else
    # Launch process asynchronously and detach
    "${CMD[@]}" &
    disown
fi