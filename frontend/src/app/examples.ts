export interface Example {
  title: string;
  prompt: string;
  template: string;
  params: Record<string, number>;
  emoji: string;
  useCase: string;
}

/** Curated showcase — users click "Proovi" and their prompt is pre-filled. */
export const EXAMPLES: Example[] = [
  {
    title: 'Riiuliklamber 32mm torule',
    prompt: 'vajan riiuliklambrit 32mm veetorule, peab kandma 5kg koormust',
    template: 'shelf_bracket',
    params: { pipe_diameter: 32, load_kg: 5, arm_length: 60 },
    emoji: '🔩',
    useCase: 'Köögi / vannitoa riiuli kinnitus ebatavalise läbimõõduga torule',
  },
  {
    title: 'Kaablihoidja 4 kaablile',
    prompt: 'kaablihoidja 4 kaablile, 6mm läbimõõt, lauaserva alla kruvitav',
    template: 'cable_clamp',
    params: { cable_diameter: 6, count: 4, screw_hole: 4 },
    emoji: '🔌',
    useCase: 'Lauaserv / seinapealse kaabli organiseerimine',
  },
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
];
