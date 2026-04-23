# AI-CAD turundus — näidissisud

Kõik tekstid on valmis kopeerimiseks. Kohenda oma häälele vastavaks enne postitamist.

---

## 1. 60-sekundiline demo skript (screencast)

**Kaader 1 (0:00–0:05)** — mustvalge tekst ekraanil:
> "You need a custom part. You don't own a 3D printer. You don't want to learn CAD."

**Kaader 2 (0:05–0:15)** — ekraanisalvestus, trükid aeglaselt:
> `konks vannituppa rätikule, kuni 3 kg, betoonseinale`

Loading spinner 1 sekund. STL ilmub three.js vaatesse ja pöörleb.

**Voice-over (või subtiiter):**
> "Describe it in any language. Claude parses your intent into a parametric spec. CadQuery generates the STL."

**Kaader 3 (0:15–0:25)** — klikid "Võrdle hindu" nupule.

Tabel 4 reaga: 3DKoda 8.40€ 2 päeva, 3DPrinditud 9.10€ 1 päev, Shapeways 12.30€ 5 päeva, Treatstock 7.80€ 4 päeva. Roheline "Cheapest" badge Treatstockil, sinine "Fastest" 3DPrinditul.

**Voice-over:**
> "Four print bureaus quote in parallel. Pick cheapest, fastest, or closest."

**Kaader 4 (0:25–0:40)** — klikid "AI Design Review".

JSON-vastus ilmub struktureeritult. Üks soovitus "Suurenda seinapaksust 5mm peale" on highlight'itud. Klikid "Rakenda" → STL uueneb vaates.

**Voice-over:**
> "Claude reviews your own design. One click applies the fix. It's self-critiquing CAD."

**Kaader 5 (0:40–0:55)** — klikid "Telli". Stripe'i checkout (mock). "Order placed. Printing at 3DPrinditud, Tallinn. Ships in 24h."

**Kaader 6 (0:55–1:00)** — logo + URL + CTA:
> "Describe. Compare. Order. No printer required. **ai-cad.ee**"

Fooni muusika: minimalne lo-fi või üldse mitte. Pane subtiitrid kohe peale, 70% vaatajatest vaatab heli vaikselt.

---

## 2. Reddit — r/SideProject postitus

**Pealkiri:**
> I built an AI that turns a sentence into a 3D-printable part and gets you 4 quotes — I don't even own a printer

**Body:**

Three months ago I got annoyed that I needed a custom shelf bracket and my options were (a) learn Fusion 360 for a week, (b) pay a freelancer €80, or (c) give up. So I built this instead.

**What it does:**
You type "L-bracket for a shelf, 5 kg load, 4 mounting holes" in any language. Claude parses the intent into a parametric spec. CadQuery (Python / OpenCascade) generates the STL. Then four 3D print bureaus quote it in parallel — you pick one, they ship it.

**Why I'm sharing:**
I'm not selling a printer. I'm not selling filament. I don't run the print bureaus. I'm basically a translation layer: natural language → geometry → supply chain.

**Stack for the curious:**
- Spring Boot 3 (Java 21) orchestrating Claude + worker + slicer + bureau APIs
- FastAPI + CadQuery worker with 20+ parametric templates
- PrusaSlicer CLI sidecar for real print-time / filament / price estimates
- Angular 18 + three.js viewer
- Reactive `Flux.merge` for parallel bureau quotes, 5s per-provider timeout
- Claude vision does a self-critique pass on the rendered STL and suggests parameter edits

**Coolest part:**
The AI Design Review loop. Claude looks at a PNG render of the generated part plus the original spec and returns structured JSON like `{ param: "wall_thickness", new_value: 5, rationale: "3mm may deform under 5kg load" }`. Every suggestion is a one-click apply button. The CAD critiques itself.

**What I need:**
Honest feedback on the onboarding flow. I'm Estonian so my English copywriting probably reads like a translated manual. Also if anyone runs a print bureau and wants an API integration, DM me.

Demo: [link]
GitHub: [link]

(Not monetized yet. Affiliate with bureaus when I flip the switch.)

---

## 3. Show HN postitus

**Pealkiri:**
> Show HN: Self-critiquing CAD — Claude generates a part, then reviews its own output

**Body (esimene kommentaar, HN konventsioon):**

Hi HN. I've been working on a pipeline where Claude doesn't just generate CAD output but also audits it.

