---
name: Champagne Cinema Leanback
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1b1b1d'
  surface-container: '#201f21'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e5e1e4'
  on-surface-variant: '#d5c4b2'
  inverse-surface: '#e5e1e4'
  inverse-on-surface: '#303032'
  outline: '#9d8e7e'
  outline-variant: '#504538'
  surface-tint: '#f9bb68'
  primary: '#ffc67b'
  on-primary: '#462b00'
  primary-container: '#e5a958'
  on-primary-container: '#633e00'
  inverse-primary: '#835505'
  secondary: '#e7c179'
  on-secondary: '#402d00'
  secondary-container: '#5c4304'
  on-secondary-container: '#d4b06a'
  tertiary: '#e4cf8c'
  on-tertiary: '#3b2f00'
  tertiary-container: '#c8b373'
  on-tertiary-container: '#53450f'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffddb6'
  primary-fixed-dim: '#f9bb68'
  on-primary-fixed: '#2a1800'
  on-primary-fixed-variant: '#643f00'
  secondary-fixed: '#ffdea3'
  secondary-fixed-dim: '#e7c179'
  on-secondary-fixed: '#261900'
  on-secondary-fixed-variant: '#5c4304'
  tertiary-fixed: '#f8e19d'
  tertiary-fixed-dim: '#dbc583'
  on-tertiary-fixed: '#231b00'
  on-tertiary-fixed-variant: '#544510'
  background: '#131315'
  on-background: '#e5e1e4'
  surface-variant: '#353437'
typography:
  display-hero:
    fontFamily: Space Grotesk
    fontSize: 64px
    fontWeight: '700'
    lineHeight: 76px
    letterSpacing: -0.02em
  headline-xl:
    fontFamily: Space Grotesk
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 58px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 38px
    fontWeight: '600'
    lineHeight: 48px
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 30px
    fontWeight: '600'
    lineHeight: 38px
  title-lg:
    fontFamily: Work Sans
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
  title-md:
    fontFamily: Work Sans
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  body-lg:
    fontFamily: Work Sans
    fontSize: 22px
    fontWeight: '400'
    lineHeight: 32px
  body-md:
    fontFamily: Work Sans
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 28px
  label-lg:
    fontFamily: Space Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: 0.02em
  label-md:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  safe-overscan-x: 90px
  safe-overscan-y: 54px
  rail-gap: 32px
  card-gap-x: 24px
  card-gap-y: 28px
  space-xs: 6px
  space-sm: 12px
  space-md: 18px
  space-lg: 24px
  space-xl: 36px
  space-2xl: 48px
  space-3xl: 64px
---

## Brand & Style

The design system delivers an immersive, high-end 10-foot television viewing experience optimized for remote-control navigation at a 3-meter distance. Tailored specifically for living room cinematic setups, the aesthetic merges the restrained sobriety of a dark screening room with the refined warmth of vintage champagne and brushed brass accents. 

Key attributes:
- **Atmosphere:** Deep cinematic immersion, velvet-like charcoal backgrounds, and luminous amber-gold focus indicators that guide the eye without causing visual fatigue in low-light environments.
- **Interaction Style:** Distinct Leanback D-pad directional ergonomics. Focus states are non-negotiable, commanding, and instantaneous, delivering tactile feedback via subtle scale elevations, radiant 2.5dp focus borders, and warm champagne halos.
- **Aesthetic Movement:** Glassmorphic Dark Luxury with precise functional borders. Opaque charcoal container foundations layer beneath smoked glass panels, letting vibrant media backdrops peek through with controlled depth.

## Colors

The palette is tuned specifically for high-contrast visibility on OLED and LED television displays while avoiding pure `#000000` clipping and harsh blue-white glare.

### Foundation & Surfaces
- **Canvas Base (`#121214`):** Warm carbon black minimizing light bleed across large panels.
- **Surface Lower (`#18181C`):** Subdued under-layer for recessed rails and background shelves.
- **Surface Card / Container (`#1C1C20`):** Default neutral card fill, providing separation against the canvas.
- **Surface Elevated / Hover (`#26262C`):** Elevated cards, active drawers, and floating panels.
- **Glassmorphic Overlays:** `rgba(28, 28, 32, 0.72)` paired with `backdrop-filter: blur(24px)` for modal dialogs and translucent bottom sheets.

