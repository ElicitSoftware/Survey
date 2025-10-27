# Elicit Survey - Default Brand Directory

This directory contains the **default brand** for the Elicit Survey application. It uses Vaadin standard colors and follows modern web design principles.

> **Note**: This brand system was implemented as part of issue #67 to allow external branding while maintaining Vaadin design standards for the default experience.

## Architecture Overview

The brand system uses a three-tier architecture:

1. **Default Brand** (this directory): Embedded in Docker image, uses Vaadin standards
2. **External Brands**: Can be mounted via Docker volume to override the default
3. **Base Theme**: Application-specific styling that remains constant across brands

## Brand Hierarchy

```
🎨 Application Theme (starter-theme)
├── Brand Integration Layer (CSS variable contracts)
├── Application-specific styling (components, animations)
└── Vaadin Lumo base theme integration

🏷️ Default Brand (./brand/ - this directory)
├── Vaadin standard colors (blue primary)
├── Standard typography (system fonts)
├── Elicit favicon and assets
└── Minimal customizations

🔄 External Brands (./um-brand/, ./test-brand/, etc.)
├── Custom colors (UM maize, healthcare blue, etc.)
├── Custom typography and fonts
├── Custom favicons and assets
└── Brand-specific overrides with !important
```

## Quick Start

### Default Brand (No Mount)
```bash
# Uses this default brand with Vaadin colors
docker run -p 8080:8080 elicitsoftware/survey:latest
```

### External Brand Override
```bash
# Overrides with University of Michigan brand
docker run -v ./um-brand:/opt/brands/um-brand:ro -p 8080:8080 elicitsoftware/survey:latest

# Overrides with Healthcare test brand
docker run -v ./test-brand:/opt/brands/test-brand:ro -p 8080:8080 elicitsoftware/survey:latest
```

## Directory Structure

```
/brand/ (Default Brand)
├── colors/
│   ├── brand-colors.css      # Vaadin standard colors + variants
│   └── palette.json          # Color metadata
├── typography/
│   ├── brand-typography.css  # Font declarations (system fonts)
│   ├── typography.json       # Font metadata
│   └── *.woff, *.woff2       # Web font files
├── visual-assets/
│   └── icons/
│       ├── favicon.ico       # Default Elicit favicon (ICO format)
│       ├── favicon-32x32.png # 32x32 Elicit favicon (PNG format)
│       └── favicon.svg       # Default Elicit favicon (SVG format, preferred)
├── logos/
│   └── stacked.png           # Brand logo assets
├── brand-config.json         # Brand metadata
├── theme.css                 # Brand-specific customizations
└── README.md                 # This file
```

## Default Brand Colors (Vaadin Standards)