The flow: natural-language prompt → Claude parses it into a parametric spec matching one of ~20 CadQuery templates (or falls back to Meshy.ai's text-to-3D for free-form shapes) → worker renders the STL → three.js produces a PNG preview → Claude vision receives the original prompt, the resolved spec, and the PNG, and returns a structured tool-use response with a score, strengths, weaknesses, and concrete parameter suggestions. Each suggestion is a clickable "Apply" button that patches the spec, clamps to the template's JSON-schema min/max, and regenerates.

The part I find interesting technically: the review step isn't a chat — it's a forced structured tool-call with the exact parameter names from the template schema, so "Apply" is a deterministic numeric patch, not a natural-language diff. No regex-parsing the LLM.

The quote-comparison side is reactive Spring Boot with `Flux.merge` across four bureau APIs, 5s per-provider timeout, 10s global. One provider being offline doesn't block the others. When a key is missing we fall back to a heuristic (€/cm³ × material multiplier, marked "~estimate").

Stack: Spring Boot 3 / Java 21, Python FastAPI + CadQuery, PrusaSlicer CLI sidecar, Angular 18 + three.js, Docker Compose / Helm.

Source: [GitHub link]
Demo: [URL]

Happy to go deep on any of it. Particularly interested if anyone has built similar self-review loops for other generative domains — I suspect the "force structured tool-call against a schema" pattern generalizes beyond CAD.

---

## 4. LinkedIn postitus (eesti turg, B2B)

Kolm kuud tagasi avastasin, et Eesti 3D-printimise teenusebürood saavad RFQ-sid ikka veel **meili teel**. PDF-iga. Käsitsi hinnastamine. Käsitsi töö-järjekord. Käsitsi klientide teavitamine.

See pole 2026. aasta ärimudel.

Ma ehitasin selle parandamiseks **PrintFlow MES** — Manufacturing Execution Systemi spetsiaalselt 3D-printimise teenusebüroodele:

→ Instant Quote Engine: klient saab hinna 2 sekundiga, mitte 2 päevaga
→ DFM audit: reeglipõhine, <100ms, ilma LLM-i ootamata
→ Printerifarm real-time dashboard (SSE stream)
→ RFQ postkast mis parsib automaatselt STL-id ja spec'id
→ Webhook integratsioonid Shopify, WooCommerce, oma e-poega

Kõik on ehitatud Spring Boot 3 + Angular 18 peale, Docker + Kubernetes, avatud lähtekood.

Otsin **3 Eesti print-büroost pilootklienti**, kes saavad 3 kuud tasuta kasutada vastutasuks aususe eest tagasisides. Kui tunned kedagi (või oled ise), saada DM.

P.S. Tarbija-pool on ka olemas (eestikeelne "kirjelda detail → saad 4 hinnapakkumist") aga see post on bürood jaoks. Sinu konkurendid vaatavad seda ka.

---

## 5. Cold email Eesti print-büroole

**Subject:** 3 kuud tasuta MES-i vastutasuks tagasiside eest

Tere [NIMI],

Nägin, et [FIRMA] töötleb [X tüüpi] tellimusi — tegin väikese uuringu ja tundub, et teie RFQ-protsess käib praegu meili ja käsitsi hinnastamise teel (kui ma eksin, ütle julgelt, siis see kiri pole sulle).

Ehitasin täisfunktsionaalse MES-süsteemi just 3D-print-büroodele: instant quote engine, DFM audit, printerifarmi real-time monitooring, RFQ postkast, webhook'id. Täielik stack on Spring Boot + Angular, Docker/k8s deploy, avatud kood.

Otsin **3 pilootklienti**, kes saavad kõik 3 kuud tasuta kasutada. Vastutasuks ainult üks asi: aus tagasiside mis töötab, mis ei tööta, mis on ärakasutatav.

Kui teid huvitab, panen 20-minutilise Teams-kõne kokku ja näitan demo. Kui vastus on ei, aitäh ajast — enam ei tülita.

Terviseid,
[SINU NIMI]
[telefon] / [GitHub link]

P.S. Kui [FIRMA] kasutab juba mõnda SaaS MES-i (3DPrinterOS, Teton, vms) — ka siis oleksin tänulik 5-minutilise vestluse eest, kuidas see teile töötab. Konkurendi-uuring, ei müüa.

---

## 6. Landing page hero (ingliskeelne)

**Pealkiri (H1):**
> Describe a part. Get 4 quotes. Order.

**Alampealkiri:**
> The AI that turns a sentence into a 3D-printable STL and finds the cheapest, fastest print shop to make it. No printer. No CAD software. No learning curve.

**3 bulletit alla (ikoonidega):**
- ✍️ Type in any language — "a shelf bracket for 5 kg, 4 holes"
- 🔍 Four print bureaus quote in parallel, in seconds
- 📦 Order with one click, delivered to your door in 1–5 days

**CTA nupp:**
> Try it free — no account needed

**Sotsiaalne tõestus rida (kui on):**
> Built by engineers who've shipped parts via 3DKoda, 3DPrinditud, Shapeways & Treatstock.

**Alt-teekond väiksem lingiga:**
> Run a print bureau? See [PrintFlow MES →]

---

## 7. Hackaday.io projekti kirjeldus

**Pealkiri:**
> AI-CAD: Natural language → parametric CAD → print bureau marketplace

**Summary (2 lauset, Hackaday näitab esilehel):**
> A full-stack pipeline that turns Estonian or English descriptions into 3D-printable STLs via Claude + CadQuery, then compares quotes from 4 print bureaus in parallel. Includes a self-critique loop where Claude reviews its own generated geometry and suggests parameter fixes.

**Details section** — pane ARCHITECTURE.md sisu siia + paar screenshot'i + demo video embed.

---

## Postitamise järjekord (esimesed 2 nädalat)

**Päev 1–3:** Tee demo video (60s). See blokeerib kõike muud.

**Päev 4:** Poste r/SideProject + r/SaaS. Vasta kommentaaridele 24h jooksul.

**Päev 5–7:** Võta Redditi feedback, paranda landing page'i copy.

**Päev 8 (teisipäev hommikul USA aja järgi):** Show HN.

**Päev 9–10:** Kui HN läks hästi, siis Product Hunt. Kui ei läinud, oota 2 nädalat ja proovi uuesti teise nurga alt.

**Päev 11:** LinkedIn post (B2B).

**Päev 12–14:** Cold emailid 10-le Eesti print-büroole. Üks email päevas, mitte burst.

**Ära tee samal päeval rohkem kui 1 suurt kanalit.** Tahad et iga kanal saaks täieliku tähelepanu ja vastad kommentaaridele ise.
