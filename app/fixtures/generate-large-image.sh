#!/usr/bin/env bash
# Generates a >10 MB JPEG fixture for testing the 413 PAYLOAD_TOO_LARGE path.
# Requires: dd, Python 3 (or ImageMagick's convert)
# Output: large-image-11mb.jpg (synthetic JPEG large enough to trigger the limit)
#
# Run once from the fixtures/ directory:
#   bash generate-large-image.sh

set -euo pipefail

OUTPUT="large-image-11mb.jpg"

# Create a synthetic JPEG by:
# 1. Creating a valid minimal JPEG header (SOI + APP0 + SOF0 + SOS + EOI)
# 2. Padding with random data to reach 11 MB
#
# Simplest cross-platform approach: use Python's PIL/Pillow if available,
# otherwise write a raw JPEG-like file padded to size.

if python3 -c "from PIL import Image" 2>/dev/null; then
  python3 -c "
from PIL import Image
import os
# Create a large white image that produces a >10 MB JPEG when uncompressed
# Use a very large canvas; quality=95 to keep file large
img = Image.new('RGB', (4096, 4096), color=(200, 200, 200))
img.save('$OUTPUT', 'JPEG', quality=95)
size = os.path.getsize('$OUTPUT')
print(f'Generated $OUTPUT: {size/1024/1024:.1f} MB')
"
else
  echo "PIL not available — generating raw padded file"
  # Write a fake JPEG: valid SOI marker + padding
  printf '\xff\xd8\xff\xe0' > "$OUTPUT"
  # Pad to 11 MB with zeros
  dd if=/dev/zero bs=1048576 count=11 >> "$OUTPUT" 2>/dev/null
  echo "Generated $OUTPUT ($(du -sh "$OUTPUT" | cut -f1))"
fi
