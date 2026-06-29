import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatNativeDateModule } from '@angular/material/core';
import { IntakeComponent } from './intake.component';
import { ApiService } from '../../core/api.service';
import { SubmitCaseResponse, ApiError } from '../../core/models';

const mockResponse: SubmitCaseResponse = {
  sessionId: 'test-session-id',
  decision: { category: 'ELIGIBLE', justification: 'OK', nextSteps: 'Następnie...' },
  firstMessage: 'Dziękujemy. To wstępna, automatyczna ocena Twojego zgłoszenia, a nie wiążąca decyzja. Ostateczną decyzję podejmuje konsultant po weryfikacji zgłoszenia.',
  caseSummary: { type: 'ZWROT', category: 'TABLETY', model: 'iPad', purchaseDate: '2025-01-01' },
};

function makeFile(size: number, type: string, name = 'test.jpg'): File {
  return { size, type, name } as unknown as File;
}

describe('IntakeComponent', () => {
  let fixture: ComponentFixture<IntakeComponent>;
  let component: IntakeComponent;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', ['submitCase']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [IntakeComponent, NoopAnimationsModule, MatNativeDateModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IntakeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function fillValidForm(): void {
    component['form'].controls.type.setValue('ZWROT');
    component['form'].controls.category.setValue('TABLETY');
    component['form'].controls.model.setValue('iPad Pro');
    component['form'].controls.purchaseDate.setValue(new Date('2025-01-01'));
    component['form'].controls.image.setValue(makeFile(100, 'image/jpeg'));
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should block submit when image is missing (TAC-003-03)', () => {
    component['form'].controls.type.setValue('ZWROT');
    component['form'].controls.category.setValue('TABLETY');
    component['form'].controls.model.setValue('iPad Pro');
    component['form'].controls.purchaseDate.setValue(new Date('2025-01-01'));
    // image not set
    component['submit']();
    expect(apiSpy.submitCase).not.toHaveBeenCalled();
  });

  it('should block submit when reason is missing for REKLAMACJA (TAC-003-01)', fakeAsync(() => {
    component['form'].controls.type.setValue('REKLAMACJA');
    tick(); // allow subscription to run
    component['form'].controls.category.setValue('TABLETY');
    component['form'].controls.model.setValue('iPad Pro');
    component['form'].controls.purchaseDate.setValue(new Date('2025-01-01'));
    component['form'].controls.image.setValue(makeFile(100, 'image/jpeg'));
    // reason not set
    component['submit']();
    expect(apiSpy.submitCase).not.toHaveBeenCalled();
  }));

  it('should mark purchaseDate invalid for future date (TAC-003-02)', () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    component['form'].controls.purchaseDate.setValue(tomorrow);
    component['form'].controls.purchaseDate.markAsTouched();
    expect(component['form'].controls.purchaseDate.errors?.['futureDate']).toBeTrue();
  });

  it('should revoke object URL on destroy (TAC-003-04)', () => {
    spyOn(URL, 'revokeObjectURL');
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake-url');
    const fakeEvent = { target: { files: [makeFile(100, 'image/jpeg')] } } as unknown as Event;
    component['onFileSelected'](fakeEvent);
    component.ngOnDestroy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
  });

  it('should set submitError and preserve data on API error (TAC-003-05)', fakeAsync(() => {
    fillValidForm();
    const error: ApiError = { code: 'LLM_UNAVAILABLE', message: 'Usługa niedostępna.' };
    apiSpy.submitCase.and.returnValue(throwError(() => error));
    component['submit']();
    tick();
    expect(component['submitError']()).toEqual(error);
    expect(component['form'].controls.model.value).toBe('iPad Pro');
  }));

  it('should navigate to /chat/:id on success', fakeAsync(() => {
    fillValidForm();
    apiSpy.submitCase.and.returnValue(of(mockResponse));
    component['submit']();
    tick();
    expect(routerSpy.navigate).toHaveBeenCalledWith(
      ['/chat', 'test-session-id'],
      { state: { response: mockResponse } }
    );
  }));
});
