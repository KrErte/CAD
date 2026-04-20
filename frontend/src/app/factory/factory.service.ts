import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Kpi {
  revenue_eur: number;
  quotes_total: number;
  quotes_draft: number;
  quotes_accepted: number;
  jobs_done_total: number;
  jobs_failed_total: number;
  jobs_queued: number;
  jobs_printing: number;
  success_rate_pct: number;
  printers_total: number;
  printers_idle: number;
  printers_printing: number;
  printers_offline: number;
  oee_pct: number;
  calculated_at: string;
}

export interface Material {
  id: number;
  name: string;
  family: string;
  price_per_kg_eur: number;
  density_g_cm3: number;
  min_wall_mm: number;
  max_overhang_deg: number;
  setup_fee_eur: number;
  active: boolean;
}

export interface Printer {
  id: number;
  name: string;
  vendor: string;
  model: string;
  status: string;
  current_job_id?: number;
  current_progress_pct?: number;
  build_volume_mm?: { x: number; y: number; z: number };
  supported_materials?: string;
  last_heartbeat_at?: string;
  adapter_type?: string;
}

export interface PrintJob {
  id: number;
  quote_id?: number;
  quote_line_id?: number;
  printer_id?: number;
  material_id?: number;
  status: string;
  priority: number;
  progress_pct: number;
  queued_at: string;
  started_at?: string;
  completed_at?: string;
  est_duration_sec?: number;
  actual_duration_sec?: number;
}

export interface Customer {
  id: number;
  kind: string;
  name: string;
  email?: string;
  phone?: string;
  vat_id?: string;
  billing_address?: string;
  default_margin_pct?: number;
  created_at: string;
}

export interface Spool {
  id: number;
  material_id: number;
  color: string;
  color_hex?: string;
  mass_initial_g: number;
  mass_remaining_g: number;
  pct_remaining: number;
  status: string;
  vendor?: string;
  assigned_printer_id?: number;
  serial_barcode?: string;
}

export interface Quote {
  id: number;
  code: string;
  customer_id?: number;
  status: string;
  subtotal_eur: number;
  total_eur: number;
  currency: string;
  created_at: string;
  accepted_at?: string;
  notes?: string;
  lines?: QuoteLine[];
}

export interface QuoteLine {
  id: number;
  filename: string;
  material_id?: number;
  quantity: number;
  volume_cm3?: number;
  weight_g?: number;
  print_time_sec?: number;
  unit_price_eur: number;
  line_total_eur: number;
  dfm_severity?: string;
  rush: boolean;
}

@Injectable({ providedIn: 'root' })
export class FactoryService {
  private http = inject(HttpClient);
  private base = '/api/printflow';

  kpi(): Observable<Kpi> { return this.http.get<Kpi>(`${this.base}/analytics/kpi`); }
  topMaterials(): Observable<any[]> { return this.http.get<any[]>(`${this.base}/analytics/top-materials`); }
  revenue(days = 30): Observable<any[]> { return this.http.get<any[]>(`${this.base}/analytics/revenue?days=${days}`); }

  materials(all = false): Observable<Material[]> {
    return this.http.get<Material[]>(`${this.base}/materials?all=${all}`);
  }
  createMaterial(m: Partial<Material>): Observable<Material> { return this.http.post<Material>(`${this.base}/materials`, m); }
  updateMaterial(id: number, m: Partial<Material>): Observable<Material> { return this.http.put<Material>(`${this.base}/materials/${id}`, m); }
  deleteMaterial(id: number): Observable<any> { return this.http.delete(`${this.base}/materials/${id}`); }

  printers(): Observable<Printer[]> { return this.http.get<Printer[]>(`${this.base}/printers`); }
  createPrinter(p: Partial<Printer>): Observable<Printer> { return this.http.post<Printer>(`${this.base}/printers`, p); }
  updatePrinter(id: number, p: Partial<Printer>): Observable<Printer> { return this.http.put<Printer>(`${this.base}/printers/${id}`, p); }
  pausePrinter(id: number): Observable<any> { return this.http.post(`${this.base}/printers/${id}/pause`, {}); }
  resumePrinter(id: number): Observable<any> { return this.http.post(`${this.base}/printers/${id}/resume`, {}); }
  cancelPrinterJob(id: number): Observable<any> { return this.http.post(`${this.base}/printers/${id}/cancel`, {}); }

  jobs(status?: string): Observable<PrintJob[]> {
    const q = status ? `?status=${status}` : '';
    return this.http.get<PrintJob[]>(`${this.base}/jobs${q}`);
  }
  cancelJob(id: number): Observable<any> { return this.http.post(`${this.base}/jobs/${id}/cancel`, {}); }
  setJobPriority(id: number, priority: number): Observable<any> {
    return this.http.post(`${this.base}/jobs/${id}/priority`, { priority });
  }

  customers(): Observable<Customer[]> { return this.http.get<Customer[]>(`${this.base}/customers`); }
  createCustomer(c: Partial<Customer>): Observable<Customer> { return this.http.post<Customer>(`${this.base}/customers`, c); }

  spools(): Observable<Spool[]> { return this.http.get<Spool[]>(`${this.base}/spools`); }
  lowStock(threshold = 100): Observable<Spool[]> {
    return this.http.get<Spool[]>(`${this.base}/spools/low-stock?threshold_g=${threshold}`);
  }

  quotes(): Observable<Quote[]> { return this.http.get<Quote[]>(`${this.base}/quotes`); }
  quote(id: number): Observable<Quote> { return this.http.get<Quote>(`${this.base}/quotes/${id}`); }
  createQuote(form: FormData): Observable<Quote> {
    return this.http.post<Quote>(`${this.base}/quotes`, form);
  }
  acceptQuote(id: number): Observable<any> {
    return this.http.post(`${this.base}/quotes/${id}/accept`, {});
  }

  rfqs(): Observable<any[]> { return this.http.get<any[]>(`${this.base}/rfq`); }
  setRfqStatus(id: number, status: string) {
    return this.http.post(`${this.base}/rfq/${id}/status`, { status });
  }

  /** SSE stream of printer events */
  openEventStream(): EventSource {
    return new EventSource(`${this.base}/printers/events/stream`);
  }
}
