# HTTPS + domeeni seadistus

Eeldused: domeen on ostetud (nt tehisaicad.ee), server töötab 62.171.153.133 peal, docker compose on up.

## 1. DNS

Domeeni-registraari paneelis (Zone, Veebimajutus, jne):

```
Type  Name            Value             TTL
A     tehisaicad.ee        62.171.153.133    300
A     www.tehisaicad.ee    62.171.153.133    300
```

Oota 5–30 min et DNS levib. Kontrolli: `dig tehisaicad.ee`

## 2. Caddy (automaatne HTTPS)

```bash
ssh root@62.171.153.133

# Install
apt update && apt install -y caddy

# Copy config
cd /opt/CAD
git pull
cp Caddyfile /etc/caddy/Caddyfile

# Kui domeen erineb tehisaicad.ee-st, asenda kohe:
sed -i 's/tehisaicad.ee/sinu-domeen.ee/g' /etc/caddy/Caddyfile

# Start
systemctl reload caddy
systemctl enable caddy

# Vaata logi
journalctl -u caddy -f
```

Caddy võtab Let's Encrypt sertifikaadi automaatselt (~30 sek). Kui DNS on õigesti, peaks https://tehisaicad.ee kohe töötama.

## 3. Firewall (UFW)

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
# Lülita välja 4200 ja 8080 direct access:
ufw deny 4200
ufw deny 8080
```

Nüüd töötab ainult läbi Caddy (HTTPS), pole otsejuurdepääsu portide kaudu.

## 4. Uuenda Spring Boot OAuth redirect_uri

Google OAuth konsoolis muuda Authorized redirect URIs:

```
Vana: http://62.171.153.133:8080/login/oauth2/code/google
Uus:  https://tehisaicad.ee/login/oauth2/code/google
```

Ja keskkonnamuutuja:

```bash
# /opt/CAD/.env
FRONTEND_URL=https://tehisaicad.ee
```

Siis `docker compose up -d backend` uuendamiseks.

## 5. Kontrolli

- `https://tehisaicad.ee` → avaneb kodulehele
- Green padlock brauseris
- `https://tehisaicad.ee/api/health` → `{"status":"ok"}`
- Login tööb: klikk "Google" → callback teeb redirectit HTTPS-ile

## Veaotsing

**"502 Bad Gateway"**: Spring Boot pole üleval või ei kuula 8080. `docker compose ps`

**"NET::ERR_CERT_AUTHORITY_INVALID"**: Let's Encrypt pole veel sertifikaati saanud. `journalctl -u caddy -n 50` — kui näed `rate limited` teadet, oota 1h.

**OAuth redirect broken**: Unustasid Google Console'is uue URL'i lisada. Paranda ja oota 5 min kuni Google'i cache aegub.
