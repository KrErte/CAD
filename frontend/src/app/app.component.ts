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
    <main style="max-width: 960px; margin: 2rem auto; padding: 0 1rem;">
      <h1>🛠 AI-CAD</h1>
      <p style="color:#94a3b8;margin-top:0">Kirjelda eesti keeles mida vaja — saad 3D-printimisvalmis STL-faili.</p>

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
