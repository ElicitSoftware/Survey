# Brand Directory

This directory contains externalized branding assets for runtime customization of the Elicit Survey application.

## Quick Start

### Docker Volume Mount
```bash
docker run -v ./custom-brand:/brand -p 8080:8080 elicitsoftware/survey:latest
```

### Docker Compose
```yaml
services:
  survey:
    image: elicitsoftware/survey:latest
    volumes:
      - ./custom-brand:/brand
    ports:
      - "8080:8080"
```

## Directory Structure

```
/brand/
├── colors/
│   ├── brand-colors.css      # CSS custom properties for colors
│   └── palette.json          # Color metadata
├── logos/
│   └── stacked.png           # Example logo file
├── typography/
│   ├── brand-typography.css  # Font declarations and styles
│   ├── typography.json       # Font metadata
│   ├── *.woff2               # Font files (WOFF2 format)
│   └── *.woff                # Font files (WOFF format)
└── visual-assets/
    └── icons/
        ├── favicon.ico       # Standard favicon
        └── favicon-32x32.png # 32x32 PNG favicon
```

## Customization

### Colors
Edit `colors/brand-colors.css` to override default colors:
```css
:root {
  --brand-primary: #YOUR_COLOR;
  --brand-primary-contrast: #YOUR_CONTRAST_COLOR;
}
```

### Fonts
1. Replace font files in `typography/`
2. Update `typography/brand-typography.css` with new @font-face declarations
3. Modify `--brand-font-primary` CSS variable

### Logos & Icons
Replace image files with your own assets, keeping the same filenames.

## Fallback Behavior

The application works in three modes:
1. **External Brand**: Uses `/brand/` mount when available
2. **Default**: Uses embedded theme when no external branding

This ensures the application always works, regardless of brand customization.

## Brand Colors

- **Primary**: Michigan Blue (#00284D)
- **Contrast**: Maize Yellow (#FFD733)  
- **Error**: Error Red (#993324)
- **Success**: Success Green (#75998E)
- **Interactive**: Interactive Blue (#4DAFFF)

## Testing

To test the brand customization:

1. **Create a custom brand directory**:
   ```bash
   mkdir custom-brand
   mkdir custom-brand/colors
   ```

2. **Create custom colors**:
   ```bash
   cat > custom-brand/colors/brand-colors.css << 'EOF'
   :root {
     --brand-primary: #2E8B57; /* Sea Green */
     --brand-primary-contrast: #FFE4B5; /* Moccasin */
   }
   EOF
   ```

3. **Test with Docker**:
   ```bash
   # Default branding
   docker run -p 8080:8080 elicitsoftware/survey:latest
   
   # Custom branding  
   docker run -v ./custom-brand:/brand -p 8081:8080 elicitsoftware/survey:latest
   ```

## Configuration

See `brand-config.json` for complete configuration reference.