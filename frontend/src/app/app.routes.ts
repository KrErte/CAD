import { Routes } from '@angular/router';
import { AppComponent } from './app.component';
import { FactoryComponent } from './factory/factory.component';
import { RfqPublicComponent } from './factory/rfq-public.component';
import { AiStudioComponent } from './ai-studio/ai-studio.component';

/**
 * Route konfiguratsioon:
 *   /             → TehisAI CAD Home (olemasolev AppComponent)
 *   /factory      → PrintFlow MES admin moodul
 *   /ai-studio    → AI Superpowers — multi-agent council, generative loop, DFM
 *   /p/:slug      → Avaliku RFQ vormi leht (tehniline klient ilma loginita)
 */
export const APP_ROUTES: Routes = [
  { path: '', pathMatch: 'full', component: AppComponent },
  { path: 'factory', component: FactoryComponent },
  { path: 'ai-studio', component: AiStudioComponent },
  { path: 'p/:slug', component: RfqPublicComponent },
  { path: '**', redirectTo: '' },
];
