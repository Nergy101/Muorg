# Muorg Frontend Style Guide

Reference this file whenever building or reviewing frontend UI. The rules here override general intuition.

**The most important rule: every piece of UI must be readable and usable in all four themes.** Test or reason through dark, light, doom, and orkish before considering a UI change done.

---

## The Four Themes

Themes are set via `data-theme` attribute on `<html>`. The dark theme is the default (no attribute or `data-theme="dark"`). Overrides live in `src/style.css` as `[data-theme="…"]` selectors.

| Theme | Character | Background | Body text |
|-------|-----------|------------|-----------|
| **Dark** (default) | Dark stone/charcoal | `#1c1917` (stone-900) | `#e7e5e4` (stone-200) |
| **Light** | Off-white warm | `#fafaf9` | `#1c1917` |
| **Doom** | Near-black red + amber text | `#0d0202` | `#c4a035` (amber) |
| **Orkish** | Light sage green | `#e8f5e9` | `#1b5e20` (dark green) |

Doom is the riskiest theme for new UI — its text is amber, not white or black, and its backgrounds are near-black red. Orkish is a light green theme (similar contrast challenge to Light but with green tones).

---

## Primary / Accent Color

The Muorg accent is a military olive-green. **Never use Tailwind's `emerald-*` palette** — it looks similar but does not respect theme overrides and is not the brand color.

| Theme | Primary | Hover |
|-------|---------|-------|
| Dark | `#5b7c32` | `#6d8f3d` |
| Light | `#4d7c2c` | `#5b8f35` |
| Doom | `#8b0000` (dark red) | `#a52a2a` |
| Orkish | `#2e7d32` (medium green) | `#388e3c` |

When adding a new CSS class that uses the primary color, **always add all four theme variants** in `src/style.css`:

```css
.my-primary-class { color: #5b7c32; }
[data-theme="light"]  .my-primary-class { color: #4d7c2c; }
[data-theme="doom"]   .my-primary-class { color: #8b0000; }
[data-theme="orkish"] .my-primary-class { color: #2e7d32; }
```

---

## Neutral Colors (Stone Palette)

Always use the Tailwind **stone** palette for neutral UI. All four themes have CSS overrides for the used stone classes — if you stay within the stone palette, theming is automatic.

| Tailwind class | Dark | Light | Doom | Orkish |
|----------------|------|-------|------|--------|
| `bg-stone-900` | `#1c1917` | `#fafaf9` | `#0d0202` | `#e8f5e9` |
| `bg-stone-800` | `#292524` | `#f5f5f4` | `#1a0505` | `#dcedc8` |
| `bg-stone-700` | `#44403c` | `#e7e5e4` | `#2d0a0a` | `#c5e1a5` |
| `bg-stone-600` | `#57534e` | `#d6d3d1` | `#4a1515` | `#aed581` |
| `text-stone-200` | `#e7e5e4` | `#1c1917` | `#c4a035` | `#1b5e20` |
| `text-stone-300` | `#d6d3d1` | `#44403c` | `#b8860b` | `#2e7d32` |
| `text-stone-400` | `#a8a29e` | `#78716c` | `#8b6914` | `#33691e` |
| `text-stone-500` | `#78716c` | `#57534e` | `#6b5012` | `#558b2f` |
| `border-stone-600` | `#57534e` | `#d6d3d1` | `#4a1515` | `#c5e1a5` |
| `border-stone-700` | `#44403c` | `#e7e5e4` | `#2d0a0a` | `#dcedc8` |

**Do not use stone shades outside this table** (e.g. `stone-100`, `stone-950`) unless you verify they have theme overrides. Check `src/style.css` first.

---

## What NOT to Use

| Avoid | Why | Use instead |
|-------|-----|-------------|
| `emerald-*` | Not the brand color; no theme overrides | Primary CSS classes or `border-primary` |
| `green-*` | Same problem | Primary CSS classes |
| `blue-*`, `purple-*`, etc. | No theme overrides; will look wrong in Doom/Orkish | Stone palette |
| `amber-*` (except star ratings) | Overridden to green in Orkish, amber in Doom | Don't use for UI chrome |
| `red-*` (except destructive actions) | Overridden to green in Orkish | Use sparingly, verify in all themes |
| Hardcoded hex in Vue templates | Not theme-aware | CSS utility class with theme variants |
| `style="color: …"` or `style="background: …"` with brand colors | Bypasses theme system — only `.text-[#5b7c32]` has overrides for doom/orkish | CSS utility class |

**Amber is reserved for star ratings only** (`fill-amber-400 text-amber-400`). Note that in Doom amber becomes darker amber, in Orkish it becomes green — both are acceptable for stars.

