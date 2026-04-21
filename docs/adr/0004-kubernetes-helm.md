# ADR-0004: Kubernetes + Helm deployment

- **Status**: accepted
- **Kuupäev**: 2026-02-01
- **Otsustajad**: @olen-krerte
- **Tehniline keerukus**: L

## Kontekst

Algne deploy oli üks VM docker-compose'iga. See töötas, kuni:
- Tulid 2. prod-instants (staging)
- HPA vaja (Claude-kulu-burst'i korral)
- Tulid multi-region plaanid

## Variandid

### Variant A: Jääme docker-compose'i, lisame reverse-proxy (Caddy) + 2. VM
- Plussid: lihtsus
- Miinused: manual scaling, mitte self-healing, deploy'id downtime'iga

### Variant B: AWS ECS Fargate
- Plussid: serverless, AWS-native, ei opsi node'e
- Miinused: vendor lock-in; tooling vähem levinud kui k8s

### Variant C: Kubernetes (k3s bare-metal / EKS managed)
- Plussid: standard, tooling (helm, kubectl) universaalne, tööturg
- Miinused: ops-keerukus (etcd, networking, RBAC)

### Variant D: Nomad + Consul
- Plussid: lihtsam kui k8s
- Miinused: väiksem community, vähem tooling (helm ekvivalent = nomad-pack
  ebaküps)

## Otsus

**Variant C: Kubernetes + Helm**. Standard industry-wide, teadmised
kandu­vad mistahes järgmise projekti peale. Algul k3s bare-metal Hetzneris
(1 × master + 2 × worker), tulevikus EKS kui vajadus tekib.

Helm chart annab:
- Repeatable deploys (ühe käsuga nii staging kui prod)
- Versioned rollout + rollback
- Template'itav config per-env'i (values-staging.yaml, values-prod.yaml)

## Tagajärjed

### Positiivsed
- HPA skaleerib backend'i + worker'i 1 → 10 podi Claude-burst'i ajal
- PDB kaitseb node-drain'i ajal availability'ut (min 1 backend peab üleval)
- PodSecurityContext runAsNonRoot enforce'ib best practice'it
- Helm + ArgoCD GitOps rongile sõidab tulevikus

### Negatiivsed
- Arendaja peab kubectl + helm õppima (vs docker-compose up)
- Resource consumption: k3s master ~500MB kõrvale backend'ist
- 1. release'i ajaks tuli kulutada ~2 nädalat k8s seadistust

### Risk'id
- **Risk**: etcd corruption / node-death andmekaoga.
  **Maandamine**: Postgres + MinIO on väljaspool k8s'i (managed PG + S3)
- **Risk**: Helm chart bugi võib take down kõik service'id.
  **Maandamine**: staging env + `helm diff` enne upgrade

## Viited
- Helm chart: `helm/ai-cad/`
- `docs/DEPLOY-HTTPS.md`
- ADR-0003 (pgvector) — Postgres on managed, mitte statefulset k8s-is
