# Privaatsuspoliitika

**Viimati uuendatud:** 15. aprill 2026

## 1. Kes me oleme

AI-CAD on [ÄRINIMI + REG-NR + AADRESS] poolt opereeritav veebiteenus. E-posti aadress GDPR-päringuteks: privacy@[DOMEEN].

## 2. Millised andmeid me kogume

**Kontoga seotud andmed (tuleb Google'i autentimisest):**
- E-posti aadress
- Nimi
- Google'i unikaalne kasutaja-ID (sub)

**Teenuse kasutamise käigus tekkivad andmed:**
- Sinu eestikeelsed kirjeldused ("promptid")
- Genereeritud STL-failid ja nende parameetrid
- Genereerimise kuupäev ja kellaaeg
- Plaani tüüp ja igakuine kasutus (quota)

**Maksetega seotud andmed:**
- Kasutame Stripe'i — me ise ei näe ega salvesta sinu kaardiandmeid
- Salvestame ainult Stripe'i kliendi-ID ja tellimuse staatuse

**Tehnilised andmed:**
- IP-aadress (rate-limiting ja turvalisuse jaoks, säilitatakse 30 päeva)
- Brauseri tüüp (serveri logides, säilitatakse 30 päeva)

## 3. Miks me andmeid kogume

- **Konto haldamine**: e-post ja nimi on vajalikud autentimiseks ja sisselogimiseks
- **Teenuse osutamine**: promptid saadetakse Anthropic'ule (Claude), et genereerida CAD-spetsifikatsioon; STL-id salvestame sinu "Minu disainid" jaoks
- **Arveldus**: tellimuse seisukorra jälgimine
- **Teenuse turvalisus**: rate-limiting, väärkasutuse tuvastamine
- **Teenuse parandamine**: anonüümne kasutusstatistika (mitu genereerimist mis templatega)

## 4. Õiguslik alus (GDPR artikkel 6)

- **Leping** (art. 6(1)(b)) — kontohalduse ja teenuse pakkumiseks
- **Õigustatud huvi** (art. 6(1)(f)) — turvalisus, pettuse tõrje
- **Nõusolek** (art. 6(1)(a)) — turunduseks ainult kui selgelt nõustud

## 5. Kellele me andmeid edastame

**Volitatud töötlejad (data processors):**
- **Anthropic PBC** (USA) — Claude AI teenus. Promptid saadetakse API kaudu. Anthropicu enda andmekaitsepoliitika: https://www.anthropic.com/legal/privacy
- **Stripe Payments Europe** (Iirimaa) — maksete töötlemine
- **Google Cloud** (EL-i andmekeskused) — OAuth autentimine
- **Kolmandad turvateenused** (nt Cloudflare DDoS-kaitseks, kui kasutusel)

Me **ei müü** andmeid kolmandatele isikutele. Me **ei kasuta** sinu promptsid ega STL-e reklaami sihtimiseks.

## 6. Kui kaua me andmeid säilitame

- **Konto andmed**: konto aktiivse olemasolu ajal. Konto kustutamisel eemaldame 30 päeva jooksul
- **Genereeritud STL-id ja promptid**: kuni sa need ise kustutad või konto kustutad
- **Maksedokumendid**: 7 aastat (Eesti raamatupidamisseadus)
- **Serveri logid (IP, kasutus)**: 30 päeva

## 7. Sinu õigused (GDPR)

Sul on õigus:
- Saada koopia kõigist andmetest, mida me sinu kohta hoiame (artikkel 15)
- Parandada ebatäpseid andmeid (artikkel 16)
- Nõuda kustutamist ("õigus olla unustatud" — artikkel 17)
- Piirata töötlemist (artikkel 18)
- Andmete ülekandmist masinloetavas vormingus (artikkel 20)
- Esitada vastuväide õigustatud huvil põhinevale töötlemisele (artikkel 21)
- Kaevata Andmekaitse Inspektsioonile (www.aki.ee)

Taotluse saatmiseks kirjuta: privacy@[DOMEEN]. Vastame 30 päeva jooksul.

## 8. Küpsised

Kasutame ainult vajalikke küpsiseid:
- **aicad_token** (localStorage, mitte cookie) — sinu sisselogimissessioon

Me **ei kasuta** turundusküpsiseid, Google Analyticsi, Facebook Pixelit ega sarnaseid jälgimisvahendeid. Statistika jaoks kasutame Plausible Analyticsit, mis ei kasuta küpsiseid ja ei profileeri kasutajaid.

## 9. Laste privaatsus

Teenus ei ole mõeldud alla 16-aastastele ilma vanema nõusolekuta.

## 10. Muudatused

Poliitika muudatustest teavitame e-postiga vähemalt 14 päeva enne jõustumist.

## 11. Kontakt

**Andmekaitsealaste küsimuste jaoks:** privacy@[DOMEEN]
**Eesti Andmekaitse Inspektsioon:** Tatari 39, Tallinn, info@aki.ee
