import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { RootComponent } from './app/root.component';
import { APP_ROUTES } from './app/app.routes';
import { authInterceptor } from './app/auth.interceptor';

// OAuth token pick-up: main.ts saves to localStorage as belt-and-suspenders
// fallback; the AuthComponent at /#/auth handles the primary flow.
const hashMatch = window.location.hash.match(/token=([^&]+)/);
if (hashMatch) {
  localStorage.setItem('aicad_token', decodeURIComponent(hashMatch[1]));
  // Don't clear the hash — let AuthComponent handle navigation
}

bootstrapApplication(RootComponent, {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(APP_ROUTES, withHashLocation()),
  ]
}).catch(err => console.error(err));
