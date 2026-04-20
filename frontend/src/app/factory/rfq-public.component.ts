import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

/**
 * Avaliku RFQ vormi komponent — klient täidab ise, ei ole login vaja.
 * Route: /p/:slug  →  saadab POST /api/printflow/rfq/public/:slug
 */
@Component({
  selector: 'app-rfq-public',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="rfq-page">
      <div class="card">
        <h1>Küsi hinnapakkumist</h1>
        <p class="lead">Täida vorm — võtame ühendust 24 tunni jooksul.</p>

        <div *ngIf="!submitted()" class="form">
          <label>Nimi <input [(ngModel)]="form.contactName" required /></label>
          <label>E-post <input type="email" [(ngModel)]="form.contactEmail" required /></label>
          <label>Telefon <input [(ngModel)]="form.contactPhone" /></label>
          <label>Kirjeldus <textarea rows="4" [(ngModel)]="form.description" placeholder="Mis on soovitav detail? Mõõtmed? Materjal?"></textarea></label>
          <label>Kogus <input type="number" min="1" [(ngModel)]="form.quantityHint" /></label>
          <label>Soovitud materjal <input [(ngModel)]="form.materialHint" placeholder="PLA, PETG, ABS..." /></label>
          <label>Tähtaeg <input type="date" [(ngModel)]="form.deadline" /></label>

          <button (click)="submit()" [disabled]="sending() || !form.contactEmail || !form.contactName">
            {{ sending() ? 'Saadame...' : 'Saada päring' }}
          </button>
          <p *ngIf="error()" class="error">{{ error() }}</p>
        </div>

        <div *ngIf="submitted()" class="success">
          <h2>✓ Aitäh!</h2>
          <p>{{ responseMessage() }}</p>
          <small>Päringu ID: <strong>#{{ responseId() }}</strong></small>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f8fafc; font-family:system-ui,sans-serif; }
    .rfq-page { max-width:640px; margin:0 auto; padding:40px 20px; }
    .card { background:white; padding:40px; border-radius:16px; box-shadow:0 4px 20px rgba(0,0,0,0.08); }
    h1 { margin:0 0 8px; font-size:32px; }
    .lead { color:#64748b; margin-bottom:32px; }
    .form { display:flex; flex-direction:column; gap:16px; }
    label { display:flex; flex-direction:column; gap:6px; font-size:13px; color:#475569; font-weight:500; }
    input, textarea {
      background:#f1f5f9; border:1px solid #e2e8f0; padding:10px 12px; border-radius:8px; font-size:15px;
    }
    input:focus, textarea:focus { outline:2px solid #6366f1; border-color:#6366f1; }
    button {
      background:#6366f1; color:white; border:0; padding:14px; border-radius:8px; font-size:16px; font-weight:600; cursor:pointer; margin-top:8px;
    }
    button:disabled { background:#cbd5e1; cursor:not-allowed; }
    .success { text-align:center; padding:40px 0; }
    .success h2 { color:#22c55e; margin:0 0 16px; }
    .error { color:#ef4444; margin-top:12px; }
  `]
})
export class RfqPublicComponent {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  form: any = { contactName: '', contactEmail: '', contactPhone: '', description: '', quantityHint: 1, materialHint: '', deadline: '' };
  sending = signal(false);
  submitted = signal(false);
  error = signal<string | null>(null);
  responseMessage = signal('');
  responseId = signal(0);

  submit() {
    const slug = this.route.snapshot.paramMap.get('slug') || 'default';
    this.sending.set(true);
    this.error.set(null);
    const body: any = {
      contactName: this.form.contactName,
      contactEmail: this.form.contactEmail,
      contactPhone: this.form.contactPhone,
      description: this.form.description,
      quantityHint: this.form.quantityHint,
      materialHint: this.form.materialHint,
      deadline: this.form.deadline ? new Date(this.form.deadline).toISOString() : null,
    };
    this.http.post<any>(`/api/printflow/rfq/public/${slug}`, body).subscribe({
      next: r => {
        this.sending.set(false);
        this.submitted.set(true);
        this.responseId.set(r.id);
        this.responseMessage.set(r.message_et || 'Täname! Oleme varsti ühenduses.');
      },
      error: err => {
        this.sending.set(false);
        this.error.set(err.error?.message || 'Viga saatmisel. Palun proovi uuesti.');
      }
    });
  }
}
