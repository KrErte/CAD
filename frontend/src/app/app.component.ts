import { Component, ElementRef, ViewChild, AfterViewInit, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { EXAMPLES, FEATURED_EXAMPLES, Example } from './examples';
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

/** One actionable suggestion from the AI review. If `param`+`new_value` are set,
 *  the UI can offer a one-click auto-apply that tweaks the slider and regenerates. */
interface Suggestion {
  label_et: string;
  rationale_et: string;
  param?: string;
  new_value?: number;
}

/** Structured AI design review from /api/review — Claude-vision critique. */
interface Review {
  score: number;
  verdict_et: string;
  strengths: string[];
  weaknesses: string[];
  suggestions: Suggestion[];
}

/** Precise slicer-based preview. `source` tells us whether PrusaSlicer ran. */
interface Preview {
  source: 'slicer' | 'heuristic' | 'heuristic_slicer_failed';
  preset?: string;
  print_time_sec: number;
  print_time_human: string;
  filament_length_m?: number;
  filament_volume_cm3?: number;
  filament_g: number;
  filament_cost_eur: number;
  volume_cm3?: number;
  bbox_mm?: { x: number; y: number; z: number };
  overhang_risk?: boolean;
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
  selector: 'app-home',
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
          <a href="#how">{{ t('nav_how') }}</a>
          <a href="#darwin" style="color:var(--accent-2);font-weight:600">Darwin CAD</a>
          <a href="#gallery">{{ t('nav_gallery') }}</a>
          <a href="#examples">{{ t('nav_examples') }}</a>
          <a href="#pricing">{{ t('nav_pricing') }}</a>
          <select class="lang-select" [ngModel]="lang()" (ngModelChange)="setLang($event)">
            <option value="et">&#127466;&#127466; ET</option>
            <option value="en">&#127468;&#127463; EN</option>
            <option value="lv">&#127473;&#127483; LV</option>
            <option value="lt">&#127473;&#127481; LT</option>
          </select>
          <button (click)="goToAuth()" class="btn-login">
            {{ t('login') }}
          </button>
        </nav>
        <nav class="header-nav" *ngIf="auth.me() as m">
          <a href="#mydesigns" (click)="loadMyDesigns()">{{ t('my_designs') }}</a>
          <a href="#gallery">{{ t('nav_gallery') }}</a>
          <a href="#orders" (click)="loadMyOrders()">{{ t('my_orders') }}</a>
          <a href="#admin" (click)="loadAdmin()" *ngIf="isAdmin()" style="color:var(--amber)">Admin</a>
          <select class="lang-select" [ngModel]="lang()" (ngModelChange)="setLang($event)">
            <option value="et">&#127466;&#127466; ET</option>
            <option value="en">&#127468;&#127463; EN</option>
            <option value="lv">&#127473;&#127483; LV</option>
            <option value="lt">&#127473;&#127481; LT</option>
          </select>
          <span class="header-plan" [class.plan-pro]="m.plan === 'PRO'" [class.plan-business]="m.plan === 'BUSINESS'">{{ m.plan }}</span>
          <span class="header-quota" *ngIf="m.limit > 0">{{ m.used }}/{{ m.limit }}</span>
          <span class="header-quota" *ngIf="m.limit < 0">&#8734;</span>
          <button (click)="auth.logout()" class="btn-logout">Logi välja</button>
        </nav>
      </div>
    </header>

    <!-- ═══════ HERO — Darwin-animated ═══════ -->
    <section class="hero-bg">
      <div class="grid-overlay"></div>
      <div class="hero-content hero-with-demo">
        <div class="hero-left">
          <div class="badge animate-in">
            &#127466;&#127466;&nbsp; Maailma esimene evolutsiooniline text-to-CAD
          </div>
          <h1 class="hero-title animate-in animate-in-delay-1">
            Üks lause.<br>
            <span class="gradient-text">Kuus disaini.</span><br>
            AI valib parima.
          </h1>
          <p class="hero-subtitle animate-in animate-in-delay-2">
            Kirjelda eesti keeles — saad <strong>6 varianti hinnetega</strong>,
            valid lemmikud, <strong>AI evolveerib</strong> järgmise põlvkonna.
            Zoo, Backflip, Adam — kõik teevad «üks-prompt-üks-vastus».
            Meie evolveerime.
          </p>
          <div class="hero-actions animate-in animate-in-delay-3">
            <a href="#darwin" class="btn-cta">
              Vaata Darwinit tööl &rarr;
            </a>
            <a href="#app" class="btn-secondary">
              Proovi tavaline &darr;
            </a>
          </div>
          <div class="hero-stats animate-in animate-in-delay-4">
            <div class="hero-stat">
              <span class="hero-stat-number">6</span>
              <span class="hero-stat-label">varianti / prompt</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-number">30s</span>
              <span class="hero-stat-label">genereerimine</span>
            </div>
            <div class="hero-stat-divider"></div>
            <div class="hero-stat">
              <span class="hero-stat-number">STL+STEP</span>
              <span class="hero-stat-label">valmis</span>
            </div>
          </div>
        </div>

        <!-- Hero right: animeeritud Darwin-eelvaade — 6 SVG varianti loopivad sisse -->
        <div class="hero-right animate-in animate-in-delay-2">
          <div class="hero-demo card-glass">
            <div class="hero-demo-header">
              <div class="hero-demo-dot" style="background:#ff5f56"></div>
              <div class="hero-demo-dot" style="background:#ffbd2e"></div>
              <div class="hero-demo-dot" style="background:#27c93f"></div>
              <span class="hero-demo-label">darwin.cad — põlvkond {{ heroGen() }}</span>
            </div>
            <div class="hero-demo-prompt">
              &ldquo;{{ heroPrompt() }}&rdquo;
            </div>
            <div class="hero-demo-grid">
              <div class="hero-variant" *ngFor="let v of heroVariants(); let i = index"
                   [class.hero-variant-winner]="v.rank === 0"
                   [style.animation-delay]="(i * 80) + 'ms'">
                <div class="hero-variant-svg" [innerHTML]="v.svg"></div>
                <div class="hero-variant-meta">
                  <span class="hero-variant-rank">#{{ v.rank + 1 }}</span>
                  <span class="hero-variant-score">{{ v.score }}/10</span>
                </div>
              </div>
            </div>
            <div class="hero-demo-footer">
              <span style="color:var(--green)">&#9679;</span> AI hindas —
              parim: <strong>{{ heroWinnerReason() }}</strong>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════ FEATURED USE CASES — "Päris elust" ═══════ -->
    <section class="section" id="use-cases">
      <div class="container">
        <div class="section-header">
          <div class="badge">PÄRIS ELUST</div>
          <h2 class="section-title">Kolm asja, mida inimesed <span class="gradient-text">eile tegid</span></h2>
          <p class="section-subtitle">Klõpsa — tekst liigub redaktorisse ja sa näed kuidas töötab.</p>
        </div>
        <div class="usecase-grid">
          <div *ngFor="let ex of featuredExamples" class="usecase-card card-glass"
               (click)="tryExample(ex); scrollToApp()">
            <div class="usecase-emoji">{{ ex.emoji }}</div>
            <div class="usecase-pain">{{ ex.painPoint }}</div>
            <div class="usecase-solution">
              <div class="usecase-title">{{ ex.title }}</div>
              <div class="usecase-meta">{{ ex.useCase }}</div>
            </div>
            <div class="usecase-cta">
              Proovi seda <span style="margin-left:.3rem">&rarr;</span>
            </div>
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
                <td style="color:var(--green);font-weight:700">12.99–79 €/kuu</td>
                <td>Tasuta (aga kulutad tundide kaupa aega)</td>
                <td>5–50 €/tk + saadetus</td>
                <td>100–500 € + inseneri tunnitasu</td>
              </tr>
              <tr>
                <td><strong>ROI (ühe detaili peal)</strong></td>
                <td style="color:var(--green);font-weight:700">≈ 0.40 € per STL</td>
                <td>Aja väärtus 15 €/h × 2h = 30 €</td>
                <td>35 € (keskmine)</td>
                <td>200 € (keskmine)</td>
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
          <h2 class="section-title">Lihtne hinnakiri, mis kasvab sinuga kaasa.</h2>
          <p class="section-subtitle">
            Alusta tasuta. Uuenda, kui vajad rohkem mudeleid või API ligipääsu.
          </p>
        </div>

        <div class="pricing-grid" style="display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:1.5rem;max-width:960px;margin:0 auto">
          <!-- FREE -->
          <div class="pricing-card card-glass">
            <div class="pricing-tier">Free</div>
            <div class="pricing-price">0 €<span class="pricing-period">/kuu</span></div>
            <div class="pricing-tagline">Proovi ilma kaardita</div>
            <ul class="pricing-features">
              <li><strong>3 mudelit</strong> kuus</li>
              <li>Kõik mallid</li>
              <li>Eestikeelne AI</li>
              <li>Metrika (kaal, aeg, mõõdud)</li>
              <li style="color:var(--text-muted)">Ei API · Ei prioriteet</li>
            </ul>
            <button *ngIf="!auth.me()" class="pricing-btn" style="background:var(--bg-card)"
                    (click)="auth.loginWithGoogle()">Alusta tasuta</button>
            <div *ngIf="auth.me()?.plan === 'FREE'" class="pricing-current">Praegune plaan</div>
          </div>

          <!-- PRO (populaarne) -->
          <div class="pricing-card card-glass pricing-popular">
            <div class="pricing-badge-top">POPULAARSEIM</div>
            <div class="pricing-tier">Pro</div>
            <div class="pricing-price">14.99 €<span class="pricing-period">/kuu</span></div>
            <div class="pricing-tagline">Inseneridele ja 3D-printijatele</div>
            <ul class="pricing-features">
              <li><strong>50 mudelit</strong> kuus</li>
              <li>Kõik mallid + Darwin CAD</li>
              <li>STEP-eksport</li>
              <li>Freeform Python-gen (sandbox)</li>
              <li>Ajalugu + re-download</li>
              <li>Prioriteetne järjekord</li>
              <li>E-mail tugi (24h)</li>
            </ul>
            <button class="pricing-btn btn-cta" (click)="upgrade('pro')"
                    [disabled]="isCurrentPlan('PRO')">
              {{ isCurrentPlan('PRO') ? 'Aktiivne' : 'Uuenda Pro' }}
            </button>
          </div>

          <!-- BUSINESS -->
          <div class="pricing-card card-glass">
            <div class="pricing-tier">Business</div>
            <div class="pricing-price">49.99 €<span class="pricing-period">/kuu</span></div>
            <div class="pricing-tagline">Tiimidele ja API-integratsioonidele</div>
            <ul class="pricing-features">
              <li><strong>200 mudelit</strong> kuus</li>
              <li>Kõik Pro omadused</li>
              <li><strong>API ligipääs</strong></li>
              <li>Kommertslitsents</li>
              <li>Prioriteetne tugi (4h)</li>
              <li>Usage analytics</li>
            </ul>
            <button class="pricing-btn" style="background:var(--bg-card)" (click)="upgrade('business')"
                    [disabled]="isCurrentPlan('BUSINESS')">
              {{ isCurrentPlan('BUSINESS') ? 'Aktiivne' : 'Uuenda Business' }}
            </button>
          </div>
        </div>

        <!-- Subscription management for logged-in users -->
        <div *ngIf="auth.me() && auth.me()!.plan !== 'FREE'"
             style="text-align:center;margin-top:1.5rem">
          <button class="pricing-btn" style="background:var(--bg-card);padding:.6rem 1.2rem;font-size:.9rem"
                  (click)="openBillingPortal()">
            Halda tellimust
          </button>
        </div>

        <!-- Garantii + trust -->
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));
             gap:1rem;margin-top:2rem;text-align:center">
          <div style="padding:1rem">
            <div style="font-size:1.8rem">🛡️</div>
            <div style="font-weight:700;margin-top:.4rem">30 päeva tagasi­makse</div>
            <div style="color:var(--text-muted);font-size:.85rem">Küsimata põhjusi</div>
          </div>
          <div style="padding:1rem">
            <div style="font-size:1.8rem">🇪🇪</div>
            <div style="font-weight:700;margin-top:.4rem">Eesti ettevõte</div>
            <div style="color:var(--text-muted);font-size:.85rem">Reg-nr + e-arve</div>
          </div>
          <div style="padding:1rem">
            <div style="font-size:1.8rem">⏱️</div>
            <div style="font-weight:700;margin-top:.4rem">Tühista 1 kliki</div>
            <div style="color:var(--text-muted);font-size:.85rem">Ei küsi "miks"</div>
          </div>
          <div style="padding:1rem">
            <div style="font-size:1.8rem">🔒</div>
            <div style="font-weight:700;margin-top:.4rem">GDPR + Stripe</div>
            <div style="color:var(--text-muted);font-size:.85rem">Kaardi­andmed meie süsteemis ei salvesta</div>
          </div>
        </div>

        <p style="color:var(--text-muted);font-size:.85rem;margin-top:2rem;text-align:center">
          Kõik hinnad sis. KM (22%). B2B e-arve saadaval. Aasta-plaanid tasuta 14-päeva proovi­periood.
        </p>
      </div>
    </section>

    <!-- ═══════ FAQ ═══════ -->
    <section class="section" id="faq">
      <div class="container" style="max-width:820px">
        <div class="section-header">
          <div class="badge">KKK</div>
          <h2 class="section-title">Küsimused, mida teised küsivad</h2>
        </div>
        <div class="faq-list">
          <details class="faq-item card-glass">
            <summary>Miks kallim kui Free-Hobi-Pro mängu­plaan oli?</summary>
            <p>
              Varasem 4.99 € oli turu tunnetamise eksperiment. Tegelik kulu — Claude API,
              CadQuery render, PrusaSlicer, salvestamine — tähendab, et piiramatu STL
              4.99 € eest ei ole jätku­suutlik. Vali: odav ja suletud 6 kuu pärast, või
              mõistlik ja kestev. Maker 12.99 € on endiselt <strong>35% odavam</strong>
              kui Zoo.dev ($20 ≈ 18.50 €).
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Mida Darwin CAD tegelikult annab?</summary>
            <p>
              Ühe promptiga saad 6 varianti, AI paneb neile hinded (1–10) ja põhjenduse.
              Valid lemmikud, klõpsad "Evolveeri" — saad järgmise põlvkonna nende
              omadustega. See pole keegi teine maailmas — Zoo, Backflip, Adam CAD teevad
              "üks-prompt-üks-vastus". 20 sessiooni Pro-plaanis = 120 disaini­varianti.
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Kas ma võin STL/STEP müüa või firmas kasutada?</summary>
            <p>
              <strong>Pro ja Team plaanides jah</strong> — täis kommertslitsents, saad
              toodet müüa, 3D-printida ja firmas kasutada ilma lisatasuta. Free ja Maker
              on isiklikuks kasutuseks — kui tekib äri, upgrade'id.
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Mis saab, kui tühistan?</summary>
            <p>
              Ajaloolised STL/STEP failid jäävad sulle alles (Maker+) ja saad neid 90
              päeva veel alla laadida. Uusi generatsioone Free-piiranguga (5/kuu).
              Tühista ühe klõpsuga seadete alt, ei küsi põhjust.
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Kas raha tagasi, kui ei meeldi?</summary>
            <p>
              Jah — <strong>30 päeva tingimusteta</strong>. Kirjuta
              <a href="mailto:refund@tehisaicad.ee">refund&#64;tehisaicad.ee</a> ja raha tuleb
              5 tööpäevaga tagasi. Ei küsi "aga miks?" — see on sinu raha.
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Kas saan e-arve / B2B-arve?</summary>
            <p>
              Jah, Team ja Enterprise plaanidel automaatne e-arve igal kuul. Maker/Pro
              plaanil küsi kord (billing&#64;tehisaicad.ee), lülitame sisse.
            </p>
          </details>
          <details class="faq-item card-glass">
            <summary>Miks peaksin uskuma, et see pole vaporware?</summary>
            <p>
              Koodi­baas on lahtine (<a href="https://github.com/KrErte/CAD" target="_blank">GitHub</a>),
              worker on CadQuery (OpenCascade) — 20 aastat vana tõsine B-Rep kernel, mitte
              AI-hallutsinatsioon. STL-id on matemaatiliselt korrektsed, mitte "paistavad
              õiged". STEP failid avanevad SolidWorks / Fusion / FreeCAD.
            </p>
          </details>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ DARWIN CAD ═══════ -->
    <section id="darwin" class="section darwin-section">
      <div class="container">
        <div class="section-header">
          <div class="badge" style="background:rgba(139,92,246,0.15);color:#c4b5fd">
            ENNEOLEMATU · PRO+ PLAAN
          </div>
          <h2 class="section-title">
            Darwin CAD — <span class="gradient-text">AI evolveerib sinu disaini</span>
          </h2>
          <p class="section-subtitle">
            Üks lause → 6 varianti → AI paneb hinded → valid lemmikud → järgmine põlvkond.
            Nii disainib bioloogia. Nii ei disaini mitte keegi teine text-to-CAD turul.
          </p>
        </div>

        <div class="darwin-app card-glass">
          <div class="darwin-input-row">
            <input class="darwin-prompt" type="text" [(ngModel)]="darwinPrompt"
                   placeholder="nt: riiuliklamber 32mm veetorule, 5kg koormus, 2 kruvi M4"
                   (keyup.enter)="darwinSeed()">
            <select class="darwin-n" [(ngModel)]="darwinN">
              <option [ngValue]="4">4 varianti</option>
              <option [ngValue]="6">6 varianti</option>
              <option [ngValue]="8">8 varianti</option>
            </select>
            <button class="btn-cta darwin-go" (click)="darwinSeed()"
                    [disabled]="darwinLoading() || !darwinPrompt">
              {{ darwinLoading() ? 'Evolveerib...' : 'Alusta evolutsiooni' }}
            </button>
          </div>

          <div *ngIf="darwinVariants().length" class="darwin-gen-header">
            <div>
              <span class="darwin-gen-badge">Põlvkond {{ darwinGeneration() }}</span>
              <span class="darwin-gen-info">
                {{ darwinVariants().length }} varianti ·
                <span *ngIf="darwinRankingSource() === 'claude'" style="color:var(--green)">AI Vision hindas</span>
                <span *ngIf="darwinRankingSource() === 'heuristic'" style="color:var(--amber)">Heuristiline hinnang</span>
              </span>
            </div>
            <div class="darwin-actions">
              <button class="btn-secondary" (click)="darwinReset()">Alusta algusest</button>
              <button class="btn-cta" (click)="darwinEvolve()"
                      [disabled]="!darwinSelected().size || darwinLoading()">
                Evolveeri valitud ({{ darwinSelected().size }}) →
              </button>
            </div>
          </div>

          <div *ngIf="darwinVariants().length" class="darwin-variants">
            <div *ngFor="let v of darwinVariants(); let i = index"
                 class="darwin-variant card-glass"
                 [class.darwin-variant-selected]="darwinSelected().has(v.variant_id)"
                 [class.darwin-variant-winner]="v.rank === 0"
                 (click)="darwinToggleSelect(v.variant_id)">
              <div class="darwin-variant-badges">
                <span class="darwin-rank-badge" [class.darwin-rank-1]="v.rank === 0">
                  #{{ v.rank + 1 }}
                </span>
                <span class="darwin-score-badge"
                      [class.darwin-score-high]="v.score >= 8"
                      [class.darwin-score-mid]="v.score >= 5 && v.score < 8">
                  {{ v.score }}/10
                </span>
              </div>
              <div class="darwin-variant-svg" [innerHTML]="v.svg_safe"></div>
              <div class="darwin-variant-metrics" *ngIf="v.metrics as m">
                <span>{{ m.weight_g_pla | number:'1.0-0' }} g</span>
                <span>· {{ m.print_time_min_estimate | number:'1.0-0' }} min</span>
                <span *ngIf="m.overhang_risk" style="color:var(--amber)">· ⚠ kalded</span>
              </div>
              <div class="darwin-variant-reason">{{ v.reasoning_et }}</div>
              <div *ngIf="darwinSelected().has(v.variant_id)" class="darwin-selected-check">✓ Valitud</div>
            </div>
          </div>

          <div *ngIf="!darwinVariants().length && !darwinLoading()" class="darwin-empty">
            <div class="darwin-empty-icon">🧬</div>
            <p>Kirjelda mida tahad ja vajuta «Alusta evolutsiooni»</p>
            <p style="color:var(--text-muted);font-size:.85rem;margin-top:.5rem">
              Kuluta üks päev insenerile — või üks minut Darwinile.
            </p>
          </div>

          <div *ngIf="darwinLoading()" class="darwin-empty">
            <div class="darwin-empty-icon" style="animation:spin 1s linear infinite">⚙️</div>
            <p>AI genereerib {{ darwinN }} varianti ja hindab...</p>
          </div>

          <div *ngIf="darwinError()" class="darwin-error">
            ❌ {{ darwinError() }}
          </div>
        </div>

        <div *ngIf="darwinHistory().length > 1" class="darwin-history">
          <h4 style="margin:2rem 0 .8rem;color:var(--text-muted);font-size:.85rem;letter-spacing:.1em;text-transform:uppercase">Evolutsioon</h4>
          <div class="darwin-history-row">
            <div *ngFor="let gen of darwinHistory(); let i = index" class="darwin-history-gen">
              <div class="darwin-history-svg" [innerHTML]="gen.winner_svg"></div>
              <div class="darwin-history-label">Põlvkond {{ i + 1 }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ FREEFORM EXPERT MODE ═══════ -->
    <section id="expert" class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge" style="background:rgba(251,191,36,0.15);color:#fde68a">
            EKSPERT-REŽIIM · PRO PLAAN
          </div>
          <h2 class="section-title">
            Freeform — <span class="gradient-text">kirjuta CadQuery Pythonit</span>
          </h2>
          <p class="section-subtitle">
            Kui 23 malli pole piisav, kirjuta ise. Sandbox: 15s timeout, 512MB mälu,
            AST-whitelist, ainult <code>cadquery + math + random</code>.
            Output: STL + STEP kohe.
          </p>
        </div>

        <div class="expert-grid card-glass">
          <div class="expert-editor-wrap">
            <div class="expert-header">
              <span class="expert-header-title">freeform.py</span>
              <div class="expert-header-actions">
                <button class="btn-secondary" (click)="ffLoadTemplate()">Laadi näidis</button>
                <button class="btn-cta" (click)="ffRun()" [disabled]="ffLoading() || !ffCode">
                  {{ ffLoading() ? 'Jookseb...' : 'Run ▶' }}
                </button>
              </div>
            </div>
            <textarea class="expert-editor" rows="18" spellcheck="false"
                      [(ngModel)]="ffCode"
                      (keydown)="ffKeydown($event)"
                      placeholder="import cadquery as cq&#10;&#10;result = cq.Workplane('XY').box(40, 20, 10)\n                   .faces('>Z').workplane()\n                   .hole(6)"></textarea>
            <div class="expert-footer">
              <span>Ctrl+Enter = Run</span>
              <span>·</span>
              <span>Ainult <code>result</code> muutuja eksporditakse STL+STEP-i</span>
            </div>
          </div>
          <div class="expert-output-wrap">
            <div class="expert-header">
              <span class="expert-header-title">Output</span>
              <span *ngIf="ffResult() as r"
                    [style.color]="r.ok ? 'var(--green)' : '#ef4444'">
                {{ r.ok ? '✓ OK · ' + r.elapsed_ms + 'ms' : '❌ ' + (r.error_kind || 'viga') }}
              </span>
            </div>
            <div class="expert-output">
              <div *ngIf="!ffResult() && !ffLoading()" class="expert-output-placeholder">
                Kirjuta CadQuery kood vasakule ja vajuta Run — STL + STEP ilmuvad siia.
              </div>
              <div *ngIf="ffLoading()" class="expert-output-placeholder">
                <div style="font-size:2rem;animation:spin 1s linear infinite">⚙️</div>
                <p>Sandbox jookseb... (max 15 sekundit)</p>
              </div>
              <div *ngIf="ffResult() as r">
                <pre *ngIf="!r.ok" class="expert-error">{{ r.error }}</pre>
                <div *ngIf="r.ok">
                  <div class="expert-downloads">
                    <a [href]="ffStlUrl()" download="model.stl" class="btn-cta">
                      ⬇ STL
                    </a>
                    <a *ngIf="ffStepUrl()" [href]="ffStepUrl()" download="model.step" class="btn-secondary">
                      ⬇ STEP
                    </a>
                  </div>
                  <div class="expert-success-meta">
                    Elapsed: {{ r.elapsed_ms }}ms
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
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
              <label style="margin-top:0">{{ t('input_label') }}</label>
              <div class="input-with-voice">
                <textarea rows="3" [(ngModel)]="prompt"
                  [placeholder]="t('input_placeholder')"></textarea>
                <button class="voice-btn" (click)="toggleVoice()"
                        [class.voice-active]="voiceActive()"
                        [title]="voiceActive() ? t('voice_stop') : t('voice_start')">
                  <span *ngIf="!voiceActive()">&#127908;</span>
                  <span *ngIf="voiceActive()" class="voice-pulse">&#128308;</span>
                </button>
              </div>
              <div class="collab-row" *ngIf="auth.me()">
                <button class="btn-collab" (click)="createCollabRoom()" *ngIf="!collabRoomId()">
                  &#128101; {{ t('collab_start') }}
                </button>
                <div *ngIf="collabRoomId()" class="collab-info">
                  &#128101; {{ t('collab_room') }}: <code>{{ collabRoomId() }}</code>
                  <span class="collab-users">{{ collabUsers() }} {{ t('collab_online') }}</span>
                </div>
                <input *ngIf="!collabRoomId()" class="collab-join-input" type="text" [(ngModel)]="collabJoinId"
                       [placeholder]="t('collab_join_placeholder')" (keyup.enter)="joinCollabRoom()">
                <button *ngIf="!collabRoomId() && collabJoinId" class="btn-collab" (click)="joinCollabRoom()">
                  {{ t('collab_join') }}
                </button>
              </div>
              <button *ngIf="auth.me()" style="margin-top:1rem;width:100%" (click)="analyze()" [disabled]="loading()">
                {{ loading() ? t('analyzing') : t('analyze') }}
              </button>
              <button *ngIf="!auth.me()" style="margin-top:1rem;width:100%" (click)="auth.loginWithGoogle()">
                {{ t('login_to_generate') }}
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
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.6rem">
                <h4 style="margin:0;color:var(--text-secondary)">Detaili näitajad</h4>
                <span *ngIf="preview() as p" class="preview-badge"
                      [class.preview-exact]="p.source === 'slicer'"
                      [title]="p.source === 'slicer' ? 'Reaalsed numbrid PrusaSlicer CLI-st' : 'Hinnang (slicer pole saadaval)'">
                  {{ p.source === 'slicer' ? '&#10003; Täpne' : '&#126; Hinnang' }}
                </span>
                <span *ngIf="previewLoading()" class="preview-badge preview-loading">Arvutan...</span>
              </div>
              <div class="metrics-grid">
                <div class="metric"><span class="metric-label">Maht</span><span class="metric-value">{{ m.volume_cm3 }} cm³</span></div>
                <div class="metric"><span class="metric-label">Mõõdud</span><span class="metric-value">{{ m.bbox_mm.x }}×{{ m.bbox_mm.y }}×{{ m.bbox_mm.z }}</span></div>
                <div class="metric">
                  <span class="metric-label">Kaal</span>
                  <span class="metric-value">
                    {{ (preview()?.filament_g ?? m.weight_g_pla) | number:'1.0-1' }}g
                    <small>{{ preview()?.preset === 'petg_default' ? 'PETG' : 'PLA' }}</small>
                  </span>
                </div>
                <div class="metric">
                  <span class="metric-label">Prindiaeg</span>
                  <span class="metric-value">
                    <ng-container *ngIf="preview() as p; else estTime">{{ p.print_time_human }}</ng-container>
                    <ng-template #estTime>~{{ m.print_time_min_estimate }} min</ng-template>
                  </span>
                </div>
                <div class="metric" *ngIf="preview() as p">
                  <span class="metric-label">Filament</span>
                  <span class="metric-value">{{ p.filament_length_m | number:'1.0-2' }} m</span>
                </div>
                <div class="metric" *ngIf="preview() as p">
                  <span class="metric-label">Materjali hind</span>
                  <span class="metric-value" style="color:var(--green)">{{ p.filament_cost_eur | number:'1.2-2' }} €</span>
                </div>
              </div>
              <div *ngIf="m.overhang_risk" class="overhang-warning">
                &#9888;&#65039; Võib vajada tugistruktuuri (supports)
              </div>
            </div>

            <!-- Download + Export -->
            <div class="card-glass download-card" *ngIf="stlUrl()">
              <div class="download-row">
                <a [href]="stlUrl()" download="model.stl" class="download-link">
                  &#11015; STL
                </a>
                <button class="download-link download-step" (click)="downloadStep()" [disabled]="stepLoading()">
                  {{ stepLoading() ? '...' : '&#11015; STEP' }}
                </button>
              </div>
              <div class="action-row">
                <button class="review-btn"
                        (click)="askReview()"
                        [disabled]="reviewLoading()"
                        *ngIf="!review()">
                  <span *ngIf="!reviewLoading()">&#129302; {{ t('ask_review') }}</span>
                  <span *ngIf="reviewLoading()">&#9203; {{ t('reviewing') }}</span>
                </button>
                <button class="order-btn" (click)="showOrderModal.set(true); fetchQuote()">
                  &#128230; {{ t('order_print') }}
                </button>
                <button class="share-btn" (click)="showShareModal.set(true)">
                  &#127760; {{ t('share_gallery') }}
                </button>
              </div>
              <div *ngIf="reviewError()" class="review-error">{{ reviewError() }}</div>
            </div>

            <!-- Order modal -->
            <div class="card-glass" *ngIf="showOrderModal()">
              <h4>&#128230; {{ t('order_title') }}</h4>
              <div class="order-form">
                <label>{{ t('material') }}</label>
                <select [(ngModel)]="orderMaterial">
                  <option value="PLA">PLA</option>
                  <option value="PETG">PETG</option>
                </select>
                <label>{{ t('color') }}</label>
                <select [(ngModel)]="orderColor">
                  <option value="must">Must</option>
                  <option value="valge">Valge</option>
                  <option value="hall">Hall</option>
                  <option value="punane">Punane</option>
                  <option value="sinine">Sinine</option>
                </select>
                <label>{{ t('quantity') }}</label>
                <input type="number" [(ngModel)]="orderQty" min="1" max="100">
                <label>{{ t('shipping_name') }}</label>
                <input type="text" [(ngModel)]="orderName">
                <label>{{ t('shipping_address') }}</label>
                <input type="text" [(ngModel)]="orderAddress">
                <label>{{ t('city') }}</label>
                <input type="text" [(ngModel)]="orderCity">
                <label>{{ t('zip') }}</label>
                <input type="text" [(ngModel)]="orderZip">
                <label>{{ t('country') }}</label>
                <select [(ngModel)]="orderCountry">
                  <option value="EE">Eesti</option>
                  <option value="LV">Latvija</option>
                  <option value="LT">Lietuva</option>
                  <option value="FI">Suomi</option>
                  <option value="SE">Sverige</option>
                  <option value="DE">Deutschland</option>
                </select>
              </div>
              <div *ngIf="orderQuote()" class="order-quote">
                <strong>{{ t('total') }}: {{ orderQuote()?.total_eur | number:'1.2-2' }} EUR</strong>
                <div style="font-size:.8rem;color:var(--text-muted)">
                  {{ t('shipping') }}: {{ orderQuote()?.shipping_eur }} EUR ·
                  {{ t('delivery') }}: {{ orderQuote()?.estimated_days }} {{ t('days') }}
                </div>
              </div>
              <div style="display:flex;gap:.5rem;margin-top:1rem">
                <button class="btn-cta" (click)="placeOrder()" [disabled]="orderLoading()">
                  {{ orderLoading() ? '...' : t('place_order') }}
                </button>
                <button class="btn-secondary" (click)="showOrderModal.set(false)">{{ t('cancel') }}</button>
              </div>
              <div *ngIf="orderSuccess()" style="color:var(--green);margin-top:.5rem">{{ orderSuccess() }}</div>
            </div>

            <!-- Share to gallery modal -->
            <div class="card-glass" *ngIf="showShareModal()">
              <h4>&#127760; {{ t('share_title') }}</h4>
              <label>{{ t('design_title') }}</label>
              <input type="text" [(ngModel)]="shareTitle" [placeholder]="t('share_title_placeholder')">
              <label>{{ t('description') }}</label>
              <textarea rows="2" [(ngModel)]="shareDesc"></textarea>
              <label>{{ t('tags') }}</label>
              <input type="text" [(ngModel)]="shareTags" placeholder="riiuliklamber, toru, DIY">
              <div style="display:flex;gap:.5rem;margin-top:1rem">
                <button class="btn-cta" (click)="shareToGallery()">{{ t('share') }}</button>
                <button class="btn-secondary" (click)="showShareModal.set(false)">{{ t('cancel') }}</button>
              </div>
            </div>

            <!-- AI Design Review card -->
            <div class="card-glass review-card" *ngIf="review() as r">
              <div class="review-header">
                <div class="review-score" [style.color]="scoreColor(r.score)"
                     [style.borderColor]="scoreColor(r.score)">
                  {{ r.score }}<small>/10</small>
                </div>
                <div class="review-verdict">
                  <div class="review-label">AI ülevaade</div>
                  <div class="review-text">{{ r.verdict_et }}</div>
                </div>
              </div>

              <div class="review-section" *ngIf="r.strengths?.length">
                <h5>&#10003; Tugevused</h5>
                <ul><li *ngFor="let s of r.strengths">{{ s }}</li></ul>
              </div>

              <div class="review-section" *ngIf="r.weaknesses?.length">
                <h5>&#9888; Mured</h5>
                <ul><li *ngFor="let w of r.weaknesses">{{ w }}</li></ul>
              </div>

              <div class="review-section" *ngIf="r.suggestions?.length">
                <h5>&#128161; Soovitused</h5>
                <div class="suggestion-list">
                  <button *ngFor="let sug of r.suggestions"
                          class="suggestion-item"
                          [class.suggestion-actionable]="sug.param && sug.new_value !== null && sug.new_value !== undefined"
                          (click)="applySuggestion(sug)"
                          [title]="sug.rationale_et">
                    <div class="sug-label">
                      {{ sug.label_et }}
                      <span *ngIf="sug.param && sug.new_value !== null && sug.new_value !== undefined"
                            class="sug-apply">&#8634; Rakenda</span>
                    </div>
                    <div class="sug-reason">{{ sug.rationale_et }}</div>
                  </button>
                </div>
              </div>

              <button class="review-dismiss" (click)="review.set(null)">Sulge ülevaade</button>
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
            <div style="display:flex;gap:.5rem;margin-top:.8rem;flex-wrap:wrap">
              <a [href]="designStlUrl(d.id)" download class="design-download">&#11015; STL</a>
              <button (click)="loadVersions(d.id)" class="design-download">&#128338; Versioonid</button>
              <button (click)="deleteDesign(d.id)" class="design-delete">&#128465;</button>
            </div>
            <div *ngIf="versionsForDesign() === d.id && designVersions().length" class="versions-list">
              <div *ngFor="let v of designVersions()" class="version-item">
                <span>v{{ v.version }} · {{ humanDate(v.created_at) }} · {{ (v.size_bytes/1024) | number:'1.0-0' }} KB</span>
                <button class="btn-collab" (click)="rollbackVersion(d.id, v.version)">&#8634; Taasta</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════ GALLERY ═══════ -->
    <section id="gallery" class="section">
      <div class="container">
        <div class="section-header">
          <div class="badge">{{ t('gallery_badge') }}</div>
          <h2 class="section-title">{{ t('gallery_title') }}</h2>
          <p class="section-subtitle">{{ t('gallery_subtitle') }}</p>
        </div>
        <div class="gallery-controls">
          <input type="text" class="gallery-search" [(ngModel)]="gallerySearch"
                 [placeholder]="t('gallery_search')" (keyup.enter)="loadGallery()">
          <div class="gallery-sort">
            <button [class.active]="gallerySort() === 'new'" (click)="gallerySort.set('new'); loadGallery()">{{ t('newest') }}</button>
            <button [class.active]="gallerySort() === 'popular'" (click)="gallerySort.set('popular'); loadGallery()">{{ t('popular') }}</button>
          </div>
        </div>
        <div class="gallery-grid">
          <div *ngFor="let g of galleryItems()" class="gallery-card card-glass">
            <div class="gallery-card-header">
              <strong>{{ g.title }}</strong>
              <span class="gallery-author">{{ g.author }}</span>
            </div>
            <div *ngIf="g.description" class="gallery-desc">{{ g.description }}</div>
            <div class="gallery-meta">
              <span class="gallery-template"><code>{{ g.template }}</code></span>
              <span *ngIf="g.tags" class="gallery-tags">{{ g.tags }}</span>
            </div>
            <div class="gallery-actions">
              <button class="gallery-like" [class.liked]="g.liked_by_me" (click)="toggleGalleryLike(g)">
                {{ g.liked_by_me ? '&#10084;' : '&#9825;' }} {{ g.likes }}
              </button>
              <button class="gallery-fork" (click)="forkGalleryDesign(g)">
                &#128260; Fork ({{ g.forks }})
              </button>
              <a class="gallery-dl" [href]="'/api/gallery/' + g.id + '/stl'" download>
                &#11015; STL
              </a>
            </div>
          </div>
        </div>
        <div *ngIf="!galleryItems().length" style="text-align:center;color:var(--text-muted);padding:3rem">
          {{ t('gallery_empty') }}
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

    <!-- ═══════ MY ORDERS ═══════ -->
    <section id="orders" *ngIf="auth.me() && myOrders().length" class="section">
      <div class="container">
        <h2 class="section-title">{{ t('my_orders') }}</h2>
        <div class="designs-grid">
          <div *ngFor="let o of myOrders()" class="card-glass design-card">
            <strong>#{{ o.id }}</strong>
            <div style="color:var(--text-muted);font-size:.8rem;margin-top:.3rem">
              {{ o.material }} · {{ o.color }} · {{ o.quantity }}x · {{ o.price_eur }} EUR
            </div>
            <div class="order-status" [class]="'order-status-' + o.status">{{ o.status }}</div>
            <div style="font-size:.75rem;color:var(--text-muted)">{{ humanDate(o.created_at) }}</div>
          </div>
        </div>
      </div>
    </section>

    <div class="section-divider"></div>

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
    .plan-business { background: rgba(139,92,246,0.15); color: #a78bfa; }
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
    .preview-badge {
      padding: .2rem .55rem; border-radius: 999px; font-size: .7rem; font-weight: 700;
      background: var(--bg-card); color: var(--text-muted); border: 1px solid var(--border);
      letter-spacing: .02em;
    }
    .preview-exact { color: var(--green); border-color: rgba(52,211,153,0.3); background: rgba(52,211,153,0.08); }
    .preview-loading { color: var(--accent-2); animation: pulse 1.2s ease-in-out infinite; }
    @keyframes pulse { 50% { opacity: .5; } }
    .metric-value small { font-size: .72rem; color: var(--text-muted); font-weight: 500; margin-left: .2rem; }

    /* AI Review */
    .review-btn {
      width: 100%; margin-top: .8rem; padding: .8rem 1rem; border-radius: var(--radius-md);
      background: linear-gradient(135deg, rgba(139,92,246,0.18), rgba(99,102,241,0.12));
      border: 1px solid rgba(139,92,246,0.35);
      color: var(--text-primary); font-weight: 600; font-size: .92rem; cursor: pointer;
      transition: all .15s ease;
    }
    .review-btn:hover:not(:disabled) {
      background: linear-gradient(135deg, rgba(139,92,246,0.28), rgba(99,102,241,0.2));
      border-color: rgba(139,92,246,0.55); transform: translateY(-1px);
    }
    .review-btn:disabled { opacity: .6; cursor: wait; }
    .review-error {
      margin-top: .6rem; padding: .5rem .7rem; border-radius: var(--radius-md);
      background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.25);
      color: #fca5a5; font-size: .82rem;
    }
    .review-card {
      margin-top: 1rem; padding: 1.4rem 1.3rem 1rem;
      background: linear-gradient(160deg, rgba(139,92,246,0.08), rgba(15,23,42,0.6));
      border: 1px solid rgba(139,92,246,0.25);
    }
    .review-header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; }
    .review-score {
      flex: 0 0 auto; display: flex; align-items: baseline; justify-content: center;
      min-width: 72px; padding: .6rem .3rem; border-radius: var(--radius-md);
      border: 2px solid; font-size: 2rem; font-weight: 800;
      background: rgba(0,0,0,0.25); font-family: var(--font-mono, monospace);
    }
    .review-score small { font-size: .7rem; opacity: .65; margin-left: .1rem; font-weight: 600; }
    .review-verdict { flex: 1; min-width: 0; }
    .review-label {
      font-size: .65rem; text-transform: uppercase; letter-spacing: .1em;
      color: var(--text-muted); font-weight: 700; margin-bottom: .25rem;
    }
    .review-text { color: var(--text-primary); font-size: .95rem; line-height: 1.4; }
    .review-section { margin-top: 1rem; }
    .review-section h5 {
      font-size: .78rem; text-transform: uppercase; letter-spacing: .06em;
      color: var(--text-secondary); margin: 0 0 .5rem; font-weight: 700;
    }
    .review-section ul { margin: 0; padding-left: 1.2rem; color: var(--text-secondary); }
    .review-section ul li { margin-bottom: .3rem; font-size: .88rem; line-height: 1.4; }
    .suggestion-list { display: flex; flex-direction: column; gap: .5rem; }
    .suggestion-item {
      text-align: left; padding: .7rem .85rem; border-radius: var(--radius-md);
      background: rgba(15,23,42,0.5); border: 1px solid var(--border);
      color: var(--text-primary); cursor: default; transition: all .15s ease;
      display: flex; flex-direction: column; gap: .25rem;
    }
    .suggestion-item.suggestion-actionable {
      cursor: pointer; border-color: rgba(99,102,241,0.4);
      background: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(15,23,42,0.5));
    }
    .suggestion-item.suggestion-actionable:hover {
      transform: translateY(-1px); border-color: rgba(99,102,241,0.65);
      background: linear-gradient(135deg, rgba(99,102,241,0.18), rgba(15,23,42,0.5));
    }
    .sug-label {
      font-weight: 600; font-size: .9rem; display: flex; justify-content: space-between;
      align-items: center; gap: .5rem;
    }
    .sug-apply {
      font-size: .7rem; font-weight: 700; padding: .18rem .5rem; border-radius: 999px;
      background: rgba(99,102,241,0.25); color: var(--accent-2); letter-spacing: .03em;
    }
    .sug-reason { font-size: .8rem; color: var(--text-muted); line-height: 1.35; }
    .review-dismiss {
      display: block; width: 100%; margin-top: 1rem; padding: .5rem;
      background: transparent; border: none; color: var(--text-muted);
      font-size: .78rem; cursor: pointer; letter-spacing: .03em;
    }
    .review-dismiss:hover { color: var(--text-secondary); }

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

    /* ── Language selector ─────────────────────────────── */
    .lang-select {
      background: var(--bg-card); color: var(--text-primary); border: 1px solid var(--border);
      padding: .25rem .4rem; border-radius: 6px; font-size: .8rem; cursor: pointer;
    }

    /* ── Voice button ──────────────────────────────────── */
    .input-with-voice { position: relative; }
    .input-with-voice textarea { width: 100%; padding-right: 3rem; }
    .voice-btn {
      position: absolute; right: .5rem; top: .5rem;
      background: var(--bg-card); border: 1px solid var(--border);
      padding: .4rem .6rem; border-radius: 8px; font-size: 1.2rem; cursor: pointer;
      transition: all .15s ease;
    }
    .voice-btn:hover { border-color: var(--accent); }
    .voice-active { border-color: #ef4444; background: rgba(239,68,68,0.1); }
    .voice-pulse { animation: pulse 0.8s ease-in-out infinite; }

    /* ── Collaboration ─────────────────────────────────── */
    .collab-row {
      display: flex; gap: .5rem; margin-top: .5rem; align-items: center; flex-wrap: wrap;
    }
    .btn-collab {
      background: rgba(99,102,241,0.12); border: 1px solid rgba(99,102,241,0.3);
      color: var(--accent-2); padding: .3rem .7rem; font-size: .8rem; border-radius: 6px; cursor: pointer;
    }
    .btn-collab:hover { border-color: var(--accent); }
    .collab-info {
      font-size: .8rem; color: var(--text-secondary); display: flex; align-items: center; gap: .4rem;
    }
    .collab-users { color: var(--green); font-weight: 600; }
    .collab-join-input {
      flex: 1; min-width: 100px; padding: .3rem .5rem; font-size: .8rem;
      background: var(--bg-card); border: 1px solid var(--border); border-radius: 6px;
      color: var(--text-primary);
    }

    /* ── Download row ──────────────────────────────────── */
    .download-row { display: flex; gap: .8rem; justify-content: center; align-items: center; }
    .download-step {
      background: transparent; border: none; cursor: pointer;
      color: var(--accent-2); font-weight: 700; font-size: 1.1rem; text-decoration: none;
    }
    .download-step:hover { color: #c4b5fd; }
    .action-row { display: flex; gap: .5rem; margin-top: .8rem; flex-wrap: wrap; }
    .action-row button { flex: 1; min-width: 120px; }
    .order-btn {
      padding: .6rem 1rem; border-radius: var(--radius-md);
      background: rgba(52,211,153,0.12); border: 1px solid rgba(52,211,153,0.3);
      color: var(--green); font-weight: 600; font-size: .85rem; cursor: pointer;
    }
    .order-btn:hover { border-color: var(--green); }
    .share-btn {
      padding: .6rem 1rem; border-radius: var(--radius-md);
      background: rgba(99,102,241,0.12); border: 1px solid rgba(99,102,241,0.3);
      color: var(--accent-2); font-weight: 600; font-size: .85rem; cursor: pointer;
    }
    .share-btn:hover { border-color: var(--accent); }

    /* ── Order form ────────────────────────────────────── */
    .order-form { display: grid; grid-template-columns: auto 1fr; gap: .4rem .8rem; align-items: center; }
    .order-form label { font-size: .85rem; color: var(--text-secondary); }
    .order-form input, .order-form select {
      padding: .35rem .5rem; background: var(--bg-deep); border: 1px solid var(--border);
      border-radius: 6px; color: var(--text-primary); font-size: .85rem;
    }
    .order-quote {
      margin-top: .8rem; padding: .8rem; border-radius: 8px;
      background: rgba(52,211,153,0.08); border: 1px solid rgba(52,211,153,0.2);
    }
    .order-status { display: inline-block; padding: .15rem .5rem; border-radius: 999px; font-size: .75rem; font-weight: 700; margin-top: .4rem; }
    .order-status-pending { background: rgba(245,158,11,0.15); color: var(--amber); }
    .order-status-confirmed { background: rgba(52,211,153,0.15); color: var(--green); }
    .order-status-shipped { background: rgba(99,102,241,0.15); color: var(--accent-2); }

    /* ── Gallery ───────────────────────────────────────── */
    .gallery-controls {
      display: flex; gap: 1rem; margin-bottom: 1.5rem; align-items: center; flex-wrap: wrap;
    }
    .gallery-search {
      flex: 1; min-width: 200px; padding: .6rem 1rem;
      background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px;
      color: var(--text-primary); font-size: .9rem;
    }
    .gallery-sort { display: flex; gap: .3rem; }
    .gallery-sort button {
      padding: .4rem .8rem; border-radius: 6px; font-size: .8rem; font-weight: 600;
      background: var(--bg-card); border: 1px solid var(--border); color: var(--text-muted); cursor: pointer;
    }
    .gallery-sort button.active { color: var(--accent-2); border-color: var(--accent); }
    .gallery-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1rem;
    }
    .gallery-card { padding: 1.2rem; }
    .gallery-card-header { display: flex; justify-content: space-between; align-items: baseline; }
    .gallery-author { font-size: .8rem; color: var(--text-muted); }
    .gallery-desc { color: var(--text-secondary); font-size: .85rem; margin: .4rem 0; }
    .gallery-meta { display: flex; gap: .5rem; flex-wrap: wrap; margin: .4rem 0; }
    .gallery-template { font-size: .8rem; }
    .gallery-tags { font-size: .75rem; color: var(--text-muted); }
    .gallery-actions { display: flex; gap: .5rem; margin-top: .6rem; }
    .gallery-like, .gallery-fork, .gallery-dl {
      padding: .3rem .6rem; border-radius: 6px; font-size: .8rem; cursor: pointer;
      background: var(--bg-card); border: 1px solid var(--border); color: var(--text-secondary);
      text-decoration: none;
    }
    .gallery-like:hover, .gallery-fork:hover { border-color: var(--accent); }
    .gallery-like.liked { color: #ef4444; border-color: rgba(239,68,68,0.4); }

    /* ── Version history ──────────────────────────────── */
    .versions-list { margin-top: .6rem; border-top: 1px solid var(--border); padding-top: .4rem; }
    .version-item {
      display: flex; justify-content: space-between; align-items: center;
      padding: .3rem 0; font-size: .8rem; color: var(--text-secondary);
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
export class AppComponent implements AfterViewInit, OnInit, OnDestroy {
  @ViewChild('viewer', { static: true }) viewer!: ElementRef<HTMLDivElement>;

  private sanitizer = inject(DomSanitizer);

  prompt = '';
  examples = EXAMPLES;
  featuredExamples = FEATURED_EXAMPLES;
  spec = signal<Spec | null>(null);
  catalog = signal<Record<string, TemplateSchema>>({});
  stlUrl = signal<string | null>(null);
  metrics = signal<Metrics | null>(null);
  preview = signal<Preview | null>(null);
  previewLoading = signal(false);
  review = signal<Review | null>(null);
  reviewLoading = signal(false);
  reviewError = signal<string | null>(null);
  myDesigns = signal<Array<{id:number;template:string;summary_et:string;size_bytes:number;created_at:string}>>([]);
  suggestions = signal<string[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  adminStats = signal<any>(null);
  adminUsers = signal<any[]>([]);
  isAdmin = signal(false);
  // Pricing
  billingCycle = signal<'month' | 'year'>('month');

  // ─────────────────────────────────────────────────────────────────────
  // i18n — multi-language support (ET/EN/LV/LT)
  // ─────────────────────────────────────────────────────────────────────
  lang = signal<string>(localStorage.getItem('lang') || 'et');

  private readonly TRANSLATIONS: Record<string, Record<string, string>> = {
    et: {
      nav_how: 'Kuidas?', nav_gallery: 'Galerii', nav_examples: 'Näited', nav_pricing: 'Hinnad',
      login: 'Logi sisse', my_designs: 'Minu disainid', my_orders: 'Tellimused',
      input_label: 'Kirjeldus eesti keeles', input_placeholder: 'Näiteks: vajan riiuliklambrit 32mm veetorule, peab kandma 5kg koormust',
      voice_start: 'Räägi', voice_stop: 'Lõpeta', analyzing: 'AI analüüsib...', analyze: 'Analüüsi',
      login_to_generate: 'Logi sisse, et genereerida',
      ask_review: 'Küsi AI ülevaadet', reviewing: 'Claude vaatab üle...',
      order_print: 'Telli trükk', share_gallery: 'Jaga galeriis',
      order_title: 'Telli 3D-print', material: 'Materjal', color: 'Värv', quantity: 'Kogus',
      shipping_name: 'Nimi', shipping_address: 'Aadress', city: 'Linn', zip: 'Postiindeks', country: 'Riik',
      total: 'Kokku', shipping: 'Saatmine', delivery: 'Tarne', days: 'päeva',
      place_order: 'Telli', cancel: 'Tühista',
      share_title: 'Jaga galeriis', design_title: 'Pealkiri', description: 'Kirjeldus',
      tags: 'Sildid', share_title_placeholder: 'Nt: Riiuliklamber 32mm torule', share: 'Jaga',
      gallery_badge: 'KOGUKOND', gallery_title: 'Disainigalerii', gallery_subtitle: 'Avasta ja forki teiste disaine.',
      gallery_search: 'Otsi disaine...', newest: 'Uusimad', popular: 'Populaarsed', gallery_empty: 'Galerii on veel tühi. Ole esimene jagaja!',
      collab_start: 'Koostöö', collab_room: 'Ruum', collab_online: 'online', collab_join_placeholder: 'Ruumi kood', collab_join: 'Liitu',
    },
    en: {
      nav_how: 'How?', nav_gallery: 'Gallery', nav_examples: 'Examples', nav_pricing: 'Pricing',
      login: 'Log in', my_designs: 'My designs', my_orders: 'Orders',
      input_label: 'Description in your language', input_placeholder: 'E.g.: I need a shelf bracket for 32mm water pipe, must hold 5kg',
      voice_start: 'Speak', voice_stop: 'Stop', analyzing: 'AI analyzing...', analyze: 'Analyze',
      login_to_generate: 'Log in to generate',
      ask_review: 'Ask AI review', reviewing: 'Claude reviewing...',
      order_print: 'Order print', share_gallery: 'Share to gallery',
      order_title: 'Order 3D print', material: 'Material', color: 'Color', quantity: 'Quantity',
      shipping_name: 'Name', shipping_address: 'Address', city: 'City', zip: 'ZIP', country: 'Country',
      total: 'Total', shipping: 'Shipping', delivery: 'Delivery', days: 'days',
      place_order: 'Place order', cancel: 'Cancel',
      share_title: 'Share to gallery', design_title: 'Title', description: 'Description',
      tags: 'Tags', share_title_placeholder: 'E.g.: Shelf bracket for 32mm pipe', share: 'Share',
      gallery_badge: 'COMMUNITY', gallery_title: 'Design Gallery', gallery_subtitle: 'Discover and fork community designs.',
      gallery_search: 'Search designs...', newest: 'Newest', popular: 'Popular', gallery_empty: 'Gallery is empty. Be the first to share!',
      collab_start: 'Collaborate', collab_room: 'Room', collab_online: 'online', collab_join_placeholder: 'Room code', collab_join: 'Join',
    },
    lv: {
      nav_how: 'Kā?', nav_gallery: 'Galerija', nav_examples: 'Piemēri', nav_pricing: 'Cenas',
      login: 'Pieslēgties', my_designs: 'Mani dizaini', my_orders: 'Pasūtījumi',
      input_label: 'Apraksts latviešu valodā', input_placeholder: 'Piem.: man vajag plaukta kronšteinu 32mm ūdens caurulei, jāiztur 5kg',
      voice_start: 'Runāt', voice_stop: 'Apstāties', analyzing: 'AI analizē...', analyze: 'Analizēt',
      login_to_generate: 'Pieslēdzieties, lai ģenerētu',
      ask_review: 'AI pārskats', reviewing: 'Claude pārskata...',
      order_print: 'Pasūtīt druku', share_gallery: 'Dalīties galerijā',
      order_title: 'Pasūtīt 3D druku', material: 'Materiāls', color: 'Krāsa', quantity: 'Daudzums',
      shipping_name: 'Vārds', shipping_address: 'Adrese', city: 'Pilsēta', zip: 'Pasta indekss', country: 'Valsts',
      total: 'Kopā', shipping: 'Piegāde', delivery: 'Piegāde', days: 'dienas',
      place_order: 'Pasūtīt', cancel: 'Atcelt',
      share_title: 'Dalīties galerijā', design_title: 'Virsraksts', description: 'Apraksts',
      tags: 'Birkas', share_title_placeholder: 'Piem.: Plaukta kronšteins 32mm caurulei', share: 'Dalīties',
      gallery_badge: 'KOPIENA', gallery_title: 'Dizainu galerija', gallery_subtitle: 'Atklājiet un forkojiet kopienas dizainus.',
      gallery_search: 'Meklēt dizainus...', newest: 'Jaunākie', popular: 'Populārākie', gallery_empty: 'Galerija ir tukša. Esiet pirmais!',
      collab_start: 'Sadarboties', collab_room: 'Istaba', collab_online: 'tiešsaistē', collab_join_placeholder: 'Istabas kods', collab_join: 'Pievienoties',
    },
    lt: {
      nav_how: 'Kaip?', nav_gallery: 'Galerija', nav_examples: 'Pavyzdžiai', nav_pricing: 'Kainos',
      login: 'Prisijungti', my_designs: 'Mano dizainai', my_orders: 'Užsakymai',
      input_label: 'Aprašymas lietuvių kalba', input_placeholder: 'Pvz.: reikia lentynos laikiklio 32mm vamzdžiui, turi atlaikyti 5kg',
      voice_start: 'Kalbėti', voice_stop: 'Sustoti', analyzing: 'AI analizuoja...', analyze: 'Analizuoti',
      login_to_generate: 'Prisijunkite, kad sugeneruotumėte',
      ask_review: 'AI apžvalga', reviewing: 'Claude peržiūri...',
      order_print: 'Užsakyti spaudą', share_gallery: 'Dalintis galerijoje',
      order_title: 'Užsakyti 3D spaudą', material: 'Medžiaga', color: 'Spalva', quantity: 'Kiekis',
      shipping_name: 'Vardas', shipping_address: 'Adresas', city: 'Miestas', zip: 'Pašto kodas', country: 'Šalis',
      total: 'Viso', shipping: 'Pristatymas', delivery: 'Pristatymas', days: 'dienos',
      place_order: 'Užsakyti', cancel: 'Atšaukti',
      share_title: 'Dalintis galerijoje', design_title: 'Pavadinimas', description: 'Aprašymas',
      tags: 'Žymos', share_title_placeholder: 'Pvz.: Lentynos laikiklis 32mm vamzdžiui', share: 'Dalintis',
      gallery_badge: 'BENDRUOMENĖ', gallery_title: 'Dizainų galerija', gallery_subtitle: 'Atraskite ir forkinkite bendruomenės dizainus.',
      gallery_search: 'Ieškoti dizainų...', newest: 'Naujausi', popular: 'Populiariausi', gallery_empty: 'Galerija tuščia. Būkite pirmas!',
      collab_start: 'Bendradarbiauti', collab_room: 'Kambarys', collab_online: 'prisijungę', collab_join_placeholder: 'Kambario kodas', collab_join: 'Prisijungti',
    },
  };

  t(key: string): string {
    return this.TRANSLATIONS[this.lang()]?.[key] || this.TRANSLATIONS['et'][key] || key;
  }

  setLang(l: string) {
    this.lang.set(l);
    localStorage.setItem('lang', l);
  }

  // ─────────────────────────────────────────────────────────────────────
  // Voice-to-CAD — Web Speech API
  // ─────────────────────────────────────────────────────────────────────
  voiceActive = signal(false);
  private recognition: any = null;

  toggleVoice() {
    if (this.voiceActive()) {
      this.recognition?.stop();
      this.voiceActive.set(false);
      return;
    }
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      this.error.set('Speech recognition not supported in this browser.');
      return;
    }
    this.recognition = new SpeechRecognition();
    this.recognition.lang = this.lang() === 'et' ? 'et-EE' : this.lang() === 'lv' ? 'lv-LV' : this.lang() === 'lt' ? 'lt-LT' : 'en-US';
    this.recognition.interimResults = true;
    this.recognition.continuous = true;
    this.recognition.onresult = (e: any) => {
      let transcript = '';
      for (let i = 0; i < e.results.length; i++) {
        transcript += e.results[i][0].transcript;
      }
      this.prompt = transcript;
    };
    this.recognition.onerror = () => this.voiceActive.set(false);
    this.recognition.onend = () => this.voiceActive.set(false);
    this.recognition.start();
    this.voiceActive.set(true);
  }

  // ─────────────────────────────────────────────────────────────────────
  // STEP Export
  // ─────────────────────────────────────────────────────────────────────
  stepLoading = signal(false);

  downloadStep() {
    const s = this.spec();
    if (!s) return;
    this.stepLoading.set(true);
    this.http.post('/api/generate/step', s, { responseType: 'arraybuffer' }).subscribe({
      next: buf => {
        this.stepLoading.set(false);
        const blob = new Blob([buf], { type: 'application/step' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = (s.template || 'model') + '.step'; a.click();
        URL.revokeObjectURL(url);
      },
      error: () => { this.stepLoading.set(false); this.error.set('STEP export failed'); },
    });
  }

  // ─────────────────────────────────────────────────────────────────────
  // Gallery
  // ─────────────────────────────────────────────────────────────────────
  galleryItems = signal<any[]>([]);
  gallerySort = signal<'new' | 'popular'>('new');
  gallerySearch = '';

  loadGallery() {
    const params = `?sort=${this.gallerySort()}&q=${encodeURIComponent(this.gallerySearch)}`;
    this.http.get<any[]>('/api/gallery' + params).subscribe({
      next: items => this.galleryItems.set(items),
      error: () => this.galleryItems.set([]),
    });
  }

  toggleGalleryLike(g: any) {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    this.http.post<any>(`/api/gallery/${g.id}/like`, {}).subscribe({
      next: r => {
        g.liked_by_me = r.liked;
        g.likes = r.likes;
        this.galleryItems.set([...this.galleryItems()]);
      },
    });
  }

  forkGalleryDesign(g: any) {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    this.http.post<any>(`/api/gallery/${g.id}/fork`, {}).subscribe({
      next: r => {
        this.error.set(null);
        this.loadMyDesigns();
        alert(r.message || 'Forked!');
      },
    });
  }

  // Share to gallery
  showShareModal = signal(false);
  shareTitle = '';
  shareDesc = '';
  shareTags = '';

  shareToGallery() {
    const designs = this.myDesigns();
    if (!designs.length) return;
    const latestDesign = designs[0];
    this.http.post<any>('/api/gallery/share', {
      designId: latestDesign.id,
      title: this.shareTitle || latestDesign.summary_et || latestDesign.template,
      description: this.shareDesc,
      tags: this.shareTags,
    }).subscribe({
      next: () => {
        this.showShareModal.set(false);
        this.loadGallery();
      },
    });
  }

  // ─────────────────────────────────────────────────────────────────────
  // Print Orders
  // ─────────────────────────────────────────────────────────────────────
  showOrderModal = signal(false);
  orderMaterial = 'PLA';
  orderColor = 'must';
  orderQty = 1;
  orderName = '';
  orderAddress = '';
  orderCity = '';
  orderZip = '';
  orderCountry = 'EE';
  orderQuote = signal<any>(null);
  orderLoading = signal(false);
  orderSuccess = signal<string | null>(null);
  myOrdersList = signal<any[]>([]);

  myOrders = this.myOrdersList;

  loadMyOrders() {
    if (!this.auth.me()) return;
    this.http.get<any[]>('/api/orders').subscribe(os => this.myOrdersList.set(os));
  }

  fetchQuote() {
    const m = this.metrics();
    if (!m) return;
    this.http.post<any>('/api/orders/quote', {
      weightG: m.weight_g_pla,
      printTimeMin: m.print_time_min_estimate,
      material: this.orderMaterial,
      infillPct: 20,
      quantity: this.orderQty,
      country: this.orderCountry,
    }).subscribe(q => this.orderQuote.set(q));
  }

  placeOrder() {
    const designs = this.myDesigns();
    if (!designs.length) { this.error.set('Generate a design first'); return; }
    this.orderLoading.set(true);
    this.http.post<any>('/api/orders', {
      designId: designs[0].id,
      material: this.orderMaterial,
      infillPct: 20,
      quantity: this.orderQty,
      color: this.orderColor,
      shippingName: this.orderName,
      shippingAddress: this.orderAddress,
      shippingCity: this.orderCity,
      shippingZip: this.orderZip,
      shippingCountry: this.orderCountry,
    }).subscribe({
      next: r => {
        this.orderLoading.set(false);
        this.orderSuccess.set(r.message);
        this.showOrderModal.set(false);
        this.loadMyOrders();
      },
      error: e => {
        this.orderLoading.set(false);
        this.error.set(e.error?.message || 'Order failed');
      },
    });
  }

  // ─────────────────────────────────────────────────────────────────────
  // Real-time Collaboration (WebSocket/STOMP placeholder)
  // ─────────────────────────────────────────────────────────────────────
  collabRoomId = signal<string | null>(null);
  collabUsers = signal(0);
  collabJoinId = '';

  createCollabRoom() {
    const roomId = Math.random().toString(36).substring(2, 10);
    this.collabRoomId.set(roomId);
    this.collabUsers.set(1);
    // In production, connect to WebSocket at /ws/collab
    // For now, room ID is shareable for others to join
  }

  joinCollabRoom() {
    if (!this.collabJoinId) return;
    this.collabRoomId.set(this.collabJoinId);
    this.collabUsers.set(2); // simplified
    this.collabJoinId = '';
  }

  // ─────────────────────────────────────────────────────────────────────
  // Version History
  // ─────────────────────────────────────────────────────────────────────
  designVersions = signal<any[]>([]);
  versionsForDesign = signal<number | null>(null);

  loadVersions(designId: number) {
    if (this.versionsForDesign() === designId) {
      this.versionsForDesign.set(null);
      this.designVersions.set([]);
      return;
    }
    this.http.get<any[]>(`/api/designs/${designId}/versions`).subscribe({
      next: vs => { this.designVersions.set(vs); this.versionsForDesign.set(designId); },
      error: () => this.designVersions.set([]),
    });
  }

  rollbackVersion(designId: number, version: number) {
    if (!confirm(`Taastada versioon ${version}?`)) return;
    this.http.post<any>(`/api/designs/${designId}/versions/${version}/rollback`, {}).subscribe({
      next: r => {
        alert(r.message || 'Taastatud!');
        this.loadMyDesigns();
        this.loadVersions(designId);
      },
    });
  }

  isCurrentPlan(tier: 'MAKER' | 'PRO' | 'TEAM' | 'BUSINESS'): boolean {
    const p = this.auth.me()?.plan as string | undefined;
    return p === tier || (tier === 'MAKER' && p === 'HOBI'); // HOBI legacy alias
  }

  // ─────────────────────────────────────────────────────────────────────
  // Hero animatsioon — 4 rotating prompti, iga 6 bracket-variandiga
  // ─────────────────────────────────────────────────────────────────────
  private HERO_SHOWS = [
    {
      prompt: 'riiuliklamber 32mm veetorule, 5kg koormus',
      winner: 'paksem arm = kindlam 5kg kohta',
      seed: 1,
    },
    {
      prompt: 'kaablihoidja 4 kaablile, lauaserv',
      winner: 'laiem põhi stabiilsem',
      seed: 2,
    },
    {
      prompt: 'karp 80×60×40 Arduinole, pealt lahtine',
      winner: 'ümarnurgad + vent-auk',
      seed: 3,
    },
    {
      prompt: 'konks 3kg seinale, 2 kruvi M4',
      winner: 'sügavam haak = turvalisem',
      seed: 4,
    },
  ];
  heroShowIdx = signal(0);
  heroGen = signal(1);
  heroVariants = signal<Array<{ svg: SafeHtml; score: number; rank: number }>>([]);
  private heroTimer: any;

  heroPrompt() { return this.HERO_SHOWS[this.heroShowIdx()].prompt; }
  heroWinnerReason() { return this.HERO_SHOWS[this.heroShowIdx()].winner; }

  private heroRotate() {
    const show = this.HERO_SHOWS[this.heroShowIdx()];
    const variants = this.generateHeroVariants(show.seed);
    // rank: AI-like — top 2 kõrgeim score
    const scored = variants.map((v, i) => ({ ...v, score: 5 + Math.floor(Math.random() * 5) }));
    scored.sort((a, b) => b.score - a.score);
    this.heroVariants.set(scored.map((v, i) => ({ ...v, rank: i })));
    this.heroGen.set(this.heroShowIdx() + 1);
  }

  private generateHeroVariants(seed: number) {
    // 6 shelf-bracket SVG variations — seed-põhised variatsioonid
    const variants: Array<{ svg: SafeHtml; score: number; rank: number }> = [];
    for (let i = 0; i < 6; i++) {
      const t = (seed + i) % 6;
      const pipeR = 14 + t * 1.2;
      const armL = 30 + (i % 3) * 6;
      const thick = 3 + (i % 2) * 1.5;
      const svg = `
        <svg viewBox="0 0 100 70" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%">
          <rect x="8" y="${35 - thick}" width="${armL}" height="${thick * 2}" rx="1.5"
                fill="url(#g${seed}${i})" stroke="#8a6fff" stroke-width="0.3"/>
          <circle cx="${8 + armL + pipeR/2}" cy="35" r="${pipeR/1.8}"
                  fill="none" stroke="#c4b5fd" stroke-width="${thick/2}"/>
          <circle cx="15" cy="${28 - thick}" r="1.5" fill="#0b0b12"/>
          <circle cx="15" cy="${42 + thick}" r="1.5" fill="#0b0b12"/>
          <defs>
            <linearGradient id="g${seed}${i}" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0%" stop-color="#6366f1"/>
              <stop offset="100%" stop-color="#8b5cf6"/>
            </linearGradient>
          </defs>
        </svg>`;
      variants.push({ svg: this.sanitizer.bypassSecurityTrustHtml(svg), score: 0, rank: 0 });
    }
    return variants;
  }

  private heroStart() {
    this.heroRotate();
    this.heroTimer = setInterval(() => {
      this.heroShowIdx.set((this.heroShowIdx() + 1) % this.HERO_SHOWS.length);
      this.heroRotate();
    }, 5500);
  }

  private heroStop() {
    if (this.heroTimer) clearInterval(this.heroTimer);
  }

  // ─────────────────────────────────────────────────────────────────────
  // Darwin CAD — päris API
  // ─────────────────────────────────────────────────────────────────────
  darwinPrompt = '';
  darwinN = 6;
  darwinLoading = signal(false);
  darwinError = signal<string | null>(null);
  darwinVariants = signal<Array<any>>([]);
  darwinGeneration = signal(1);
  darwinTemplate = signal('');
  darwinRankingSource = signal<'claude' | 'heuristic'>('claude');
  darwinSelected = signal<Set<string>>(new Set());
  darwinHistory = signal<Array<{ generation: number; winner_svg: SafeHtml }>>([]);

  fallbackSvg(): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(
      `<svg viewBox="0 0 100 70" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%;opacity:.3">
         <rect x="10" y="25" width="80" height="20" rx="2" fill="#6366f1"/>
       </svg>`);
  }

  private sanitizeVariant(v: any) {
    // Worker saadab SVG-raw stringi "svg" nime all; mõned tagastavad ka "svg_dataurl"
    let rawSvg = v.svg || '';
    if (v.svg_dataurl && typeof v.svg_dataurl === 'string' && v.svg_dataurl.startsWith('data:image/svg')) {
      // dataurl → embed img
      rawSvg = `<img src="${v.svg_dataurl}" style="width:100%;height:100%;object-fit:contain" alt="variant">`;
    }
    if (!rawSvg) rawSvg = `<svg viewBox="0 0 100 70" xmlns="http://www.w3.org/2000/svg" style="width:100%;height:100%">
       <rect x="10" y="25" width="80" height="20" rx="2" fill="#6366f1" opacity="0.3"/></svg>`;
    v.svg_safe = this.sanitizer.bypassSecurityTrustHtml(rawSvg);
    return v;
  }

  darwinSeed() {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    if (!this.darwinPrompt || !this.darwinPrompt.trim()) return;
    this.darwinError.set(null);
    this.darwinLoading.set(true);
    this.darwinVariants.set([]);
    this.darwinSelected.set(new Set());
    this.darwinHistory.set([]);

    this.http.post<any>('/api/evolve/seed', {
      prompt_et: this.darwinPrompt,
      n: this.darwinN,
    }).subscribe({
      next: (r) => {
        this.darwinLoading.set(false);
        const variants = (r.variants || []).map((v: any) => this.sanitizeVariant({...v}));
        this.darwinVariants.set(variants);
        this.darwinGeneration.set(r.generation || 1);
        this.darwinTemplate.set(r.template || '');
        this.darwinRankingSource.set(r.ranking_source === 'heuristic' ? 'heuristic' : 'claude');
        this.appendHistory(variants[0]);
      },
      error: (e) => {
        this.darwinLoading.set(false);
        this.darwinError.set(e.error?.message || 'Darwin ebaõnnestus. Kontrolli API-võtit.');
      },
    });
  }

  darwinToggleSelect(id: string) {
    const s = new Set(this.darwinSelected());
    if (s.has(id)) s.delete(id); else s.add(id);
    if (s.size > 3) {
      // Max 3 vanemat korraga — eemaldame vanima
      const first = s.values().next().value;
      if (first) s.delete(first);
    }
    this.darwinSelected.set(s);
  }

  darwinEvolve() {
    const selectedIds = this.darwinSelected();
    if (!selectedIds.size) return;
    this.darwinError.set(null);
    this.darwinLoading.set(true);

    const parents = this.darwinVariants().filter(v => selectedIds.has(v.variant_id));
    this.http.post<any>('/api/evolve/cross', {
      parents,
      n: this.darwinN,
      mutation: 0.2,
      prompt_et: this.darwinPrompt,
    }).subscribe({
      next: (r) => {
        this.darwinLoading.set(false);
        const variants = (r.variants || []).map((v: any) => this.sanitizeVariant({...v}));
        this.darwinVariants.set(variants);
        this.darwinGeneration.set(r.generation || this.darwinGeneration() + 1);
        this.darwinRankingSource.set(r.ranking_source === 'heuristic' ? 'heuristic' : 'claude');
        this.darwinSelected.set(new Set());
        this.appendHistory(variants[0]);
      },
      error: (e) => {
        this.darwinLoading.set(false);
        this.darwinError.set(e.error?.message || 'Evolutsioon ebaõnnestus.');
      },
    });
  }

  darwinReset() {
    this.darwinVariants.set([]);
    this.darwinSelected.set(new Set());
    this.darwinHistory.set([]);
    this.darwinGeneration.set(1);
    this.darwinError.set(null);
  }

  private appendHistory(winner: any) {
    if (!winner) return;
    const h = this.darwinHistory();
    h.push({ generation: this.darwinGeneration(), winner_svg: winner.svg_safe });
    this.darwinHistory.set([...h]);
  }

  // ─────────────────────────────────────────────────────────────────────
  // Freeform sandbox — Pro plaan
  // ─────────────────────────────────────────────────────────────────────
  ffCode = '';
  ffLoading = signal(false);
  ffResult = signal<any | null>(null);

  private FF_TEMPLATE = `import cadquery as cq

# Muuda mõõte siin ja vajuta Run ▶
WIDTH  = 60
DEPTH  = 40
HEIGHT = 25
WALL   = 2.5

result = (
    cq.Workplane("XY")
    .box(WIDTH, DEPTH, HEIGHT)
    .faces(">Z").workplane()
    .rect(WIDTH - 2*WALL, DEPTH - 2*WALL)
    .cutBlind(-(HEIGHT - WALL))
    .edges("|Z").fillet(3)
)
`;

  ffLoadTemplate() {
    this.ffCode = this.FF_TEMPLATE;
  }

  ffKeydown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      this.ffRun();
    }
  }

  ffRun() {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    if (!this.ffCode) return;
    this.ffLoading.set(true);
    this.ffResult.set(null);
    this.http.post<any>('/api/freeform/generate', { code: this.ffCode })
      .subscribe({
        next: (r) => { this.ffLoading.set(false); this.ffResult.set(r); },
        error: (e) => {
          this.ffLoading.set(false);
          this.ffResult.set({
            ok: false,
            error: e.error?.message || e.message || 'Sandbox ebaõnnestus',
            error_kind: e.error?.error_kind || 'network',
          });
        },
      });
  }

  ffStlUrl(): string | null {
    const stl = this.ffResult()?.files?.stl;
    return stl ? 'data:model/stl;base64,' + stl : null;
  }

  ffStepUrl(): string | null {
    const step = this.ffResult()?.files?.step;
    return step ? 'data:model/step;base64,' + step : null;
  }

  // ─────────────────────────────────────────────────────────────────────
  scrollToApp() {
    document.getElementById('app')?.scrollIntoView({ behavior: 'smooth' });
  }

  ngOnInit() {
    this.heroStart();
    this.loadGallery();
  }

  ngOnDestroy() {
    this.heroStop();
  }

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

  private router = inject(Router);
  constructor(private http: HttpClient, public auth: AuthService) {}

  goToAuth() { this.router.navigate(['/auth']); }

  ngAfterViewInit() {
    const el = this.viewer.nativeElement;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x050a18);
    this.camera = new THREE.PerspectiveCamera(45, el.clientWidth / el.clientHeight, 1, 2000);
    this.camera.position.set(200, 200, 200);
    // preserveDrawingBuffer: keep canvas contents around so toDataURL() works
    // for the AI review screenshot. Small perf cost, never noticeable in practice.
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
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

  upgrade(tier: 'pro' | 'business') {
    if (!this.auth.me()) { this.auth.loginWithGoogle(); return; }
    this.http.post<{ url: string }>('/api/stripe/checkout', { tier }).subscribe({
      next: r => { window.location.href = r.url; },
      error: e => this.error.set(e.error?.message || 'Stripe pole veel seadistatud — tule tagasi peagi!'),
    });
  }

  openBillingPortal() {
    this.http.post<{ url: string }>('/api/stripe/portal', {}).subscribe({
      next: r => { window.location.href = r.url; },
      error: e => this.error.set(e.error?.message || 'Tellimuse haldamine pole saadaval.'),
    });
  }

  updateParam(k: string, v: number) {
    const s = this.spec();
    if (!s) return;
    const next = { ...s, params: { ...s.params, [k]: +v } };
    this.spec.set(next);
    this.fetchMetrics(next);
    // Invalidate the precise slicer preview — params changed, numbers are stale.
    this.preview.set(null);
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
    this.preview.set(null);
    this.review.set(null);
    this.reviewError.set(null);
    this.http.post('/api/generate', s, { responseType: 'arraybuffer' }).subscribe({
      next: buf => {
        const blob = new Blob([buf], { type: 'application/sla' });
        this.stlUrl.set(URL.createObjectURL(blob));
        this.renderSTL(buf);
        this.loading.set(false);
        this.auth.refreshMe();
        // Fire precise slicer preview in parallel — UI keeps working while it resolves.
        this.fetchPreview(s);
      },
      error: e => {
        if (e.status === 402) this.error.set('Tasuta piir on täis. Uuenda PRO-le.');
        else if (e.status === 401) this.error.set('Logi sisse, et genereerida.');
        else this.error.set(e.error?.message || e.message);
        this.loading.set(false);
      },
    });
  }

  /** Grab the three.js canvas as a base64 PNG (no data: prefix). */
  private captureCanvasPng(): string | null {
    try {
      // Force a fresh render so preserveDrawingBuffer captures the latest frame.
      if (this.renderer && this.scene && this.camera) {
        this.renderer.render(this.scene, this.camera);
        const dataUrl = this.renderer.domElement.toDataURL('image/png');
        const comma = dataUrl.indexOf(',');
        return comma >= 0 ? dataUrl.slice(comma + 1) : null;
      }
    } catch {
      // Cross-origin textures / context-loss — fall through.
    }
    return null;
  }

  /**
   * Ask Claude to critique the current design (vision + spec + original prompt).
   * Returns a structured {score, strengths, weaknesses, suggestions[]} — rendered
   * in a peer-review card with clickable auto-apply fixes.
   */
  askReview() {
    const s = this.spec();
    if (!s) return;
    this.reviewLoading.set(true);
    this.reviewError.set(null);
    this.review.set(null);
    const body = {
      spec: s,
      prompt_et: this.prompt || null,
      image_base64: this.captureCanvasPng(),
    };
    this.http.post<Review>('/api/review', body).subscribe({
      next: r => { this.review.set(r); this.reviewLoading.set(false); },
      error: e => {
        this.reviewError.set(e.error?.message || 'AI ülevaade ebaõnnestus');
        this.reviewLoading.set(false);
      },
    });
  }

  /**
   * One-click auto-apply: take a suggestion's (param, new_value), clamp to
   * the template's min/max, patch the spec, re-fetch metrics + regenerate STL.
   * Returns true if applied, false if the suggestion was non-numeric.
   */
  applySuggestion(sug: Suggestion) {
    if (!sug.param || typeof sug.new_value !== 'number') return false;
    const s = this.spec();
    if (!s) return false;
    const schema = this.schemaFor(s.template, sug.param);
    let v = sug.new_value;
    if (schema) v = Math.min(schema.max, Math.max(schema.min, v));
    const next = { ...s, params: { ...s.params, [sug.param]: v } };
    this.spec.set(next);
    this.fetchMetrics(next);
    this.preview.set(null);
    this.review.set(null);
    this.generate();
    return true;
  }

  scoreColor(n: number): string {
    if (n >= 8) return 'var(--green)';
    if (n >= 5) return 'var(--amber)';
    return '#ef4444';
  }

  /**
   * Ask backend for PrusaSlicer-precise print time / filament mass / cost.
   * Backend silently falls back to the worker heuristic if the slicer sidecar
   * is down, so this is always safe to call.
   */
  fetchPreview(s: Spec) {
    this.previewLoading.set(true);
    this.http.post<Preview>('/api/preview', s).subscribe({
      next: p => { this.preview.set(p); this.previewLoading.set(false); },
      error: () => { this.preview.set(null); this.previewLoading.set(false); },
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
