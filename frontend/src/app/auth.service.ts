import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Me {
  id: number;
  email: string;
  name: string;
  plan: 'FREE' | 'PRO' | 'BUSINESS';
  used: number;
  limit: number; // -1 = unlimited
}

const KEY = 'aicad_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  me = signal<Me | null>(null);
  token = signal<string | null>(localStorage.getItem(KEY));

  constructor(private http: HttpClient) {
    // Pick up token from #/auth?token=... redirect
    const hash = window.location.hash;
    const m = hash.match(/token=([^&]+)/);
    if (m) {
      this.setToken(decodeURIComponent(m[1]));
      history.replaceState(null, '', window.location.pathname);
    }
    if (this.token()) this.refreshMe();
  }

  setToken(t: string) {
    localStorage.setItem(KEY, t);
    this.token.set(t);
    this.refreshMe();
  }

  refreshMe() {
    this.http.get<Me>('/api/me').subscribe({
      next: m => this.me.set(m),
      error: () => this.logout(),
    });
  }

  logout() {
    localStorage.removeItem(KEY);
    this.token.set(null);
    this.me.set(null);
  }

  loginWithGoogle() {
    window.location.href = '/oauth2/authorization/google';
  }
}
