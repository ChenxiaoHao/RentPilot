---
name: RentPilot Android
colors:
  surface: '#fcf9f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fcf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f2'
  surface-container: '#f0edec'
  surface-container-high: '#ebe7e7'
  surface-container-highest: '#e5e2e1'
  on-surface: '#1c1b1b'
  on-surface-variant: '#4c4546'
  inverse-surface: '#313030'
  inverse-on-surface: '#f3f0ef'
  outline: '#7e7576'
  outline-variant: '#cfc4c5'
  surface-tint: '#5e5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1b1b'
  on-primary-container: '#848484'
  inverse-primary: '#c6c6c6'
  secondary: '#5e5e5e'
  on-secondary: '#ffffff'
  secondary-container: '#e3e2e2'
  on-secondary-container: '#646464'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#1b1b1b'
  on-tertiary-container: '#848484'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#e3e2e2'
  secondary-fixed-dim: '#c7c6c6'
  on-secondary-fixed: '#1b1c1c'
  on-secondary-fixed-variant: '#464747'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c6'
  on-tertiary-fixed: '#1b1b1b'
  on-tertiary-fixed-variant: '#474747'
  background: '#fcf9f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.025em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 30px
    letterSpacing: -0.015em
  title-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: -0.005em
  body-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Manrope
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  gutter-lg: 1.5rem
  margin: 1.25rem
  margin-sm: 1rem
  margin-lg: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style

This design system expresses pure utilitarian luxury, drawing inspiration from high-end architectural editorialism and minimalist consumer hardware interfaces. Built specifically for an elevated Android property management and rental experience, it projects uncompromising precision, trust, and absolute clarity.

The target audience consists of discerning property owners, institutional asset managers, and high-value residents who require complex property management operations to feel seamless, weightless, and calm. The visual experience is devoid of decorative noise or synthetic novelty:
- **Atmospheric Restraint:** Utterly monochrome. Visual priority is dictated entirely through scale, weight, and luminous separation rather than hue.
- **Sculpted Geometry:** Deeply curved, continuous squircle cards paired with full-capsule floating interactive fixtures deliver a tactile, physical presence native to modern Android surfaces.
- **Generous Spatial Ratios:** Layouts favor deliberate breathing room and structural negative space, transforming density into effortless scanability.

## Colors

The palette is strictly achromatic. Color gradients, chromatic accents (blues, purples, greens), and saturation tints are strictly prohibited across all layers, tokens, and visual assets.

### Core Tokens
- **Background / Canvas:** `#FFFFFF` — The absolute foundation. Pure, radiant white canvas for all root application screens.
- **Primary Text & Emphases:** `#111111` — Deep ink black for titles, values, primary states, and critical data figures. Avoid pure `#000000` on raw text to preserve visual comfort.
- **Interactive Primary / Solid Action:** `#000000` — Absolute black dedicated exclusively to high-priority interactive components, primary button fills, and stark visual anchors. Text and glyphs atop this token are strictly `#FFFFFF`.
- **Secondary Text / Inactive States:** `#888888` — Neutral balanced mid-gray calibrated for metadata, auxiliary field descriptions, timestamps, and resting state icons.
- **Card & Container Fill:** `#F7F7F7` — Soft structural gray used to define architectural card planes without heavy visual obstruction.
- **Borders & Dividers:** `#E5E5E5` — Delicate hairline bounds providing crisp structural definition under direct light conditions.
- **Active State Underlay / Pill Wash:** `#EAEAEA` — Subtle neutral wash used inside floating capsules and segmented controls to designate current selection without color bias.

## Typography

The typography uses **Manrope** across all roles. Its geometric construction, structural modern legibility, and refined numeric glyphs match high-end asset tracking and financial reporting interfaces.

- **Weight Hierarchy:** Restricted to Regular (`400`), SemiBold (`600`), and Bold (`700`). Medium (`500`) is preserved strictly for micro-labels and auxiliary indicators.
- **Tracking & Kern:** Headlines and prominent figures enforce tight negative letter spacing (`-0.03em` to `-0.01em`) to create a condensed, deliberate editorial quality. Micro-labels and tabular labels employ slight positive tracking (`+0.01em` to `+0.04em`) to ensure legibility on Android OLED displays.
- **Numeric Figures:** Financial figures, occupancy rates, and square footage must always use Manrope's proportional lining figures with crisp vertical alignment.

## Layout & Spacing

The layout is built upon an 8pt base grid with a responsive Android structural model:
- **Mobile Handsets (< 600dp):** Fluid container, single primary column layout. Outer canvas margin is strictly `1.25rem` (20dp). Inter-card gap is `1rem` (16dp).
- **Large Foldables & Tablets (≥ 600dp):** Dual-pane or 12-column adaptive layout with `2rem` (32dp) canvas margin and `1.5rem` (24dp) gutter spacing.