- **Primary**: `hsl(214, 90%, 52%)` - Vaadin Lumo Blue (#1676F3)
- **Error**: `hsl(3, 85%, 48%)` - Lumo Error Red  
- **Success**: `hsl(145, 72%, 30%)` - Lumo Success Green
- **Warning**: `hsl(43, 100%, 48%)` - Lumo Warning Yellow
- **Base**: `hsl(0, 0%, 100%)` - White
- **Typography**: System font stack (Roboto, Segoe UI, etc.)

## Creating External Brands

### 1. Directory Structure
```bash
mkdir my-brand
mkdir my-brand/colors
mkdir my-brand/typography  
mkdir my-brand/visual-assets/icons
```

### 2. Brand Colors
Create `my-brand/colors/brand-colors.css`:
```css
:root {
  /* Primary Brand Colors */
  --brand-primary: #YOUR_PRIMARY_COLOR;
  --brand-primary-contrast: #YOUR_CONTRAST_COLOR;
  --brand-base: #FFFFFF;
  
  /* Status Colors */
  --brand-error: #YOUR_ERROR_COLOR;
  --brand-success: #YOUR_SUCCESS_COLOR;
  --brand-warning: #YOUR_WARNING_COLOR;
  
  /* REQUIRED: Color Variants for Vaadin Component Integration */
  --brand-primary-50pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.5);
  --brand-primary-10pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.1);
  --brand-error-50pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.5);
  --brand-error-10pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.1);
  --brand-success-50pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.5);
  --brand-success-10pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.1);
  --brand-warning-50pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.5);
  --brand-warning-10pct: rgba(YOUR_R, YOUR_G, YOUR_B, 0.1);
  
  /* Typography */
  --brand-font-primary: "Your Font", system-ui, sans-serif;
}
```

### 3. Theme Integration
Create `my-brand/theme.css`:
```css
@import url('./colors/brand-colors.css');
@import url('./typography/brand-typography.css');

/* Vaadin Lumo Theme Integration with !important for external brands */
html {
  --lumo-primary-color: var(--brand-primary) !important;
  --lumo-primary-contrast-color: var(--brand-primary-contrast) !important;
  --lumo-primary-color-50pct: var(--brand-primary-50pct) !important;
  --lumo-primary-color-10pct: var(--brand-primary-10pct) !important;
  --lumo-error-color: var(--brand-error) !important;
  --lumo-error-color-50pct: var(--brand-error-50pct) !important;
  --lumo-error-color-10pct: var(--brand-error-10pct) !important;
  --lumo-success-color: var(--brand-success) !important;
  --lumo-success-color-50pct: var(--brand-success-50pct) !important;
  --lumo-success-color-10pct: var(--brand-success-10pct) !important;
  --lumo-warning-color: var(--brand-warning) !important;
  --lumo-warning-color-50pct: var(--brand-warning-50pct) !important;
  --lumo-warning-color-10pct: var(--brand-warning-10pct) !important;
}
```

### 4. Brand Metadata
Create `my-brand/brand-config.json`:
```json
{
  "name": "My Custom Brand",
  "version": "1.0.0", 
  "description": "Custom brand implementation",
  "organization": "My Organization"
}
```
## How It Works

### 1. File Serving (BrandStaticFileFilter)
Brand files are served via a servlet filter with three-tier fallback:
```
Request: /brand/colors/brand-colors.css
1. Check: /brand/colors/brand-colors.css (external mount)
2. Check: brand/colors/brand-colors.css (local/embedded)  
3. Return: 404 if not found in either location
```

### 2. CSS Loading Order
```
1. Vaadin Base Theme (Lumo)
2. Application Theme (starter-theme) - provides CSS variable contracts
3. Brand CSS (this directory or external mount) - populates variables
```

### 3. CSS Variable Contract
The application theme defines CSS variable contracts with fallbacks:
```css
/* Application theme provides this structure */
html {
  --lumo-primary-color: var(--brand-primary, hsl(214, 90%, 52%));
  --lumo-error-color: var(--brand-error, hsl(3, 85%, 48%));
  /* etc. */
}
```

Brand CSS files populate the `--brand-*` variables:
```css
/* Brand CSS provides the values */
:root {
  --brand-primary: #YOUR_COLOR;
  --brand-error: #YOUR_ERROR_COLOR;
  /* etc. */
}
```

### 4. Fallback Behavior
- **No brand directory**: Uses Vaadin standard colors from fallbacks
- **Default brand** (this directory): Uses Vaadin colors explicitly defined
- **External brand**: Overrides with custom colors using `!important`

## Development vs Production

### Development Mode
```bash
# Uses local ./brand directory (this one)
./mvnw quarkus:dev
```

### Docker Production
```bash
# Default brand (this directory copied into image)
docker run -p 8080:8080 survey:latest

# External brand override
docker run -v ./my-brand:/opt/brands/my-brand:ro -p 8080:8080 survey:latest
```

## Testing Different Brands

### Example 1: Default Brand (Vaadin Blue)
```bash
docker run -p 8080:8080 survey:latest
# Result: Blue buttons, Vaadin standard colors, Elicit favicon
```

### Example 2: University of Michigan Brand
```bash
docker run -v ./um-brand:/opt/brands/um-brand:ro -p 8081:8080 survey:latest
# Result: Maize yellow buttons, UM blue accents, UM favicon
```

### Example 3: Healthcare Test Brand
```bash
docker run -v ./test-brand:/opt/brands/test-brand:ro -p 8082:8080 survey:latest
# Result: Healthcare blue buttons, teal accents, healthcare favicon
```

### Example 4: Custom Brand
```bash
# Create custom brand
mkdir my-green-brand
echo ':root { --brand-primary: #2E8B57 !important; }' > my-green-brand/theme.css

# Test it
docker run -v ./my-green-brand:/opt/brands/my-green-brand:ro -p 8083:8080 survey:latest
# Result: Sea green buttons
```

## Brand Detection

The application automatically detects the active brand and displays it in the HTML:
```html
<!-- Default brand -->
<meta name="brand-info" content="Default Brand: Elicit Survey Application - Default Brand (v1.0.0)">

<!-- External brand -->
<meta name="brand-info" content="External Brand: University of Michigan - Michigan Medicine (v1.0.0)">
```

## File Requirements

### Required Files (for full brand)
- `colors/brand-colors.css` - Color definitions
- `theme.css` - Lumo integration and customizations
- `brand-config.json` - Brand metadata

### Optional Files
- `typography/brand-typography.css` - Custom fonts
- `visual-assets/icons/favicon.svg` - Custom favicon (SVG format, preferred)
- `visual-assets/icons/favicon.ico` - Custom favicon (ICO format, fallback)
- `visual-assets/icons/favicon-32x32.png` - Custom 32x32 favicon
- Font files (.woff2, .woff) - Custom web fonts
- `logos/` directory - Brand logo assets

### Minimal Brand Example
```css
/* Minimal my-brand/theme.css */
:root {
  --brand-primary: #FF6B35 !important;
  --brand-primary-contrast: #FFFFFF !important;
}
```

This is sufficient to change the primary color throughout the application.

## Troubleshooting

### Brand Colors Not Appearing
**Problem**: External brand is mounted but colors remain default blue.
**Solution**: Ensure opacity variants are defined:
```css
:root {
  --brand-primary: #FF6B35 !important;
  --brand-primary-50pct: rgba(255, 107, 53, 0.5) !important;
  --brand-primary-10pct: rgba(255, 107, 53, 0.1) !important;
}
```

### Favicon Not Updating
**Problem**: Browser shows old favicon after brand change.
**Solution**: 
1. Restart the Docker container
2. Clear browser cache (Cmd+Shift+R)
3. Try incognito/private browsing mode
4. Check that favicon exists in brand directory at `visual-assets/icons/favicon.svg`

### Brand Detection Shows "Default Brand"
**Problem**: Meta tag shows default brand instead of external brand.
**Solution**:
1. Restart Docker container after mounting brand volume
2. Verify volume mount path: `-v ./my-brand:/opt/brands/my-brand:ro`
3. Check that `brand-config.json` or `brand-info.json` exists

### Colors Work Partially
**Problem**: Primary button changes color but other elements remain default.
**Solution**: Define all required opacity variants (50pct and 10pct) for each color:
- `--brand-primary-50pct` and `--brand-primary-10pct`
- `--brand-error-50pct` and `--brand-error-10pct`
- `--brand-success-50pct` and `--brand-success-10pct`
- `--brand-warning-50pct` and `--brand-warning-10pct`