### Accent & Focus Hierarchy
- **Primary Accent (`#E5A958`):** Warm champagne gold used for badges, active tab markers, metadata chips, and primary play actions.
- **Focus Active / Highlight (`#FCD58B`):** Radiant champagne used for focused element borders, highlighted item titles, and active sliders.
- **Focus Bloom / Peak Glow (`#FFE8A3`):** High-luminosity champagne reserved for outer glow envelopes and micro-indicators on D-pad engagement.
- **Muted Border / Outlines:** `rgba(229, 169, 88, 0.16)` for resting boundaries; increases to solid `#FCD58B` with an outer `#FFE8A3` luminous halo on selection.

### Text & Contrast
- **Text Primary (`#F4F4F6`):** Off-white formulation delivering high legibility without harsh blooming.
- **Text Secondary (`#B8B8C0`):** Neutral gray-lavender ensuring WCAG AAA legibility against dark surfaces for metadata.
- **Text Inverse (`#121214`):** Used inside solid gold focus buttons or pill badges.

## Typography

Typography is explicitly engineered for a 1080p viewport observed from a 3-meter distance (10-foot experience). Sub-18px text is forbidden to prevent unreadable screen clutter.

- **Headline Font (`Space Grotesk`):** Clean geometric terminals with high distinction between glyphs, ensuring fast scanning of show titles, category headers, and playback banners.
- **Body & Metadata Font (`Work Sans`):** Balanced, open apertures with neutral grotesque curves, retaining maximum legibility across variable bitrate streams and complex plot summaries.
- **Minimum Threshold:** The absolute minimum body size is set at `20px` for minor auxiliary details (e.g., audio formats, stream codecs), while primary body copy rests at `22px`.
- **Text Wrapping & Truncation:** Paragraphs in description panes never exceed 3 lines before manual ellipsis truncation to prevent content pushing horizontal rails off-screen.

## Layout & Spacing

Designed rigidly around the canonical **1920x1080** Leanback raster (16:9 canvas):

- **Overscan TV Safe Margin:** 
  - Horizontal: `90px` (approx 5% inset)
  - Vertical: `54px` (approx 5% inset)
  - All interactive nodes, focused rails, and modal controls must strictly reside within this boundary to prevent display edge clipping on legacy panels.
- **Horizontal Category Rails:** 
  - Standard poster cards follow an aspect ratio of `2:3` (e.g., `240px x 360px`) or `16:9` landscape (e.g., `380px x 214px`).
  - Inter-card horizontal gap is fixed at `24px`.
  - Vertical spacing between distinct rails is fixed at `32px`.
- **Navigation Shelf:** Left-aligned persistent icon navigation shelf uses an inactive width of `96px`, expanding smoothly to `320px` overlay on remote D-pad Left engagement.
- **Scroll Orchestration:** Scrolling is strictly item-driven. The focused element remains pegged at an anchor position (typically `240px` from the left edge), while the entire rail scrolls beneath it.

## Elevation & Depth

Elevation in this television interface is conveyed through focused luminescence, subtle spatial expansion, and warm champagne atmospheric halos rather than generic cast shadows.

- **Resting Layer (Elevation 0):** Flat background `#121214` to surface `#1C1C20`. No shadows; separation is maintained by low-contrast outlines (`1px solid rgba(255, 255, 255, 0.06)`).
- **Glassmorphic Underlay (Elevation 1):** Translucent backing panels used behind detail overlays and modal player hubs: `background: rgba(24, 24, 28, 0.85); backdrop-filter: blur(28px); border: 1px solid rgba(229, 169, 88, 0.12);`.
- **Focused State (Elevation 2 - The 10-Foot Focus Highlight):**
  - **Scale Factor:** Focused cards scale up smoothly by `1.08x` using hardware-accelerated transforms (`transition: transform 180ms cubic-bezier(0.2, 0.0, 0.0, 1.0)`).
  - **Focus Ring:** A crisp, high-definition `2.5dp` (approx `3px` on 1080p) border in `#FCD58B`.
  - **Champagne Halo (Glow):** Dual-layer radial atmospheric blur:
    - Layer A: `0 0 0 2.5px #FCD58B`
    - Layer B: `0 8px 32px rgba(252, 213, 139, 0.35), 0 2px 8px rgba(229, 169, 88, 0.25)`
