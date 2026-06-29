import { FormControl } from '@angular/forms';
import { futureDateForbidden, fileSizeMax, fileTypeAllowed } from './index';

function makeFile(size: number, type: string): File {
  return { size, type, name: 'test' } as unknown as File;
}

describe('futureDateForbidden', () => {
  it('should return null for null value', () => {
    const ctrl = new FormControl(null);
    expect(futureDateForbidden(ctrl)).toBeNull();
  });

  it('should return null for today', () => {
    const ctrl = new FormControl(new Date());
    expect(futureDateForbidden(ctrl)).toBeNull();
  });

  it('should return null for yesterday', () => {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const ctrl = new FormControl(yesterday);
    expect(futureDateForbidden(ctrl)).toBeNull();
  });

  it('should return error for tomorrow', () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const ctrl = new FormControl(tomorrow);
    expect(futureDateForbidden(ctrl)).toEqual({ futureDate: true });
  });
});

describe('fileSizeMax', () => {
  const tenMB = 10 * 1024 * 1024;

  it('should return null for file within limit', () => {
    const ctrl = new FormControl(makeFile(9 * 1024 * 1024, 'image/jpeg'));
    expect(fileSizeMax(tenMB)(ctrl)).toBeNull();
  });

  it('should return error for file over limit', () => {
    const ctrl = new FormControl(makeFile(11 * 1024 * 1024, 'image/jpeg'));
    expect(fileSizeMax(tenMB)(ctrl)).toEqual({
      fileSizeMax: { max: tenMB, actual: 11 * 1024 * 1024 },
    });
  });
});

describe('fileTypeAllowed', () => {
  it('should return null for allowed type', () => {
    const ctrl = new FormControl(makeFile(100, 'image/jpeg'));
    expect(fileTypeAllowed(['image/jpeg'])(ctrl)).toBeNull();
  });

  it('should return error for disallowed type', () => {
    const ctrl = new FormControl(makeFile(100, 'image/gif'));
    expect(fileTypeAllowed(['image/jpeg'])(ctrl)).toEqual({
      fileType: { allowed: ['image/jpeg'], actual: 'image/gif' },
    });
  });
});
