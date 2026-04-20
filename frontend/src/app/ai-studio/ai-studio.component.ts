import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import {
  AiStudioService,
  CouncilResult,
  DfmReport,
  IterateEvent,
  PersonaReview,
} from './ai-studio.service';

/**
 * AI Superpowers — eraldi stuudio-tööriist (route /ai-studio).
 *
 * Kolm peamist tab'i:
 *   1. Nõukogu — 4-agent multi-agent review
 *   2. Iteraator — streaming "iterate until perfect"
 *   3. DFM — reeglipõhine auto-audit + heat-map-stiilis issued
 */
@Component({
  selector: 'app-ai-studio',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  template: `
    <div class="ai-studio">
      <header>
        <h1>🧠 AI Superpowers Studio</h1>
        <p class="subtitle">
          Sügav AI-audit sinu parameetrilisele disainile. Paste spec JSON või
          lase auto-load'il sama disain, mida avas home-page.
        </p>
      </header>

      <section class="input-panel">
        <label>
          <strong>Template</strong>
          <select [(ngModel)]="template">
            <option>shelf_bracket</option><option>hook</option><option>box</option>
            <option>adapter</option><option>cable_clamp</option><option>tag</option>
            <option>vesa_adapter</option><option>enclosure</option><option>wall_mount</option>
            <option>tool_holder</option><option>spur_gear</option><option>living_hinge</option>
            <option>snap_fit_clip</option><option>phone_stand</option>
            <option>corner_bracket</option><option>raspberry_pi_case</option>
            <option>pot_planter</option>
          </select>
        </label>

        <label>
          <strong>Params (JSON)</strong>
          <textarea [(ngModel)]="paramsJson" rows="4"
                    placeholder='{"load_kg":10,"wall_thickness":2.5}'></textarea>
        </label>

        <label>
          <strong>Kasutaja originaal soov (EE)</strong>
          <input [(ngModel)]="promptEt" placeholder="nt: tugev klamber 10kg koormusele" />
        </label>

        <div class="actions">
          <button class="primary" (click)="runCouncil()" [disabled]="loading()">
            🏛 Kutsu nõukogu (4 agenti)
          </button>
          <button (click)="runDfm()" [disabled]="loading()">🔧 DFM-audit</button>
          <button class="accent" (click)="startIterate()" [disabled]="loading()">
            🔁 Itereeri täiuseni
          </button>
        </div>
      </section>

      <!-- NÕUKOGU -->
      <section *ngIf="council()" class="council">
        <div class="synthesis" [attr.data-verdict]="council()?.synthesis?.overall">
          <div class="score-circle">
            <span class="val">{{ council()!.council_score.toFixed(1) }}</span>
            <span class="lbl">koondhinne</span>
          </div>
          <div class="verdict-box">
            <div class="overall-badge">{{ verdictLabel(council()!.synthesis.overall) }}</div>
            <p>{{ council()!.synthesis.verdict_et }}</p>
            <ol *ngIf="council()!.synthesis.top_actions.length">
              <li *ngFor="let a of council()!.synthesis.top_actions">
                <strong>{{ a.label_et }}</strong>
                <span class="backed-by" *ngIf="a.backed_by?.length">
                  — toetavad: {{ a.backed_by!.join(', ') }}
                </span>
                <p>{{ a.rationale_et }}</p>
              </li>
            </ol>
          </div>
        </div>

        <div class="agents">
          <article *ngFor="let agent of council()!.agents" class="agent" [attr.data-persona]="agent.persona">
            <header>
              <h3>{{ agent.persona_display }}</h3>
              <div class="mini-score" [style.--score]="agent.score">
                {{ agent.score }}/10
              </div>
            </header>
            <p class="verdict">{{ agent.verdict_et }}</p>
            <ul class="findings">
              <li *ngFor="let f of agent.findings">{{ f }}</li>
            </ul>
            <div class="sugg" *ngIf="agent.suggestions?.length">
              <strong>Soovitused:</strong>
              <ul>
                <li *ngFor="let s of agent.suggestions">
                  <span class="sugg-label">{{ s.label_et }}</span>
                  <span *ngIf="s.param && s.new_value !== undefined" class="chip">
                    {{ s.param }} → {{ s.new_value }}
                  </span>
                  <div class="sugg-reason">{{ s.rationale_et }}</div>
                </li>
              </ul>
            </div>
          </article>
        </div>
      </section>

      <!-- DFM -->
      <section *ngIf="dfm()" class="dfm">
        <header>
          <h2>DFM-audit</h2>
          <div class="score-badge" [attr.data-level]="dfmLevel()">
            {{ dfm()!.score }}/10
          </div>
        </header>
        <p class="dfm-summary">{{ dfm()!.summary_et }}</p>
        <div class="counts">
          <span class="count critical">🔴 {{ dfm()!.counts.critical }} kriitilised</span>
          <span class="count warning">🟡 {{ dfm()!.counts.warning }} hoiatused</span>
          <span class="count info">🔵 {{ dfm()!.counts.info }} info</span>
        </div>
        <ul class="issues">
          <li *ngFor="let i of dfm()!.issues" [attr.data-severity]="i.severity">
            <div class="issue-head">
              <span class="rule">{{ i.rule }}</span>
              <span class="sev-badge">{{ i.severity }}</span>
            </div>
            <p>{{ i.message_et }}</p>
            <div *ngIf="i.affected_param" class="fix-chip">
              Soovitus: <code>{{ i.affected_param }}</code>
              <span *ngIf="i.suggested_value !== undefined"> → <b>{{ i.suggested_value }}</b></span>
            </div>
          </li>
        </ul>
      </section>

      <!-- ITERATOR -->
      <section *ngIf="iterEvents().length" class="iterator">
        <header>
          <h2>🔁 Generative loop</h2>
          <div class="target">
            target ≥ <strong>{{ targetScore }}</strong>
          </div>
        </header>

        <div class="timeline">
          <div *ngFor="let e of iterEvents(); let idx = index" class="tl-item"
               [attr.data-type]="e.type">
            <div class="tl-step">{{ stepOf(e) }}</div>
            <div class="tl-body">
              <ng-container [ngSwitch]="e.type">
                <div *ngSwitchCase="'start'">
                  <strong>Algab iteratsioon.</strong> Target ≥ {{ $any(e).target }}
                </div>
                <div *ngSwitchCase="'review'">
                  <strong>Review</strong>
                  <span class="score-chip" [style.--score]="$any(e).score">
                    {{ $any(e).score }}/10
                  </span>
                  <p>{{ $any(e).verdict_et }}</p>
                </div>
                <div *ngSwitchCase="'patch'" class="patch">
                  <strong>Patch</strong> <code>{{ $any(e).param }}</code>:
                  <s>{{ $any(e).old }}</s> → <b>{{ $any(e).new }}</b>
                  <p>{{ $any(e).rationale_et }}</p>
                </div>
                <div *ngSwitchCase="'stop'" class="stop" [attr.data-reason]="$any(e).reason">
                  <strong>Lõpp:</strong> {{ stopReasonLabel($any(e).reason) }}
                  <span class="final-score">final: {{ $any(e).final_score }}/10</span>
                  <p *ngIf="$any(e).final_verdict_et">{{ $any(e).final_verdict_et }}</p>
                </div>
                <div *ngSwitchCase="'error'" class="err">
                  <strong>Viga:</strong> {{ $any(e).message }}
                </div>
              </ng-container>
            </div>
          </div>
        </div>
      </section>

      <div *ngIf="error()" class="error-bar">{{ error() }}</div>
    </div>
  `,
  styles: [`
    .ai-studio { max-width: 1200px; margin: 0 auto; padding: 24px; color: #eee;
                 font-family: system-ui, sans-serif; }
    header h1 { font-size: 28px; margin: 0 0 4px; }
    .subtitle { color: #9aa; margin: 0 0 24px; }

    .input-panel { background: #1a1d24; border-radius: 12px; padding: 20px;
                   display: grid; grid-template-columns: 1fr 2fr; gap: 12px;
                   margin-bottom: 24px; }
    .input-panel label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #9aa; }
    .input-panel label:nth-child(2), .input-panel label:nth-child(3) { grid-column: span 2; }
    .input-panel input, .input-panel select, .input-panel textarea {
      background: #0e1014; color: #eee; border: 1px solid #333;
      border-radius: 6px; padding: 8px 10px; font-family: ui-monospace, monospace;
      font-size: 13px;
    }
    .input-panel .actions { grid-column: span 2; display: flex; gap: 8px; margin-top: 8px; }
    button { background: #2a2f3a; color: #eee; border: 1px solid #444;
             padding: 10px 16px; border-radius: 6px; cursor: pointer; font-size: 14px; }
    button:hover { background: #3a4050; }
    button.primary { background: linear-gradient(135deg, #4a69ff, #6e52ff); border: none; }
    button.accent { background: linear-gradient(135deg, #ff6e3a, #ffa44a); border: none; color: #111; font-weight: 600; }
    button:disabled { opacity: 0.5; cursor: wait; }

    .council { background: #1a1d24; border-radius: 12px; padding: 20px; margin-bottom: 24px; }
    .synthesis { display: flex; gap: 20px; margin-bottom: 24px; padding: 16px;
                 background: #0e1014; border-radius: 10px; }
    .synthesis[data-verdict="ship_it"] { border-left: 4px solid #4ade80; }
    .synthesis[data-verdict="iterate"] { border-left: 4px solid #fbbf24; }
    .synthesis[data-verdict="redesign"] { border-left: 4px solid #f87171; }

    .score-circle { width: 100px; height: 100px; border-radius: 50%;
                    background: radial-gradient(circle, #4a69ff 0%, #1a1d24 70%);
                    display: flex; flex-direction: column; align-items: center;
                    justify-content: center; flex-shrink: 0; }
    .score-circle .val { font-size: 28px; font-weight: 700; }
    .score-circle .lbl { font-size: 10px; color: #9aa; }

    .overall-badge { display: inline-block; padding: 3px 10px; border-radius: 4px;
                     background: #2a2f3a; font-size: 11px; letter-spacing: 0.8px;
                     text-transform: uppercase; margin-bottom: 8px; }
    .verdict-box ol { padding-left: 20px; margin-top: 8px; }
    .verdict-box li { margin-bottom: 8px; }
    .backed-by { font-size: 12px; color: #9aa; margin-left: 6px; }

    .agents { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
    .agent { background: #0e1014; border-radius: 10px; padding: 16px;
             border-top: 3px solid var(--color, #4a69ff); }
    .agent[data-persona="structural"]  { --color: #f87171; }
    .agent[data-persona="print"]       { --color: #4a69ff; }
    .agent[data-persona="cost"]        { --color: #4ade80; }
    .agent[data-persona="aesthetics"]  { --color: #c084fc; }
    .agent header { display: flex; justify-content: space-between; align-items: center;
                    padding: 0; border: 0; margin: 0 0 8px; background: none; }
    .agent h3 { margin: 0; font-size: 15px; }
    .mini-score { font-weight: 700; font-size: 16px;
                  color: hsl(calc(var(--score, 5) * 12), 70%, 55%); }
    .agent .verdict { font-style: italic; color: #d0d4dc; margin: 4px 0 10px; }
    .agent .findings { padding-left: 18px; font-size: 13px; color: #b8bcc4; }
    .agent .findings li { margin-bottom: 4px; }
    .agent .sugg { margin-top: 10px; font-size: 13px; }
    .agent .sugg ul { padding-left: 18px; }
    .agent .sugg li { margin-bottom: 6px; }
    .sugg-label { font-weight: 600; }
    .chip { background: #2a2f3a; padding: 1px 8px; border-radius: 4px; font-family: ui-monospace;
            font-size: 11px; margin-left: 6px; }
    .sugg-reason { color: #9aa; font-size: 12px; margin-top: 2px; }

    .dfm { background: #1a1d24; border-radius: 12px; padding: 20px; margin-bottom: 24px; }
    .dfm header { display: flex; justify-content: space-between; align-items: center;
                  padding: 0; background: none; margin: 0 0 12px; }
    .score-badge { padding: 8px 16px; border-radius: 8px; font-weight: 700; font-size: 18px; }
    .score-badge[data-level="good"] { background: #1a4a2a; color: #4ade80; }
    .score-badge[data-level="ok"]   { background: #4a3a1a; color: #fbbf24; }
    .score-badge[data-level="bad"]  { background: #4a1a1a; color: #f87171; }
    .counts { display: flex; gap: 8px; margin: 12px 0; }
    .count { padding: 6px 12px; background: #0e1014; border-radius: 6px; font-size: 13px; }
    .issues { list-style: none; padding: 0; }
    .issues li { background: #0e1014; padding: 12px; border-radius: 8px; margin-bottom: 8px;
                 border-left: 4px solid var(--sev-col, #4a69ff); }
    .issues li[data-severity="critical"] { --sev-col: #f87171; }
    .issues li[data-severity="warning"]  { --sev-col: #fbbf24; }
    .issues li[data-severity="info"]     { --sev-col: #4a69ff; }
    .issue-head { display: flex; justify-content: space-between; font-size: 12px; color: #9aa;
                  margin-bottom: 6px; }
    .sev-badge { text-transform: uppercase; font-weight: 700; letter-spacing: 1px; }
    .fix-chip { font-size: 12px; color: #c084fc; margin-top: 4px; }

    .iterator { background: #1a1d24; border-radius: 12px; padding: 20px; }
    .iterator header { display: flex; justify-content: space-between; align-items: center;
                       margin: 0 0 16px; padding: 0; background: none; }
    .timeline { position: relative; padding-left: 24px; border-left: 2px solid #2a2f3a; }
    .tl-item { position: relative; margin-bottom: 14px; padding-left: 12px; }
    .tl-item::before { content: ''; position: absolute; left: -31px; top: 4px;
                       width: 12px; height: 12px; border-radius: 50%; background: #4a69ff;
                       border: 2px solid #1a1d24; }
    .tl-item[data-type="patch"]::before { background: #ff6e3a; }
    .tl-item[data-type="stop"]::before { background: #4ade80; width: 16px; height: 16px; left: -33px; }
    .tl-item[data-type="error"]::before { background: #f87171; }
    .tl-step { position: absolute; left: -60px; top: 2px; font-size: 11px; color: #9aa;
               font-family: ui-monospace; }
    .tl-body { background: #0e1014; padding: 10px 14px; border-radius: 8px; }
    .score-chip { background: #2a2f3a; padding: 2px 8px; border-radius: 4px;
                  color: hsl(calc(var(--score, 5) * 12), 70%, 55%); font-weight: 700;
                  margin-left: 8px; }
    .patch code { background: #2a2f3a; padding: 1px 6px; border-radius: 3px; }
    .stop { font-weight: 600; }
    .stop[data-reason="target_reached"] { color: #4ade80; }
    .stop[data-reason="no_improvement"] { color: #fbbf24; }
    .stop[data-reason="max_iter"] { color: #fbbf24; }
    .final-score { margin-left: 12px; color: #9aa; font-weight: 400; }
    .err { color: #f87171; }

    .error-bar { background: #4a1a1a; color: #f87171; padding: 12px; border-radius: 8px;
                 margin-top: 16px; }
  `],
})
export class AiStudioComponent {
  private ai = inject(AiStudioService);