- **Z-Index Layering:** Focused items immediately snap to `z-index: 50` to prevent clipping behind adjacent neighboring cards.

## Shapes

The design maintains an organic, high-end cinema demeanor using consistent rounded geometry that prevents visual harshness on expansive displays:

- **Cards & Media Tiles:** Base radius of `16px` (`rounded-lg`). Provides clean silhouette clarity while scaling during focus.
- **Pill Badges & Quick Action Tags:** Full pill radius (`9999px` / `rounded-full`) for stream quality indicators (e.g., `4K`, `HDR10+`, `Dolby Atmos`), audio track badges, and category chips.
- **Focus Rings:** Follow the exact boundary curve of their parent element with an offset of `2px` or direct perimeter contact to ensure consistent border width around rounded corners.
- **Modal Dialogs & System Drawers:** Soft curvature of `24px` (`rounded-xl`) to anchor floating content within the viewing frame.

## Components

### Media Cards (Poster & Landscape)
- **Resting:** Opaque container `#1C1C20`, border `1px solid rgba(255, 255, 255, 0.06)`, radius `16px`. Title sits beneath the media poster in `title-md` (`#B8B8C0`).
- **Focused:** Scaled `1.08x`, border `2.5dp` solid `#FCD58B`, glow `0 8px 30px rgba(252, 213, 139, 0.32)`. The title color shifts to `#F4F4F6` with accent highlights shifting to `#FFE8A3`.

### Action Buttons
- **Primary / Focused:** Background `#E5A958` fill with `#121214` typography (`label-lg`, bold). Focus ring `#FFE8A3` (width `2.5dp`) with ambient champagne outer aura.
- **Secondary (Resting):** Translucent fill `rgba(255, 255, 255, 0.08)`, border `1px solid rgba(229, 169, 88, 0.2)`, text `#F4F4F6`.
- **Secondary (Focused):** Background shifts to `rgba(229, 169, 88, 0.24)`, border turns solid `#FCD58B`, text shifts to white.

### Metadata Chips & Quality Badges
- **Form Factor:** Pill shape (`rounded-full`), height `34px`, padding `0 14px`.
- **Styling:** Smoked surface `rgba(28, 28, 32, 0.9)`, border `1px solid rgba(229, 169, 88, 0.3)`, text in `label-md` `#E5A958`. Used consistently for runtime, release year, resolution (`4K UHD`), and rating badges.

### Horizontal Navigation Rails & Tabs
- **Tab Inactive:** Typography `title-lg`, color `#8C8C96`, padding `12px 24px`.
- **Tab Active:** Typography `title-lg` (bold), color `#F4F4F6`.
- **Tab Focused via D-pad:** Background `rgba(229, 169, 88, 0.15)`, border `2.5dp solid #FCD58B`, gold baseline indicator dot (diameter `6px`).

### Live Playback OSD (On-Screen Display)
- **Scrubber Bar:** Track height `6px`, background `rgba(255, 255, 255, 0.2)`. Progress fill `#E5A958`.
- **Focused Scrubber Thumb:** Enlarges from `12px` to `22px` diameter, solid `#FFE8A3` with glowing halo `0 0 16px rgba(252, 213, 139, 0.8)`. Timecode tooltips render at `label-lg` above the track.

### Selection Lists & Setting Rows
- **Height:** `64px` per item.
- **Focused Row:** Background `rgba(229, 169, 88, 0.12)`, left accent border `4px solid #FCD58B`, right checkmark or chevron highlighted in `#FFE8A3`.