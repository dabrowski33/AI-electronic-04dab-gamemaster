import { DECISION_CATEGORY_LABELS, EQUIPMENT_CATEGORY_LABELS } from './index';

describe('Models', () => {
  it('should have 11 equipment category labels', () => {
    expect(Object.keys(EQUIPMENT_CATEGORY_LABELS).length).toBe(11);
  });

  it('should have 4 decision category labels', () => {
    expect(Object.keys(DECISION_CATEGORY_LABELS).length).toBe(4);
  });
});
