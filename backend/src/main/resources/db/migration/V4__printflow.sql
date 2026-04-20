-- =====================================================================
-- V4 — PrintFlow MES moodul
-- Lisab tootmislahenduse: Organizations, Customers, Materials, Spools,
--                         Printers, Jobs, BuildPlates, Quotes, RFQs, DFM.
-- =====================================================================

-- ----- Organisations (multi-tenant) ----------------------------------

CREATE TABLE organizations (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(64) NOT NULL UNIQUE,
    plan            VARCHAR(32) NOT NULL DEFAULT 'SOLO',  -- SOLO|STUDIO|FARM|ENTERPRISE
    owner_user_id   BIGINT NOT NULL REFERENCES users(id),
    hourly_rate_eur NUMERIC(8,2) NOT NULL DEFAULT 2.50,
    default_margin_pct INT NOT NULL DEFAULT 40,
    default_setup_fee_eur NUMERIC(8,2) NOT NULL DEFAULT 3.00,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizations_owner ON organizations(owner_user_id);

CREATE TABLE organization_members (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL DEFAULT 'OPERATOR',  -- OWNER|ADMIN|OPERATOR|VIEWER
    invited_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    UNIQUE(organization_id, user_id)
);

CREATE INDEX idx_orgmember_user ON organization_members(user_id);
CREATE INDEX idx_orgmember_org  ON organization_members(organization_id);

-- ----- Customers (B2B + B2C kliendid, iga org-i enda list) ------------

CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind            VARCHAR(8) NOT NULL DEFAULT 'B2C',   -- B2B | B2C
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(64),
    vat_id          VARCHAR(32),
    billing_address TEXT,
    shipping_address TEXT,
    notes           TEXT,
    default_margin_pct INT,
    linked_user_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_org     ON customers(organization_id);
CREATE INDEX idx_customers_email   ON customers(organization_id, email);
CREATE INDEX idx_customers_user    ON customers(linked_user_id);

-- ----- Materials + Filament spools ------------------------------------

CREATE TABLE materials (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    family          VARCHAR(16) NOT NULL,  -- PLA|PETG|ABS|PC|TPU|ASA|NYLON|RESIN|OTHER
    price_per_kg_eur NUMERIC(8,2) NOT NULL DEFAULT 25.00,
    density_g_cm3   NUMERIC(6,3) NOT NULL DEFAULT 1.240,
    slicer_preset   VARCHAR(128),
    min_wall_mm     NUMERIC(4,2) NOT NULL DEFAULT 1.2,
    max_overhang_deg INT NOT NULL DEFAULT 50,
    setup_fee_eur   NUMERIC(8,2),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_materials_org ON materials(organization_id);

CREATE TABLE filament_spools (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    material_id     BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    color           VARCHAR(64),
    color_hex       VARCHAR(7),
    mass_initial_g  INT NOT NULL DEFAULT 1000,
    mass_remaining_g INT NOT NULL DEFAULT 1000,
    serial_barcode  VARCHAR(64),
    vendor          VARCHAR(128),
    lot_code        VARCHAR(64),
    purchased_at    DATE,
    expires_at      DATE,
    assigned_printer_id BIGINT,   -- FK added below after printers created
    status          VARCHAR(16) NOT NULL DEFAULT 'FULL',  -- FULL|PARTIAL|EMPTY|DISPOSED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_spools_org      ON filament_spools(organization_id);
CREATE INDEX idx_spools_material ON filament_spools(material_id);
CREATE INDEX idx_spools_status   ON filament_spools(organization_id, status);

-- ----- Printers -------------------------------------------------------

CREATE TABLE printers (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    vendor          VARCHAR(64),
    model           VARCHAR(64),
    build_volume_x_mm INT NOT NULL DEFAULT 220,
    build_volume_y_mm INT NOT NULL DEFAULT 220,
    build_volume_z_mm INT NOT NULL DEFAULT 250,
    supported_material_families TEXT NOT NULL DEFAULT 'PLA,PETG',  -- CSV
    adapter_type    VARCHAR(32) NOT NULL DEFAULT 'MOCK',
    adapter_url     VARCHAR(512),
    adapter_api_key_encrypted TEXT,
    hourly_rate_eur NUMERIC(8,2),
    status          VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    current_job_id  BIGINT,   -- FK added after print_jobs
    progress_pct    INT NOT NULL DEFAULT 0,
    bed_temp_c      NUMERIC(5,1),
    hotend_temp_c   NUMERIC(5,1),
    last_heartbeat_at TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_printers_org    ON printers(organization_id);
CREATE INDEX idx_printers_status ON printers(organization_id, status);

-- Now add the FK from spools → printers
ALTER TABLE filament_spools
    ADD CONSTRAINT fk_spool_printer
    FOREIGN KEY (assigned_printer_id) REFERENCES printers(id) ON DELETE SET NULL;

CREATE TABLE printer_events (
    id              BIGSERIAL PRIMARY KEY,
    printer_id      BIGINT NOT NULL REFERENCES printers(id) ON DELETE CASCADE,
    event_type      VARCHAR(32) NOT NULL,   -- HEARTBEAT|JOB_START|JOB_DONE|JOB_FAIL|ERROR|TEMP|COMMAND
    payload         TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_printer_events_printer ON printer_events(printer_id, occurred_at DESC);
CREATE INDEX idx_printer_events_type    ON printer_events(event_type, occurred_at DESC);

-- ----- DFM reports ----------------------------------------------------

CREATE TABLE dfm_reports (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    file_name       VARCHAR(255),
    size_bytes      INT NOT NULL DEFAULT 0,
    bbox_x_mm       NUMERIC(8,2),
    bbox_y_mm       NUMERIC(8,2),
    bbox_z_mm       NUMERIC(8,2),
    volume_cm3      NUMERIC(10,3),
    triangles       INT,
    is_watertight   BOOLEAN,
    self_intersections INT,
    min_wall_mm     NUMERIC(5,2),
    overhang_area_cm2 NUMERIC(10,3),
    overhang_pct    NUMERIC(5,2),
    thin_features_count INT,
    issues          TEXT,    -- JSON-encoded array of issue objects
    severity        VARCHAR(8) NOT NULL DEFAULT 'OK',  -- OK|WARN|BLOCK
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dfm_org ON dfm_reports(organization_id, created_at DESC);

-- ----- Quotes ---------------------------------------------------------

CREATE TABLE quotes (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    customer_id     BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    quote_number    VARCHAR(32) NOT NULL,   -- e.g. "Q-2026-0001"
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- DRAFT|SENT|ACCEPTED|REJECTED|EXPIRED
    total_eur       NUMERIC(10,2) NOT NULL DEFAULT 0,
    subtotal_eur    NUMERIC(10,2) NOT NULL DEFAULT 0,
    setup_fee_eur   NUMERIC(10,2) NOT NULL DEFAULT 0,
    margin_pct      INT NOT NULL DEFAULT 40,
    rush_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.0,
    discount_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,
    valid_until     TIMESTAMPTZ,
    public_token    VARCHAR(64) UNIQUE,  -- customer accept link
    notes           TEXT,
    created_by_user_id BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    accepted_at     TIMESTAMPTZ,
    UNIQUE(organization_id, quote_number)
);

CREATE INDEX idx_quotes_org      ON quotes(organization_id, created_at DESC);
CREATE INDEX idx_quotes_customer ON quotes(customer_id);
CREATE INDEX idx_quotes_status   ON quotes(organization_id, status);

CREATE TABLE quote_lines (
    id              BIGSERIAL PRIMARY KEY,
    quote_id        BIGINT NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    line_no         INT NOT NULL,
    file_name       VARCHAR(255),
    stl_bytes       BYTEA,
    quantity        INT NOT NULL DEFAULT 1,
    material_id     BIGINT REFERENCES materials(id) ON DELETE RESTRICT,
    infill_pct      INT NOT NULL DEFAULT 20,
    layer_height_mm NUMERIC(4,2) NOT NULL DEFAULT 0.20,
    color           VARCHAR(32),
    unit_price_eur  NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_eur       NUMERIC(10,2) NOT NULL DEFAULT 0,
    slicer_result   TEXT,    -- JSON-encoded slicer response
    dfm_report_id   BIGINT REFERENCES dfm_reports(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quote_lines_quote ON quote_lines(quote_id);

-- ----- Print jobs -----------------------------------------------------

CREATE TABLE print_jobs (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    quote_id        BIGINT REFERENCES quotes(id) ON DELETE SET NULL,
    quote_line_id   BIGINT REFERENCES quote_lines(id) ON DELETE SET NULL,
    build_plate_id  BIGINT,    -- FK added below after build_plates
    material_id     BIGINT REFERENCES materials(id),
    spool_id        BIGINT REFERENCES filament_spools(id) ON DELETE SET NULL,
    printer_id      BIGINT REFERENCES printers(id) ON DELETE SET NULL,
    priority        INT NOT NULL DEFAULT 50,
    job_name        VARCHAR(255),
    gcode_ref       VARCHAR(255),  -- path or spool of the gcode
    estimated_time_sec INT,
    estimated_filament_g NUMERIC(8,2),
    status          VARCHAR(16) NOT NULL DEFAULT 'QUEUED',  -- QUEUED|ASSIGNED|PRINTING|PAUSED|DONE|FAILED|CANCELLED
    progress_pct    INT NOT NULL DEFAULT 0,
    queued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    failure_reason  TEXT,
    retries         INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_jobs_org     ON print_jobs(organization_id, status);
CREATE INDEX idx_jobs_printer ON print_jobs(printer_id, status);
CREATE INDEX idx_jobs_queue   ON print_jobs(organization_id, status, priority DESC, queued_at);

-- Now the FK from printers to print_jobs for current_job_id
ALTER TABLE printers
    ADD CONSTRAINT fk_printer_current_job
    FOREIGN KEY (current_job_id) REFERENCES print_jobs(id) ON DELETE SET NULL;

-- ----- Build plates (nested groups) -----------------------------------

CREATE TABLE build_plates (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    printer_id      BIGINT REFERENCES printers(id) ON DELETE SET NULL,
    material_id     BIGINT REFERENCES materials(id),
    plate_x_mm      INT,
    plate_y_mm      INT,
    nesting_data    TEXT,      -- JSON-encoded nesting rectangles
    status          VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    printed_at      TIMESTAMPTZ
);

CREATE INDEX idx_plates_org     ON build_plates(organization_id, status);
CREATE INDEX idx_plates_printer ON build_plates(printer_id);

ALTER TABLE print_jobs
    ADD CONSTRAINT fk_job_plate
    FOREIGN KEY (build_plate_id) REFERENCES build_plates(id) ON DELETE SET NULL;

-- ----- RFQ (Request For Quote) ---------------------------------------

CREATE TABLE rfqs (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    contact_name    VARCHAR(255) NOT NULL,
    contact_email   VARCHAR(255) NOT NULL,
    contact_phone   VARCHAR(64),
    description     TEXT,
    quantity_hint   INT,
    material_hint   VARCHAR(64),
    deadline        DATE,
    attachments     TEXT,     -- JSON-encoded file list
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',   -- NEW|IN_REVIEW|QUOTED|LOST|WON
    assigned_to_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    quote_id        BIGINT REFERENCES quotes(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rfq_org    ON rfqs(organization_id, status, created_at DESC);
CREATE INDEX idx_rfq_status ON rfqs(status);

-- ----- Webhook subscriptions -----------------------------------------

CREATE TABLE webhook_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_types     VARCHAR(255) NOT NULL,   -- CSV, e.g. "job.complete,quote.accepted,spool.low"
    target_url      VARCHAR(1024) NOT NULL,
    secret          VARCHAR(128),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_fired_at   TIMESTAMPTZ,
    last_status_code INT
);

CREATE INDEX idx_webhooks_org ON webhook_subscriptions(organization_id, active);

-- ----- Sequences for quote numbering ---------------------------------

CREATE SEQUENCE IF NOT EXISTS quote_number_seq START 1;

-- ----- Seed a default organization for existing admin users ----------
-- (olemasolevad kasutajad saavad automaatselt "My Workshop" org-i et
--  PrintFlow UI töötaks ka enne kui kasutaja käsitsi org-i loob)

INSERT INTO organizations (name, slug, plan, owner_user_id, hourly_rate_eur, default_margin_pct)
SELECT 'My Workshop', 'workshop-' || id, 'SOLO', id, 2.50, 40
FROM users
WHERE id NOT IN (SELECT owner_user_id FROM organizations)
ON CONFLICT DO NOTHING;

INSERT INTO organization_members (organization_id, user_id, role, accepted_at)
SELECT o.id, o.owner_user_id, 'OWNER', NOW()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_members om
    WHERE om.organization_id = o.id AND om.user_id = o.owner_user_id
);

-- Seed default PLA material for each org
INSERT INTO materials (organization_id, name, family, price_per_kg_eur, density_g_cm3, min_wall_mm, max_overhang_deg)
SELECT id, 'Default PLA', 'PLA', 25.00, 1.240, 1.2, 50
FROM organizations
WHERE NOT EXISTS (SELECT 1 FROM materials WHERE organization_id = organizations.id);

INSERT INTO materials (organization_id, name, family, price_per_kg_eur, density_g_cm3, min_wall_mm, max_overhang_deg)
SELECT id, 'Default PETG', 'PETG', 28.00, 1.270, 1.0, 45
FROM organizations
WHERE NOT EXISTS (SELECT 1 FROM materials WHERE organization_id = organizations.id AND family = 'PETG');
