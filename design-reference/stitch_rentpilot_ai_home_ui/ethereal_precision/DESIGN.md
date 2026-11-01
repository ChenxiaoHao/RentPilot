---
name: Ethereal Precision
colors:
  surface: '#faf8fe'
  surface-dim: '#dad9df'
  surface-bright: '#faf8fe'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f3f8'
  surface-container: '#eeedf3'
  surface-container-high: '#e9e7ed'
  surface-container-highest: '#e3e2e7'
  on-surface: '#1a1b1f'
  on-surface-variant: '#414755'
  inverse-surface: '#2f3034'
  inverse-on-surface: '#f1f0f5'
  outline: '#717786'
  outline-variant: '#c1c6d7'
  surface-tint: '#005bc1'
  primary: '#0058bc'
  on-primary: '#ffffff'
  primary-container: '#0070eb'
  on-primary-container: '#fefcff'
  inverse-primary: '#adc6ff'
  secondary: '#5f3add'
  on-secondary: '#ffffff'
  secondary-container: '#7857f8'
  on-secondary-container: '#fffbff'
  tertiary: '#006762'
  on-tertiary: '#ffffff'
  tertiary-container: '#00837c'
  on-tertiary-container: '#f3fffd'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a41'
  on-primary-fixed-variant: '#004493'
  secondary-fixed: '#e6deff'
  secondary-fixed-dim: '#cabeff'
  on-secondary-fixed: '#1c0062'
  on-secondary-fixed-variant: '#4918c8'
  tertiary-fixed: '#61f9ef'
  tertiary-fixed-dim: '#39dcd2'
  on-tertiary-fixed: '#00201e'
  on-tertiary-fixed-variant: '#00504c'
  background: '#faf8fe'
  on-background: '#1a1b1f'
  surface-variant: '#e3e2e7'
typography:
  display-hero:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-hero-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
    letterSpacing: 0.04em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1.25rem
  margin-desktop: 2.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system embodies the calculated serenity of high-end Apple-inspired hardware and interface design, specifically tuned for young, upwardly mobile urban professionals in Shanghai navigating high-stakes residential and lifestyle decisions. 

The aesthetic marries clinical minimalism with an ethereal digital intelligence. It rejects clutter, cognitive overload, and loud consumer real-estate tropes. Instead, it projects the quiet confidence of an executive concierge: spacious, rational, translucent, and hyper-considered.

### Visual Philosophy
- **Restrained Modernism:** Heavy reliance on negative space, pure off-white canvas surfaces, and strictly disciplined layout density.
- **Atmospheric Depth:** Visual interest is not derived from heavy lines or garish drop-shadows, but rather through ethereal ambient color auras, delicate border delineations, and fluid translucent backdrops.
- **Contextual Precision:** Tailored for dynamic urban life, balancing dense informational metrics (subway commutes, rent-to-income ratios, neighborhood walkability, air quality indexes) with pristine legibility and calming breathing room.

## Colors

The palette establishes an ultra-clean, luminous environment where color carries informational gravity and emotional tranquility.

### Primary Foundations
- **Canvas Base (`#F5F5F7`):** The non-emissive off-white base layer, mitigating ocular fatigue while elevating layered white cards.
- **Card Surface (`#FFFFFF`):** Pure pristine white dedicated to floating cards, active sheets, and conversational AI bubbles.
- **Ink Primary (`#1D1D1F`):** Deep volcanic black for authoritative headline hierarchy and active state iconography.
- **Ink Secondary (`#86868B`):** Muted stone gray providing effortless hierarchy for supporting metadata, microcopy, and inactive controls.

### Accent & Ambient Auras
- **System Accent (`#007AFF`):** The iconic electric azure representing actionable intelligence, verified data points, and confirmed selection states.
- **Aura Spectral Gradient (`#EBF3FF` to `#F3EEFF`):** A soft, low-contrast diffuse mesh deployed behind AI insight blocks, commute calculators, and highlight summaries to subtly telegraph algorithmic computation.
- **Tertiary Cyan (`#00C7BE`):** Reserved strictly for positive environmental indicators (e.g., optimal transit nodes, favorable sun exposure, low noise metrics).

## Typography

Typography delivers the crisp, systematic clarity of Apple’s human interface standards paired with the fluid geometry required for dual-script harmony (Latin and Simplified Chinese glyph matching via fallback to PingFang SC).

### Typographic Principles
- **Tracking Discipline:** Larger headline tiers demand tight, negative tracking (-0.015em to -0.03em) to feel mechanical and editorial. Micro-labels require expanded letter-spacing (+0.02em to +0.04em) for immediate recognition at scale.
- **Proportional Numerals:** All financial values (RMB figures, square-meter metrics, deposit amounts) utilize tabular figures to maintain horizontal alignment across split comparison grids.
- **Hierarchical Density:** Strict two-tier color styling: Primary `#1D1D1F` for all critical informational paths; `#86868B` for tertiary units, labels, and contextual footnotes.

## Layout & Spacing

The layout philosophy centers on a fluid, edge-to-edge structural hierarchy engineered for modern mobile screens, accounting specifically for Android translucent navigation bars and top status bar insets.

### Structural Framework
- **Mobile Foundation:** A 4-column fluid mobile grid anchored by `1.25rem` (20px) outer margins and `1rem` (16px) gutters. 
- **Desktop/Tablet Reflow:** Expands to an 8-column layout on tablet and a 12-column layout capped at 1200px max-width on desktop, transitioning margins to `2.5rem`.
- **System Bar Safeguards:** Bottom views account for dynamic Android gesture indicators via an enforced safe-padding offset of `4.5rem` (bottom nav height) + dynamic safe-inset bottom. Top app bars preserve a minimum of `3.25rem` under the status bar.

