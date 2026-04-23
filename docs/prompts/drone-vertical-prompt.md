# Prompt Claude Code'ile — drooni-vertikal (template'id + landing page)

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: lisa 5 drooni/FPV template'i ja eraldi landing sub-page `/drones`

## Konteksts

Meie toode AI-CAD on praegu generalistlik — template'ite-nimekiri katab koduseid asju (konks, klamber, lillepott). FPV-drooni kogukond on **massiline ostuvalmis turg**: r/FPV (800k+ kasutajat), r/Multicopter, Discord-serverid, YouTube-kanalid. Nad trükivad drooni-osasid **pidevalt** ja otsivad just-parameetrilisi lahendusi ("sama mount, aga mulle vaja 2306 mootorile mitte 2204-le").

Selle PR-iga lisame viis FPV-spetsiifilist template'i ja eraldi `/drones` sub-page'i, kust saab neid otse demo-nupu kaudu testida. Eesmärk: spetsialiseerunud SEO-landing, spetsialiseerunud demo-näited, spetsialiseerunud Reddit/Discord postitus-materjal.

## Mida teha

### 1. Viis uut CadQuery template'i

Failitee: `worker/app/templates/`

Iga template on eraldi fail: `motor_mount.py`, `prop_guard.py`, `fpv_camera_mount.py`, `fc_stack_mount.py`, `battery_tray.py`. Iga fail kasutab olemasolevat `@register` decorator mustrit (vt `worker/app/templates/shelf_bracket.py` näitena).

**1a. `motor_mount`**

