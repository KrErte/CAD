export interface Example {
  title: string;
  prompt: string;
  template: string;
  params: Record<string, number>;
  emoji: string;
  useCase: string;
  /** Featured examples on landing hero ("päriselust" cards) */
  featured?: boolean;
  /** One-line pain point — why user needed this (used in featured cards) */
  painPoint?: string;
}

/**
 * Curated showcase — users click "Proovi" and their prompt is pre-filled.
 *
 * <p>Top 3 (featured:true) kuvatakse landing-is "Päris elust" sektsioonis
 * suurte kaartidega. Ülejäänud kuvatakse traditsioonilises grid-is allpool.
 */
export const EXAMPLES: Example[] = [
  // ── FEATURED — päris kasutusjuhtumid landing-is ─────────────────────────
  {
    title: 'Veetoru klamber 32 mm',
    prompt: 'vajan riiuliklambrit 32mm veetorule, peab kandma 5kg koormust, 2 kruvi M4 seina',
    template: 'shelf_bracket',
    params: { pipe_diameter: 32, load_kg: 5, arm_length: 60, screw_hole: 4 },
    emoji: '🚰',
    useCase: 'Köögi / vannitoa riiuli kinnitus ebatavalise läbimõõduga torule',
    featured: true,
    painPoint: '"Tegime köögiremondi, aga kõik klambrid IKEA-s on 28 mm või 40 mm. Meie toru on 32 mm. Ei sobi midagi."',
  },
  {
    title: 'Drooni kaameraraam',
    prompt: 'karp 40x30x20mm mini-kaamerale, pealt lahtine, 4 kruviauku M3 nurkades, seinapaks 2mm',
    template: 'box',
    params: { width: 40, depth: 30, height: 20, wall: 2 },
    emoji: '🚁',
    useCase: 'FPV-drooni või ROS-roboti kaamera kinnitus',
    featured: true,
    painPoint: '"Vajan drooni alla kaameraraami. Thingiverse-is 20 varianti, ükski ei sobi mu mõõtudele. Tellimine Hiinast võtab 3 nädalat."',
  },
  {
    title: 'IKEA sahtli varuosa',
    prompt: 'kaablihoidja 4 kaablile, 6mm läbimõõt, lauaserva alla kruvitav, M4 kruviauk',
    template: 'cable_clamp',
    params: { cable_diameter: 6, count: 4, screw_hole: 4 },
    emoji: '🛋️',
    useCase: 'IKEA PAX / BESTA varuosa, mis ei ole müügil',
    featured: true,
    painPoint: '"IKEA ei müü enam varuosa, mida ma vajan. Poest uue kapi ostmine maksab 200 €. Vajan 20 grammi plastikut."',
  },

  // ── TAVALISED NÄITED — grid-is allpool ──────────────────────────────────
  {
    title: 'Konks 3kg koormusele',
    prompt: 'seinakinnituseks konks 3kg koormusele, kaks kruvi-auku M4',
    template: 'hook',
    params: { load_kg: 3, depth: 40, screw_hole: 4 },
    emoji: '🪝',
    useCase: 'Garderoob, koridor, töötuba',
  },
  {
    title: 'Karp 80×60×40 elektroonikale',
    prompt: 'karp 80x60x40mm arduino jaoks, pealt lahtine, seinapaks 2mm',
    template: 'box',
    params: { width: 80, depth: 60, height: 40, wall: 2 },
    emoji: '📦',
    useCase: 'Arduino / Raspberry Pi enclosure',
  },
  {
    title: 'Adapter 25mm → 32mm',
    prompt: 'adapter 25mm torult 32mm torule, pikkus 40mm',
    template: 'adapter',
    params: { inner_d: 25, outer_d: 32, length: 40 },
    emoji: '🔗',
    useCase: 'Tolmuimeja toru, niisutussüsteem, vee-adaptrid',
  },
  {
    title: 'Märgistussilt võtmekimbule',
    prompt: 'võtmekimbu silt "KELDER", auk 5mm, ümarad nurgad',
    template: 'tag',
    params: { width: 40, height: 20, hole: 5 },
    emoji: '🏷️',
    useCase: 'Võtmed, kaablid, riiulitel olevad karbid',
  },
  {
    title: 'Lillepott 100mm taimele',
    prompt: 'lillepott ülalt 100mm, alt 80mm, kõrgus 120mm, 4 drenaaziauku põhjas',
    template: 'pot_planter',
    params: { top_diameter: 100, bottom_diameter: 80, height: 120, wall: 2.5, drain_holes: 4, drain_diameter: 6 },
    emoji: '🪴',
    useCase: 'Aknalaua taimed, sukulendid, rõdu-istutused',
  },
];

/** Filter: ainult hero-kaarti featured näited */
export const FEATURED_EXAMPLES = EXAMPLES.filter(e => e.featured);
