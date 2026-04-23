# ADR-0002: CAD-genereerimine Python / CadQuery sidecar'is

- **Status**: accepted
- **Kuupäev**: 2025-09-20
- **Otsustajad**: @olen-krerte
- **Tehniline keerukus**: L

## Kontekst

Meil on vaja parameetriline CAD (praegu STL export) generator. Claude
tagastab JSON spec'i ({template, params}) ja me peame sellest tegema
STL fail'i, mida kasutaja saab 3D-printida.

## Variandid

### Variant A: CadQuery (Python) sidecar
- Plussid:
  - CadQuery on fluent Python-API OpenCascade'i peal — kiirelt
    skriptitav, laialdane funktsionaalsus (boolean, fillet, revolve)
  - Kood = parameetrid — muuda arv ja saad uue kuju
  - CC-BY litsents, community
- Miinused:
  - Python-spetsiifiline — vajame eraldi service'i (FastAPI)
  - OpenCascade C++ dependency image'is (~200 MB)

### Variant B: OpenJSCAD (JS, otse backend'is)
- Plussid: kõik Node.js-ist — monoreepo
- Miinused: OpenJSCAD funkt'id on piiratud (ei toeta keerukaid surface'e);
  community väiksem

### Variant C: FreeCAD Python API (headless)
- Plussid: vastup kui CadQuery, palju dokumentatsiooni
- Miinused: GUI-oriented kood, headless mode on hack — konteineris problemaatiline

### Variant D: Ehita C++ OpenCascade wrapper ise
- Plussid: max performance
- Miinused: 6 kuud arendust, maintain'imist väga palju

## Otsus

**Variant A: CadQuery Python sidecar**. Funktsionaalsus piisav, kiire
arendus-tsükkel. Tsiteeritud tulemus (JSON spec → STL) on võrreldav.

Sidecar arhitektuur (FastAPI worker port 8000) lubab meil:
- Backend'il jääda puhas Java'ks
- Sõltumatu skaleerumine (kui STL-generatsioon muutub bottleneck'iks,
  skaleerime ainult worker'eid)
- A/B testimine (proovida mõnda JSCAD'i sama API taga tulevikus)

## Tagajärjed

### Positiivsed
- 3 nädalat POC → 50+ template'i toetatud
- Python debug-REPL on arendajale kiire iteratsioon

### Negatiivsed
- Distributed systems debugging (mis juhtus worker'is kui test mini
  Spring Boot'is) — lahendas distributed tracing (OTel, ADR-0005)
- Container size ~500 MB (OpenCascade libc deps)

### Risk'id
- **Risk**: CadQuery ise pole 1.0 (v2.x development), võib breaking change'e
  teha. **Maandamine**: pin ==2.4.0, upgrade'ime ettevaatlikult.

## Viited
- [CadQuery docs](https://cadquery.readthedocs.io/)
- [OpenCascade](https://www.opencascade.com/)
