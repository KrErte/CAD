import { Routes } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthComponent } from './auth.component';
import { FactoryComponent } from './factory/factory.component';
import { RfqPublicComponent } from './factory/rfq-public.component';

/**
 * Route konfiguratsioon:
 *   /             → TehisAI CAD Home (olemasolev AppComponent)
 *   /factory      → PrintFlow MES admin moodul
 *   /p/:slug      → Avaliku RFQ vormi leht (tehniline klient ilma loginita)
 */
export const APP_ROUTES: Routes = [
  { path: '', pathMatch: 'full', component: AppComponent },
  { path: 'factory', component: FactoryComponent },
  { path: 'p/:slug', component: RfqPublicComponent },
  { path: 'auth', component: AuthComponent },
  { path: '**', redirectTo: '' },
];
