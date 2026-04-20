import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root shell — ainus komponent, mis mountitakse <app-root> külge.
 * Kogu päris sisu tuleb router-outletist (vt. app.routes.ts).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet></router-outlet>`,
})
export class RootComponent {}