Parameetrid:
- `motor_size` (enum, default `"2204"`) — valikus `"1404"`, `"1806"`, `"2204"`, `"2207"`, `"2306"`, `"2812"`, `"3110"` — määrab bolt-pattern'i (1404→M2 9mm, 1806→M2 12mm, 2204→M3 12mm, 2207→M3 16mm, 2306→M3 16mm, 2812→M3 19mm, 3110→M3 25mm)
- `frame_thickness` (number, min 2, max 8, default 4, unit "mm") — carbon raami paksus, kuhu mount kinnitub
- `arm_width` (number, min 10, max 40, default 22, unit "mm") — raami-harupaksus
- `screw_diameter` (number, auto-derived `motor_size`-ist, aga override'atav) — M2 või M3

Geomeetria: põhiplaat `arm_width × arm_width`, paksusega 3 mm. Peal 4 keermega motor-bolt-auku õige spatsiooniga (center → hole-center = bolt-pattern/2). All 2 kinnitusauku raamile screw_diameter-läbimõõduga. Keerme-augud peavad olema **läbivad** (ei ole threaded-insert-boss'i praegu — kasutaja paneb ise mutri või keerab PLA-sse). Filleti serv 1 mm kõikide väliste nurkade vahel.

**1b. `prop_guard`**

Parameetrid:
- `prop_diameter_inches` (number, min 2, max 9, default 5, unit "inches") — propelleri läbimõõt tollides
- `wall_thickness` (number, min 2, max 6, default 3, unit "mm")
- `mount_style` (enum, default `"frame_corner"`) — `"frame_corner"` (kinnitab raami nurka M3-ga) või `"motor_top"` (kinnitab mootori ülaosale). Praeguses v1 implementeeri ainult `"frame_corner"`.
- `clearance` (number, min 3, max 15, default 5, unit "mm") — vaba ruum propelleri ja guard'i vahel

Geomeetria: ringikujuline rõngas, sisediam = prop_diameter × 25.4 + 2 × clearance, paksus 8 mm, wall_thickness paksus. Üks kinnitusribi 30 mm pikk, 8 mm paks, raami-poolsel küljel, M3 augu. CadQuery'ga `cq.Workplane("XY").circle(outer).circle(inner).extrude(height)`.

**1c. `fpv_camera_mount`**

Parameetrid:
- `camera_model` (enum, default `"runcam_phoenix2"`) — valikus `"runcam_phoenix2"`, `"runcam_nano4"`, `"foxeer_predator5"`, `"caddx_ratel2"`, `"caddx_ant"`, `"generic_micro"`, `"generic_nano"`. Igal oma bolt-pattern ja laius:
  - micro (19mm wide, 19mm bolt-pattern)
  - nano (14mm wide, 14mm bolt-pattern)
  - caddx_ant (12mm wide, 12mm bolt-pattern)
- `frame_width` (number, min 16, max 35, default 20, unit "mm") — raami standup'ide vahe
- `tilt_angle` (number, min 0, max 60, default 25, unit "degrees") — kaamera kaldenurk

Geomeetria: kaks külgplaati `frame_width` kaugusel, kalduvad tilt_angle võrra. Kummalgi M2 avad õige bolt-pattern'iga. Mõlemad plaadid on 1.5 mm paksused TPU-sobivad (kasutaja võib valida materjali). Raami-kinnitusavad all — 2 tk M3.

**1d. `fc_stack_mount`**

Parameetrid:
- `stack_size` (enum, default `"20x20"`) — `"16x16"`, `"20x20"`, `"25.5x25.5"`, `"30.5x30.5"`
- `post_height` (number, min 5, max 30, default 15, unit "mm")
- `vibration_dampening` (boolean, default true) — kui true, lisa 4 tk 8 mm diameetriga silikoondampingu-tasku, muidu soliidne
- `frame_cutout` (number, min 20, max 50, default 35, unit "mm") — raami-ava kus mount kinnitub

Geomeetria: ruudukujuline baseplate = (frame_cutout + 6)², paksus 3 mm. Peal 4 M3-auku stack_size-vahega (center). Post'id tõusevad iga nurga juurest post_height kõrguseks, M3 keermega. Kui vibration_dampening on true, siis iga M3-augu ümber 10 mm diam × 2 mm sügav tasku silikondamperi jaoks.

**1e. `battery_tray`**

Parameetrid:
- `battery_length` (number, min 30, max 120, default 75, unit "mm")
- `battery_width` (number, min 20, max 50, default 35, unit "mm")
- `battery_height` (number, min 15, max 40, default 25, unit "mm")
- `velcro_slot_width` (number, default 16, unit "mm") — standard 16 mm velcro-rihm
- `frame_width` (number, min 25, max 60, default 35, unit "mm") — raami laius kuhu tray kinnitub

Geomeetria: ristkülikukujuline alus (battery_length + 10) × (battery_width + 10), paksus 2.5 mm. Küljed 3 mm kõrgusega üles. Alus sees 2 velcro-ava velcro_slot_width × 3 mm. Kaks kinnitusava all M3 frame_width-vahega.

### JSON-skeemid

Iga template vajab ka JSON-skeemi `worker/app/schemas/` kaustas. Kopeeri olemasoleva template'i skeemist (nt `shelf_bracket.json`) struktuur ja kohenda:

```json
{
  "template": "motor_mount",
  "description": { "en": "...", "et": "..." },
  "params": {
    "motor_size": {
      "type": "enum",
      "values": ["1404", "1806", "2204", "2207", "2306", "2812", "3110"],
      "default": "2204",
      "description_et": "Brushless mootori suurus (tuhandes-numberformaat)"
    },
    ...
  }
}
```

Registreeri skeemid `worker/app/main.py`-is `template_catalog`-i sees.

### 2. Intent-parsing bias drooni-märksõnadele

Leia `backend/src/main/java/com/aicad/claude/ClaudeClient.java` (või kus `parseIntent` live'ib). Lisa prompt-template'isse (see mis saadetakse Haiku'le) uus sektsioon:

```
Domain hints:
- If the user mentions "FPV", "drone", "quadcopter", "quad", "propeller", "prop", "brushless motor", or motor-size codes like "2204", "2306", "2807", "2812", strongly prefer these templates: motor_mount, prop_guard, fpv_camera_mount, fc_stack_mount, battery_tray.
- Motor-size codes map: first 2 digits = stator diameter (mm), last 2 digits = stator height (mm). Always pass as enum string like "2306".
- FC stack sizes: "20x20", "30.5x30.5" are most common for mini-quads, "25.5x25.5" for larger.
```

**Tähtis**: ära muuda üldist süsteemi-promptti drastiliselt — lisa ainult "Domain hints" sektsioon. See on täpsustus, mitte ülekirjutus.

### 3. Uus Angular route `/drones`

Failitee: `frontend/src/app/drones/drones.component.ts` (standalone, signals, sama muster nagu `pricing.component.ts`).

Sisu:

- **Hero-sektsioon** — pealkiri "FPV drone parts, described in plain English", alampealkiri "Parametric motor mounts, prop guards, FC stacks, camera mounts. Print-ready STL in seconds. No CAD required."
- **Demo-widget** (sama mis `/home`-s, aga pre-loaded drooni-näidetega). Reuse'i `demo.component.ts` kui see on olemas, muidu embed uuesti.
- **"Try these" chip'id demo-widget'i alla**:
  - "Motor mount for 2306"
  - "Prop guard for 5-inch"
  - "FC stack mount 30.5x30.5 with dampeners"
  - "FPV camera mount Runcam Phoenix 25 degrees"
  - "Battery tray for 1500mAh 4S"
  - "TPU pod for Mobula7"
- **Gallery-sektsioon** 3x2 ruudustik näide-render'itega (võib praegu olla placeholder-pildid `/assets/drones/*.png` — tegeli render'id tee hiljem käsitsi). Iga kaardi all on prompt mis selle genereeris — klikitav, kopeerib demo-välja.
- **Materials-sektsioon** — soovitused: "PLA OK raamile, PETG raami-osadele, TPU 95A dampinguosadele ja prop guard'idele, ABS mootori-ümber (kuumus)".
- **CTA-riba** — "Need a part we don't have a template for? Our free-form AI handles custom shapes → [Start 14-day trial]".
- **SEO metadata** — pealkiri `<title>FPV Drone Parts Generator — AI-CAD</title>`, meta description, OpenGraph-tags (og:title, og:description, og:image kasutades `/assets/drones/og-image.png` placeholder).

Angular'is — kasuta `Meta` ja `Title` teenuseid `@angular/platform-browser`-ist route-komponendi `ngOnInit`-is, et neid meta-tag'e dünaamiliselt uuendada.

### 4. Navbar ja sitemap

- Lisa "Drones" link ülemisse navbar'i (kõrval Pricing, Factory, Home)
- Uuenda `frontend/src/sitemap.xml` — lisa `<url><loc>https://ai-cad.ee/drones</loc>...`
- Uuenda `frontend/src/robots.txt` vajaduse korral (tõenäoliselt pole vaja, aga vaata üle et allow käib)

### 5. Testid

**Worker (Python pytest)** — `worker/tests/test_drone_templates.py`:

Smoke-test igale template'ile:
- Genereeri default-parameetritega, STL peab olema > 1 kB ja < 50 MB
- Genereeri min ja max parameetritega piirides
- Genereeri kui üks enum-väärtus on invaliid → oodatud `ValidationError`

**Backend (JUnit)** — `ClaudeClientTest.java` uuendus:
- Mock'itud Claude-vastusega test et kui prompt sisaldab "FPV" märksõna ja vastus tagastab `motor_mount` template'i, parseIntent tagastab kehtiva Spec'i

**Frontend (Jasmine)** — `drones.component.spec.ts`:
- Kontrollib et 6 "Try these" chip'i renderdavad
- Kontrollib et chip-klikk täidab demo-welle pron'miga
- Kontrollib et `<title>` on uuendatud `"FPV Drone Parts Generator — AI-CAD"`-iks

### 6. Dokumentatsioon

- Lisa `docs/TEMPLATES.md` (kui pole, loo) uued 5 template'it samasse formaati nagu olemasolevad
- Uuenda `README.md` — template'ite nimekirja kuvatavas tabelis lisa 5 uut rida koos kirjeldusega
- Lisa `docs/DRONES.md` (lühike, 1 lehekülg) — selgitab kellele `/drones` suunatud, millised materjalid, FPV-terminoloogia mis tagati ("bolt pattern", "stack size", "FC", "ESC" lahti kirjutatud tabeliga)

## Piirangud

- **Ära puuduta** olemasolevaid template'eid — ainult uusi lisa.
- **Ära muuda** olemasoleva demo-widget'i limite'e (2/päev per IP) — drooni-näited kasutavad sama kvoote.
- **Ära lisa** drooni-spetsiifilisi piiranguid hinnastamisse — samad plaanid kehtivad.
- **Ära loo** propelleri-template'i (võib olla ohtlik, peame `/api/review`-s hoiatama — see on eraldi ülesanne).
- **Ära implementeeri** `mount_style: "motor_top"` varianti `prop_guard`-is — ainult `"frame_corner"` v1-is.
- **Ära tee** reaalseid render-pilte galerii jaoks — kasuta placeholder'eid koodis, genereeri pildid käsitsi hiljem.

## Acceptance criteria

1. `cd worker && pytest -v` läheb rohelisena läbi, sh uued 5 template-smoke-testid
2. `cd backend && ./gradlew test` läheb läbi
3. `cd frontend && npm test` läheb läbi
4. Lokaalselt `docker compose up` — URL `http://localhost:4200/#/drones` laeb sisu
5. Demo-widget'il `/drones` lehel saab klikata iga 6 chip'i ja see täidab välja õige promptiga
6. `POST /api/generate` (authenticated) `{"prompt": "motor mount for 2306"}` tagastab Spec'i kus `template = "motor_mount"` ja `params.motor_size = "2306"`
7. `GET http://localhost:4200/sitemap.xml` sisaldab `/drones` URL-i
8. Brauseris DevTools → Elements → `<head>` näitab `<title>FPV Drone Parts Generator — AI-CAD</title>` `/drones` lehel
9. Navbar näitab "Drones" linki mõlemal `/home`, `/factory`, `/pricing` ja `/drones` lehel

Commit-sõnum: `feat(drones): add 5 FPV templates + /drones landing page + intent bias`

---

## Pärast merge'i — turundus-tegevused, mida sina teed käsitsi

(Claude Code ei tee seda, aga hea meeles pidada)

1. Tee 3 reaalset render-pilti uutest template'idest (OpenSCAD/Blender või three.js screenshot) ja asenda placeholder'id `/assets/drones/` kaustas
2. Kirjuta Reddit r/FPV postitus + lisa demo-video link
3. Lisa ProductHunt launchi drooni-näited "Featured use cases" osa
4. Tee LinkedIn postitus kuhu pildistad CadQuery-koodi kõrvuti renderduse STL-iga (tehniline publik armastab)
5. Liitu 2 Discord-serveriga (nt ModelForge, ImpulseRC community) ja küsi feedback'i
