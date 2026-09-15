#!/usr/bin/env python3
"""Derive the in-app brand PNGs from the master ribbon artwork.

Source of truth is docs/logo/02-图层 1.png (1416x1537 RGBA). The master is already
alpha-clean: the gray "backdrop" pixels are RGBA ≈ (125, 125, 127, 0), so we can
extract the ribbon by simply compositing over a transparent canvas — no chroma
key, no flood-fill, no edge detection. We then upscale with Lanczos and center
the result on a square canvas with a fixed padding fraction.

The 24 dp home toolbar mark and the 26 dp toast mark both downsample the
previous 1024 px source roughly 20× to ~50 px on a 1080p TV panel, which
collapses the heavy anti-aliased edge band (the committed 1024 px mark is
32.7 % partial-alpha pixels) into a soft / jagged border. Re-rendering the
mark at 2048 px gives a 4× denser edge band so the small-screen silhouette
survives downsampling without losing its gradient and film-strip detail.

Two flavours are written from the master:

  * ic_logo_mark.png    — 2048×2048, ~10 % canvas padding (home toolbar,
                          toast, splash, mobile header, Glide error fallback).
  * ic_splash_mark.png  — 2048×2048, ~25 % canvas padding (splash mark only;
                          the extra breathing room keeps the mark centred in
                          the splash wordmark group).

The other brand PNGs (ribbon / aurora variants) and the TV launcher banner
PNGs are not derivable from the master — they carry their own art — so we
re-sample the committed PNGs to the target dimensions with Lanczos. That
preserves the exact visual but doubles or quadruples the linear resolution,
which is the only thing that helps the 24-26 dp surface sizes and the TV
launcher banner. Re-sampling rather than re-rendering guarantees the result
matches the existing brand decisions bit-for-bit aside from the resolution
bump; the --check mode below verifies that.

Usage:
    python3 tools/brand/make_logo_assets.py           # write every PNG
    python3 tools/brand/make_logo_assets.py --check   # verify committed bytes
"""

from __future__ import annotations

import argparse
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs" / "logo" / "02-图层 1.png"

NODPI = ROOT / "app/src/main/res/drawable-nodpi"
LEANBACK_DRAWABLE = ROOT / "app/src/leanback/res/drawable"

# Mark / splash mark are derived from the master.
MARK_SIZE = 2048
MARK_PADDING = 0.10  # fraction of the canvas on each side, ~10 % all around
SPLASH_MARK_PADDING = 0.25  # extra breathing room for the splash wordmark group

# Variants are re-sampled from the committed PNG so we don't reinterpret art.
VARIANT_SIZE = 2048
BANNER_W, BANNER_H = 1280, 720


def _load_master() -> Image.Image:
    if not MASTER.exists():
        raise SystemExit(f"error: master artwork missing: {MASTER}")
    return Image.open(MASTER).convert("RGBA")


def _ribbon_bbox(image: Image.Image, alpha_threshold: int = 16) -> tuple[int, int, int, int]:
    """Return the tight bbox of the ribbon (alpha ≥ threshold) inside ``image``."""
    alpha = image.split()[-1]
    bbox = alpha.point(lambda p: 255 if p >= alpha_threshold else 0).getbbox()
    if bbox is None:
        raise SystemExit("error: master has no opaque ribbon pixels")
    return bbox


def _center_on_canvas(
    ribbon: Image.Image, canvas_size: tuple[int, int], padding: float
) -> Image.Image:
    """Paste the tightly-cropped ``ribbon`` centred on a transparent canvas.

    ``padding`` is the fraction of the canvas kept clear on each side. The
    ribbon is uniformly scaled so that ``(1 - 2*padding) * canvas`` equals the
    longer side of the cropped ribbon, which keeps the proportions identical
    to the master regardless of the canvas size.
    """
    width, height = canvas_size
    target_long = max(int((1 - 2 * padding) * width), int((1 - 2 * padding) * height))
    scale = target_long / max(ribbon.size)
    new_size = (
        max(1, round(ribbon.size[0] * scale)),
        max(1, round(ribbon.size[1] * scale)),
    )
    ribbon = ribbon.resize(new_size, Image.LANCZOS)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    x = (width - new_size[0]) // 2
    y = (height - new_size[1]) // 2
    canvas.alpha_composite(ribbon, dest=(x, y))
    return canvas


