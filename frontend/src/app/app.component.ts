import { Component, ElementRef, ViewChild, AfterViewInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { EXAMPLES, Example } from './examples';
import * as THREE from 'three';
// @ts-ignore — STLLoader ships without types in @types/three
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

interface Spec {
  template: string;
  params: Record<string, number>;
  summary_et?: string;
}

interface Metrics {
  volume_cm3: number;
  bbox_mm: { x: number; y: number; z: number };
  weight_g_pla: number;
  print_time_min_estimate: number;
  overhang_risk: boolean;
}

interface ParamSchema {
  type: string;
  unit: string;
  min: number;
  max: number;
  default: number;
}

interface TemplateSchema {
  description: string;
  params: Record<string, ParamSchema>;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- ═══════ HEADER ═══════ -->
    <header class="header">
      <div class="header-inner">
        <a href="#" class="logo">
          <span class="logo-icon">&#9670;</span>
          <span class="logo-text">TehisAI<span style="color:var(--accent-2)">CAD</span></span>
        </a>
        <nav class="header-nav" *ngIf="!auth.me()">
          <a href="#how">Kuidas?</a>
          <a href="#examples">Näited</a>
          <a href="#pricing">Hinnad</a>
          <button (click)="auth.loginWithGoogle()" class="btn-login">
            Logi sisse
          </button>
        </nav>
        <nav class="header-nav" *ngIf="auth.me() as m">
          <a href="#mydesigns" (click)="loadMyDesigns()">Minu disainid</a>
          <a href="#admin" (click)="loadAdmin()" *ngIf="isAdmin()" style="color:var(--amber)">Admin</a>
          <span class="header-plan" [class.plan-pro]="m.plan === 'PRO'">{{ m.plan }}</span>
          <span class="header-quota" *ngIf="m.limit > 0">{{ m.used }}/{{ m.limit }}</span>
          <span class="header-quota" *ngIf="m.limit < 0">&#8734;</span>
          <button (click)="auth.logout()" class="btn-logout">Logi välja</button>
        </nav>
      </div>
    </header>

    <!-- ═══════ HERO ═══════ -->
    <section class="hero-bg">
      <div class="grid-overlay"></div>
      <div class="hero-content">
        <div class="badge animate-in">
          &#127466;&#127466;&nbsp; Esimene eestikeelne AI-CAD
        </div>
        <h1 class="hero-title animate-in animate-in-delay-1">
          Kirjelda.<br>
          <span class="gradient-text">Genereeri.</span><br>
          Prindi.
        </h1>
        <p class="hero-subtitle animate-in animate-in-delay-2">
          Kirjelda eesti keeles mida vajad — saad <strong>30 sekundiga</strong>
          printimisvalmis STL-faili. Parameetriline insenerikvaliteet,
          mitte AI-mesh.
        </p>
        <div class="hero-actions animate-in animate-in-delay-3">
          <a href="#app" class="btn-cta">
            Proovi tasuta &rarr;
          </a>
          <a href="#how" class="btn-secondary">
            Vaata kuidas &darr;
          </a>
        </div>
        <div class="hero-stats animate-in animate-in-delay-4">
          <div class="hero-stat">
            <span class="hero-stat-number">23</span>
            <span class="hero-stat-label">malli</span>
          </div>
          <div class="hero-stat-divider"></div>
          <div class="hero-stat">
            <span class="hero-stat-number">30s</span>
            <span class="hero-stat-label">genereerimine</span>
          </div>
          <div class="hero-stat-divider"></div>
          <div class="hero-stat">
            <span class="hero-stat-number">0.1mm</span>
            <span class="hero-stat-label">täpsus</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════ HOW IT WORKS ═══════ -->
    <section id="how" class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge">3 SAMMU</div>
          <h2 class="section-title">Kuidas see tööb?</h2>
          <p class="section-subtitle">Tavaline CAD: nädalad õppimist. TehisAI CAD: 30 sekundit.</p>
        </div>
        <div class="steps-grid">
          <div class="step-card card-glass">
            <div class="step-number">01</div>
            <h3>Kirjelda</h3>
            <p>Kirjuta eesti keeles mida vajad. Näiteks: «25mm torule riiuliklamber, peab kandma 5kg».</p>
          </div>
          <div class="step-card card-glass">
            <div class="step-number">02</div>
            <h3>Kohanda</h3>
            <p>AI tuvastab malli ja parameetrid. Liiguta slidereid — näed mõõtude muutumist reaalajas.</p>
          </div>
          <div class="step-card card-glass">
            <div class="step-number">03</div>
            <h3>Prindi</h3>
            <p>Lae STL alla ja saada printerisse. Insenerikvaliteet, mitte mesh — kannab koormust.</p>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ FEATURES BENTO ═══════ -->
    <section class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge">MIKS MEID</div>
          <h2 class="section-title">Mitte lihtsalt AI-tööriist.<br><span class="gradient-text">Inseneritööriist.</span></h2>
        </div>
        <div class="bento-grid">
          <div class="card-glass bento-featured">
            <div class="bento-icon">&#9889;</div>
            <h3>30 sekundit vs 5 päeva</h3>
            <p>3D-modelleerija: 2–5 päeva, 100–500 €.<br>TehisAI CAD: 30 sekundit, 0 €.</p>
            <div class="bento-visual">
              <div class="time-compare">
                <div class="time-bar time-us" style="width:5%"><span>30s</span></div>
                <div class="time-bar time-them" style="width:90%"><span>5 päeva</span></div>
              </div>
            </div>
          </div>
          <div class="card-glass">
            <div class="bento-icon">&#127466;&#127466;</div>
            <h3>Eesti keel on emakeel</h3>
            <p>Fusion 360, Onshape — kõik inglise keeles. Meie saame aru: «riiuliklambrit 32mm torule».</p>
          </div>
          <div class="card-glass">
            <div class="bento-icon">&#127919;</div>
            <h3>Parameetriline CadQuery</h3>
            <p>Mitte orgaaniline mesh nagu Meshy/Tripo — päris B-Rep geomeetria mis kannab koormust.</p>
          </div>
          <div class="card-glass">
            <div class="bento-icon">&#128295;</div>
            <h3>Kohanda brauseris</h3>
            <p>AI paneb parameetrid, sina liigutad slidereid. 1mm ei mahu? Muuda ja regenereeri.</p>
          </div>
          <div class="card-glass bento-wide">
            <div class="bento-icon">&#128274;</div>
            <h3>Sinu disain, sinu fail</h3>
            <p>STL on sinu. Prindi kus tahad — Eesti partnerid (3DKoda) kuni oma printer garaažis. Andmeid ei jaga.</p>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ COMPARISON TABLE ═══════ -->
    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">Võrdlus alternatiividega</h2>
        </div>
        <div class="card-glass" style="padding:0;overflow:hidden">
          <table>
            <thead>
              <tr>
                <th></th>
                <th style="color:var(--accent)">TehisAI CAD</th>
                <th>Thingiverse</th>
                <th>Hiina tellimus</th>
                <th>CAD-modelleerija</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>Aeg</strong></td>
                <td style="color:var(--green);font-weight:700">30 sek</td>
                <td>Tunde otsida</td>
                <td>2–4 nädalat</td>
                <td>2–5 päeva</td>
              </tr>
              <tr>
                <td><strong>Hind</strong></td>
                <td style="color:var(--green);font-weight:700">0–14.99 €/kuu</td>
                <td>Tasuta</td>
                <td>5–50 €/tk</td>
                <td>100–500 €</td>
              </tr>
              <tr>
                <td><strong>Täpsus</strong></td>
                <td style="color:var(--green);font-weight:700">Sinu mõõdud</td>
                <td>Lähim variant</td>
                <td>Standardne</td>
                <td>Sinu mõõdud</td>
              </tr>
              <tr>
                <td><strong>Eesti keel</strong></td>
                <td style="color:var(--green);font-weight:700">&#10003;</td>
                <td style="color:var(--text-muted)">&#10007;</td>
                <td style="color:var(--text-muted)">&#10007;</td>
                <td>Sõltub</td>
              </tr>
              <tr>
                <td><strong>Kohandamine</strong></td>
                <td style="color:var(--green);font-weight:700">Reaalajas</td>
                <td style="color:var(--text-muted)">Ei</td>
                <td style="color:var(--text-muted)">Ei</td>
                <td>Aeglane</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ EXAMPLES ═══════ -->
    <section id="examples" class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge">NÄITED</div>
          <h2 class="section-title">Klõpsa. Genereeri. Prindi.</h2>
          <p class="section-subtitle">Kliki näitele — prompt ja parameetrid täidetakse automaatselt.</p>
        </div>
        <div class="examples-grid">
          <div *ngFor="let ex of examples" class="example-card card-glass" (click)="tryExample(ex)">
            <div class="example-emoji">{{ ex.emoji }}</div>
            <h3>{{ ex.title }}</h3>
            <p class="example-prompt">«{{ ex.prompt }}»</p>
            <p class="example-usecase">{{ ex.useCase }}</p>
            <div class="example-cta">Proovi &rarr;</div>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ WHO IS IT FOR ═══════ -->
    <section class="section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">Kellele see sobib?</h2>
        </div>
        <div class="audience-grid">
          <div class="audience-card card-glass">
            <div class="audience-icon">&#128681;</div>
            <strong>Droonitootjad</strong>
            <p>Sensori-mount'id, kaabliklambrid, jig'id — 24h iteratsioonitsükkel.</p>
          </div>
          <div class="audience-card card-glass">
            <div class="audience-icon">&#127968;</div>
            <strong>DIY-entusiastid</strong>
            <p>Riiuliklambrid ebatavalistele torudele, konksud, organiseerijad.</p>
          </div>
          <div class="audience-card card-glass">
            <div class="audience-icon">&#128736;</div>
            <strong>Väiketootjad</strong>
            <p>Asendusosad, prototüübid, kliendispetsiifilised adapterid.</p>
          </div>
          <div class="audience-card card-glass">
            <div class="audience-icon">&#127891;</div>
            <strong>Koolid & fablab'id</strong>
            <p>Õpilased saavad ideed 3D-ks muuta ilma CAD-tarkvara õppimata.</p>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ PRICING ═══════ -->
    <section id="pricing" class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge">HINNAD</div>
          <h2 class="section-title">Lihtne hinnastus. Peidetud tasusid pole.</h2>
          <p class="section-subtitle">Proovi tasuta. Uuenda kui vajad rohkem.</p>
        </div>
        <div class="pricing-grid">
          <div class="pricing-card card-glass">
            <div class="pricing-tier">Free</div>
            <div class="pricing-price">0 €<span class="pricing-period">/kuu</span></div>
            <ul class="pricing-features">
              <li>3 STL-i kuus</li>
              <li>Kõik 23 malli</li>
              <li>Eestikeelne AI</li>
              <li>Brauseris kohandamine</li>
              <li>3D eelvaade</li>
            </ul>
            <button *ngIf="!auth.me()" class="pricing-btn" style="background:var(--bg-card)"
                    (click)="auth.loginWithGoogle()">Alusta tasuta</button>
            <div *ngIf="auth.me()?.plan === 'FREE'" class="pricing-current">Praegune plaan</div>
          </div>

          <div class="pricing-card card-glass pricing-popular">
            <div class="pricing-tier">Hobi</div>
            <div class="pricing-price">4.99 €<span class="pricing-period">/kuu</span></div>
            <div class="pricing-save">49 €/a — säästad 2 kuud</div>
            <ul class="pricing-features">
              <li><strong>Piiramatult</strong> STL-i</li>
              <li>Ajalugu + re-download</li>
              <li>Uued mallid automaatselt</li>
              <li>E-mail tugi</li>
              <li>Metrika (kaal, aeg, mõõdud)</li>
            </ul>
            <button class="pricing-btn btn-cta" (click)="upgrade('hobi')"
                    [disabled]="auth.me()?.plan === 'PRO'">
              {{ auth.me()?.plan === 'PRO' ? 'Aktiivne' : 'Uuenda' }}
            </button>
          </div>

          <div class="pricing-card card-glass">
            <div class="pricing-tier">Pro</div>
            <div class="pricing-price">14.99 €<span class="pricing-period">/kuu</span></div>
            <div class="pricing-save">149 €/a</div>
            <ul class="pricing-features">
              <li>Kõik Hobi omadused</li>
              <li><strong>STEP-eksport</strong></li>
              <li>API (1000 calls/kuu)</li>
              <li>Prioriteetne järjekord</li>
              <li>Kommertslitsents</li>
            </ul>
            <button class="pricing-btn" style="background:var(--bg-card)" (click)="upgrade('pro')">
              Uuenda Pro-le
            </button>
          </div>
        </div>
        <p style="color:var(--text-muted);font-size:.85rem;margin-top:2rem;text-align:center">
          Kõik hinnad sis. KM. Tühista millal tahes. B2B alates 199 €/kuu —
          <a href="mailto:hello@tehisaicad.ee">võta ühendust</a>.
        </p>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ APP (main workspace) ═══════ -->
    <main id="app" class="section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">Proovi kohe</h2>
          <p class="section-subtitle">Kirjelda mida vajad — saad printimisvalmis STL-i sekunditega.</p>
        </div>

        <div class="workspace-grid">
          <!-- Left: Input + params -->
          <div class="workspace-left">
            <div class="card-glass">
              <label style="margin-top:0">Kirjeldus eesti keeles</label>
              <textarea rows="3" [(ngModel)]="prompt"
                placeholder="Näiteks: vajan riiuliklambrit 32mm veetorule, peab kandma 5kg koormust"></textarea>
              <button *ngIf="auth.me()" style="margin-top:1rem;width:100%" (click)="analyze()" [disabled]="loading()">
                {{ loading() ? 'AI analüüsib...' : 'Analüüsi' }}
              </button>
              <button *ngIf="!auth.me()" style="margin-top:1rem;width:100%" (click)="auth.loginWithGoogle()">
                Logi sisse, et genereerida
              </button>
            </div>

            <div class="card-glass" *ngIf="spec() as s">
              <h3 style="margin-bottom:.2rem">{{ s.summary_et || s.template }}</h3>
              <p style="color:var(--text-muted);font-size:.85rem;margin-bottom:1rem">
                <code>{{ s.template }}</code> — {{ catalogFor(s.template)?.description }}
              </p>
              <div *ngFor="let k of paramKeys(s)" class="param-row">
                <label>
                  {{ k }}
                  <span style="color:var(--text-muted)">
                    ({{ schemaFor(s.template, k)?.min }}–{{ schemaFor(s.template, k)?.max }}
                    {{ schemaFor(s.template, k)?.unit }})
                  </span>
                  <strong style="float:right;color:var(--accent-2)">{{ s.params[k] }}</strong>
                </label>
                <input type="range"
                  [min]="schemaFor(s.template, k)?.min || 0"
                  [max]="schemaFor(s.template, k)?.max || 100"
                  [step]="0.5"
                  [ngModel]="s.params[k]" (ngModelChange)="updateParam(k, $event)">
              </div>
              <button style="margin-top:1.2rem;width:100%" class="btn-cta" (click)="generate()" [disabled]="loading()">
                {{ loading() ? 'Genereerin...' : 'Genereeri STL' }}
              </button>
            </div>

            <!-- Metrics -->
            <div class="card-glass" *ngIf="metrics() as m">
              <h4 style="margin-bottom:.6rem;color:var(--text-secondary)">Detaili näitajad</h4>
              <div class="metrics-grid">
                <div class="metric"><span class="metric-label">Maht</span><span class="metric-value">{{ m.volume_cm3 }} cm³</span></div>
                <div class="metric"><span class="metric-label">Mõõdud</span><span class="metric-value">{{ m.bbox_mm.x }}×{{ m.bbox_mm.y }}×{{ m.bbox_mm.z }}</span></div>
                <div class="metric"><span class="metric-label">Kaal</span><span class="metric-value">{{ m.weight_g_pla }}g PLA</span></div>
                <div class="metric"><span class="metric-label">Prindiaeg</span><span class="metric-value">~{{ m.print_time_min_estimate }} min</span></div>
              </div>
              <div *ngIf="m.overhang_risk" class="overhang-warning">
                &#9888;&#65039; Võib vajada tugistruktuuri (supports)
              </div>
            </div>

            <!-- Download -->
            <div class="card-glass download-card" *ngIf="stlUrl()">
              <a [href]="stlUrl()" download="model.stl" class="download-link">
                &#11015; Lae STL alla
              </a>
            </div>

            <!-- Error -->
            <div class="card-glass error-card" *ngIf="error()">
              {{ error() }}
              <div *ngIf="suggestions().length" class="suggestion-chips">
                <button *ngFor="let s of suggestions()" (click)="pickTemplate(s)" class="chip">{{ s }}</button>
              </div>
            </div>
          </div>

          <!-- Right: 3D viewer -->
          <div class="workspace-right">
            <div class="viewer-container card-glass" style="padding:0;overflow:hidden">
              <div #viewer class="viewer-canvas"></div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- ═══════ ADMIN ═══════ -->
    <section id="admin" *ngIf="adminStats()" class="section">
      <div class="container">
        <h2 class="section-title">Admin Dashboard</h2>
        <div class="admin-stats-grid">
          <div class="card-glass admin-stat" *ngFor="let s of adminStatCards()">
            <span class="admin-stat-label">{{ s.label }}</span>
            <span class="stat-number" [style.color]="s.color">{{ s.value }}</span>
          </div>
        </div>
        <div *ngIf="adminUsers().length" style="margin-top:2rem">
          <h3 style="margin-bottom:1rem">Kasutajad</h3>
          <div class="card-glass" style="padding:0;overflow-x:auto">
            <table>
              <thead>
                <tr>
                  <th>E-mail</th><th>Nimi</th><th>Plaan</th><th>STL sel kuul</th><th>Disaine</th><th>Liitus</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let u of adminUsers()">
                  <td>{{ u.email }}</td>
                  <td>{{ u.name }}</td>
                  <td><span class="badge" [style.background]="u.plan==='PRO'?'rgba(52,211,153,0.15)':''" [style.color]="u.plan==='PRO'?'var(--green)':''">{{ u.plan }}</span></td>
                  <td>{{ u.stlThisMonth }}</td>
                  <td>{{ u.totalDesigns }}</td>
                  <td>{{ humanDate(u.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════ MY DESIGNS ═══════ -->
    <section id="mydesigns" *ngIf="auth.me() && myDesigns().length" class="section">
      <div class="container">
        <h2 class="section-title">Minu disainid</h2>
        <p class="section-subtitle">Viimased 50 genereeritud detaili.</p>
        <div class="designs-grid">
          <div *ngFor="let d of myDesigns()" class="card-glass design-card">
            <strong>{{ d.summary_et || d.template }}</strong>
            <div style="color:var(--text-muted);font-size:.8rem;margin-top:.3rem">
              <code>{{ d.template }}</code> &middot; {{ humanDate(d.created_at) }} &middot; {{ (d.size_bytes/1024) | number:'1.0-0' }} KB
            </div>
            <div style="display:flex;gap:.5rem;margin-top:.8rem">
              <a [href]="designStlUrl(d.id)" download class="design-download">&#11015; STL</a>
              <button (click)="deleteDesign(d.id)" class="design-delete">&#128465;</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════ FOOTER ═══════ -->
    <footer class="footer">
      <div class="container">
        <div class="footer-grid">
          <div>
            <div class="logo" style="margin-bottom:.5rem">
              <span class="logo-icon">&#9670;</span>
              <span class="logo-text">TehisAI<span style="color:var(--accent-2)">CAD</span></span>
            </div>
            <p style="color:var(--text-muted);font-size:.85rem;max-width:280px">
              Eesti esimene AI-põhine 3D-disaini tööriist. Sõnadest printimisvalmis detailiks.
            </p>
          </div>
          <div>
            <h4 class="footer-heading">Toode</h4>
            <a href="#how">Kuidas tööb</a>
            <a href="#examples">Näited</a>
            <a href="#pricing">Hinnad</a>
            <a href="#app">Proovi</a>
          </div>
          <div>
            <h4 class="footer-heading">Õiguslik</h4>
            <a href="/legal/terms">Kasutustingimused</a>
            <a href="/legal/privacy">Privaatsuspoliitika</a>
          </div>
          <div>
            <h4 class="footer-heading">Kontakt</h4>
            <a href="mailto:hello@tehisaicad.ee">hello&#64;tehisaicad.ee</a>
          </div>
        </div>
        <div class="footer-bottom">
          &copy; 2026 TehisAI CAD &middot; Eestis loodud &#127466;&#127466;
        </div>
      </div>
    </footer>
  `,
  styles: [`
    /* ── Header ─────────────────────────────────────────── */
    .header {
      position: sticky; top: 0; z-index: 100;
      background: rgba(5, 10, 24, 0.85);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--border);
    }
    .header-inner {
      max-width: 1100px; margin: 0 auto;
      display: flex; justify-content: space-between; align-items: center;
      padding: .65rem 1.5rem;
    }
    .logo { display: flex; align-items: center; gap: .4rem; text-decoration: none; }
    .logo-icon { font-size: 1.2rem; color: var(--accent); }
    .logo-text { font-weight: 800; font-size: 1.05rem; color: var(--text-primary); letter-spacing: -0.02em; }
    .header-nav { display: flex; align-items: center; gap: 1.2rem; font-size: .9rem; }
    .header-nav a { color: var(--text-secondary); font-weight: 500; }
    .header-nav a:hover { color: var(--text-primary); }
    .btn-login {
      background: var(--accent); padding: .4rem 1rem; border-radius: 8px;
      font-size: .85rem; font-weight: 600;
    }
    .btn-logout {
      background: transparent; border: 1px solid var(--border);
      color: var(--text-muted); padding: .35rem .8rem; font-size: .8rem;
    }
    .header-plan {
      padding: .15rem .5rem; border-radius: 999px; font-size: .75rem; font-weight: 700;
      background: rgba(99,102,241,0.15); color: var(--accent-2);
    }
    .plan-pro { background: rgba(52,211,153,0.15); color: var(--green); }
    .header-quota { color: var(--text-muted); font-size: .8rem; font-weight: 500; }

    /* ── Hero ───────────────────────────────────────────── */
    .hero-content {
      position: relative; z-index: 1;
      max-width: 800px; margin: 0 auto;
      padding: 7rem 1.5rem 5rem;
      text-align: center;
    }
    .hero-title {
      font-size: clamp(2.5rem, 7vw, 4.5rem);
      font-weight: 900;
      line-height: 1.05;
      letter-spacing: -0.04em;
      margin: 1.5rem 0;
    }
    .hero-subtitle {
      font-size: 1.15rem; color: var(--text-secondary);
      max-width: 560px; margin: 0 auto 2rem; line-height: 1.7;
    }
    .hero-actions { display: flex; gap: .8rem; justify-content: center; flex-wrap: wrap; }
    .hero-stats {
      display: flex; justify-content: center; align-items: center; gap: 2rem;
      margin-top: 3.5rem; padding-top: 2rem;
      border-top: 1px solid var(--border);
    }
    .hero-stat { text-align: center; }
    .hero-stat-number { display: block; font-size: 1.8rem; font-weight: 800; color: var(--text-primary); letter-spacing: -0.03em; }
    .hero-stat-label { font-size: .8rem; color: var(--text-muted); font-weight: 500; }
    .hero-stat-divider { width: 1px; height: 40px; background: var(--border); }

    /* ── Sections ───────────────────────────────────────── */
    .section { padding: 5rem 0; }
    .container { max-width: 1100px; margin: 0 auto; padding: 0 1.5rem; }
    .section-header { text-align: center; margin-bottom: 3rem; }
    .section-title { font-size: clamp(1.8rem, 4vw, 2.8rem); letter-spacing: -0.03em; margin: .8rem 0 .5rem; }
    .section-subtitle { color: var(--text-secondary); font-size: 1.05rem; max-width: 540px; margin: 0 auto; }

    /* ── Steps ──────────────────────────────────────────── */
    .steps-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.2rem; }
    .step-card { text-align: center; padding: 2rem 1.5rem; }
    .step-number {
      font-size: 3rem; font-weight: 900; letter-spacing: -0.05em;
      background: linear-gradient(135deg, var(--accent), transparent);
      -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
      opacity: 0.3;
    }
    .step-card h3 { margin: .5rem 0; font-size: 1.15rem; }
    .step-card p { color: var(--text-secondary); font-size: .9rem; }
    @media (max-width: 768px) { .steps-grid { grid-template-columns: 1fr; } }

    /* ── Bento features ────────────────────────────────── */
    .bento-icon { font-size: 1.8rem; margin-bottom: .5rem; }
    .bento-featured { padding: 2rem; }
    .bento-featured h3 { font-size: 1.3rem; }
    .bento-wide { grid-column: span 2; }
    @media (max-width: 768px) { .bento-wide { grid-column: span 1; } }
    .bento-grid h3 { margin-bottom: .3rem; }
    .bento-grid p { color: var(--text-secondary); font-size: .9rem; }
    .time-compare { margin-top: 1.2rem; display: flex; flex-direction: column; gap: .4rem; }
    .time-bar {
      height: 28px; border-radius: 6px; display: flex; align-items: center; padding: 0 .8rem;
      font-size: .8rem; font-weight: 700;
    }
    .time-us { background: linear-gradient(90deg, var(--accent), var(--accent-2)); min-width: 60px; }
    .time-them { background: rgba(239,68,68,0.15); color: #fca5a5; }

    /* ── Examples ───────────────────────────────────────── */
    .examples-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.2rem;
    }
    .example-card { cursor: pointer; transition: all var(--transition); padding: 1.5rem; }
    .example-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-glow); }
    .example-emoji { font-size: 2.2rem; margin-bottom: .4rem; }
    .example-card h3 { font-size: 1.05rem; margin-bottom: .3rem; }
    .example-prompt { color: var(--text-muted); font-style: italic; font-size: .9rem; margin: .3rem 0; }
    .example-usecase { color: var(--text-secondary); font-size: .85rem; }
    .example-cta { margin-top: .8rem; color: var(--accent); font-size: .85rem; font-weight: 600; }

    /* ── Audience ───────────────────────────────────────── */
    .audience-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.2rem;
    }
    .audience-card { text-align: center; padding: 1.5rem; }
    .audience-icon { font-size: 2rem; margin-bottom: .5rem; }
    .audience-card p { color: var(--text-secondary); font-size: .9rem; margin-top: .4rem; }

    /* ── Pricing ────────────────────────────────────────── */
    .pricing-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 1.2rem;
      align-items: start;
    }
    .pricing-card { padding: 2rem; text-align: center; }
    .pricing-tier {
      text-transform: uppercase; letter-spacing: .08em; font-size: .8rem;
      font-weight: 700; color: var(--text-muted);
    }
    .pricing-price { font-size: 2.8rem; font-weight: 900; letter-spacing: -0.04em; margin: .3rem 0; }
    .pricing-period { font-size: 1rem; color: var(--text-muted); font-weight: 400; }
    .pricing-save { color: var(--green); font-size: .85rem; font-weight: 500; margin-bottom: .8rem; }
    .pricing-features {
      list-style: none; padding: 0; margin: 1rem 0; text-align: left;
      color: var(--text-secondary); line-height: 2;
    }
    .pricing-features li::before { content: '\\2713  '; color: var(--accent); font-weight: 700; }
    .pricing-btn { width: 100%; margin-top: 1rem; }
    .pricing-current {
      margin-top: 1rem; padding: .5rem; border-radius: 8px;
      background: rgba(52,211,153,0.1); color: var(--green); font-weight: 600; font-size: .9rem;
    }

    /* ── Workspace ──────────────────────────────────────── */
    .workspace-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; align-items: start;
    }
    @media (max-width: 900px) { .workspace-grid { grid-template-columns: 1fr; } }
    .workspace-left { display: flex; flex-direction: column; gap: 1rem; }
    .workspace-right { position: sticky; top: 80px; }
    .viewer-canvas { width: 100%; height: 520px; border-radius: var(--radius-lg); }
    .param-row { margin-bottom: .2rem; }

    /* Metrics */
    .metrics-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: .6rem; }
    .metric { display: flex; flex-direction: column; }
    .metric-label { font-size: .75rem; color: var(--text-muted); font-weight: 500; }
    .metric-value { font-size: .95rem; font-weight: 700; color: var(--text-primary); }
    .overhang-warning {
      margin-top: .7rem; padding: .6rem; border-radius: 8px;
      background: rgba(245,158,11,0.1); border: 1px solid rgba(245,158,11,0.2);
      color: var(--amber); font-size: .85rem;
    }

    /* Download */
    .download-card { text-align: center; }
    .download-link {
      display: inline-flex; align-items: center; gap: .4rem;
      color: var(--green); font-weight: 700; font-size: 1.1rem; text-decoration: none;
    }
    .download-link:hover { color: #6ee7b7; }

    /* Error */
    .error-card { background: rgba(239,68,68,0.08); border-color: rgba(239,68,68,0.2); color: #fca5a5; }
    .suggestion-chips { display: flex; gap: .4rem; flex-wrap: wrap; margin-top: .6rem; }
    .chip {
      background: var(--bg-card); color: var(--text-primary);
      padding: .3rem .7rem; border-radius: 999px; font-size: .85rem;
      border: 1px solid var(--border);
    }
    .chip:hover { border-color: var(--accent); }

    /* ── Admin ──────────────────────────────────────────── */
    .admin-stats-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem;
    }
    .admin-stat { text-align: center; padding: 1.5rem; }
    .admin-stat-label { display: block; color: var(--text-muted); font-size: .8rem; font-weight: 500; margin-bottom: .3rem; }

    /* ── Designs ────────────────────────────────────────── */
    .designs-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 1rem; margin-top: 1.5rem;
    }
    .design-card { padding: 1.2rem; }
    .design-download {
      flex: 1; text-align: center; background: var(--bg-card); color: var(--text-primary);
      padding: .4rem; border-radius: 8px; text-decoration: none; font-size: .85rem; font-weight: 600;
      border: 1px solid var(--border); transition: all var(--transition);
    }
    .design-download:hover { border-color: var(--accent); }
    .design-delete {
      background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2);
      color: #fca5a5; padding: .4rem .6rem; font-size: .85rem;
    }

    /* ── Footer ─────────────────────────────────────────── */
    .footer {
      margin-top: 3rem; padding: 4rem 0 2rem;
      border-top: 1px solid var(--border);
      background: var(--bg-deep);
    }
    .footer-grid {
      display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 2rem;
    }
    @media (max-width: 768px) { .footer-grid { grid-template-columns: 1fr 1fr; } }
    .footer-heading {
      font-size: .8rem; text-transform: uppercase; letter-spacing: .08em;
      color: var(--text-muted); margin-bottom: .8rem; font-weight: 600;
    }
    .footer-grid a {
      display: block; color: var(--text-secondary); font-size: .9rem;
      margin-bottom: .4rem; font-weight: 400;
    }
    .footer-bottom {
      margin-top: 3rem; padding-top: 1.5rem; border-top: 1px solid var(--border);
      color: var(--text-muted); font-size: .8rem; text-align: center;
    }
  `],
})
export class AppComponent implements AfterViewInit {
  @ViewChild('viewer', { static: true }) viewer!: ElementRef<HTMLDivElement>;

  prompt = '';
  examples = EXAMPLES;
  spec = signal<Spec | null>(null);
  catalog = signal<Record<string, TemplateSchema>>({});
  stlUrl = signal<string | null>(null);
  metrics = signal<Metrics | null>(null);
  myDesigns = signal<Array<{id:number;template:string;summary_et:string;size_bytes:number;created_at:string}>>([]);
  suggestions = signal<string[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  adminStats = signal<any>(null);
  adminUsers = signal<any[]>([]);
  isAdmin = signal(false);

  catalogFor(name: string): TemplateSchema | undefined { return this.catalog()[name]; }
  schemaFor(name: string, param: string): ParamSchema | undefined { return this.catalog()[name]?.params[param]; }

  adminStatCards(): Array<{label:string;value:any;color:string}> {
    const s = this.adminStats();
    if (!s) return [];
    return [
      { label: 'Kasutajaid', value: s.totalUsers, color: 'var(--accent-2)' },
      { label: 'PRO', value: s.proUsers, color: 'var(--green)' },
      { label: 'Disaine kokku', value: s.totalDesigns, color: 'var(--text-primary)' },
      { label: 'Sel kuul', value: s.thisMonthDesigns, color: 'var(--amber)' },
      { label: 'Aktiivseid', value: s.thisMonthActiveUsers, color: '#f0abfc' },
    ];
  }

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private mesh?: THREE.Mesh;

  constructor(private http: HttpClient, public auth: AuthService) {}

  ngAfterViewInit() {
    const el = this.viewer.nativeElement;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x050a18);
    this.camera = new THREE.PerspectiveCamera(45, el.clientWidth / el.clientHeight, 1, 2000);
    this.camera.position.set(200, 200, 200);
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(el.clientWidth, el.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    el.appendChild(this.renderer.domElement);
    const controls = new OrbitControls(this.camera, this.renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.5));
    const light1 = new THREE.DirectionalLight(0x6366f1, 0.8);
    light1.position.set(2, 3, 1);
    this.scene.add(light1);
    const light2 = new THREE.DirectionalLight(0xa78bfa, 0.4);
    light2.position.set(-1, 2, -1);
    this.scene.add(light2);
    // Grid helper for professional look
    const grid = new THREE.GridHelper(300, 30, 0x1e293b, 0x111827);
    grid.position.y = -50;
    this.scene.add(grid);
    const animate = () => {
      requestAnimationFrame(animate);
      controls.update();
      this.renderer.render(this.scene, this.camera);
    };
    animate();
    // Responsive
    const ro = new ResizeObserver(() => {
      const w = el.clientWidth, h = el.clientHeight;
      this.camera.aspect = w / h;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(w, h);
    });
    ro.observe(el);
    this.http.get<Record<string, TemplateSchema>>('/api/templates').subscribe(c => this.catalog.set(c));
    if (this.auth.token()) {
      this.http.get<any>('/api/admin/stats').subscribe({ next: () => this.isAdmin.set(true), error: () => {} });
    }
  }

  paramKeys(s: Spec): string[] { return Object.keys(s.params || {}); }

  pickTemplate(name: string) {
    const tpl = this.catalog()[name];
    if (!tpl) return;
    const params: Record<string, number> = {};
    for (const [k, v] of Object.entries(tpl.params)) params[k] = v.default;
    this.spec.set({ template: name, params, summary_et: tpl.description });
    this.error.set(null);
    this.suggestions.set([]);
    this.fetchMetrics({ template: name, params, summary_et: tpl.description });
  }

  loadMyDesigns() {
    if (!this.auth.me()) return;
    this.http.get<any[]>('/api/designs').subscribe(xs => this.myDesigns.set(xs as any));
  }

  designStlUrl(id: number): string { return `/api/designs/${id}/stl?token=${this.auth.token()}`; }

  loadAdmin() {
    this.http.get<any>('/api/admin/stats').subscribe({
      next: s => {
        this.adminStats.set(s);
        this.isAdmin.set(true);
        this.http.get<any[]>('/api/admin/users').subscribe(u => this.adminUsers.set(u));
      },
      error: () => this.isAdmin.set(false),
    });
  }

  deleteDesign(id: number) {
    if (!confirm('Kustutada jäädavalt?')) return;
    this.http.delete(`/api/designs/${id}`).subscribe(() => this.loadMyDesigns());
  }

  humanDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('et-EE') + ' ' + d.toLocaleTimeString('et-EE', { hour: '2-digit', minute: '2-digit' });
  }

  tryExample(ex: Example) {
    this.prompt = ex.prompt;
    const s: Spec = { template: ex.template, params: { ...ex.params }, summary_et: ex.title };
    this.spec.set(s);
    this.fetchMetrics(s);
    document.getElementById('app')?.scrollIntoView({ behavior: 'smooth' });
  }

  upgrade(tier: 'hobi' | 'pro') {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    this.http.post<{ url: string }>('/api/billing/checkout', { tier }).subscribe({
      next: r => { window.location.href = r.url; },
      error: e => this.error.set(e.error?.message || 'Stripe pole veel seadistatud — tule tagasi peagi!'),
    });
  }

  updateParam(k: string, v: number) {
    const s = this.spec();
    if (!s) return;
    const next = { ...s, params: { ...s.params, [k]: +v } };
    this.spec.set(next);
    this.fetchMetrics(next);
  }

  private metricsTimer: any;
  fetchMetrics(s: Spec) {
    if (!this.auth.me()) return;
    clearTimeout(this.metricsTimer);
    this.metricsTimer = setTimeout(() => {
      this.http.post<Metrics>('/api/metrics', s).subscribe({
        next: m => this.metrics.set(m),
        error: () => this.metrics.set(null),
      });
    }, 250);
  }

  analyze() {
    this.error.set(null);
    this.loading.set(true);
    this.http.post<Spec>('/api/spec', { prompt: this.prompt }).subscribe({
      next: s => { this.spec.set(s); this.loading.set(false); this.fetchMetrics(s); },
      error: e => {
        const msg = e.error?.message || e.message;
        const sugg = e.error?.suggestions || [];
        if (sugg.length) {
          this.error.set(`${msg}\nProovi: ${sugg.join(', ')}`);
          this.suggestions.set(sugg);
        } else if (e.status === 429) {
          this.error.set('Liiga palju päringuid. Oota veidi või uuenda PRO-le.');
        } else if (e.status === 401) {
          this.error.set('Logi sisse, et kasutada AI-d.');
        } else {
          this.error.set(msg);
        }
        this.loading.set(false);
      },
    });
  }

  generate() {
    const s = this.spec();
    if (!s) return;
    this.loading.set(true);
    this.http.post('/api/generate', s, { responseType: 'arraybuffer' }).subscribe({
      next: buf => {
        const blob = new Blob([buf], { type: 'application/sla' });
        this.stlUrl.set(URL.createObjectURL(blob));
        this.renderSTL(buf);
        this.loading.set(false);
        this.auth.refreshMe();
      },
      error: e => {
        if (e.status === 402) this.error.set('Tasuta piir on täis. Uuenda PRO-le.');
        else if (e.status === 401) this.error.set('Logi sisse, et genereerida.');
        else this.error.set(e.error?.message || e.message);
        this.loading.set(false);
      },
    });
  }

  private renderSTL(buf: ArrayBuffer) {
    const loader = new STLLoader();
    const geom = loader.parse(buf);
    geom.center();
    if (this.mesh) this.scene.remove(this.mesh);
    const material = new THREE.MeshStandardMaterial({
      color: 0x6366f1,
      metalness: 0.15,
      roughness: 0.5,
      envMapIntensity: 1.0,
    });
    this.mesh = new THREE.Mesh(geom, material);
    this.scene.add(this.mesh);
    const box = new THREE.Box3().setFromObject(this.mesh);
    const size = box.getSize(new THREE.Vector3()).length();
    this.camera.position.set(size * 1.2, size * 0.8, size * 1.2);
    this.camera.lookAt(0, 0, 0);
  }
}
