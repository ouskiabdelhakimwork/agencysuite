import { MODULE_CODES, isModuleCode, modulePath } from './module-code';

describe('isModuleCode', () => {
  it('accepts the ten catalogue codes', () => {
    expect(MODULE_CODES).toHaveLength(10);
    for (const code of MODULE_CODES) {
      expect(isModuleCode(code)).toBe(true);
    }
  });

  it.each(['travel', '', null, undefined, 'UNKNOWN', 42, {}])('rejects %s', (value) => {
    expect(isModuleCode(value)).toBe(false);
  });

  it('keeps the backend catalogue order', () => {
    expect(MODULE_CODES).toEqual([
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
    ]);
  });

  it('builds lowercase dashed route paths', () => {
    expect(MODULE_CODES.map(modulePath)).toEqual([
      'travel',
      'ticketing',
      'giftbox',
      'transport',
      'visa',
      'mice',
      'portal',
      'omra-hajj',
      'inbound',
      'fleet',
    ]);
  });
});