def render_mark() -> Image.Image:
    master = _load_master()
    bbox = _ribbon_bbox(master)
    ribbon = master.crop(bbox)
    return _center_on_canvas(ribbon, (MARK_SIZE, MARK_SIZE), MARK_PADDING)


def render_splash_mark() -> Image.Image:
    master = _load_master()
    bbox = _ribbon_bbox(master)
    ribbon = master.crop(bbox)
    return _center_on_canvas(ribbon, (MARK_SIZE, MARK_SIZE), SPLASH_MARK_PADDING)


def render_resample(source: pathlib.Path, size: tuple[int, int]) -> Image.Image:
    """Re-sample ``source`` to ``size`` with Lanczos.

    If the on-disk PNG is already the target size, the loaded image is
    returned untouched so repeated runs of the script are idempotent — Lanczos
    is a low-pass filter and re-applying it to a same-size image drifts the
    pixels by a couple of shades each run, which would make ``--check`` fail
    even when nothing has changed.
    """
    if not source.exists():
        raise SystemExit(f"error: variant source missing: {source}")
    image = Image.open(source).convert("RGBA")
    if image.size != size:
        image = image.resize(size, Image.LANCZOS)
    return image


TARGETS: dict[pathlib.Path, Image.Image | pathlib.Path] = {}

# Targets filled in by the renderers so --check can iterate them.
def _build_targets() -> dict[pathlib.Path, Image.Image]:
    mark = render_mark()
    splash = render_splash_mark()
    targets: dict[pathlib.Path, Image.Image] = {
        NODPI / "ic_logo_mark.png": mark,
        NODPI / "ic_splash_mark.png": splash,
        NODPI / "ic_logo_ribbon.png": render_resample(
            NODPI / "ic_logo_ribbon.png", (VARIANT_SIZE, VARIANT_SIZE)
        ),
        NODPI / "ic_logo_aurora_glass.png": render_resample(
            NODPI / "ic_logo_aurora_glass.png", (VARIANT_SIZE, VARIANT_SIZE)
        ),
        NODPI / "ic_logo_aurora_minimal.png": render_resample(
            NODPI / "ic_logo_aurora_minimal.png", (VARIANT_SIZE, VARIANT_SIZE)
        ),
        NODPI / "ic_banner_image.png": render_resample(
            NODPI / "ic_banner_image.png", (BANNER_W, BANNER_H)
        ),
        NODPI / "ic_banner_bg.png": render_resample(
            NODPI / "ic_banner_bg.png", (BANNER_W, BANNER_H)
        ),
        LEANBACK_DRAWABLE / "ic_banner.png": render_resample(
            LEANBACK_DRAWABLE / "ic_banner.png", (BANNER_W, BANNER_H)
        ),
    }
    return targets


def _save(image: Image.Image, target: pathlib.Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, "PNG", optimize=True)
    print(f"wrote {target.relative_to(ROOT)} ({target.stat().st_size / 1024:.1f} KB)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero if any committed PNG differs from a fresh render",
    )
    args = parser.parse_args()

    targets = _build_targets()
    if args.check:
        stale = []
        for target, image in targets.items():
            if not target.exists():
                stale.append((target, "missing"))
                continue
            on_disk = Image.open(target).convert("RGBA")
            if on_disk.size != image.size:
                stale.append((target, f"size {on_disk.size} != {image.size}"))
                continue
            # For the mark + splash mark the on-disk bytes must equal the
            # freshly-rendered-from-master bytes; for the variants the script
            # is a no-op when on-disk already matches the target size, so the
            # byte equality falls out of idempotency.
            if on_disk.tobytes() != image.convert("RGBA").tobytes():
                stale.append((target, "pixel bytes differ"))
        if stale:
            for target, reason in stale:
                print(
                    f"error: {target.relative_to(ROOT)} is stale ({reason}); "
                    "re-run tools/brand/make_logo_assets.py",
                    file=sys.stderr,
                )
            return 1
        for target in targets:
            print(f"ok: {target.relative_to(ROOT)} is up to date")
        return 0

    for target, image in targets.items():
        _save(image, target)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())