---

## CSS Utility Classes

All classes are in `src/style.css`. Use these instead of ad-hoc Tailwind when semantic or theme-aware behaviour is needed.

### Layout

| Class | Use |
|-------|-----|
| `icon-btn` | **Always use for icon-only buttons.** Provides `inline-flex`, centered content, `shrink-0`, `rounded`. Add size + color via Tailwind. |

```html
<!-- Correct icon button -->
<button type="button" class="icon-btn h-7 w-7 text-stone-500 hover:bg-stone-600 hover:text-stone-200" aria-label="Close">
  <FeatherIcon name="x" class="h-4 w-4" />
</button>
```

Common sizes: `h-6 w-6` (small toolbar), `h-7 w-7` (modal header), `h-8 w-8` (prominent).

### Active / Selected State

| Class | Use |
|-------|-----|
| `settings-option-card--active` | Border + tinted bg for a selected option card. Add `shadow-inner` via Tailwind if desired. Theme-aware across all four themes. |
| `settings-option-badge` | The small "Active" pill inside a selected card. Add `rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide` via Tailwind. |

### Buttons

| Class | Use |
|-------|-----|
| `settings-action-btn` | Primary filled action button. Handles bg, hover, and all four themes. Add `rounded px-3 py-1.5 text-sm font-medium` via Tailwind. |
| `border-primary` | Primary-colored border. Theme-aware. |
| `shuffle-active-bg` | Primary background for toggle-active states. Theme-aware. |

### Notifications / Callouts

| Class | Use |
|-------|-----|
| `settings-update-notice` | Subtle primary-tinted border + bg for informational notice boxes. Theme-aware. |

### Scrollbars

| Class | Use |
|-------|-----|
| `os-theme-muorg` | OverlayScrollbars theme — applied automatically by `useOverlayScrollbars`. Do not add manually. |

---

## Adding New Theme-Aware Colors

If you need a color that's tied to the primary but not covered by an existing class:

1. Add the base rule with the dark-theme value.
2. Add all three override rules immediately below it in `src/style.css`.
3. Document it in this file.

```css
/* Example: new primary-tinted text */
.my-new-class { color: #5b7c32; }
[data-theme="light"]  .my-new-class { color: #4d7c2c; }
[data-theme="doom"]   .my-new-class { color: #8b0000; }
[data-theme="orkish"] .my-new-class { color: #2e7d32; }
```

Never add only the dark variant and call it done — Doom and Orkish users will see the wrong color.

---

## Tailwind Conventions

- **Stone for all neutrals.** See the table above.
- **Hover (dark panels):** `hover:bg-stone-600 hover:text-stone-200`
- **Hover (lighter panels):** `hover:bg-stone-700`
- **Disabled:** `disabled:cursor-not-allowed disabled:opacity-60`
- **Focus rings:** Handled globally in `style.css`. Don't add custom focus ring colors.
- **No hardcoded hex in templates.** Use CSS classes.

---

## Typography Scale

| Role | Classes |
|------|---------|
| Section divider label | `text-[11px] font-semibold uppercase tracking-wide text-stone-500` |
| Sub-heading | `text-xs font-semibold text-stone-400` |
| Body / form label | `text-xs font-medium text-stone-500` |
| Description / help text | `text-xs text-stone-500` |
| Fine print | `text-[11px] text-stone-400` |

These use the stone palette, so all four theme mappings apply automatically.

---

## Icons

Use `<FeatherIcon name="…" />` from `src/components/shared/FeatherIcon.vue`. Common sizes:

| Size | Class | Use |
|------|-------|-----|
| Tiny | `h-3 w-3` | Dense inline icons |
| Small | `h-3.5 w-3.5` | Toolbar, labels |
| Standard | `h-4 w-4` | Most UI |
| Large | `h-5 w-5` | Prominent actions |

---

## Theme Checklist for New UI

Before marking any UI task done, verify each of the following:

- [ ] **Dark** — looks correct on near-black stone background with white text
- [ ] **Light** — readable on off-white background with dark text (stone-200 becomes near-black)
- [ ] **Doom** — readable on near-black red bg with *amber* body text (not white/gray); primary is dark red
- [ ] **Orkish** — readable on light green bg with dark green text; primary is medium green
- [ ] No hardcoded hex colors in Vue templates
- [ ] No `emerald-*` or other non-stone Tailwind colors used for UI chrome
- [ ] Any new primary-color CSS class has all four `[data-theme]` variants
- [ ] Icon buttons use `icon-btn` class
- [ ] Active/selected states use `settings-option-card--active` + `settings-option-badge`
