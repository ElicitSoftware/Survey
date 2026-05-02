import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/app-layout/src/vaadin-app-layout.js';
import '@vaadin/side-nav/src/vaadin-side-nav.js';
import '@vaadin/side-nav/src/vaadin-side-nav-item.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import '@vaadin/scroller/src/vaadin-scroller.js';
import '@vaadin/app-layout/src/vaadin-drawer-toggle.js';
import '@vaadin/button/src/vaadin-button.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/src/vaadin-icon.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/combo-box/src/vaadin-combo-box.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'lit';
import 'Frontend/generated/jar-resources/comboBoxConnector.js';
import '@vaadin/component-base/src/debounce.js';
import '@vaadin/component-base/src/async.js';
import '@vaadin/combo-box/src/vaadin-combo-box-placeholder.js';
import '@vaadin/multi-select-combo-box/src/vaadin-multi-select-combo-box.js';
import '@vaadin/notification/src/vaadin-notification.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';
import 'react-router';
import 'react';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === '1dac43dce5c65eb12c3c4b31826d2f302459f39ba72e21a98d3705d823c2abf9') {
    pending.push(import('./chunks/chunk-7df48eae3185c3212d169067c1e45e4d4cbc51d3a83d0534fcc357a41dd584a9.js'));
  }
  if (key === '7a800cd2336c13fd70ba08b98acd80387c1d7f0583bacf4f715ae258f4868166') {
    pending.push(import('./chunks/chunk-bae99aece9a8abd31b2bbc10dd1d8dfe763292a523531c59a492af04802d1e18.js'));
  }
  if (key === '237f8eb1316aec71e065f67893b2d15fef72f37321c098d62eef8fe460a2d469') {
    pending.push(import('./chunks/chunk-8a99146d6396738b42ba644072799371e733ddcf0334b1fba3bb99cb07e12b1b.js'));
  }
  if (key === 'd925afc834672b8ee57c18949eb79a691aa917a2efdf273cacc6e1c5ca582b86') {
    pending.push(import('./chunks/chunk-8a99146d6396738b42ba644072799371e733ddcf0334b1fba3bb99cb07e12b1b.js'));
  }
  if (key === '44ae6655248a3d602a1c00ef8f9c64df979e16aafc4372fa0100ca15c38de6c6') {
    pending.push(import('./chunks/chunk-3684f15955a2ba0e3ef66368319c7c84b8a89c025538b1dc0cdc6752fb06e08a.js'));
  }
  if (key === '9c837bcc3f1cacdbc20dd97167de19965007951b39d8211c3f1a54f484a0fbfa') {
    pending.push(import('./chunks/chunk-bae99aece9a8abd31b2bbc10dd1d8dfe763292a523531c59a492af04802d1e18.js'));
  }
  return Promise.all(pending);
}

window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}