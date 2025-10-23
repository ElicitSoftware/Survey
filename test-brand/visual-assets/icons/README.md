# Healthcare Test Brand Icons

Healthcare-focused icon system supporting the overall brand identity.

## Icon System Overview

Icons are designed to complement the Healthcare brand while providing clear, recognizable symbols for healthcare applications.

### Icon Categories

#### Core Brand Icons

| Icon | File | Usage | Description |
|------|------|-------|-------------|
| **Favicon** | `favicon.svg` | Browser tabs, bookmarks | Simplified cross in brand colors |
| **Healthcare Heart** | `healthcare-heart.svg` | Caring, patient focus | Heart with medical cross overlay |
| **Medical Cross** | `medical-cross.svg` | Medical services | Professional healthcare symbol |
| **Stethoscope** | `stethoscope.svg` | Medical care | Traditional medical instrument |

### Design Principles

#### Healthcare Symbolism
- **Cross**: Universal healthcare symbol
- **Heart**: Care, compassion, life
- **Stethoscope**: Medical expertise, examination
- **Circular Forms**: Wholeness, unity, healing

#### Visual Consistency
- **Color Palette**: Healthcare Blue (#1B365D) and Teal (#4A9B8E)
- **Style**: Clean, professional, immediately recognizable
- **Weight**: Consistent stroke weights and proportions
- **Scale**: Optimized for various sizes and applications

## Technical Specifications

### SVG Format Benefits
- **Scalable**: Crisp at any size
- **Small File Size**: Efficient loading
- **Customizable**: Colors can be modified via CSS
- **Accessible**: Screen reader compatible with proper markup

### Size Guidelines

#### Favicon Usage
- **16x16px**: Browser tabs (minimum)
- **32x32px**: High-DPI displays
- **48x48px**: Desktop shortcuts
- **180x180px**: Apple touch icon

#### Interface Icons
- **16px**: Small UI elements, inline text
- **24px**: Standard interface icons
- **32px**: Larger interface elements
- **48px**: Feature icons, cards

#### Display Icons
- **64px**: Section headers, feature highlights
- **96px**: Hero sections, main features
- **128px**: Large display applications

### Color Variations

#### Primary (Default)
- **Healthcare Blue**: #1B365D for primary elements
- **Healthcare Teal**: #4A9B8E for highlights and accents
- **White**: #FFFFFF for inner details and contrast

#### Single Color
- **Blue Only**: For blue-themed sections
- **Teal Only**: For wellness-focused content
- **White**: For dark backgrounds
- **Black**: For printing and high contrast needs

## Usage Guidelines

### Healthcare Applications

#### Patient-Facing Materials
- Use softer, more approachable icons (heart, caring symbols)
- Larger sizes for accessibility
- Clear, simple designs for easy recognition

#### Medical Professional Interfaces
- More clinical icons (cross, stethoscope)
- Smaller sizes for efficient layouts
- Professional, authoritative styling

#### Emergency/Urgent Care
- High contrast versions
- Immediately recognizable symbols
- Clear visibility in stress situations

### Digital Applications

#### Website Icons
```html
<!-- Standard implementation -->
<img src="medical-cross.svg" alt="Medical Services" width="24" height="24">

<!-- CSS implementation -->
.icon-medical {
  background-image: url('medical-cross.svg');
  width: 24px;
  height: 24px;
}
```

#### Mobile App Icons
- Ensure clarity at small sizes
- Test on various screen densities
- Maintain brand recognition in app stores

### Accessibility Standards

#### Color Contrast
- Icons meet WCAG 2.1 AA contrast requirements
- Alternative versions for high contrast needs
- Clear distinction from background colors

#### Screen Reader Support
```html
<!-- Decorative icon -->
<img src="healthcare-heart.svg" alt="" role="presentation">

<!-- Functional icon -->
<img src="medical-cross.svg" alt="Medical Services">

<!-- Icon with text -->
<span>
  <img src="stethoscope.svg" alt="">
  Medical Consultation
</span>
```

#### Touch Accessibility
- Minimum 44px touch targets for interactive icons
- Adequate spacing between clickable icons
- Clear focus indicators for keyboard navigation

## Implementation Best Practices

### Performance Optimization
- **Inline SVG**: For icons used multiple times
- **External SVG**: For occasionally used icons
- **Icon Fonts**: Not recommended (accessibility issues)
- **Sprite Sheets**: For collections of related icons

### CSS Integration
```css
.icon {
  display: inline-block;
  width: 1.5em;
  height: 1.5em;
  vertical-align: middle;
  fill: currentColor;
}

.icon-healthcare {
  fill: var(--healthcare-teal);
}

.icon-medical {
  fill: var(--healthcare-blue);
}
```

### Responsive Scaling
```css
/* Base size */
.icon { width: 24px; height: 24px; }

/* Tablet */
@media (max-width: 768px) {
  .icon { width: 20px; height: 20px; }
}

/* Mobile */
@media (max-width: 480px) {
  .icon { width: 18px; height: 18px; }
}
```

## Healthcare Context Considerations

### Patient Experience
- **Clarity**: Icons should immediately convey meaning
- **Comfort**: Avoid clinical or intimidating symbols when possible
- **Universal**: Cross-cultural recognition important
- **Trust**: Professional appearance builds confidence

### Medical Professional Use
- **Efficiency**: Quick recognition in fast-paced environments
- **Accuracy**: Precise meaning for medical context
- **Standards**: Alignment with healthcare industry conventions
- **Integration**: Works with medical software and systems

### Legal and Compliance

#### Medical Symbol Usage
- Respect trademark and copyright for medical symbols
- Follow healthcare industry standards and regulations
- Ensure appropriate use of medical cross symbol
- Comply with accessibility regulations (ADA, Section 508)

#### Brand Compliance
- Use only approved icon variations
- Maintain consistent brand application
- Follow healthcare brand guidelines
- Obtain approval for new icon development

---

**Healthcare Icon System**  
*Clear Communication • Professional Trust • Accessible Design*