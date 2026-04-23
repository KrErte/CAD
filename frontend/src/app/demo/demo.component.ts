import { Component, signal, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import * as THREE from 'three';
// @ts-ignore
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

@Component({
  selector: 'app-demo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="demo-widget">
      <div class="demo-header">
        <span class="demo-badge">DEMO</span>
        <span class="demo-counter" [class.demo-limit-reached]="remaining() <= 0">
          {{ remaining() }}/{{ dailyLimit }} left today
        </span>
      </div>
      <div class="demo-input-row">
        <input class="demo-prompt" type="text"
               [(ngModel)]="prompt"
               placeholder="Try: shelf bracket for 25mm pipe, 5kg load"
               (keyup.enter)="generate()"
               [disabled]="loading() || remaining() <= 0">
        <button class="demo-btn" (click)="generate()"
                [disabled]="loading() || !prompt || remaining() <= 0">
          {{ loading() ? 'Generating...' : 'Generate' }}
        </button>
      </div>
      <div class="demo-viewer" #demoViewer>
        <div *ngIf="!hasModel() && !loading() && !error()" class="demo-placeholder">
          Your 3D model will appear here
        </div>
        <div *ngIf="error()" class="demo-error">{{ error() }}</div>
      </div>
      <div *ngIf="remaining() <= 0" class="demo-cta-overlay">
        <p>Daily demo limit reached</p>
        <a href="/pricing" class="demo-upgrade-btn">Sign up for more &rarr;</a>
      </div>
    </div>
  `,
  styles: [`
    .demo-widget {
      background: var(--bg-card, #1a1a2e);
      border: 1px solid var(--border, #333);
      border-radius: 12px;
      padding: 1.2rem;
      margin-top: 1.5rem;
      position: relative;
    }
    .demo-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: .8rem;
    }
    .demo-badge {
      background: rgba(99,102,241,.15);
      color: var(--accent, #6366f1);
      font-size: .7rem;
      font-weight: 700;
      padding: .2rem .6rem;
      border-radius: 4px;
      letter-spacing: .5px;
    }
    .demo-counter {
      font-size: .85rem;
      color: var(--text-muted, #888);
    }
    .demo-limit-reached { color: var(--red, #ef4444); }
    .demo-input-row {
      display: flex;
      gap: .5rem;
      margin-bottom: .8rem;
    }
    .demo-prompt {
      flex: 1;
      padding: .6rem .8rem;
      border-radius: 8px;
      border: 1px solid var(--border, #333);
      background: var(--bg, #0f0f1a);
      color: var(--text, #e0e0e0);
      font-size: .9rem;
    }
    .demo-btn {
      padding: .6rem 1.2rem;
      border-radius: 8px;
      border: none;
      background: var(--accent, #6366f1);
      color: #fff;
      cursor: pointer;
      font-size: .9rem;
      white-space: nowrap;
    }
    .demo-btn:disabled { opacity: .5; cursor: not-allowed; }
    .demo-viewer {
      height: 200px;
      border-radius: 8px;
      background: var(--bg, #0f0f1a);
      position: relative;
      overflow: hidden;
    }
    .demo-placeholder, .demo-error {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: var(--text-muted, #666);
      font-size: .9rem;
    }
    .demo-error { color: var(--red, #ef4444); }
    .demo-cta-overlay {
      text-align: center;
      margin-top: .8rem;
      padding: .8rem;
      background: rgba(99,102,241,.08);
      border-radius: 8px;
    }
    .demo-cta-overlay p {
      margin: 0 0 .5rem;
      color: var(--text-muted, #888);
      font-size: .9rem;
    }
    .demo-upgrade-btn {
      color: var(--accent, #6366f1);
      text-decoration: none;
      font-weight: 600;
      font-size: .9rem;
    }
  `],
})
export class DemoComponent implements AfterViewInit {
  @ViewChild('demoViewer', { static: true }) viewerEl!: ElementRef<HTMLDivElement>;

  prompt = '';
  dailyLimit = 2;
  remaining = signal(2);
  loading = signal(false);
  error = signal('');
  hasModel = signal(false);

  private scene?: THREE.Scene;
  private camera?: THREE.PerspectiveCamera;
  private renderer?: THREE.WebGLRenderer;
  private controls?: OrbitControls;

  constructor(private http: HttpClient) {}

  ngAfterViewInit() {
    this.initThree();
  }

  generate() {
    if (!this.prompt || this.remaining() <= 0) return;
    this.loading.set(true);
    this.error.set('');

    // First get spec from templates (demo uses a simple approach)
    this.http.post('/api/demo/generate',
      { template: 'shelf_bracket', params: { pipe_diameter: 25, load_kg: 5 } },
      { responseType: 'arraybuffer' }
    ).subscribe({
      next: (data) => {
        this.loading.set(false);
        this.remaining.update(v => v - 1);
        this.loadSTL(data as ArrayBuffer);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 429) {
          this.remaining.set(0);
          this.error.set('Daily demo limit reached. Sign up for more!');
        } else {
          this.error.set(err.error?.message || 'Generation failed. Try again.');
        }
      }
    });
  }

  private initThree() {
    const el = this.viewerEl.nativeElement;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x0f0f1a);

    this.camera = new THREE.PerspectiveCamera(45, el.clientWidth / el.clientHeight, 0.1, 1000);
    this.camera.position.set(80, 60, 80);

    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(el.clientWidth, el.clientHeight);
    el.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;

    this.scene.add(new THREE.AmbientLight(0xffffff, 0.6));
    const dir = new THREE.DirectionalLight(0xffffff, 0.8);
    dir.position.set(50, 100, 50);
    this.scene.add(dir);

    const animate = () => {
      requestAnimationFrame(animate);
      this.controls?.update();
      this.renderer?.render(this.scene!, this.camera!);
    };
    animate();
  }

  private loadSTL(data: ArrayBuffer) {
    if (!this.scene) return;
    // Remove previous meshes
    const toRemove = this.scene.children.filter(c => c instanceof THREE.Mesh);
    toRemove.forEach(c => this.scene!.remove(c));

    const geometry = new STLLoader().parse(data);
    geometry.computeBoundingBox();
    geometry.center();
    const material = new THREE.MeshPhongMaterial({ color: 0x6366f1, flatShading: true });
    const mesh = new THREE.Mesh(geometry, material);
    this.scene.add(mesh);
    this.hasModel.set(true);

    // Fit camera
    const box = geometry.boundingBox!;
    const size = box.getSize(new THREE.Vector3()).length();
    this.camera!.position.set(size, size * 0.8, size);
    this.camera!.lookAt(0, 0, 0);
    this.controls?.target.set(0, 0, 0);
  }
}
