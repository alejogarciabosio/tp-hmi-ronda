#!/usr/bin/env bash
# Uso: armar-demo.sh <carpeta de cuadros> <docs/evidencias>
set -euo pipefail
frames="$1"; dest="$2"
mkdir -p "$dest/video" "$dest/capturas"
ffmpeg -y -loglevel error -framerate 6 -i "$frames/f_%05d.png" \
  -vf "scale=540:-2:flags=lanczos" -c:v libx264 -preset medium -crf 24 -pix_fmt yuv420p -movflags +faststart \
  "$dest/video/supervision-demo.mp4"
for f in "$frames"/key_*.png; do
  cp "$f" "$dest/capturas/supervision-$(basename "${f#*key_}")"
done
ffprobe -v error -show_entries format=duration,size -of default=nw=1 "$dest/video/supervision-demo.mp4"