### Spacing Intent
- Micro gaps (`space-xs`, `space-sm`) structure atomic relationships within tags, icon-to-label binds, and metadata clusters.
- Core padding (`space-md`, `space-lg`) breathes spatial dignity into card surfaces and recommendation feeds.
- Macro spacing (`space-xl`) isolates algorithmic insight sections from standard rental inventories.

## Elevation & Depth

Elevation rejects heavy drop shadows in favor of ambient luminescence and glassmorphic depth.

### Layering Architecture
- **Base Canvas (Level 0):** Pure `#F5F5F7` background.
- **Surface Elevation (Level 1):** `#FFFFFF` cards resting on the base canvas. Visual separation is accomplished via a whisper-thin outline (`1px solid rgba(0, 0, 0, 0.04)`) and an ultra-diffuse ambient shadow: `box-shadow: 0 4px 24px -2px rgba(0, 0, 0, 0.03), 0 2px 6px -1px rgba(0, 0, 0, 0.02)`.
- **Intelligent Focus / AI Surface (Level 2):** Applied to AI suggestions, conversational sheets, and commute scoring cards. Utilizes a radial gradient wash (`linear-gradient(135deg, #EBF3FF 0%, #F3EEFF 100%)`) with an intensified soft plume shadow: `box-shadow: 0 12px 36px -6px rgba(124, 92, 252, 0.08)`.
- **Floating Overlays & Android Navigation (Level 3):** Glassmorphic surface leveraging `rgba(255, 255, 255, 0.82)` with `backdrop-filter: blur(20px) saturate(180%)` and a top borderline of `1px solid rgba(0, 0, 0, 0.06)`.

## Shapes

The shape system adopts smooth, continuous squircle geometry (characteristic of iOS and luxury industrial consumer electronics), with border radii scaled between 20px and 28px for primary containers.

### Curvature Geometry
- **Primary Content Cards & AI Insights:** Engineered with an explicit continuous corner radius of `1.5rem` (24px) up to `1.75rem` (28px).
- **Interactive Buttons & Form Fields:** Standardized at `1rem` (16px) for compact elements, or fully pill-shaped (`9999px`) for high-order primary triggers and quick-prompt AI suggestions.
- **Android Bottom Navigation Shell:** Contoured top edges with `1.75rem` (28px) border radius when floating detached, or crisp flush borders with full-radius internal action pills.

## Components

### Buttons
- **Primary Action:** Solid `#007AFF` fill with crisp white text (`#FFFFFF`), `font-weight: 600`, pill radius (`9999px`), min-height 52px. Active state incorporates micro-scale depression (`transform: scale(0.98)`).
- **AI Trigger Variant:** Background with a dynamic subtle gradient blend (`linear-gradient(135deg, #007AFF 0%, #7C5CFC 100%)`), emitting a soft ambient glow shadow (`0 8px 20px rgba(0, 122, 255, 0.25)`).
- **Secondary / Tertiary:** Frosted white or `#FFFFFF` container with `1px solid rgba(0, 0, 0, 0.06)`, text color `#1D1D1F`.

### Input Fields & Search Bars
- Background set to `#FFFFFF` (or `rgba(255, 255, 255, 0.8)`), 52px height, corner radius 20px. 
- Inset border `1px solid rgba(0, 0, 0, 0.06)`. Focus state transitions to `1px solid #007AFF` with a halo aura `0 0 0 4px rgba(0, 122, 255, 0.12)`.
- Integrated AI prompt icon at trailing edge with ethereal gradient accent.

### Cards & Property Modules
- Constructed on a pure white surface (`#FFFFFF`) with a 24px corner radius.
- Imagery utilizes an internal radius of 18px with inset padding of 8px around the frame, evoking high-end framed gallery cards.
- Integrated stat pill tags floating atop imagery (e.g., "12 min to Jing'an Temple", "Line 2/7 Hub") with `backdrop-filter: blur(12px)` and `rgba(29, 29, 31, 0.72)` background.

### Chips & Filter Pills
- Inactive: Borderless `#FFFFFF` surface or subtle `#EAEAEA` tint with `#1D1D1F` text, radius `9999px`, padding 8px 16px.
- Active: `#1D1D1F` fill with `#FFFFFF` text or `#EBF3FF` tint with `#007AFF` text and border `1px solid rgba(0, 122, 255, 0.2)`.

### Android Floating Navigation Bar
- Detached floating bar seated 16px above the Android navigation gesture indicator.
- Frosted glass construction: `rgba(255, 255, 255, 0.85)` surface, `backdrop-filter: blur(24px)`, corner radius 28px, subtle shadow `0 10px 30px rgba(0, 0, 0, 0.06)`.
- Active tabs feature a subtle illuminated blue dot below the icon.

### Contextual Decision Assistant & AI Insight Panels
- Distinguished from standard rental cards by a 24px curved container wrapped in a 1px border gradient (`linear-gradient(135deg, rgba(0, 122, 255, 0.2), rgba(124, 92, 252, 0.2))`).
- Backing is an ultra-soft gradient wash `#EBF3FF` into `#F3EEFF`.
- Highlights include dynamic commute radar maps, trade-off comparisons, and natural-language summaries.