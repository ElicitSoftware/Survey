# Healthcare Test Brand Typography

Professional healthcare typography system based on design guidelines and industry best practices.

## Typography Philosophy

Healthcare typography must prioritize readability, accessibility, and trust. Our font choices reflect professional excellence while maintaining warmth and approachability for patients and families.

## Font Hierarchy

### Primary Fonts

| Font Family | Use Case | Source |
|-------------|----------|--------|
| **Open Sans** | Primary sans-serif, body text, UI | [Google Fonts](https://fonts.google.com/specimen/Open+Sans) |
| **Montserrat** | Headlines, emphasis, branding | [Google Fonts](https://fonts.google.com/specimen/Montserrat) |
| **Roboto** | Secondary applications, data | [Google Fonts](https://fonts.google.com/specimen/Roboto) |
| **Source Code Pro** | Code, technical data | [Google Fonts](https://fonts.google.com/specimen/Source+Code+Pro) |

### Font Selection Rationale

#### Open Sans
- **Medical Clarity**: Excellent readability for patient information
- **Professional Trust**: Widely used in healthcare applications
- **Accessibility**: High legibility for all reading abilities
- **Multilingual**: Comprehensive character set support

#### Montserrat
- **Authority**: Strong presence for healthcare branding
- **Modern Professional**: Contemporary without being trendy
- **Versatility**: Works across digital and print media
- **Hierarchy**: Clear distinction for headers

## Typography Scale

| Element | Font Family | Size | Weight | Line Height | Color |
|---------|-------------|------|--------|-------------|-------|
| **Display** | Montserrat | 60px | Bold | 1.25 | Healthcare Blue |
| **H1** | Montserrat | 48px | Bold | 1.25 | Healthcare Blue |
| **H2** | Montserrat | 36px | Semibold | 1.25 | Healthcare Blue |
| **H3** | Montserrat | 30px | Semibold | 1.5 | Healthcare Blue |
| **H4** | Montserrat | 24px | Medium | 1.5 | Healthcare Navy |
| **H5** | Montserrat | 20px | Medium | 1.5 | Healthcare Navy |
| **H6** | Montserrat | 18px | Medium | 1.5 | Healthcare Navy |
| **Body Large** | Open Sans | 18px | Normal | 1.625 | Charcoal |
| **Body** | Open Sans | 16px | Normal | 1.625 | Charcoal |
| **Body Small** | Open Sans | 14px | Normal | 1.5 | Dark Gray |
| **Caption** | Open Sans | 12px | Normal | 1.5 | Dark Gray |

## Healthcare-Specific Typography

### Medical Text
```css
.healthcare-medical-text {
  font-family: 'Roboto', sans-serif;
  font-size: 16px;
  line-height: 2.0; /* Extra spacing for medical content */
  color: var(--healthcare-charcoal);
}
```

### Patient Information
```css
.healthcare-patient-info {
  font-family: 'Open Sans', sans-serif;
  font-size: 18px;
  font-weight: 500;
  color: var(--healthcare-blue);
}
```

### Disclaimers and Legal
```css
.healthcare-disclaimer {
  font-family: 'Open Sans', sans-serif;
  font-size: 12px;
  font-style: italic;
  color: var(--healthcare-dark-gray);
}
```

## Accessibility Standards

### WCAG Compliance
- **AA Level**: All text meets minimum contrast requirements
- **Large Text**: 18pt+ or 14pt+ bold at 3:1 contrast
- **Regular Text**: 16pt and below at 4.5:1 contrast
- **Focus Indicators**: Clear, consistent focus styling

### Readability Guidelines
- **Line Length**: Maximum 75 characters for optimal reading
- **Line Height**: Minimum 1.5x font size for body text
- **Letter Spacing**: Slightly increased for medical terminology
- **Paragraph Spacing**: Adequate white space between sections

### Screen Reader Support
- **Semantic HTML**: Proper heading hierarchy
- **Alt Text**: Descriptive text for all images
- **Skip Links**: Navigation shortcuts for assistive technology

## Implementation Guidelines

### CSS Variables
```css
--healthcare-font-primary: 'Open Sans', sans-serif;
--healthcare-font-heading: 'Montserrat', sans-serif;
--healthcare-font-secondary: 'Roboto', sans-serif;
--healthcare-font-mono: 'Source Code Pro', monospace;
```

### Typography Classes
- `.healthcare-display` - Large promotional text
- `.healthcare-heading-1` through `.healthcare-heading-6` - Hierarchical headers
- `.healthcare-body`, `.healthcare-body-large`, `.healthcare-body-small` - Content text
- `.healthcare-medical-text` - Medical content with enhanced readability
- `.healthcare-patient-info` - Important patient information
- `.healthcare-disclaimer` - Legal and disclaimer text

### Responsive Behavior

#### Mobile Optimization
- Headers scale down appropriately for small screens
- Body text maintains readability at 16px minimum
- Touch targets sized for accessibility (44px minimum)

#### Tablet Adjustments
- Moderate scaling maintains hierarchy
- Line lengths optimized for tablet reading patterns

## Usage Guidelines

### Do's
- Use Open Sans for all body text and patient information
- Apply Montserrat for headers and branding elements
- Maintain consistent line heights for readability
- Test with screen readers and accessibility tools
- Ensure sufficient contrast ratios

### Don'ts
- Don't use decorative fonts for medical information
- Don't reduce font sizes below accessibility minimums
- Don't use all caps for large blocks of text
- Don't sacrifice readability for design aesthetics
- Don't ignore responsive scaling needs

## Healthcare Context

### Patient-Focused Design
- **Clarity**: Medical information must be immediately understandable
- **Comfort**: Typography should reduce anxiety and stress
- **Authority**: Professional appearance builds trust
- **Accessibility**: Inclusive design for all patients

### Multi-generational Audience
- **Older Adults**: Larger text sizes, high contrast
- **Young Families**: Modern, approachable styling
- **Healthcare Professionals**: Efficient, scannable layouts
- **Diverse Communities**: Multilingual and cultural considerations

## Print Considerations

- **Medical Forms**: High contrast, clear field labels
- **Patient Education**: Large, readable text with good spacing
- **Reports**: Professional formatting with clear hierarchy
- **Signage**: Bold, accessible wayfinding typography

---

**Professional Healthcare Typography**  
*Clear Communication • Trusted Information • Accessible Design*