import { Component, ElementRef, ViewChild, AfterViewInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
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
    <section style="background:linear-gradient(135deg,#1e1b4b 0%,#0f172a 100%);padding:4rem 1rem;border-bottom:1px solid #1e293b">
      <div style="max-width:960px;margin:0 auto">
        <div style="display:inline-block;padding:.3rem .7rem;background:#312e81;border-radius:999px;font-size:.8rem;color:#c7d2fe;margin-bottom:1rem">
          🇪🇪 Esimene eestikeelne AI-CAD tööriist
        </div>
        <h1 style="font-size:clamp(2rem,5vw,3.2rem);line-height:1.1;margin:0 0 1rem;background:linear-gradient(90deg,#a5b4fc,#f0abfc);-webkit-background-clip:text;-webkit-text-fill-color:transparent">
          🛠 AI-CAD — sõnadest 3D-detailini 30 sekundiga
        </h1>
        <p style="color:#cbd5e1;font-size:1.15rem;max-width:640px;margin-top:0">
          Kirjelda eesti keeles mis sul vaja on. Saad printimisvalmis STL-faili,
          mida saad kohe oma 3D-printeri juurde saata. Ei mingit CAD-i õppimist,
          ei mingit Hiinast tellimist, ei mingit 3D-modelleerijaga vaidlemist mõõtude üle.
        </p>
        <div style="display:flex;gap:.6rem;margin-top:1.5rem;flex-wrap:wrap">
          <a href="#app" style="background:#6366f1;color:white;padding:.7rem 1.2rem;border-radius:8px;text-decoration:none;font-weight:600">Proovi kohe →</a>
          <a href="#why" style="color:#c7d2fe;padding:.7rem 1.2rem;text-decoration:none">Miks meid?</a>
        </div>
      </div>
    </section>

    <section id="why" style="max-width:960px;margin:3rem auto;padding:0 1rem">
      <h2 style="font-size:1.8rem;margin-bottom:.3rem">Miks just meid valida?</h2>
      <p style="color:#94a3b8;margin-top:0">Võrdlus Eesti kontekstis — millised on sinu alternatiivid?</p>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:1rem;margin-top:1.5rem">
        <div class="card">
          <div style="font-size:1.8rem">⚡</div>
          <h3 style="margin:.3rem 0">30 sekundit vs 5 päeva</h3>
          <p style="color:#94a3b8;margin:0">3D-modelleerija võtab ühe detaili jaoks 2–5 päeva ja 100–500 €. Meilt saad sama 30 sekundiga, täpselt sinu mõõtudega.</p>
        </div>
        <div class="card">
          <div style="font-size:1.8rem">🇪🇪</div>
          <h3 style="margin:.3rem 0">Eesti keel on emakeel</h3>
          <p style="color:#94a3b8;margin:0">Fusion 360, Onshape, FreeCAD — kõik inglise keeles, õppimiskõver on pikk. Meie mõistame «riiuliklambrit 32mm torule».</p>
        </div>
        <div class="card">
          <div style="font-size:1.8rem">🎯</div>
          <h3 style="margin:.3rem 0">Parameetriline, mitte AI-mesh</h3>
          <p style="color:#94a3b8;margin:0">Teised AI-tööriistad (Meshy, Tripo) teevad orgaanilisi mesh'e, mida ei saa printida kandekoormusele. Meil on päris tehnilised CadQuery-mudelid.</p>
        </div>
        <div class="card">
          <div style="font-size:1.8rem">🔧</div>
          <h3 style="margin:.3rem 0">Kohandad brauseris</h3>
          <p style="color:#94a3b8;margin:0">AI paneb parameetrid paika, aga sina liigutad liugurit — mõõt ei mahu 1mm piires? Paranda ja regenereeri sekundi jooksul.</p>
        </div>
        <div class="card">
          <div style="font-size:1.8rem">💸</div>
          <h3 style="margin:.3rem 0">Tasuta kuni 3 STL-i kuus</h3>
          <p style="color:#94a3b8;margin:0">Freemium — proovid ilma kaardita. Professionaalidele 4.99 €/kuu piiramatult või pay-per-print koos partnertrüki'aga.</p>
        </div>
        <div class="card">
          <div style="font-size:1.8rem">🔒</div>
          <h3 style="margin:.3rem 0">Sinu disain, sinu fail</h3>
          <p style="color:#94a3b8;margin:0">STL jääb sinule. Laed alla, prindid kus tahad — meil Eesti partneritest (3DKoda, 3DPrinditud) kuni oma printeri garaažis.</p>
        </div>
      </div>

      <h3 style="margin-top:2.5rem">Võrdlus alternatiividega</h3>
      <div class="card" style="padding:0;overflow:hidden">
        <table style="width:100%;border-collapse:collapse;color:#e2e8f0">
          <thead style="background:#1e293b">
            <tr>
              <th style="text-align:left;padding:.8rem">&nbsp;</th>
              <th style="padding:.8rem">AI-CAD</th>
              <th style="padding:.8rem;color:#94a3b8">Thingiverse</th>
              <th style="padding:.8rem;color:#94a3b8">Hiina tellimus</th>
              <th style="padding:.8rem;color:#94a3b8">CAD-modelleerija</th>
            </tr>
          </thead>
          <tbody>
            <tr><td style="padding:.6rem .8rem">Aeg</td><td style="padding:.6rem .8rem;color:#a7f3d0">30 sek</td><td style="padding:.6rem .8rem">Tunde otsida</td><td style="padding:.6rem .8rem">2–4 nädalat</td><td style="padding:.6rem .8rem">2–5 päeva</td></tr>
            <tr style="background:#0f172a"><td style="padding:.6rem .8rem">Hind</td><td style="padding:.6rem .8rem;color:#a7f3d0">Tasuta / 4.99 €</td><td style="padding:.6rem .8rem">Tasuta</td><td style="padding:.6rem .8rem">5–50 €</td><td style="padding:.6rem .8rem">100–500 €</td></tr>
            <tr><td style="padding:.6rem .8rem">Täpsus</td><td style="padding:.6rem .8rem;color:#a7f3d0">Sinu mm-id</td><td style="padding:.6rem .8rem">Lähim variant</td><td style="padding:.6rem .8rem">Standardne</td><td style="padding:.6rem .8rem">Sinu mm-id</td></tr>
            <tr style="background:#0f172a"><td style="padding:.6rem .8rem">Eesti keel</td><td style="padding:.6rem .8rem;color:#a7f3d0">✓</td><td style="padding:.6rem .8rem">✗</td><td style="padding:.6rem .8rem">✗</td><td style="padding:.6rem .8rem">Sõltub</td></tr>
          </tbody>
        </table>
      </div>

      <h3 style="margin-top:2.5rem">Kellele see sobib?</h3>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:1rem">
        <div class="card">
          <strong>🚁 Droonitootjad</strong>
          <p style="color:#94a3b8;margin:.4rem 0 0;font-size:.95rem">Kaabliklambrid, sensori-mount'id, jig'id — 24h iteratsioonitsükkel.</p>
        </div>
        <div class="card">
          <strong>🏠 DIY-entusiastid</strong>
          <p style="color:#94a3b8;margin:.4rem 0 0;font-size:.95rem">Riiuliklambrid ebatavalistele torudele, konksud, kaabliorganiseerijad.</p>
        </div>
        <div class="card">
          <strong>🔧 Väiketootjad</strong>
          <p style="color:#94a3b8;margin:.4rem 0 0;font-size:.95rem">Asendusosad, prototüübid, kliendispetsiifilised adapterid.</p>
        </div>
        <div class="card">
          <strong>🎓 Koolid &amp; fablab'id</strong>
          <p style="color:#94a3b8;margin:.4rem 0 0;font-size:.95rem">Õpilased saavad ideed 3D-ks muuta ilma CAD-tarkvara õppimata.</p>
        </div>
      </div>
    </section>

    <main id="app" style="max-width: 960px; margin: 2rem auto; padding: 0 1rem;">
      <h2 style="margin-bottom:.3rem">Proovi kohe</h2>
      <p style="color:#94a3b8;margin-top:0">Kirjelda mida vaja — saad printimisvalmis STL-i sekunditega.</p>

      <div class="card">
        <label>Kirjeldus</label>
        <textarea rows="3" [(ngModel)]="prompt"
          placeholder="Näiteks: vajan riiuliklambrit 32mm veetorule, peab kandma 5kg koormust"></textarea>
        <button style="margin-top:.75rem" (click)="analyze()" [disabled]="loading()">
          {{ loading() ? 'Analüüsin...' : 'Analüüsi' }}
        </button>
      </div>

      <div class="card" *ngIf="spec() as s">
        <h2>{{ s.summary_et || s.template }}</h2>
        <p style="color:#94a3b8;margin-top:-.4rem">
          Template: <code>{{ s.template }}</code>
          — {{ catalogFor(s.template)?.description }}
        </p>
        <div *ngFor="let k of paramKeys(s)">
          <label>
            {{ k }}
            <span style="color:#64748b">
              ({{ schemaFor(s.template, k)?.min }}–{{ schemaFor(s.template, k)?.max }}
              {{ schemaFor(s.template, k)?.unit }})
            </span>
            <strong style="float:right">{{ s.params[k] }}</strong>
          </label>
          <input type="range"
            [min]="schemaFor(s.template, k)?.min || 0"
            [max]="schemaFor(s.template, k)?.max || 100"
            [step]="0.5"
            [ngModel]="s.params[k]" (ngModelChange)="updateParam(k, $event)">
        </div>
        <button style="margin-top:1rem" (click)="generate()" [disabled]="loading()">
          {{ loading() ? 'Genereerin...' : 'Genereeri STL' }}
        </button>
      </div>

      <div class="card" *ngIf="stlUrl()">
        <a [href]="stlUrl()" download="model.stl">⬇ Lae STL alla</a>
      </div>

      <div class="card" style="padding:0">
        <div #viewer style="width:100%;height:480px;border-radius:10px;overflow:hidden"></div>
      </div>

      <div class="card" *ngIf="error()" style="background:#7f1d1d">{{ error() }}</div>
    </main>
  `,
})
export class AppComponent implements AfterViewInit {
  @ViewChild('viewer', { static: true }) viewer!: ElementRef<HTMLDivElement>;

  prompt = '';
  spec = signal<Spec | null>(null);
  catalog = signal<Record<string, TemplateSchema>>({});
  stlUrl = signal<string | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  catalogFor(name: string): TemplateSchema | undefined {
    return this.catalog()[name];
  }
  schemaFor(name: string, param: string): ParamSchema | undefined {
    return this.catalog()[name]?.params[param];
  }

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private mesh?: THREE.Mesh;

  constructor(private http: HttpClient) {}

  ngAfterViewInit() {
    const el = this.viewer.nativeElement;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x0f172a);
    this.camera = new THREE.PerspectiveCamera(45, el.clientWidth / el.clientHeight, 1, 2000);
    this.camera.position.set(200, 200, 200);
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(el.clientWidth, el.clientHeight);
    el.appendChild(this.renderer.domElement);
    const controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.6));
    const light = new THREE.DirectionalLight(0xffffff, 0.8);
    light.position.set(1, 1, 1);
    this.scene.add(light);
    const animate = () => {
      requestAnimationFrame(animate);
      controls.update();
      this.renderer.render(this.scene, this.camera);
    };
    animate();
    this.http.get<Record<string, TemplateSchema>>('/api/templates')
      .subscribe(c => this.catalog.set(c));
  }

  paramKeys(s: Spec): string[] {
    return Object.keys(s.params || {});
  }

  updateParam(k: string, v: number) {
    const s = this.spec();
    if (!s) return;
    this.spec.set({ ...s, params: { ...s.params, [k]: +v } });
  }

  analyze() {
    this.error.set(null);
    this.loading.set(true);
    this.http.post<Spec>('/api/spec', { prompt: this.prompt }).subscribe({
      next: s => { this.spec.set(s); this.loading.set(false); },
      error: e => { this.error.set(e.error?.message || e.message); this.loading.set(false); },
    });
  }

  generate() {
    const s = this.spec();
    if (!s) return;
    this.loading.set(true);
    this.http.post('/api/generate', s, { responseType: 'arraybuffer' }).subscribe({
      next: buf => {
        const blob = new Blob([buf], { type: 'application/sla' });
        const url = URL.createObjectURL(blob);
        this.stlUrl.set(url);
        this.renderSTL(buf);
        this.loading.set(false);
      },
      error: e => { this.error.set(e.message); this.loading.set(false); },
    });
  }

  private renderSTL(buf: ArrayBuffer) {
    const loader = new STLLoader();
    const geom = loader.parse(buf);
    geom.center();
    if (this.mesh) this.scene.remove(this.mesh);
    const material = new THREE.MeshStandardMaterial({ color: 0x6366f1, metalness: 0.1, roughness: 0.6 });
    this.mesh = new THREE.Mesh(geom, material);
    this.scene.add(this.mesh);
    const box = new THREE.Box3().setFromObject(this.mesh);
    const size = box.getSize(new THREE.Vector3()).length();
    this.camera.position.set(size, size, size);
    this.camera.lookAt(0, 0, 0);
  }
}
