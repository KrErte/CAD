# Prompt Claude Code'ile — avalik showcase / gallerii-leht

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: loo `/showcase` — curated avalik gallerii näidete STL-idest, mida meie süsteem oskab genereerida

## Konteksts

Meil on:
- `/home` demo-widget (unauth, 2/päev per IP) — näitab **ühte** genereerimist korraga, liveina
- `/drones` sub-page — niche'itud FPV näidetega
- `/pricing` — hinnad

Puudub leht, mis vastab küsimusele **"mida kõike see saab?"**. Uuelt külastajalt Redditist/HN-ist eeldame, et ta tahab näha **20+ valmisnäidet enne kui üldse prompti kirjutab** — sotsiaalne tõestus + inspiratsioon. Showcase-leht täidab selle rolli.

Iga näide on eraldi indekseeritav URL (`/showcase/gopro-hero-mount`), mis tähendab — korralikult tehtuna — orgaaniline SEO pikas perspektiivis on selle lehe kõige väärtuslikum tulemus.

## Mida teha

### 1. Näidete andmebaas (staatiline JSON v1-is)

Failitee: `frontend/src/assets/showcase/showcase-catalog.json`

Struktuur (20–30 kirjet, jaota allpool kategooriasse):

```json
[
  {
    "slug": "shelf-bracket-5kg",
    "category": "home",
    "title_en": "L-shaped shelf bracket, 5 kg load",
    "title_et": "L-kujuline riiuli-klamber, 5 kg koormus",
    "prompt_en": "L-shaped shelf bracket, 5 kg load, 4 mounting holes",
    "prompt_et": "L-kujuline riiuli-klamber, 5 kg koormus, 4 kinnitusauku",
    "template": "shelf_bracket",
    "params": { "wall_thickness": 6, "arm_length": 120, "mounting_holes": 4 },
    "preview_image": "/assets/showcase/shelf-bracket-5kg.png",
    "stl_url": "/assets/showcase/shelf-bracket-5kg.stl",
    "material_suggestion": "PETG",
    "print_time_min": 142,
    "filament_grams": 38,
    "estimated_cost_eur": 1.20,
    "description_en": "Rigid L-bracket sized for a 5 kg shelf load...",
    "description_et": "Jäik L-klamber 5 kg riiuli-koormuse jaoks...",
    "tags": ["home", "shelf", "bracket", "functional"]
  },
  ...
]
```

**Kategooriad** (minimaalselt 5 näidet igas):
- `home` — konksud, klambrid, karbid, sildid
- `workshop` — tool holder'id, cable clip'id, DIN-rail mount'id
- `fpv` — drooni-osad (linkida `/drones`-iga)
- `tech` — Raspberry Pi, elektroonika-korpused, stack-mount'id
- `garden` — lillepotid, istikute-sildid, kasti-liigendid

Võid ka lisa kategooria `ai-generated` — näited mis on tulnud Template Forge'ist (kui see juba olemas), näitamaks, et library kasvab automaatselt.

### 2. Placeholder pildid

Kuna reaalsed render'id teeme käsitsi hiljem, kasuta v1-s placeholder-pilte:

- Kõigile kirjetele: `/assets/showcase/placeholders/default.png` — genereeritud SVG gradient'iga ja teksti "Preview rendering..."
- Tähtsamatele 6–8 näitele (demo-reel jaoks): tee tegelik render kas käsitsi OpenSCAD/Blender'is või CadQuery Python'iga kirjuta lihtne render-skript `scripts/render-showcase.py`, mis kasutab matplotlib 3D projection'i STL-i jaoks. Lihtne, piisav v1-ks.

