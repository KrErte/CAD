import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { RootComponent } from './app/root.component';
import { APP_ROUTES } from './app/app.routes';
import { authInterceptor } from './app/auth.interceptor';

/**
 * Bootstrap root — RootComponent on vaid <router-outlet>, tegelik sisu tuleb
 * APP_ROUTES-ist:
 *   /          → AppComponent (home)
 *   /factory   → FactoryComponent (PrintFlow MES)
 *   /p/:slug   → RfqPublicComponent (avalik RFQ vorm)
 *
 * Kasutame withHashLocation — ei vaja backendi HTML5 history-rewrite config'i.
 */
bootstrapApplication(RootComponent, {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(APP_ROUTES, withHashLocation()),
  ]
}).catch(err => console.error(err));
