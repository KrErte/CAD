import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { RootComponent } from './app/root.component';
import { APP_ROUTES } from './app/app.routes';
import { authInterceptor } from './app/auth.interceptor';

// OAuth token pick-up ENNE Angular bootstrap'i — router wildcard redirect
// muudab hash'i enne kui AuthService konstruktor jõuab token'i lugeda.
const hashMatch = window.location.hash.match(/token=([^&]+)/);
if (hashMatch) {
  localStorage.setItem('aicad_token', decodeURIComponent(hashMatch[1]));
  history.replaceState(null, '', window.location.pathname + '#/');
}

bootstrapApplication(RootComponent, {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(APP_ROUTES, withHashLocation()),
  ]
}).catch(err => console.error(err));
