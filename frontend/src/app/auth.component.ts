import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  standalone: true,
  imports: [CommonModule],
  selector: 'app-auth',
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <h1 class="logo">TehisAI CAD</h1>
        <p class="subtitle">AI-powered 3D modeling</p>

        @if (auth.me()) {
          <div class="user-info">
            <p>Logged in as <strong>{{ auth.me()!.email }}</strong></p>
            <button class="btn btn-primary" (click)="goToApp()">Go to app</button>
          </div>
        } @else {
          <button class="btn btn-google" (click)="auth.loginWithGoogle()">
            <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">
              <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
              <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853"/>
              <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.997 8.997 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05"/>
              <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 6.29C4.672 4.163 6.656 2.58 9 3.58z" fill="#EA4335"/>
            </svg>
            Sign in with Google
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #0f172a;
      font-family: 'Inter', system-ui, sans-serif;
    }
    .auth-card {
      background: #1e293b;
      border-radius: 16px;
      padding: 48px;
      text-align: center;
      min-width: 360px;
      box-shadow: 0 25px 50px rgba(0,0,0,.4);
    }
    .logo {
      color: #f1f5f9;
      font-size: 28px;
      margin: 0 0 8px;
    }
    .subtitle {
      color: #94a3b8;
      margin: 0 0 32px;
      font-size: 14px;
    }
    .user-info {
      color: #cbd5e1;
    }
    .user-info strong {
      color: #f1f5f9;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      padding: 12px 24px;
      border: none;
      border-radius: 8px;
      font-size: 15px;
      cursor: pointer;
      font-weight: 500;
      transition: background .15s;
    }
    .btn-primary {
      background: #6366f1;
      color: white;
      margin-top: 16px;
    }
    .btn-primary:hover { background: #4f46e5; }
    .btn-google {
      background: #f1f5f9;
      color: #1e293b;
    }
    .btn-google:hover { background: #e2e8f0; }
  `]
})
export class AuthComponent implements OnInit {
  auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.auth.setToken(token);
      this.router.navigate(['/']);
    }
  }

  goToApp() {
    this.router.navigate(['/']);
  }
}
