import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FactoryService, Kpi, Material, Printer, PrintJob, Customer, Spool, Quote } from './factory.service';

type Tab = 'dashboard' | 'quotes' | 'printers' | 'jobs' | 'materials' | 'spools' | 'customers' | 'rfq';

@Component({
  selector: 'app-factory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="factory">
      <aside class="sidebar">
        <h1 class="brand">PrintFlow<small>MES</small></h1>
        <nav>
          <button (click)="tab.set('dashboard')" [class.active]="tab()==='dashboard'">
            <span class="icon">📊</span> Juhtpaneel
          </button>
          <button (click)="tab.set('quotes')" [class.active]="tab()==='quotes'">
            <span class="icon">💶</span> Pakkumised
          </button>
          <button (click)="tab.set('rfq')" [class.active]="tab()==='rfq'">
            <span class="icon">📥</span> RFQ postkast
          </button>
          <button (click)="tab.set('printers')" [class.active]="tab()==='printers'">
            <span class="icon">🖨️</span> Printerid
          </button>
          <button (click)="tab.set('jobs')" [class.active]="tab()==='jobs'">
            <span class="icon">⚙️</span> Tööjärjekord
          </button>
          <button (click)="tab.set('materials')" [class.active]="tab()==='materials'">
            <span class="icon">🧪</span> Materjalid
          </button>
          <button (click)="tab.set('spools')" [class.active]="tab()==='spools'">
            <span class="icon">🧵</span> Filamendi-spoolid
          </button>
          <button (click)="tab.set('customers')" [class.active]="tab()==='customers'">
            <span class="icon">👥</span> Kliendid
          </button>
        </nav>
        <div class="status" *ngIf="kpi()">
          <div class="pulse" [class.live]="streamConnected()"></div>
          {{ streamConnected() ? 'Otseülekanne aktiivne' : 'Ühendatakse...' }}
        </div>
      </aside>

      <main class="content">
        <!-- DASHBOARD -->
        <section *ngIf="tab()==='dashboard'">
          <h2>Juhtpaneel</h2>
          <div *ngIf="!kpi()" class="loading">Laen...</div>
          <div *ngIf="kpi() as k" class="kpi-grid">
            <div class="kpi-card primary">
              <div class="label">Tulu (aktseptitud pakkumised)</div>
              <div class="value">{{ k.revenue_eur | number:'1.2-2' }} €</div>
            </div>
            <div class="kpi-card">
              <div class="label">Printerite OEE</div>
              <div class="value">{{ k.oee_pct }}%</div>
              <div class="sub">{{ k.printers_printing }}/{{ k.printers_total }} prindib</div>
            </div>
            <div class="kpi-card">
              <div class="label">Success rate</div>
              <div class="value" [class.warn]="k.success_rate_pct < 85">{{ k.success_rate_pct }}%</div>
              <div class="sub">{{ k.jobs_done_total }} valmis / {{ k.jobs_failed_total }} failed</div>
            </div>
            <div class="kpi-card">
              <div class="label">Töös / järjekorras</div>
              <div class="value">{{ k.jobs_printing }} / {{ k.jobs_queued }}</div>
            </div>
            <div class="kpi-card">
              <div class="label">Pakkumisi mustandid / aktsept</div>
              <div class="value">{{ k.quotes_draft }} / {{ k.quotes_accepted }}</div>
              <div class="sub">kokku {{ k.quotes_total }}</div>
            </div>
          </div>

          <h3 style="margin-top:32px">Top materjalid (90 päeva)</h3>
          <table class="tbl" *ngIf="topMat().length">
            <thead><tr><th>Materjal</th><th>Perekond</th><th>Töid</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of topMat()">
                <td>{{ m.name || 'ID ' + m.material_id }}</td>
                <td>{{ m.family }}</td>
                <td>{{ m.job_count }}</td>
              </tr>
            </tbody>
          </table>
          <p *ngIf="!topMat().length" class="hint">Pole veel piisavalt töid. Alusta pakkumisega →</p>
        </section>

        <!-- QUOTES -->
        <section *ngIf="tab()==='quotes'">
          <div class="row-head">
            <h2>Pakkumised</h2>
            <button class="btn primary" (click)="showQuoteForm.set(!showQuoteForm())">
              {{ showQuoteForm() ? 'Peida' : '+ Uus pakkumine' }}
            </button>
          </div>

          <div *ngIf="showQuoteForm()" class="card">
            <h3>Uus pakkumine (upload STL/STEP)</h3>
            <div class="form-grid">
              <label>Klient
                <select [(ngModel)]="newQuote.customerId">
                  <option [value]="null">— puudub —</option>
                  <option *ngFor="let c of customers()" [value]="c.id">{{ c.name }}</option>
                </select>
              </label>
              <label>Materjal
                <select [(ngModel)]="newQuote.materialId">
                  <option *ngFor="let m of materials()" [value]="m.id">{{ m.family }} {{ m.name }} ({{ m.price_per_kg_eur }}€/kg)</option>
                </select>
              </label>
              <label>Kogus
                <input type="number" min="1" [(ngModel)]="newQuote.quantity" />
              </label>
              <label>Kiirusaste
                <select [(ngModel)]="newQuote.rush">
                  <option [ngValue]="false">Tavaline</option>
                  <option [ngValue]="true">Rush (+30%)</option>
                </select>
              </label>
              <label>Marginaal %
                <input type="number" step="1" [(ngModel)]="newQuote.marginPct" />
              </label>
              <label class="full">STL/STEP/OBJ/3MF fail
                <input type="file" (change)="onFile($event)" accept=".stl,.step,.stp,.obj,.3mf" />
              </label>
              <label class="full">Märkused
                <textarea rows="2" [(ngModel)]="newQuote.notes"></textarea>
              </label>
            </div>
            <button class="btn primary" [disabled]="!newQuote.file || submitting()" (click)="submitQuote()">
              {{ submitting() ? 'Analüüsin + hinnastan...' : 'Loo pakkumine' }}
            </button>
            <p *ngIf="quoteError()" class="error">{{ quoteError() }}</p>
          </div>

          <table class="tbl">
            <thead>
              <tr>
                <th>Kood</th><th>Staatus</th><th>Kokku</th><th>Loodud</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let q of quotes()" [class.accepted]="q.status==='ACCEPTED'">
                <td>{{ q.code }}</td>
                <td><span class="pill" [class]="'pill-' + q.status">{{ q.status }}</span></td>
                <td>{{ q.total_eur | number:'1.2-2' }} €</td>
                <td>{{ q.created_at | date:'short' }}</td>
                <td>
                  <button *ngIf="q.status==='DRAFT' || q.status==='SENT'" class="btn sm" (click)="accept(q.id)">Aktsepti</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- RFQ -->
        <section *ngIf="tab()==='rfq'">
          <h2>RFQ postkast (klientide päringud)</h2>
          <table class="tbl">
            <thead><tr><th>Kontakt</th><th>Kirjeldus</th><th>Tähtaeg</th><th>Staatus</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let r of rfqs()">
                <td>{{ r.contact_name }}<br><small>{{ r.contact_email }}</small></td>
                <td>{{ r.description }}</td>
                <td>{{ r.deadline | date:'shortDate' }}</td>
                <td><span class="pill">{{ r.status }}</span></td>
                <td>
                  <select [ngModel]="r.status" (ngModelChange)="updateRfqStatus(r.id, $event)">
                    <option>NEW</option>
                    <option>IN_REVIEW</option>
                    <option>QUOTED</option>
                    <option>LOST</option>
                    <option>CLOSED</option>
                  </select>
                </td>
              </tr>
              <tr *ngIf="!rfqs().length"><td colspan="5" class="hint">RFQ postkast on tühi.</td></tr>
            </tbody>
          </table>
        </section>

        <!-- PRINTERS -->
        <section *ngIf="tab()==='printers'">
          <div class="row-head">
            <h2>Printerifarm</h2>
            <button class="btn primary" (click)="showPrinterForm.set(!showPrinterForm())">
              {{ showPrinterForm() ? 'Peida' : '+ Lisa printer' }}
            </button>
          </div>
          <div *ngIf="showPrinterForm()" class="card">
            <div class="form-grid">
              <label>Nimi<input [(ngModel)]="newPrinter.name" placeholder="Bambu X1C #1" /></label>
              <label>Vendor<input [(ngModel)]="newPrinter.vendor" placeholder="Bambu Lab" /></label>
              <label>Mudel<input [(ngModel)]="newPrinter.model" placeholder="X1C" /></label>
              <label>Adapter tüüp
                <select [(ngModel)]="newPrinter.adapter_type">
                  <option value="MOCK">MOCK (simulatsioon)</option>
                  <option value="BAMBU">Bambu</option>
                  <option value="MOONRAKER">Moonraker/Klipper</option>
                  <option value="OCTOPRINT">OctoPrint</option>
                  <option value="PRUSA_CONNECT">Prusa Connect</option>
                </select>
              </label>
              <label>Toetatud materjalid (CSV)
                <input [(ngModel)]="newPrinter.supported_materials" placeholder="PLA,PETG,PLA-CF" />
              </label>
            </div>
            <button class="btn primary" (click)="savePrinter()">Salvesta</button>
          </div>

          <div class="printer-grid">
            <div *ngFor="let p of printers()" class="printer-card" [class]="'status-' + p.status">
              <div class="head">
                <strong>{{ p.name }}</strong>
                <span class="pill" [class]="'pill-' + p.status">{{ p.status }}</span>
              </div>
              <small>{{ p.vendor }} {{ p.model }} · {{ p.adapter_type }}</small>
              <div class="progress" *ngIf="p.status==='PRINTING'">
                <div class="bar" [style.width.%]="p.current_progress_pct || 0"></div>
                <span>{{ p.current_progress_pct || 0 }}%</span>
              </div>
              <div class="actions">
                <button *ngIf="p.status==='PRINTING'" class="btn sm" (click)="pausePrinter(p.id)">Paus</button>
                <button *ngIf="p.status==='PAUSED'" class="btn sm" (click)="resumePrinter(p.id)">Jätka</button>
                <button *ngIf="p.status==='PRINTING' || p.status==='PAUSED'" class="btn sm danger" (click)="cancelPrinterJob(p.id)">Tühista</button>
              </div>
              <small *ngIf="p.last_heartbeat_at" class="hint">Viimane puls: {{ p.last_heartbeat_at | date:'short' }}</small>
            </div>
            <div *ngIf="!printers().length" class="hint">Pole veel printereid. Lisa esimene →</div>
          </div>
        </section>

        <!-- JOBS -->
        <section *ngIf="tab()==='jobs'">
          <div class="row-head">
            <h2>Tööjärjekord</h2>
            <select [(ngModel)]="jobFilter" (ngModelChange)="loadJobs()">
              <option value="">Kõik</option>
              <option value="QUEUED">Järjekorras</option>
              <option value="PRINTING">Prindib</option>
              <option value="DONE">Valmis</option>
              <option value="FAILED">Ebaõnnestunud</option>
              <option value="CANCELLED">Tühistatud</option>
            </select>
          </div>
          <table class="tbl">
            <thead>
              <tr>
                <th>ID</th><th>Quote</th><th>Printer</th><th>Staatus</th>
                <th>Progress</th><th>Prio</th><th>Järjekorda lisatud</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let j of jobs()">
                <td>#{{ j.id }}</td>
                <td>#{{ j.quote_id }}</td>
                <td>{{ j.printer_id || '—' }}</td>
                <td><span class="pill" [class]="'pill-' + j.status">{{ j.status }}</span></td>
                <td>{{ j.progress_pct }}%</td>
                <td>{{ j.priority }}</td>
                <td>{{ j.queued_at | date:'short' }}</td>
                <td>
                  <button *ngIf="j.status==='QUEUED'" class="btn sm" (click)="bumpPriority(j.id, j.priority)">↑ Prio</button>
                  <button *ngIf="j.status==='QUEUED' || j.status==='PRINTING'" class="btn sm danger" (click)="cancelJob(j.id)">Tühista</button>
                </td>
              </tr>
              <tr *ngIf="!jobs().length"><td colspan="8" class="hint">Pole töid.</td></tr>
            </tbody>
          </table>
        </section>

        <!-- MATERIALS -->
        <section *ngIf="tab()==='materials'">
          <div class="row-head">
            <h2>Materjalid</h2>
            <button class="btn primary" (click)="showMaterialForm.set(!showMaterialForm())">
              + Uus materjal
            </button>
          </div>
          <div *ngIf="showMaterialForm()" class="card">
            <div class="form-grid">
              <label>Nimi<input [(ngModel)]="newMaterial.name" /></label>
              <label>Perekond
                <select [(ngModel)]="newMaterial.family">
                  <option>PLA</option><option>PETG</option><option>ABS</option>
                  <option>ASA</option><option>TPU</option><option>NYLON</option>
                  <option>PLA-CF</option><option>PA-CF</option><option>PC</option>
                </select>
              </label>
              <label>Hind €/kg<input type="number" step="0.01" [(ngModel)]="newMaterial.price_per_kg_eur" /></label>
              <label>Tihedus g/cm³<input type="number" step="0.01" [(ngModel)]="newMaterial.density_g_cm3" /></label>
              <label>Min seinapaksus mm<input type="number" step="0.1" [(ngModel)]="newMaterial.min_wall_mm" /></label>
              <label>Setup fee €<input type="number" step="0.5" [(ngModel)]="newMaterial.setup_fee_eur" /></label>
            </div>
            <button class="btn primary" (click)="saveMaterial()">Salvesta</button>
          </div>
          <table class="tbl">
            <thead><tr><th>Perekond</th><th>Nimi</th><th>€/kg</th><th>ρ g/cm³</th><th>Min wall</th><th>Aktiivne</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of materials()">
                <td><strong>{{ m.family }}</strong></td>
                <td>{{ m.name }}</td>
                <td>{{ m.price_per_kg_eur }}</td>
                <td>{{ m.density_g_cm3 }}</td>
                <td>{{ m.min_wall_mm }} mm</td>
                <td>{{ m.active ? '✓' : '✗' }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- SPOOLS -->
        <section *ngIf="tab()==='spools'">
          <div class="row-head">
            <h2>Filamendi-spoolid</h2>
            <button class="btn" (click)="loadLowStock()">⚠ Näita madalat varu</button>
          </div>
          <table class="tbl">
            <thead><tr><th>Materjal ID</th><th>Värv</th><th>Alles</th><th>%</th><th>Staatus</th><th>Vendor</th></tr></thead>
            <tbody>
              <tr *ngFor="let s of spools()" [class.warn]="s.pct_remaining < 10">
                <td>{{ s.material_id }}</td>
                <td>
                  <span class="color-dot" [style.background]="s.color_hex || '#888'"></span>
                  {{ s.color }}
                </td>
                <td>{{ s.mass_remaining_g }} / {{ s.mass_initial_g }} g</td>
                <td>{{ s.pct_remaining }}%</td>
                <td><span class="pill">{{ s.status }}</span></td>
                <td>{{ s.vendor }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- CUSTOMERS -->
        <section *ngIf="tab()==='customers'">
          <div class="row-head">
            <h2>Kliendid</h2>
            <button class="btn primary" (click)="showCustomerForm.set(!showCustomerForm())">+ Uus klient</button>
          </div>
          <div *ngIf="showCustomerForm()" class="card">
            <div class="form-grid">
              <label>Tüüp
                <select [(ngModel)]="newCustomer.kind">
                  <option value="B2B">B2B</option>
                  <option value="B2C">B2C</option>
                  <option value="INTERNAL">Sisemine</option>
                </select>
              </label>
              <label>Nimi<input [(ngModel)]="newCustomer.name" /></label>
              <label>E-post<input [(ngModel)]="newCustomer.email" /></label>
              <label>Telefon<input [(ngModel)]="newCustomer.phone" /></label>
              <label>KMKR<input [(ngModel)]="newCustomer.vat_id" /></label>
              <label>Marginaal %<input type="number" [(ngModel)]="newCustomer.default_margin_pct" /></label>
            </div>
            <button class="btn primary" (click)="saveCustomer()">Salvesta</button>
          </div>
          <table class="tbl">
            <thead><tr><th>Nimi</th><th>Tüüp</th><th>E-post</th><th>KMKR</th><th>Marginaal</th></tr></thead>
            <tbody>
              <tr *ngFor="let c of customers()">
                <td><strong>{{ c.name }}</strong></td>
                <td>{{ c.kind }}</td>
                <td>{{ c.email }}</td>
                <td>{{ c.vat_id }}</td>
                <td>{{ c.default_margin_pct }}%</td>
              </tr>
            </tbody>
          </table>
        </section>
      </main>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#0f172a; color:#e2e8f0; font-family:system-ui,-apple-system,sans-serif; }
    .factory { display:grid; grid-template-columns:240px 1fr; min-height:100vh; }
    .sidebar { background:#1e293b; padding:24px 12px; border-right:1px solid #334155; }
    .brand { font-size:20px; margin:0 0 24px 12px; font-weight:800; letter-spacing:-0.5px; }
    .brand small { display:block; color:#818cf8; font-size:11px; font-weight:500; letter-spacing:2px; }
    nav { display:flex; flex-direction:column; gap:2px; }
    nav button { background:transparent; border:0; color:#cbd5e1; text-align:left; padding:10px 14px; border-radius:8px; cursor:pointer; font-size:14px; display:flex; align-items:center; gap:10px; }
    nav button:hover { background:#334155; }
    nav button.active { background:#6366f1; color:white; }
    .icon { display:inline-block; width:20px; }
    .status { margin-top:32px; padding:12px; font-size:12px; color:#94a3b8; display:flex; align-items:center; gap:8px; }
    .pulse { width:8px; height:8px; border-radius:50%; background:#ef4444; }
    .pulse.live { background:#22c55e; box-shadow:0 0 8px #22c55e; animation:pulse 2s infinite; }
    @keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.4; } }

    .content { padding:32px 40px; overflow-y:auto; max-height:100vh; }
    h2 { margin:0 0 24px; font-size:28px; font-weight:700; }
    h3 { font-size:18px; margin:16px 0 12px; color:#cbd5e1; }
    .row-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; }
    .loading { color:#94a3b8; padding:20px; }
    .hint { color:#64748b; font-size:13px; padding:20px 0; }

    .kpi-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:16px; }
    .kpi-card { background:#1e293b; padding:20px; border-radius:12px; border:1px solid #334155; }
    .kpi-card.primary { background:linear-gradient(135deg,#6366f1,#8b5cf6); border-color:#6366f1; }
    .kpi-card .label { font-size:12px; text-transform:uppercase; letter-spacing:1px; color:#94a3b8; margin-bottom:8px; }
    .kpi-card.primary .label { color:#e0e7ff; }
    .kpi-card .value { font-size:32px; font-weight:700; }
    .kpi-card .value.warn { color:#fbbf24; }
    .kpi-card .sub { font-size:12px; color:#94a3b8; margin-top:4px; }

    .tbl { width:100%; border-collapse:collapse; background:#1e293b; border-radius:8px; overflow:hidden; }
    .tbl th, .tbl td { padding:10px 14px; text-align:left; border-bottom:1px solid #334155; font-size:14px; }
    .tbl th { background:#334155; font-weight:600; color:#cbd5e1; font-size:12px; text-transform:uppercase; letter-spacing:0.5px; }
    .tbl tr:hover { background:#273449; }
    .tbl tr.accepted { border-left:3px solid #22c55e; }
    .tbl tr.warn { border-left:3px solid #fbbf24; }

    .pill { padding:2px 10px; border-radius:12px; font-size:11px; background:#334155; font-weight:600; }
    .pill-DRAFT { background:#64748b; }
    .pill-ACCEPTED { background:#22c55e; color:#052e16; }
    .pill-SENT { background:#3b82f6; }
    .pill-DONE { background:#22c55e; color:#052e16; }
    .pill-FAILED { background:#ef4444; }
    .pill-QUEUED { background:#f59e0b; color:#431407; }
    .pill-PRINTING { background:#6366f1; animation:pulse 2s infinite; }
    .pill-IDLE { background:#64748b; }
    .pill-OFFLINE { background:#ef4444; }

    .btn { background:#334155; color:#e2e8f0; border:0; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:14px; font-weight:500; }
    .btn:hover { background:#475569; }
    .btn.primary { background:#6366f1; color:white; }
    .btn.primary:hover { background:#4f46e5; }
    .btn.primary:disabled { background:#334155; cursor:not-allowed; }
    .btn.sm { padding:4px 10px; font-size:12px; }
    .btn.danger { background:#dc2626; }
    .btn.danger:hover { background:#b91c1c; }

    .card { background:#1e293b; padding:20px; border-radius:12px; margin-bottom:24px; border:1px solid #334155; }
    .form-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:12px; margin-bottom:16px; }
    .form-grid .full { grid-column:1/-1; }
    .form-grid label { display:flex; flex-direction:column; gap:4px; font-size:12px; color:#94a3b8; text-transform:uppercase; letter-spacing:0.5px; }
    .form-grid input, .form-grid select, .form-grid textarea {
      background:#0f172a; color:#e2e8f0; border:1px solid #334155; padding:8px 10px; border-radius:6px; font-size:14px;
    }
    .form-grid input:focus, .form-grid select:focus { outline:2px solid #6366f1; border-color:#6366f1; }

    .printer-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:16px; }
    .printer-card { background:#1e293b; padding:16px; border-radius:12px; border:1px solid #334155; }
    .printer-card.status-OFFLINE { opacity:0.5; border-color:#ef4444; }
    .printer-card.status-PRINTING { border-color:#6366f1; }
    .printer-card .head { display:flex; justify-content:space-between; align-items:center; margin-bottom:4px; }
    .printer-card .progress { margin:12px 0; background:#0f172a; border-radius:4px; height:8px; position:relative; overflow:hidden; }
    .printer-card .progress .bar { background:linear-gradient(90deg,#6366f1,#8b5cf6); height:100%; transition:width 0.3s; }
    .printer-card .progress span { position:absolute; top:-20px; right:0; font-size:11px; color:#94a3b8; }
    .printer-card .actions { display:flex; gap:6px; margin-top:12px; }
    .color-dot { display:inline-block; width:12px; height:12px; border-radius:50%; vertical-align:middle; margin-right:6px; border:1px solid #334155; }

    .error { color:#ef4444; margin-top:8px; font-size:13px; }
  `]
})
export class FactoryComponent implements OnInit, OnDestroy {
  private svc = inject(FactoryService);

  tab = signal<Tab>('dashboard');
  kpi = signal<Kpi | null>(null);
  topMat = signal<any[]>([]);
  materials = signal<Material[]>([]);
  printers = signal<Printer[]>([]);
  jobs = signal<PrintJob[]>([]);
  customers = signal<Customer[]>([]);
  spools = signal<Spool[]>([]);
  quotes = signal<Quote[]>([]);
  rfqs = signal<any[]>([]);

  streamConnected = signal(false);
  submitting = signal(false);
  quoteError = signal<string | null>(null);

  showQuoteForm = signal(false);
  showPrinterForm = signal(false);
  showMaterialForm = signal(false);
  showCustomerForm = signal(false);

  jobFilter = '';

  newQuote: any = { customerId: null, materialId: null, quantity: 1, rush: false, marginPct: 30, notes: '', file: null };
  newPrinter: any = { name: '', vendor: '', model: '', adapter_type: 'MOCK', supported_materials: 'PLA,PETG' };
  newMaterial: any = { name: '', family: 'PLA', price_per_kg_eur: 25, density_g_cm3: 1.24, min_wall_mm: 0.8, setup_fee_eur: 5, active: true };
  newCustomer: any = { kind: 'B2B', name: '', email: '', phone: '', vat_id: '', default_margin_pct: 30 };

  private pollTimer?: any;
  private eventSource?: EventSource;

  ngOnInit() {
    this.loadAll();
    this.openSse();
    // Fallback polling iga 10s puhul kui SSE kukub
    this.pollTimer = setInterval(() => this.refreshLive(), 10000);
  }

  ngOnDestroy() {
    this.eventSource?.close();
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  private loadAll() {
    this.svc.kpi().subscribe(k => this.kpi.set(k));
    this.svc.topMaterials().subscribe(m => this.topMat.set(m));
    this.svc.materials(true).subscribe(m => this.materials.set(m));
    this.svc.printers().subscribe(p => this.printers.set(p));
    this.svc.jobs().subscribe(j => this.jobs.set(j));
    this.svc.customers().subscribe(c => this.customers.set(c));
    this.svc.spools().subscribe(s => this.spools.set(s));
    this.svc.quotes().subscribe(q => this.quotes.set(q));
    this.svc.rfqs().subscribe(r => this.rfqs.set(r));
  }

  private refreshLive() {
    this.svc.kpi().subscribe(k => this.kpi.set(k));
    this.svc.printers().subscribe(p => this.printers.set(p));
    if (this.tab() === 'jobs') this.loadJobs();
  }

  private openSse() {
    try {
      this.eventSource = this.svc.openEventStream();
      this.eventSource.onopen = () => this.streamConnected.set(true);
      this.eventSource.onerror = () => this.streamConnected.set(false);
      this.eventSource.addEventListener('printer-event', (e: any) => {
        // incremental update — refresh printers + jobs
        this.svc.printers().subscribe(p => this.printers.set(p));
      });
    } catch { /* EventSource pole toetatud — kasutame polling fallbacki */ }
  }

  onFile(e: Event) {
    const input = e.target as HTMLInputElement;
    this.newQuote.file = input.files?.[0] || null;
  }

  submitQuote() {
    if (!this.newQuote.file) return;
    this.submitting.set(true);
    this.quoteError.set(null);
    const fd = new FormData();
    fd.append('file', this.newQuote.file);
    if (this.newQuote.customerId) fd.append('customer_id', String(this.newQuote.customerId));
    if (this.newQuote.materialId) fd.append('material_id', String(this.newQuote.materialId));
    fd.append('quantity', String(this.newQuote.quantity));
    fd.append('rush', String(this.newQuote.rush));
    fd.append('margin_pct', String(this.newQuote.marginPct));
    if (this.newQuote.notes) fd.append('notes', this.newQuote.notes);

    this.svc.createQuote(fd).subscribe({
      next: () => {
        this.submitting.set(false);
        this.showQuoteForm.set(false);
        this.newQuote = { customerId: null, materialId: this.newQuote.materialId, quantity: 1, rush: false, marginPct: 30, notes: '', file: null };
        this.svc.quotes().subscribe(q => this.quotes.set(q));
      },
      error: err => {
        this.submitting.set(false);
        this.quoteError.set(err.error?.message || err.message || 'Pakkumise loomine ebaõnnestus');
      }
    });
  }

  accept(id: number) {
    this.svc.acceptQuote(id).subscribe(() => {
      this.svc.quotes().subscribe(q => this.quotes.set(q));
      this.svc.jobs().subscribe(j => this.jobs.set(j));
    });
  }

  savePrinter() {
    this.svc.createPrinter(this.newPrinter).subscribe(() => {
      this.showPrinterForm.set(false);
      this.svc.printers().subscribe(p => this.printers.set(p));
    });
  }

  saveMaterial() {
    this.svc.createMaterial(this.newMaterial).subscribe(() => {
      this.showMaterialForm.set(false);
      this.svc.materials(true).subscribe(m => this.materials.set(m));
    });
  }

  saveCustomer() {
    this.svc.createCustomer(this.newCustomer).subscribe(() => {
      this.showCustomerForm.set(false);
      this.svc.customers().subscribe(c => this.customers.set(c));
    });
  }

  loadJobs() {
    this.svc.jobs(this.jobFilter || undefined).subscribe(j => this.jobs.set(j));
  }

  loadLowStock() {
    this.svc.lowStock(100).subscribe(s => this.spools.set(s));
  }

  pausePrinter(id: number) { this.svc.pausePrinter(id).subscribe(() => this.refreshLive()); }
  resumePrinter(id: number) { this.svc.resumePrinter(id).subscribe(() => this.refreshLive()); }
  cancelPrinterJob(id: number) { this.svc.cancelPrinterJob(id).subscribe(() => this.refreshLive()); }

  cancelJob(id: number) { this.svc.cancelJob(id).subscribe(() => this.loadJobs()); }
  bumpPriority(id: number, current: number) { this.svc.setJobPriority(id, current + 10).subscribe(() => this.loadJobs()); }

  updateRfqStatus(id: number, status: string) {
    this.svc.setRfqStatus(id, status).subscribe(() => this.svc.rfqs().subscribe(r => this.rfqs.set(r)));
  }
}