  template = 'shelf_bracket';
  paramsJson = '{"pipe_diameter":32,"load_kg":10,"arm_length":120,"wall_thickness":2.5}';
  promptEt = 'tugev L-klamber 10kg koormusele';
  targetScore = 8.5;

  council = signal<CouncilResult | null>(null);
  dfm = signal<DfmReport | null>(null);
  iterEvents = signal<IterateEvent[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  dfmLevel = computed(() => {
    const d = this.dfm();
    if (!d) return 'good';
    if (d.score >= 8) return 'good';
    if (d.score >= 5) return 'ok';
    return 'bad';
  });

  private parseParams(): any {
    try {
      return JSON.parse(this.paramsJson);
    } catch (e) {
      this.error.set('Params JSON on vigane: ' + (e as Error).message);
      throw e;
    }
  }

  runCouncil() {
    this.error.set(null);
    this.loading.set(true);
    const spec = { template: this.template, params: this.parseParams() };
    this.ai.council(spec, this.promptEt).subscribe({
      next: (r) => {
        this.council.set(r);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set('Nõukogu ebaõnnestus: ' + (e?.error?.message ?? e?.message));
        this.loading.set(false);
      },
    });
  }

  runDfm() {
    this.error.set(null);
    this.loading.set(true);
    const spec = { template: this.template, params: this.parseParams() };
    this.ai.dfm(spec).subscribe({
      next: (r) => {
        this.dfm.set(r);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set('DFM ebaõnnestus: ' + (e?.error?.message ?? e?.message));
        this.loading.set(false);
      },
    });
  }

  async startIterate() {
    this.error.set(null);
    this.loading.set(true);
    this.iterEvents.set([]);

    const spec = { template: this.template, params: this.parseParams() };
    const stream = this.ai.iterateStream(spec, this.promptEt, this.targetScore);
    const reader = stream.getReader();
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        this.iterEvents.update((list) => [...list, value]);
      }
    } catch (err: any) {
      this.error.set('Iterate ebaõnnestus: ' + err?.message);
    } finally {
      this.loading.set(false);
    }
  }

  verdictLabel(v: string): string {
    return v === 'ship_it' ? '✅ PRINDI KOHE' : v === 'iterate' ? '🔁 VÄIKSED PARANDUSED' : '↺ REDESIGN';
  }

  stopReasonLabel(r: string): string {
    return (
      {
        target_reached: 'Eesmärgi saavutasin!',
        max_iter: 'Max iteratsioonid täis',
        no_improvement: 'Viimane patch halvendas',
        no_patch_available: 'AI ei anna rohkem soovitusi',
        review_failed: 'Review ebaõnnestus',
      } as any
    )[r] ?? r;
  }

  stepOf(e: IterateEvent): string {
    if ('step' in e) return `#${e.step}`;
    if (e.type === 'start') return 'init';
    if (e.type === 'stop') return 'done';
    return '';
  }
}