Content surfaces must avoid dense grouping. Vertical spacing sections must leverage `space-xl` (36dp) between distinct workflow blocks to sustain an unhurried, luxury aesthetic. All interactive elements feature minimum 48x48dp target bounding boxes, even when visual capsule borders are smaller.

## Elevation & Depth

Visual plane separation relies strictly on **tonal contrast, hairline structural framing, and frosted backdrop occlusion**, completely rejecting conventional muddy drop shadows.

- **Surface Tiers:**
  - **Level 0 (Canvas):** Pure `#FFFFFF` background.
  - **Level 1 (Structural Cards & Sections):** `#F7F7F7` solid container fill bordered by a 1px solid hairline of `#E5E5E5`. Completely flat (0 elevation).
  - **Level 2 (In-Card Embedded Modules & Inputs):** `#FFFFFF` fill within a `#F7F7F7` parent card, framed with a 1px `#E5E5E5` hairline.
  - **Level 3 (Floating Overlays & Action Capsules):** Semi-translucent `#FFFFFF` at 85% opacity with real-time `24px` backdrop-blur (frosted glass) and a 1px hairline border in `#E5E5E5`.
- **Shadow Metric:** If native Android surfaces require depth casting for detached floating overlays (such as the bottom capsule navigation or contextual sheets), apply an ambient, ultra-diffused, uncolored occlusion: `box-shadow: 0 12px 32px rgba(0, 0, 0, 0.04), 0 2px 6px rgba(0, 0, 0, 0.02)`. Tinted, harsh, or high-opacity shadows are strictly prohibited.

## Shapes

The geometric signature is defined by continuous, ultra-smooth curvatures that oscillate between wide squircle surfaces and clean, friction-free capsules:

- **Large Surfaces & Content Cards:** Continuous smooth rounded corners using `rounded-xl` (24px to 28px). These large radii soften complex multi-tier rental data into approachable, elegant tablets.
- **Input Fields & Sub-containers:** Set to `rounded-lg` (16px) for interior elements or nested cards within a 24px master surface.
- **Interactive Targets, Floating Nav, & Badges:** Full-pill geometry (`rounded-full` / `9999px`). Buttons, floating status chips, and navigation bars must always terminate in pure semicircular arcs.

## Components

### Buttons
- **Primary Action:** Solid `#000000` background, pure `#FFFFFF` typography and iconography. Full-pill radius (`9999px`), 52px height for mobile ergonomics, horizontal padding `24px`. Flat, with no shadow. State changes trigger an instant opacity reduction to `0.85`.
- **Secondary Action:** Transparent background with 1px `#E5E5E5` border, `#111111` typography. Full-pill radius. Active state fills to `#F7F7F7`.
- **Tertiary / Ghost:** Pure `#111111` text, zero border, zero fill. State press displays an `#EAEAEA` oval pill wash behind text.

### Floating Bottom Capsule Navigation
- Suspended `20px` above the bottom screen safe area, centered horizontally with fixed margins.
- Height: 64px, full-pill geometry (`rounded-full`).
- Surface: `#FFFFFF` at 88% opacity paired with `20px` backdrop blur, wrapped in a 1px hairline `#E5E5E5` border and subtle ambient occlusion.
- Navigation items use clean, monochrome linework icons (`#888888`).
- **Active State:** The selected destination is framed by a dedicated `#EAEAEA` oval pill indicator behind a bold `#111111` icon. No colored dots or underlines.

### Chips & Filter Pills
- Full-pill geometry (`rounded-full`), height: 36px, horizontal padding `16px`.
- Unselected: Fill `#FFFFFF`, border 1px `#E5E5E5`, text `#888888`.
- Selected: Fill `#111111`, border 1px `#111111`, text `#FFFFFF`.
- In counter/metric chips, metadata is typeset in `label-sm` with tabular tracking.

### Content Cards
- Background `#F7F7F7`, corner radius `24px` (`rounded-xl`), 1px continuous hairline border `#E5E5E5`.
- Internal padding is generous: minimum `20px` to `24px`.
- Inner grouped metrics or preview modules utilize crisp `#FFFFFF` rectangular sub-cards with `16px` radius and identical `#E5E5E5` borders to establish clean visual nesting.

### Input Fields & Controls
- Height: 52px, corner radius `16px` (`rounded-lg`).
- Fill: `#FFFFFF`, 1px border `#E5E5E5`, placeholder text `#888888`, input value `#111111`.
- Focused state transitions border to `#111111` with no glow rings or colored outlines.
- Checkboxes and radios are strictly monochrome: unselected states use a 1.5px `#E5E5E5` outline with `#FFFFFF` fill; selected states use a solid `#000000` fill with a pure `#FFFFFF` checkmark or inner core.

### Lists & Dividers
- Lists sit flush within `#F7F7F7` cards or directly on the `#FFFFFF` canvas with generous 16px vertical padding per row.
- Separators must never run edge-to-edge; inset divider lines 16px from both borders using `#E5E5E5` at 0.5px height.