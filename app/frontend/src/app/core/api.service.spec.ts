import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiService } from './api.service';
import { SubmitCaseResponse, ApiError } from './models';

const mockResponse: SubmitCaseResponse = {
  sessionId: 'abc-123',
  decision: {
    category: 'ELIGIBLE',
    justification: 'OK',
    nextSteps: 'Następne kroki',
  },
  firstMessage: 'Dziękujemy za zgłoszenie. To wstępna, automatyczna ocena Twojego zgłoszenia, a nie wiążąca decyzja. Ostateczną decyzję podejmuje konsultant po weryfikacji zgłoszenia.',
  caseSummary: {
    type: 'ZWROT',
    category: 'TABLETY',
    model: 'iPad Pro',
    purchaseDate: '2025-01-01',
  },
};

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('submitCase success returns SubmitCaseResponse', (done) => {
    service.submitCase(new FormData()).subscribe({
      next: (res) => {
        expect(res.sessionId).toBe('abc-123');
        expect(res.decision.category).toBe('ELIGIBLE');
        done();
      },
    });
    const req = httpMock.expectOne('/api/v1/cases');
    req.flush(mockResponse);
  });

  it('submitCase 413 rejects with PAYLOAD_TOO_LARGE', (done) => {
    service.submitCase(new FormData()).subscribe({
      error: (err: ApiError) => {
        expect(err.code).toBe('PAYLOAD_TOO_LARGE');
        done();
      },
    });
    const req = httpMock.expectOne('/api/v1/cases');
    req.flush(null, { status: 413, statusText: 'Payload Too Large' });
  });

  it('submitCase 415 rejects with UNSUPPORTED_MEDIA_TYPE', (done) => {
    service.submitCase(new FormData()).subscribe({
      error: (err: ApiError) => {
        expect(err.code).toBe('UNSUPPORTED_MEDIA_TYPE');
        done();
      },
    });
    const req = httpMock.expectOne('/api/v1/cases');
    req.flush(null, { status: 415, statusText: 'Unsupported Media Type' });
  });
});
