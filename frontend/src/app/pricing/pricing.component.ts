import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

interface PlanInfo {
  id: string;
  segment: string;
  name: string;
  price: string;
  featured: boolean;
  limits: Record<string, number>;
}

@Component({
  selector: 'app-pricing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="pricing-page">
      <header class="pricing-header">
        <a routerLink="/" class="pricing-back">&larr; Back to TehisAI CAD</a>
        <h1>Simple pricing for every stage</h1>
        <p class="pricing-subtitle">Start free. Upgrade when you need more.</p>
      </header>

      <!-- Tab bar -->
      <div class="pricing-tabs">
        <button *ngFor="let tab of tabs"
                class="pricing-tab"
                [class.pricing-tab-active]="activeTab() === tab.id"
                (click)="activeTab.set(tab.id)">
          {{ tab.label }}
        </button>
      </div>

      <!-- ═══ MAKERS TAB ═══ -->
      <div class="pricing-grid" *ngIf="activeTab() === 'makers'">
        <div class="pricing-card" *ngFor="let p of makerPlans" [class.pricing-featured]="p.featured">
          <div *ngIf="p.featured" class="pricing-badge">MOST POPULAR</div>
          <h3>{{ p.name }}</h3>
          <div class="pricing-price">{{ p.price }}</div>
          <ul class="pricing-features">
            <li *ngIf="p.id === 'MAKER'"><strong>100</strong> generations/mo</li>
            <li *ngIf="p.id === 'MAKER'"><strong>30</strong> AI reviews/mo</li>
            <li *ngIf="p.id === 'MAKER'"><strong>10</strong> Meshy free-form/mo</li>
            <li *ngIf="p.id === 'MAKER'">All templates</li>
            <li *ngIf="p.id === 'MAKER'">Estonian AI</li>

            <li *ngIf="p.id === 'CREATOR'"><strong>500</strong> generations/mo</li>
            <li *ngIf="p.id === 'CREATOR'"><strong>150</strong> AI reviews/mo</li>
            <li *ngIf="p.id === 'CREATOR'"><strong>50</strong> Meshy free-form/mo</li>
            <li *ngIf="p.id === 'CREATOR'">Darwin CAD</li>
            <li *ngIf="p.id === 'CREATOR'">STEP export</li>
            <li *ngIf="p.id === 'CREATOR'">Priority support</li>
          </ul>
          <button class="pricing-cta" [class.pricing-cta-featured]="p.featured">
            {{ p.id === 'MAKER' ? 'Get started free' : 'Upgrade to Creator' }}
          </button>
        </div>
      </div>

      <!-- ═══ BUREAUS TAB ═══ -->
      <div class="pricing-grid pricing-grid-4" *ngIf="activeTab() === 'bureaus'">
        <div class="pricing-card" *ngFor="let p of bureauPlans" [class.pricing-featured]="p.featured">
          <div *ngIf="p.featured" class="pricing-badge">RECOMMENDED</div>
          <h3>{{ p.name }}</h3>
          <div class="pricing-price">{{ p.price }}</div>
          <ul class="pricing-features">
            <li *ngIf="p.id === 'BUREAU_STARTER'"><strong>50</strong> orders/mo</li>
            <li *ngIf="p.id === 'BUREAU_STARTER'"><strong>1</strong> printer</li>
            <li *ngIf="p.id === 'BUREAU_STARTER'">Instant quoting</li>

            <li *ngIf="p.id === 'BUREAU_STUDIO'"><strong>500</strong> orders/mo</li>
            <li *ngIf="p.id === 'BUREAU_STUDIO'"><strong>10</strong> printers</li>
            <li *ngIf="p.id === 'BUREAU_STUDIO'">DFM analysis</li>
            <li *ngIf="p.id === 'BUREAU_STUDIO'">Job queue</li>

            <li *ngIf="p.id === 'BUREAU_FACTORY'">Unlimited orders</li>
            <li *ngIf="p.id === 'BUREAU_FACTORY'">Unlimited printers</li>
            <li *ngIf="p.id === 'BUREAU_FACTORY'">SSE real-time</li>
            <li *ngIf="p.id === 'BUREAU_FACTORY'">Webhook integrations</li>

            <li *ngIf="p.id === 'BUREAU_ENTERPRISE'">Everything in Factory</li>
            <li *ngIf="p.id === 'BUREAU_ENTERPRISE'">Custom SLA</li>
            <li *ngIf="p.id === 'BUREAU_ENTERPRISE'">Dedicated support</li>
            <li *ngIf="p.id === 'BUREAU_ENTERPRISE'">On-prem option</li>
          </ul>
          <button class="pricing-cta" [class.pricing-cta-featured]="p.featured">
            {{ p.id === 'BUREAU_ENTERPRISE' ? 'Contact sales' : 'Start ' + p.name }}
          </button>
        </div>
      </div>

      <!-- ═══ DEVELOPERS TAB ═══ -->
      <div class="pricing-grid" *ngIf="activeTab() === 'developers'">
        <div class="pricing-card" *ngFor="let p of devPlans" [class.pricing-featured]="p.featured">
          <div *ngIf="p.featured" class="pricing-badge">BEST VALUE</div>
          <h3>{{ p.name }}</h3>
          <div class="pricing-price">{{ p.price }}</div>
          <div *ngIf="p.id === 'DEV_TRIAL'" class="pricing-chip">PAYG after trial</div>
          <ul class="pricing-features">
            <li *ngIf="p.id === 'DEV_TRIAL'"><strong>500</strong> generations total</li>
            <li *ngIf="p.id === 'DEV_TRIAL'">14-day trial</li>
            <li *ngIf="p.id === 'DEV_TRIAL'">60 req/min</li>

            <li *ngIf="p.id === 'DEV_GROWTH'"><strong>1,000</strong> generations/mo</li>
            <li *ngIf="p.id === 'DEV_GROWTH'">60 req/min</li>
            <li *ngIf="p.id === 'DEV_GROWTH'">REST API</li>
            <li *ngIf="p.id === 'DEV_GROWTH'">Commercial license</li>

            <li *ngIf="p.id === 'DEV_BUSINESS'"><strong>5,000</strong> generations/mo</li>
            <li *ngIf="p.id === 'DEV_BUSINESS'">300 req/min</li>
            <li *ngIf="p.id === 'DEV_BUSINESS'">REST API</li>
            <li *ngIf="p.id === 'DEV_BUSINESS'">Priority support (4h)</li>
            <li *ngIf="p.id === 'DEV_BUSINESS'">Usage analytics</li>
          </ul>
          <button class="pricing-cta" [class.pricing-cta-featured]="p.featured">
            {{ p.id === 'DEV_TRIAL' ? 'Start free trial' : 'Choose ' + p.name }}
          </button>
        </div>
      </div>

      <!-- ═══ FAQ ═══ -->
      <section class="pricing-faq">
        <h2>Frequently Asked Questions</h2>
        <details class="faq-item" *ngFor="let faq of faqs">
          <summary>{{ faq.q }}</summary>
          <p>{{ faq.a }}</p>
        </details>
      </section>
    </div>
  `,
  styles: [`
    .pricing-page {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2rem 1.5rem 4rem;
      color: var(--text, #e0e0e0);
      font-family: inherit;
    }
    .pricing-header {
      text-align: center;
      margin-bottom: 2rem;
    }
    .pricing-back {
      color: var(--text-muted, #888);
      text-decoration: none;
      font-size: .9rem;
    }
    .pricing-header h1 {
      font-size: 2.2rem;
      margin: 1rem 0 .5rem;
    }
    .pricing-subtitle {
      color: var(--text-muted, #888);
      font-size: 1.1rem;
    }
    .pricing-tabs {
      display: flex;
      justify-content: center;
      gap: .5rem;
      margin-bottom: 2rem;
    }
    .pricing-tab {
      padding: .6rem 1.5rem;
      border-radius: 8px;
      border: 1px solid var(--border, #333);
      background: transparent;
      color: var(--text-muted, #888);
      cursor: pointer;
      font-size: .95rem;
      transition: all .2s;
    }
    .pricing-tab-active {
      background: var(--accent, #6366f1);
      color: #fff;
      border-color: var(--accent, #6366f1);
    }
    .pricing-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.5rem;
      max-width: 700px;
      margin: 0 auto 3rem;
    }
    .pricing-grid-4 {
      max-width: 1100px;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    }
    .pricing-card {
      background: var(--bg-card, #1a1a2e);
      border: 1px solid var(--border, #333);
      border-radius: 12px;
      padding: 2rem 1.5rem;
      position: relative;
      transition: transform .2s;
    }
    .pricing-card:hover { transform: translateY(-2px); }
    .pricing-featured {
      border-color: var(--accent, #6366f1);
      box-shadow: 0 0 20px rgba(99,102,241,.15);
    }
    .pricing-badge {
      position: absolute;
      top: -10px;
      left: 50%;
      transform: translateX(-50%);
      background: var(--accent, #6366f1);
      color: #fff;
      font-size: .7rem;
      font-weight: 700;
      padding: .25rem .8rem;
      border-radius: 20px;
      letter-spacing: .5px;
    }
    .pricing-card h3 {
      margin: 0 0 .5rem;
      font-size: 1.3rem;
    }
    .pricing-price {
      font-size: 1.8rem;
      font-weight: 700;
      margin-bottom: 1rem;
    }
    .pricing-chip {
      display: inline-block;
      background: rgba(99,102,241,.15);
      color: var(--accent, #6366f1);
      font-size: .75rem;
      font-weight: 600;
      padding: .2rem .6rem;
      border-radius: 4px;
      margin-bottom: .5rem;
    }
    .pricing-features {
      list-style: none;
      padding: 0;
      margin: 0 0 1.5rem;
    }
    .pricing-features li {
      padding: .4rem 0;
      font-size: .9rem;
      color: var(--text-muted, #aaa);
    }
    .pricing-features li::before {
      content: '\\2713 ';
      color: var(--green, #10b981);
      margin-right: .3rem;
    }
    .pricing-cta {
      width: 100%;
      padding: .75rem;
      border-radius: 8px;
      border: 1px solid var(--border, #333);
      background: transparent;
      color: var(--text, #e0e0e0);
      cursor: pointer;
      font-size: .95rem;
      transition: all .2s;
    }
    .pricing-cta:hover { background: var(--bg-hover, #252540); }
    .pricing-cta-featured {
      background: var(--accent, #6366f1);
      color: #fff;
      border-color: var(--accent, #6366f1);
    }
    .pricing-cta-featured:hover { opacity: .9; background: var(--accent, #6366f1); }
    .pricing-faq {
      max-width: 700px;
      margin: 0 auto;
    }
    .pricing-faq h2 {
      text-align: center;
      margin-bottom: 1.5rem;
    }
    .faq-item {
      background: var(--bg-card, #1a1a2e);
      border: 1px solid var(--border, #333);
      border-radius: 8px;
      margin-bottom: .75rem;
      padding: 1rem 1.2rem;
    }
    .faq-item summary {
      cursor: pointer;
      font-weight: 600;
      font-size: .95rem;
    }
    .faq-item p {
      margin: .75rem 0 0;
      color: var(--text-muted, #aaa);
      font-size: .9rem;
      line-height: 1.6;
    }
  `],
})
export class PricingComponent {
  activeTab = signal<'makers' | 'bureaus' | 'developers'>('makers');

  tabs = [
    { id: 'makers' as const, label: 'Makers' },
    { id: 'bureaus' as const, label: 'Print Bureaus' },
    { id: 'developers' as const, label: 'Developers' },
  ];

  makerPlans = [
    { id: 'MAKER', name: 'Maker', price: 'Free', featured: false },
    { id: 'CREATOR', name: 'Creator', price: '29.99 EUR/mo', featured: true },
  ];

  bureauPlans = [
    { id: 'BUREAU_STARTER', name: 'Starter', price: '49 EUR/mo', featured: false },
    { id: 'BUREAU_STUDIO', name: 'Studio', price: '149 EUR/mo', featured: true },
    { id: 'BUREAU_FACTORY', name: 'Factory', price: '399 EUR/mo', featured: false },
    { id: 'BUREAU_ENTERPRISE', name: 'Enterprise', price: 'Custom', featured: false },
  ];

  devPlans = [
    { id: 'DEV_TRIAL', name: 'Trial', price: 'Free / 14 days', featured: false },
    { id: 'DEV_GROWTH', name: 'Growth', price: '79 EUR/mo', featured: true },
    { id: 'DEV_BUSINESS', name: 'Business', price: '249 EUR/mo', featured: false },
  ];

  faqs = [
    {
      q: 'Can I switch plans at any time?',
      a: 'Yes. Upgrades take effect immediately with prorated billing. Downgrades apply at the end of the current billing cycle.'
    },
    {
      q: 'What happens when I hit my limit?',
      a: 'You\'ll see a clear notification with options to upgrade or wait for the monthly reset. We never charge overage fees automatically.'
    },
    {
      q: 'Do you offer annual discounts?',
      a: 'Yes — annual plans save ~20%. Contact us for custom enterprise agreements.'
    },
    {
      q: 'Is there a refund policy?',
      a: '30-day unconditional refund on all paid plans. Email refund@tehisaicad.ee — no questions asked.'
    },
    {
      q: 'Can I use generated models commercially?',
      a: 'Creator and all Bureau/Developer plans include a full commercial license. Maker (free) is for personal use.'
    },
  ];
}
