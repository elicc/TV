# Lucide TV interface icons

The SVG files in `svg/` are the official Lucide `0.344.0` sources used by the
TV design prototype. They are pinned to upstream commit
`a0aa1326828256ec68ed0a5c8fb3706e67f553e4` and converted into native Android
VectorDrawables; the Android build never downloads icon assets at runtime.

Regenerate and verify the committed resources with:

```bash
python3 tools/lucide/generate_android_vectors.py
python3 tools/lucide/generate_android_vectors.py --check
```

See `LICENSE` for Lucide's ISC license.
