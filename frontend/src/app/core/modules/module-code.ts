/**
 * Codes des modules activables, dans l'ordre du catalogue.
 *
 * Liste a garder alignee sur l'enum backend `ma.agencysuite.shared.module.ModuleCode`
 * (meme ordre). Dette notee : generer cette liste depuis l'OpenAPI plus tard.
 */
export const MODULE_CODES = [
  'TRAVEL',
  'TICKETING',
  'GIFTBOX',
  'TRANSPORT',
  'VISA',
  'MICE',
  'PORTAL',
  'OMRA_HAJJ',
  'INBOUND',
  'FLEET',
] as const;

export type ModuleCode = (typeof MODULE_CODES)[number];

export function isModuleCode(value: unknown): value is ModuleCode {
  return typeof value === 'string' && (MODULE_CODES as readonly string[]).includes(value);
}

/** Chemin de route d'un module : minuscules avec tirets (OMRA_HAJJ -> omra-hajj). */
export function modulePath(code: ModuleCode): string {
  return code.toLowerCase().replaceAll('_', '-');
}
