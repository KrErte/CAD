import { Component, Input, OnChanges, SimpleChanges, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  PricingService,
  PricingQuote,
  PricingCompareResponse,
  PricingCompareRequest,
} from './pricing.service';

/**
 * Hinnavõrdlus-paneel. Sisend: STL-ist tuletatud metrics (volumeCm3, weightG,
 * bbox). Näitab 4 partnerit paralleelselt koos hinna, tarneaja, "Telli" nupuga.
 *
 * <p>Sorteeritud vaikimisi hinna järgi (odavaim ülal). Tagastatud OFFLINE-
 * providerid on peidetud. ERROR-status kuvab väikese warn-ikooniga, aga rida jääb.
 *
 * <p>Komponent EI tee POST-i automaatselt — kasutaja klikib "Võrdle" nupu, et
 * vältida tuhande päringu saatmist igal parameetri-muudatusel.
 */
@Component({
  selector: 'app-pricing-compare',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="pricing-panel">
      <header class="pricing-panel__header">
        <h3>Hinnavõrdlus</h3>
        <div class="pricing-panel__controls">
          <label>
            Materjal:
            <select [(ngModel)]="material" (change)="resetQuotes()">
              <option value="PLA">PLA</option>
              <option value="PETG">PETG</option>
              <option value="ABS">ABS</option>
              <option value="TPU">TPU</option>
              <option value="RESIN">Vaik (SLA)</option>
            </select>
          </label>
          <label>
            Täidis:
            <select [(ngModel)]="infill" (change)="resetQuotes()">
              <option [ngValue]="10">10 %</option>
              <option [ngValue]="20">20 %</option>
              <option [ngValue]="50">50 %</option>
              <option [ngValue]="100">100 %</option>
            </select>
          </label>
          <label>
            Kogus:
            <input type="number" min="1" max="100" [(ngModel)]="copies" (change)="resetQuotes()">
          </label>
          <button (click)="compare()" [disabled]="loading() || !canCompare()">
            {{ loading() ? 'Võrdlen…' : 'Võrdle hindu' }}
          </button>
        </div>
      </header>

      <div *ngIf="!canCompare()" class="pricing-panel__hint">
        Genereeri kõigepealt STL — hinnavõrdlus vajab ruumala ja massi.
      </div>

      <div *ngIf="loading()" class="pricing-panel__loading">
        <div *ngFor="let p of skeletonRows" class="pricing-row pricing-row--skeleton">
          <div class="pricing-row__skel"></div>
        </div>
      </div>

      <table *ngIf="!loading() && visibleQuotes().length > 0" class="pricing-table">
        <thead>
          <tr>
            <th>Partner</th>
            <th>Hind</th>
            <th>Tarne</th>
            <th>Staatus</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let q of visibleQuotes()"
              [class.pricing-row--cheapest]="q.providerId === response()?.cheapestProviderId"
              [class.pricing-row--fastest]="q.providerId === response()?.fastestProviderId"
              [class.pricing-row--error]="q.status === 'ERROR'">
            <td>
              <strong>{{ q.providerDisplayName }}</strong>
              <span *ngIf="q.providerId === response()?.cheapestProviderId" class="badge badge--cheapest">Odavaim</span>
              <span *ngIf="q.providerId === response()?.fastestProviderId" class="badge badge--fastest">Kiireim</span>
            </td>
            <td>
              <ng-container *ngIf="q.priceEur != null">
                <strong>{{ q.priceEur | number:'1.2-2' }} €</strong>
                <small *ngIf="q.status === 'FALLBACK'" class="price-fallback">~hinnang</small>
              </ng-container>
              <span *ngIf="q.priceEur == null">—</span>
            </td>
            <td>
              <ng-container *ngIf="q.deliveryDaysMin != null">
                {{ q.deliveryDaysMin }}–{{ q.deliveryDaysMax }} päeva
              </ng-container>
              <span *ngIf="q.deliveryDaysMin == null">—</span>
            </td>
            <td>
              <span class="status status--{{ q.status.toLowerCase() }}">
                {{ statusLabel(q.status) }}
              </span>
              <small *ngIf="q.errorMessage">{{ q.errorMessage }}</small>
            </td>
            <td>
              <a *ngIf="q.orderUrl && q.status !== 'ERROR'"
                 [href]="q.orderUrl" target="_blank" rel="noopener"
                 class="order-btn">Telli →</a>
            </td>
          </tr>
        </tbody>
      </table>

      <footer *ngIf="response()" class="pricing-panel__footer">
        Vastus {{ response()!.totalMillis }} ms
        <span *ngIf="hiddenCount() > 0">· {{ hiddenCount() }} peidetud (offline)</span>
      </footer>
    </section>
  `,
  styles: [`
    .pricing-panel { background: #fff; border: 1px solid #e3e3e3; border-radius: 8px;
      padding: 16px; margin-top: 16px; font-family: system-ui, sans-serif; }
    .pricing-panel__header { display: flex; justify-content: space-between;
      align-items: flex-start; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; }
    .pricing-panel__header h3 { margin: 0; font-size: 16px; }
    .pricing-panel__controls { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .pricing-panel__controls label { font-size: 13px; display: flex; align-items: center; gap: 4px; }
    .pricing-panel__controls select, .pricing-panel__controls input { padding: 4px 8px; }
    .pricing-panel__controls button { padding: 6px 14px; background: #2563eb; color: #fff;
      border: 0; border-radius: 4px; cursor: pointer; font-weight: 500; }
    .pricing-panel__controls button:disabled { opacity: 0.5; cursor: not-allowed; }
    .pricing-panel__hint { color: #6b7280; font-style: italic; padding: 12px 0; }
    .pricing-panel__loading { display: flex; flex-direction: column; gap: 6px; }
    .pricing-row--skeleton { height: 36px; background: linear-gradient(90deg,
      #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%); background-size: 200% 100%;
      animation: skel 1.2s infinite; border-radius: 4px; }
    @keyframes skel { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
    .pricing-table { width: 100%; border-collapse: collapse; font-size: 14px; }
    .pricing-table th, .pricing-table td { text-align: left; padding: 8px;
      border-bottom: 1px solid #f3f4f6; }
    .pricing-table th { font-size: 12px; color: #6b7280; text-transform: uppercase; }
    .pricing-row--cheapest { background: rgba(34, 197, 94, 0.08); }
    .pricing-row--error td { opacity: 0.6; }
    .badge { font-size: 10px; padding: 2px 6px; border-radius: 4px; margin-left: 6px;
      text-transform: uppercase; font-weight: 600; }
    .badge--cheapest { background: #dcfce7; color: #166534; }
    .badge--fastest  { background: #dbeafe; color: #1e40af; }
    .price-fallback { color: #6b7280; font-size: 11px; margin-left: 4px; }
    .status { font-size: 12px; padding: 2px 8px; border-radius: 999px; }
    .status--ok { background: #dcfce7; color: #166534; }
    .status--fallback { background: #fef3c7; color: #92400e; }
    .status--error { background: #fee2e2; color: #991b1b; }
    .status--offline { background: #f3f4f6; color: #6b7280; }
    .order-btn { background: #2563eb; color: #fff; padding: 6px 12px; border-radius: 4px;
      text-decoration: none; font-size: 13px; font-weight: 500; }
    .order-btn:hover { background: #1d4ed8; }
    .pricing-panel__footer { margin-top: 12px; font-size: 12px; color: #6b7280; }
  `],
})
export class PricingCompareComponent implements OnChanges {
  private api = inject(PricingService);

  /** STL-i metrikud sisendina — kutsuv komponent peab need varustama. */
  @Input() volumeCm3?: number;
  @Input() weightG?: number;
  @Input() bbox?: { x: number; y: number; z: number };

  material = 'PLA';
  infill = 20;
  copies = 1;

  response = signal<PricingCompareResponse | null>(null);
  loading = signal(false);

  skeletonRows = Array(4).fill(0);

  visibleQuotes = computed(() =>
    (this.response()?.quotes ?? [])
      .filter(q => q.status !== 'OFFLINE')
      .sort((a, b) => this.sortKey(a) - this.sortKey(b)),
  );

  hiddenCount = computed(() =>
    (this.response()?.quotes ?? []).filter(q => q.status === 'OFFLINE').length,
  );

  ngOnChanges(changes: SimpleChanges): void {
    // Iga kord kui STL regenereeritakse (uus volumeCm3) — invalidaadime vanad
    // hinnad, et kasutaja ei klikiks "Telli" aegunud hinnaga.
    if (changes['volumeCm3'] || changes['weightG']) {
      this.resetQuotes();
    }
  }

  canCompare(): boolean {
    return !!this.volumeCm3 && !!this.weightG && !!this.bbox;
  }

  compare(): void {
    if (!this.canCompare()) return;
    this.loading.set(true);
    const req: PricingCompareRequest = {
      volumeCm3: this.volumeCm3!,
      weightG: this.weightG!,
      bbox: this.bbox!,
      material: this.material,
      infillPercent: this.infill,
      copies: this.copies,
      countryCode: 'EE',
    };
    this.api.compare(req).subscribe({
      next: res => {
        this.response.set(res);
        this.loading.set(false);
      },
      error: err => {
        console.error('pricing compare failed', err);
        this.loading.set(false);
      },
    });
  }

  resetQuotes(): void {
    this.response.set(null);
  }

  statusLabel(s: PricingQuote['status']): string {
    return {
      OK: 'OK',
      FALLBACK: 'Hinnang',
      ERROR: 'Viga',
      OFFLINE: 'Pole saadaval',
    }[s];
  }

  /** Sorteerimiskriteerium: hinna järgi kasvavalt, error ja fallback-id lõppu. */
  private sortKey(q: PricingQuote): number {
    if (q.status === 'ERROR') return 1e9;
    if (q.priceEur == null) return 1e9 - 1;
    // Eelistame OK-sid fallback'idele sama hinna juures
    const statusBonus = q.status === 'OK' ? 0 : 0.001;
    return q.priceEur + statusBonus;
  }
}
