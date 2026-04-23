import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DemoComponent } from './demo.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';

describe('DemoComponent', () => {
  let component: DemoComponent;
  let fixture: ComponentFixture<DemoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemoComponent, HttpClientTestingModule],
    }).compileComponents();
    fixture = TestBed.createComponent(DemoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display daily limit counter', () => {
    const counter = fixture.nativeElement.querySelector('.demo-counter');
    expect(counter).toBeTruthy();
    expect(counter.textContent).toContain('2/2 left today');
  });

  it('should disable button when limit reached', () => {
    component.remaining.set(0);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.demo-btn') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });

  it('should show CTA overlay when limit reached', () => {
    component.remaining.set(0);
    fixture.detectChanges();
    const overlay = fixture.nativeElement.querySelector('.demo-cta-overlay');
    expect(overlay).toBeTruthy();
    expect(overlay.textContent).toContain('Daily demo limit reached');
  });

  it('should show upgrade link when limit reached', () => {
    component.remaining.set(0);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('.demo-upgrade-btn');
    expect(link).toBeTruthy();
    expect(link.getAttribute('href')).toBe('/pricing');
  });
});
