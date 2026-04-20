"""
Deep Design-for-Manufacturing (DFM) analyzer — reeglipõhine geomeetria-
auditor CadQuery mudelile.

Miks reeglipõhine + LLM kombo?
  - Reeglid on KIIRED (< 100ms), DETERMINISTLIKUD ja EELNEVAD
    teadmised (FDM-printimine on 30 aastat vana, reeglid on teada).
  - LLM töötab reeglite AVASTUSTE pealt — ta on hea soovitus-tekstiga,
    mitte numbrilise mõõtmisega.

Iga reegel tagastab DFMIssue objekti:
    severity: "critical" | "warning" | "info"
    rule:     "thin_wall" | "overhang" | "min_feature" | ...
    message_et: inimloetav eesti keeles põhjus
    affected_param: param, mida peaks patch'ima (kui teada)
    suggested_value: uus soovituslik väärtus (number)
    location: { x, y, z } kus probleem geomeetrias on (kui lokaalne)

POST /dfm  { template, params }  →  { issues:[...], score, summary_et }

Score = 10 − Σ(severity_weight × count) clampatud [1..10].
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional

from fastapi import HTTPException
from pydantic import BaseModel

# FDM reeglite konstandid (kehtivad tavalise 0.4mm düüsi + PLA/PETG juures)
FDM_MIN_WALL_MM = 1.2        # alla selle: kiht ei ole struktuurselt mõistlik
FDM_IDEAL_MIN_WALL = 2.0     # tavaliselt 4 perimeetrit
FDM_MAX_OVERHANG_DEG = 45.0  # üle selle tekib drooping ilma supportita
FDM_MAX_BRIDGE_MM = 40.0     # üle selle tekib säng ilma täitmata
FDM_MIN_FEATURE_MM = 0.8     # alla selle düüs ei suuda eraldada
FDM_MAX_UNSUPPORTED_ISLAND_MM = 5.0  # saar väiksem kui see = toe nõue


@dataclass
class DFMIssue:
    severity: str           # "critical" | "warning" | "info"
    rule: str
    message_et: str
    affected_param: Optional[str] = None
    suggested_value: Optional[float] = None
    location: Optional[Dict[str, float]] = None

    def to_dict(self) -> Dict[str, Any]:
        d = {k: v for k, v in asdict(self).items() if v is not None}
        return d


SEVERITY_WEIGHT = {"critical": 3.0, "warning": 1.2, "info": 0.4}


class DfmRequest(BaseModel):
    template: str
    params: Dict[str, Any]


# ---------------------------------------------------------------------------
# Rule implementations
# ---------------------------------------------------------------------------

def _get(params: dict, *keys, default=None):
    """Safe nested param lookup — some templates use wall_thickness, teised 'wall'."""
    for k in keys:
        if k in params and params[k] is not None:
            return params[k]
    return default


def rule_thin_wall(template: str, params: dict) -> List[DFMIssue]:
    """Seinapaksus alla FDM-reegli piiri on kriitiline, alla ideaalse = hoiatus."""
    issues: List[DFMIssue] = []

    wall_keys = ("wall_thickness", "wall", "thickness")
    wall = _get(params, *wall_keys)
    if wall is None:
        return issues

    param_name = next((k for k in wall_keys if k in params), wall_keys[0])
    if wall < FDM_MIN_WALL_MM:
        issues.append(DFMIssue(
            severity="critical",
            rule="thin_wall",
            message_et=(
                f"Seinapaksus {wall}mm on alla FDM miinimumi ({FDM_MIN_WALL_MM}mm). "
                f"Detail ei ole prinditav — sein laguneb peale jahtumist."
            ),
            affected_param=param_name,
            suggested_value=FDM_IDEAL_MIN_WALL,
        ))
    elif wall < FDM_IDEAL_MIN_WALL:
        issues.append(DFMIssue(
            severity="warning",
            rule="thin_wall",
            message_et=(
                f"Seinapaksus {wall}mm on õhuke — soovitame vähemalt {FDM_IDEAL_MIN_WALL}mm "
                "(≥ 4 perimeetrit 0.4mm düüsile), eriti kui detail kannab koormust."
            ),
            affected_param=param_name,
            suggested_value=FDM_IDEAL_MIN_WALL,
        ))

    # Koormuse + seinapaksuse seos shelf_bracket ja hook puhul
    load = _get(params, "load_kg")
    if load is not None and wall is not None:
        # Empiiriline: iga kg vajab u. 0.4mm lisaseina üle baasi 3mm
        required = 3.0 + 0.4 * load
        if wall < required - 0.5:   # 0.5mm tolerants
            issues.append(DFMIssue(
                severity="warning",
                rule="load_vs_wall",
                message_et=(
                    f"{load}kg koormuse jaoks soovitame seinapaksust ≥ {required:.1f}mm. "
                    f"Praegune {wall}mm võib plastilise deformatsiooniga vastu pidada, "
                    "aga pikas plaanis väändub."
                ),
                affected_param=param_name,
                suggested_value=round(required, 1),
            ))
    return issues


def rule_overhang(template: str, params: dict) -> List[DFMIssue]:
    """
    Hinge overhangi riski template'i enda teadmiste põhjal.
    Kuna me ei analüüsi päris STL-i üle kolmnurkade, kasutame
    template-spetsiifilisi heuristikuid.
    """
    issues: List[DFMIssue] = []

    # Hook: kui reach >> load_kg × base, tekib libe kalle
    if template == "hook":
        reach = _get(params, "reach", default=50)
        load = _get(params, "load_kg", default=3)
        if reach > 60 and load > 5:
            issues.append(DFMIssue(
                severity="warning",
                rule="overhang",
                message_et=(
                    f"Konks reach={reach}mm + {load}kg — konks asetseb prinditava orientatsioonis "
                    "tõenäoliselt > 45° kalde all. Lisa tugisild (support) või prinditav "
                    "orientatsioon külili."
                ),
                affected_param="reach",
                suggested_value=min(60, reach),
            ))

    # Shelf bracket: kui arm_length on palju pikem kui pipe_diameter, risk murdumisele
    if template == "shelf_bracket":
        arm = _get(params, "arm_length", default=120)
        pipe = _get(params, "pipe_diameter", default=32)
        if arm > 4 * pipe:
            issues.append(DFMIssue(
                severity="warning",
                rule="lever_arm",
                message_et=(
                    f"Klambrikäe arm_length={arm}mm on > 4× toruläbimõõt ({pipe}mm). "
                    "See on pika hoobega konstruktsioon, mis PLA peal võib aja jooksul "
                    "väänduma. Kaalu ribi lisamist või PETG materjali."
                ),
                affected_param="arm_length",
                suggested_value=round(4 * pipe, 0),
            ))

    # Living hinge: cuts arv mõjutab paindumise raadiust
    if template == "living_hinge":
        cuts = _get(params, "hinge_cuts", default=7)
        if cuts < 5:
            issues.append(DFMIssue(
                severity="warning",
                rule="hinge_cuts",
                message_et=(
                    f"Living-hinge sälkude arv {cuts} on väike — paindumine lööb "
                    "koondunud pinge, materjal võib mõne kõverduse järel lõhki minna. "
                    "Soovitame ≥ 7 sälki."
                ),
                affected_param="hinge_cuts",
                suggested_value=max(7, cuts),
            ))
    return issues


def rule_bridge(template: str, params: dict) -> List[DFMIssue]:
    """Sild = õhu-sild kahe tugipunkti vahel. Üle 40mm hakkab ripp-jooksma."""
    issues: List[DFMIssue] = []

    # Box/enclosure: kui width/depth ületab 40mm JA ei ole keskel tugisid
    if template in ("box", "enclosure"):
        width = _get(params, "width", "length")
        depth = _get(params, "depth", "width")
        if width and width > FDM_MAX_BRIDGE_MM:
            issues.append(DFMIssue(
                severity="info",
                rule="bridge",
                message_et=(
                    f"Karbi laius {width}mm > {FDM_MAX_BRIDGE_MM}mm. Kui prindid kaane "
                    "ilma toeta, tekib allatera ripp. Lisa ristriba või prindi kaas "
                    "eraldi."
                ),
            ))

    # Enclosure lid: vent_slots pilud üle 40mm
    if template == "enclosure":
        vents = _get(params, "vent_slots", default=6)
        if vents > 10:
            issues.append(DFMIssue(
                severity="info",
                rule="vent_density",
                message_et=(
                    f"Ventilatsiooni pilud: {vents}. Liiga palju pilusid nõrgestab "
                    "ülemist seina — iga pilu on sild. Soovitame ≤ 8."
                ),
                affected_param="vent_slots",
                suggested_value=8,
            ))
    return issues


def rule_min_feature(template: str, params: dict) -> List[DFMIssue]:
    """Alla 0.8mm feature-id kaovad düüsi kiirust arvestades."""
    issues: List[DFMIssue] = []

    # Screw hole alla 2mm — kas on piisavalt ruumi perimetrile?
    for hole_key in ("screw_hole", "bore", "hole", "hole_diameter"):
        hole = _get(params, hole_key)
        if hole is not None and hole < 2.0:
            issues.append(DFMIssue(
                severity="warning",
                rule="min_hole",
                message_et=(
                    f"Kinnitusava Ø{hole}mm on peaaegu düüsi läbimõõdu tasemel — "
                    "printer võib augu täitsa kinni plõksata. Soovitame ≥ 2mm."
                ),
                affected_param=hole_key,
                suggested_value=2.5,
            ))
            break

    # Gear teeth mitte liiga peenikesed
    if template == "spur_gear":
        module = _get(params, "module", default=2)
        if module < 1.0:
            issues.append(DFMIssue(
                severity="critical",
                rule="gear_tooth",
                message_et=(
                    f"Hambamoodul {module} annab hambatipu, mis on FDM-printeri jaoks "
                    "liiga peenike — hambad jäävad ümarad ja ei haardu. Soovitame "
                    "module ≥ 1.5."
                ),
                affected_param="module",
                suggested_value=1.5,
            ))

    # Snap-fit paksusala alla 1mm = ebaõnnestunud klips
    if template == "snap_fit_clip":
        thickness = _get(params, "thickness", default=1.5)
        if thickness < 1.0:
            issues.append(DFMIssue(
                severity="critical",
                rule="snap_thickness",
                message_et=(
                    f"Snap-fit paksus {thickness}mm on liiga peenike — klips murdub "
                    "esimesel kasutusel. PLA jaoks ≥ 1.5mm."
                ),
                affected_param="thickness",
                suggested_value=1.5,
            ))
    return issues


def rule_footprint(template: str, params: dict) -> List[DFMIssue]:
    """Kontrolli detaili mõõtmeid printeri tavalise prindivoodi suhtes."""
    issues: List[DFMIssue] = []

    # Tyüpilise FDM-printeri voodi 220 × 220 × 250
    BED_X = 220
    BED_Y = 220
    BED_Z = 250

    # Suurimad mõõtmed erinevatel template'idel
    big = [
        _get(params, "length", "width", "arm_length", "depth", default=0),
        _get(params, "width", "depth", default=0),
        _get(params, "height", "thickness", default=0),
    ]
    mx = max(big)
    if mx > min(BED_X, BED_Y) - 10:
        issues.append(DFMIssue(
            severity="warning",
            rule="footprint",
            message_et=(
                f"Detaili suurim mõõde {mx}mm läheneb tüüpilise 220×220mm voodi piirile. "
                "Suurematel printeritel OK, aga Ender/Prusa mini peal tuleb lõigata "
                "osadeks või pöörata diagonaalselt."
            ),
        ))
    return issues


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

ALL_RULES = [
    rule_thin_wall,
    rule_overhang,
    rule_bridge,
    rule_min_feature,
    rule_footprint,
]


def analyze(template: str, params: dict) -> Dict[str, Any]:
    """Jooksuta kõik reeglid, kogu issues, arvuta score, koosta kokkuvõte."""
    issues: List[DFMIssue] = []
    for rule in ALL_RULES:
        try:
            issues.extend(rule(template, params))
        except Exception as e:
            # Üks reegel crashib — ei peata kogu analüüsi
            issues.append(DFMIssue(
                severity="info",
                rule=f"{rule.__name__}_error",
                message_et=f"Reegel '{rule.__name__}' ebaõnnestus: {e}",
            ))

    # Score: 10 miinus kaalutud defektide summa
    penalty = sum(SEVERITY_WEIGHT.get(i.severity, 0.5) for i in issues)
    score = max(1.0, min(10.0, round(10.0 - penalty, 1)))

    # Kokkuvõtlik eestikeelne verdikt
    crit = sum(1 for i in issues if i.severity == "critical")
    warn = sum(1 for i in issues if i.severity == "warning")
    info = sum(1 for i in issues if i.severity == "info")

    if crit > 0:
        summary = (
            f"DFM leidis {crit} kriitilist probleemi — detail ei ole praegusel kujul "
            f"prinditav. Rakenda soovitatud parandused enne generate'i."
        )
    elif warn > 0:
        summary = (
            f"DFM leidis {warn} hoiatust. Detail on prinditav, aga paar asja tuleks "
            f"üle vaadata parema tulemuse saamiseks."
        )
    elif info > 0:
        summary = f"Detail on prinditav. {info} märkust tavapraktika kohta."
    else:
        summary = "Detail on puhas — ükski DFM reegel ei leidnud probleeme. Saad printida."

    return {
        "template": template,
        "score": score,
        "summary_et": summary,
        "counts": {"critical": crit, "warning": warn, "info": info},
        "issues": [i.to_dict() for i in issues],
    }


def register_routes(app, TEMPLATES):
    """Kutsuge pärast kõiki @register kataloogide täitmist."""

    @app.post("/dfm")
    def dfm_endpoint(req: DfmRequest):
        tpl = TEMPLATES.get(req.template)
        if not tpl:
            raise HTTPException(404, f"Unknown template: {req.template}")
        # Valideerida param'id template skeemi vastu oleks parem, aga praegu
        # analysis on param-põhine ja tolerantne puuduvate väljade suhtes.
        return analyze(req.template, req.params)
