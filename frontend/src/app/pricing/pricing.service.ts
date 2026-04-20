import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Peegeldab backend'i DTO-sid — kui Java-poolel muutub väli, kohaneme siin.
 */
export type PricingQuoteStatus = 'OK' | 'ERROR' | 'FALLBACK' | 'OFFLINE';

export interface PricingQuote {
  providerId: string;
  providerDisplayName: string;
  status: PricingQuoteStatus;
  priceEur?: number;
  priceMinorUnits?: number;
  currency?: string;
  deliveryDaysMin?: number;
  deliveryDaysMax?: number;
  material?: string;
  orderUrl?: string;
  notes?: string;
  errorMessage?: string;
  responseMillis: number;
}

export interface PricingCompareResponse {
  quotes: PricingQuote[];
  cheapestProviderId: string | null;
  fastestProviderId: string | null;
  totalMillis: number;
}

export interface PricingCompareRequest {
  volumeCm3: number;
  weightG: number;
  bbox: { x: number; y: number; z: number };
  material?: string;
  infillPercent?: number;
  copies?: number;
  countryCode?: string;
  userEmail?: string;
}

@Injectable({ providedIn: 'root' })
export class PricingService {
  private http = inject(HttpClient);

  compare(req: PricingCompareRequest): Observable<PricingCompareResponse> {
    return this.http.post<PricingCompareResponse>('/api/pricing/compare', req);
  }

  listProviders(): Observable<{ id: string; name: string; enabled: boolean }[]> {
    return this.http.get<{ id: string; name: string; enabled: boolean }[]>('/api/pricing/providers');
  }
}
