import { unsafeCSS, registerStyles } from '@vaadin/vaadin-themable-mixin/register-styles';

import vaadinTextFieldCss from 'themes/starter-theme/components/vaadin-text-field.css?inline';
import brandedHeaderCss from 'themes/starter-theme/components/branded-header.css?inline';


if (!document['_vaadintheme_starter-theme_componentCss']) {
  registerStyles(
        'vaadin-text-field',
        unsafeCSS(vaadinTextFieldCss.toString())
      );
      registerStyles(
        'branded-header',
        unsafeCSS(brandedHeaderCss.toString())
      );
      
  document['_vaadintheme_starter-theme_componentCss'] = true;
}

if (import.meta.hot) {
  import.meta.hot.accept((module) => {
    window.location.reload();
  });
}

