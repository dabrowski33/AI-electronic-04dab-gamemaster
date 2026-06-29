import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiError, SubmitCaseResponse } from './models';
import { parseSseStream } from './sse-parser';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  submitCase(formData: FormData): Observable<SubmitCaseResponse> {
    return this.http.post<SubmitCaseResponse>('/api/v1/cases', formData).pipe(
      catchError((err: HttpErrorResponse) => throwError(() => this.normalizeError(err)))
    );
  }

  async *streamChat(sessionId: string, message: string): AsyncGenerator<string> {
    const response = await fetch(`/api/v1/cases/${sessionId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ message }),
    });
    if (!response.ok || !response.body) {
      throw this.normalizeError({ status: response.status } as HttpErrorResponse);
    }
    yield* parseSseStream(response.body);
  }

  private normalizeError(err: HttpErrorResponse | { status: number }): ApiError {
    if ('error' in err && err.error && typeof err.error === 'object') {
      return err.error as ApiError;
    }
    const status = err.status;
    if (status === 413) return { code: 'PAYLOAD_TOO_LARGE', message: 'Plik jest za duży.' };
    if (status === 415) return { code: 'UNSUPPORTED_MEDIA_TYPE', message: 'Nieobsługiwany format pliku.' };
    return { code: 'LLM_UNAVAILABLE', message: 'Usługa chwilowo niedostępna. Spróbuj ponownie.' };
  }
}
