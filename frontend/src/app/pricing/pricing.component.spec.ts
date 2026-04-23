import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PricingComponent } from './pricing.component';
import { RouterTestingModule } from '@angular/router/testing';

describe('PricingComponent', () => {
  let component: PricingComponent;
  let fixture: ComponentFixture<PricingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PricingComponent, RouterTestingModule],
    }).compileComponents();
    fixture = TestBed.createComponent(PricingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have 3 tabs', () => {
    expect(component.tabs.length).toBe(3);
  });

  it('Makers tab should show 2 cards', () => {
    component.activeTab.set('makers');
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.pricing-card');
    expect(cards.length).toBe(2);
  });

  it('Bureaus tab should show 4 cards', () => {
    component.activeTab.set('bureaus');
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.pricing-card');
    expect(cards.length).toBe(4);
  });

  it('Developers tab should show 3 cards with PAYG chip', () => {
    component.activeTab.set('developers');
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.pricing-card');
    expect(cards.length).toBe(3);
    const chip = fixture.nativeElement.querySelector('.pricing-chip');
    expect(chip).toBeTruthy();
    expect(chip.textContent).toContain('PAYG');
  });

  it('Creator should be featured on Makers tab', () => {
    component.activeTab.set('makers');
    fixture.detectChanges();
    const featured = fixture.nativeElement.querySelectorAll('.pricing-featured');
    expect(featured.length).toBe(1);
    expect(featured[0].querySelector('h3').textContent).toBe('Creator');
  });

  it('Studio should be featured on Bureaus tab', () => {
    component.activeTab.set('bureaus');
    fixture.detectChanges();
    const featured = fixture.nativeElement.querySelectorAll('.pricing-featured');
    expect(featured.length).toBe(1);
    expect(featured[0].querySelector('h3').textContent).toBe('Studio');
  });

  it('Growth should be featured on Developers tab', () => {
    component.activeTab.set('developers');
    fixture.detectChanges();
    const featured = fixture.nativeElement.querySelectorAll('.pricing-featured');
    expect(featured.length).toBe(1);
    expect(featured[0].querySelector('h3').textContent).toBe('Growth');
  });
});