Loo skript `scripts/render-showcase.py` mis:
- Loeb `showcase-catalog.json`
- Iga kirje kohta: genereerib STL-i (kutsub worker'it: `POST /api/generate`) → salvestab `frontend/src/assets/showcase/{slug}.stl`
- Teeb lihtsa matplotlib-render'i 3 nurga alt (isometric, front, top) → kompositsiooni-PNG salvestab `frontend/src/assets/showcase/{slug}.png`
- Käivitatav `python scripts/render-showcase.py --all` või `--slug shelf-bracket-5kg`

See skript on **tööriist sinu jaoks**, mitte automaatne CI-osa. Lase ise kontrolli all.

### 3. Angular showcase-route

Failitee: `frontend/src/app/showcase/showcase-list.component.ts` + `showcase-detail.component.ts`

**`/showcase`** — list-view:
- **Hero-sektsioon** — pealkiri "Showcase — what AI-CAD can make", alampealkiri "30+ parametric parts ready to generate, tweak, or order in one click"
- **Kategooria-filtreerimine** — horizontal chip-bar "All / Home / Workshop / FPV / Tech / Garden"
- **Otsing** — top-right search input, filtreerib title + tags
- **Grid** — 3 veergu desktop'is, 2 tablet, 1 mobile. Iga kaart:
  - Preview-pilt (square aspect ratio, object-fit: cover)
  - Kategooria-badge paremal üleval
  - Pealkiri
  - Materjali-soovitus + print-time + cost (väikesed ikoonidega)
  - "Try this prompt →" klikil avab `/showcase/{slug}`
- **CTA bänner lehe lõpus** — "Don't see what you need? Describe it in plain English → [Try demo]"

**`/showcase/{slug}`** — detail-view:
- **Big preview** vasakul, 60% laiusest. Kui kasutaja klikib "View 3D" → switch'ib three.js interactive viewer'ile (laeb `stl_url`)
- **Paremal** — pealkiri, prompt tekstikastis monospace-fondiga (kopeeritav), kategooria, materjali-soovitus detailsemalt (paari lausega miks PETG vs PLA), print-specs (aeg, kaal, hind), template-nimi linkina (`/docs#template-{name}`)
- **"Try this prompt" suur primary-nupp** — suunab `/home?prompt={encoded}` → demo-widget pre-fill'ib prompti'ga
- **"Get it printed" secondary-nupp** — suunab partner-pricing lehele selle STL-iga
- **All** — "Related" sektsioon, 3 samast kategooriast näidet
- **JSON-LD schema.org `Product`** HTML-is SEO jaoks

### 4. SEO metadata

- **Per-page `<title>`** — list: "Showcase — Parametric 3D Parts | AI-CAD", detail: "{title_en} — AI-CAD Showcase"
- **Meta description** per page — detail-view'l kasuta `description_en` esimest 155 tähemärki
- **OpenGraph** tags: `og:image` = preview_image, `og:type: product`, `og:title`, `og:description`
- **Twitter Card** — `summary_large_image`
- **Sitemap** — lisa iga slug `sitemap.xml`-isse automaatselt (uuenda sitemap-generaatorit kui on, või käsitsi listi 20–30 URL-i praegu)
- **Canonical** URL — pane `<link rel="canonical">` iga detail-lehele

Uuenda `frontend/src/app/app.component.ts` (või kus iganes globaalne SEO on) + kasuta Angular `Meta` ja `Title` teenuseid per-route.

### 5. Deeplink demo-widget'isse

Demo-widget `demo.component.ts`-isse lisa query-param support: kui `?prompt=X` on URL-is (base64 või URL-encoded), pre-fill prompt-tekstiväljale kohe. See võimaldab "Try this prompt" nupu töötada:

```typescript
ngOnInit() {
  this.route.queryParams.subscribe(params => {
    if (params['prompt']) {
      this.promptInput.set(decodeURIComponent(params['prompt']));
      // optional: auto-generate pärast 500ms viidet
    }
  });
}
```

Aga **ära auto-generate'i** — kasutaja peab siiski "Generate" nuppu klikkima, see hoiab demo-kvoote säästlikult (2/päev per IP).

### 6. Navbar ja navigatsioon

Lisa "Showcase" link ülemisse navbar'i. Järjekord: `Home / Showcase / Drones / Pricing / Factory`. Kui mobile-navbar on olemas, lisa ka sinna.

Lisa `/home` hero-sektsiooni kõrval tekst "Not sure where to start? [Browse our showcase →]". Üks-kahe näite preview pilt kõrval sellest.

### 7. Analüüs-tracking

Lisa iga showcase-kaardi klikkile analytics-event: `showcase_card_click` koos `{slug, category}`. "Try this prompt" klikkile: `showcase_try_click` koos `{slug}`. "Get it printed" klikkile: `showcase_order_click`.

Kui sul pole veel frontend'i analytics-wrapperit, loo lihtne `AnalyticsService.track(event, props)` mis paneb `window.dataLayer.push(...)` või POST'ib `/api/events` endpoint'ile. Integreerib hiljem Plausible / PostHog-iga.

### 8. Testid

- **Angular**:
  - `showcase-list.component.spec.ts` — renderdab kõik kirjed, filtreerib kategooria järgi, otsing töötab
  - `showcase-detail.component.spec.ts` — laeb õige kirje slug'i järgi, "Try this prompt" nupp navigeerib õige query-param'iga
  - Meta-teenuste mock — kontrolli et `<title>` ja `meta[name=description]` uuenevad

- **E2E smoke** (kui Cypress/Playwright seadistatud):
  - Ava `/showcase` → näe 30 kaarti
  - Klikka "Home" filter → näe ainult home-kategooriaid
  - Klikka esimene kaart → URL muutub `/showcase/{slug}`
  - Klikka "Try this prompt" → URL muutub `/home?prompt=...`, demo-widget välja on pre-filled

### 9. Dokumentatsioon

Uus fail `docs/SHOWCASE.md`:
- Kuidas näidet lisada (JSON-kirje + pilt + STL)
- Render-skripti kasutamine
- SEO-soovitused iga näite kirjeldusele (155 tähemärki, märksõnad)
- Kuidas mõõta showcase-lehe ROI-d (analytics-sündmused + konversioon demo → trial)

## Piirangud

- **Ära lae** STL-e dünaamiliselt genereerimise teel kui kasutaja showcase-lehte laeb — see oleks kallis ja aeglane. STL on eelrenderдитud ja served'itud staatikast.
- **Ära lisa** user-submitted examples funktsionaalsust — see on v2 (moderatsioon, spam-filter, GDPR).
- **Ära integreeri** tegelikku 3D-printerite API-ga "Get it printed" jaoks — see nupp viib olemasolevasse partneri-hinnavõrdluse sektsiooni, mis juba töötab.
- **Ära tee** animatsioone ega fade'e väsitavaks — lihtne grid, kiire laadimine (Lighthouse-skoor ≥ 90).

## Acceptance criteria

1. `/showcase` laeb 30+ kaarti, filter-chip'id töötavad, otsing filtreerib reaalajas
2. `/showcase/shelf-bracket-5kg` (või mõni teine slug) laeb detail-vaate korrektse info + pildiga
3. "Try this prompt" nupp navigeerib `/home?prompt=...`-ile ja demo-widget'i prompt-väli on täidetud
4. View-source iga detail-lehe `<head>` sisaldab kehtivat `og:image`, `og:title`, `meta[name=description]`
5. Sitemap.xml sisaldab 30+ uut URL-i
6. Lighthouse-audit lehel näitab: Performance ≥ 85, SEO ≥ 95, Accessibility ≥ 90
7. `scripts/render-showcase.py --slug shelf-bracket-5kg` genereerib kehtiva STL + PNG
8. Navbar näitab "Showcase" linki, mobile-navbar'is samuti
9. Kõik testid läbivad
10. `docs/SHOWCASE.md` olemas ja viidatud README-s

Commit-sõnum: `feat(showcase): add public showcase gallery with 30+ curated examples`

---

## Täiendav mõju — mille kinnitab SEO-tööriist 3 kuu pärast

- Iga slug on eraldi URL mille pealkirjas on long-tail-märksõna ("shelf bracket 5kg load 3d print generator")
- 30 näidet × keskmiselt 3 otsingumärksõna = ~90 long-tail-fraasi, millega su toode võib indeksisse saada
- Google suhtub hästi "tools that solve specific problems" lehtedesse — see muster on populaarne, AdSense-free, kiire

Minu kogemusest sarnaste niche-dev-toodete seas: showcase-leht võib tuua **30–40% orgaanilisest trafikust** 6 kuu pärast, kui iga näite lehel on 300+ sõna kirjeldust ja OpenGraph on korralik.
