import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideMarkdown } from 'ngx-markdown';
import { ChatComponent } from './chat.component';
import { ApiService } from '../../core/api.service';
import { DECISION_CATEGORY_LABELS, DecisionCategory, SubmitCaseResponse } from '../../core/models';

const DISCLAIMER =
  'To wstępna, automatyczna ocena Twojego zgłoszenia, a nie wiążąca decyzja. Ostateczną decyzję podejmuje konsultant po weryfikacji zgłoszenia.';

const mockResponse: SubmitCaseResponse = {
  sessionId: 'sess-001',
  decision: { category: 'ELIGIBLE', justification: 'OK', nextSteps: 'Następnie...' },
  firstMessage: `Dziękujemy za zgłoszenie.\n\n${DISCLAIMER}`,
  caseSummary: {
    type: 'ZWROT',
    category: 'TABLETY',
    model: 'iPad',
    purchaseDate: '2025-01-01',
  },
};

async function* fakeStream(tokens: string[]): AsyncGenerator<string> {
  for (const t of tokens) yield t;
}

describe('ChatComponent', () => {
  let fixture: ComponentFixture<ChatComponent>;
  let component: ChatComponent;
  let apiSpy: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', ['streamChat']);

    await TestBed.configureTestingModule({
      imports: [ChatComponent, NoopAnimationsModule],
      providers: [
        provideMarkdown(),
        { provide: ApiService, useValue: apiSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { params: { sessionId: 'sess-001' } } },
        },
      ],
    }).compileComponents();

    // Set router state before creating component
    history.replaceState({ response: mockResponse }, '');

    fixture = TestBed.createComponent(ChatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render first message from router state on init (TAC-003-08)', () => {
    const msgs = component['messages']();
    expect(msgs.length).toBe(1);
    expect(msgs[0].role).toBe('assistant');
    expect(msgs[0].content).toContain('Dziękujemy');
  });

  it('first message should contain mandatory disclaimer', () => {
    const msgs = component['messages']();
    expect(msgs[0].content).toContain(DISCLAIMER);
  });

  it('should return correct label for each decision category', () => {
    const categories: DecisionCategory[] = [
      'ELIGIBLE',
      'NOT_ELIGIBLE',
      'NEEDS_HUMAN_REVIEW',
      'MORE_INFO_REQUIRED',
    ];
    for (const cat of categories) {
      expect(component['getDecisionLabel'](cat)).toBe(DECISION_CATEGORY_LABELS[cat]);
    }
  });

  it('should return empty string for unknown decision category', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect(component['getDecisionClass']('UNKNOWN' as any)).toBe('');
  });

  it('should accumulate tokens and clear streaming flag (TAC-003-07)', fakeAsync(async () => {
    apiSpy.streamChat.and.returnValue(fakeStream(['Witaj', ' świecie', '!']));
    component['messageInput'].setValue('Pytanie');
    const sendPromise = component['sendMessage']();
    // streaming starts
    expect(component['streaming']()).toBeTrue();
    await sendPromise;
    tick();
    const msgs = component['messages']();
    const lastMsg = msgs[msgs.length - 1];
    expect(lastMsg.content).toBe('Witaj świecie!');
    expect(lastMsg.streaming).toBeFalse();
    expect(component['streaming']()).toBeFalse();
  }));

  it('should not send when streaming is active', async () => {
    component['streaming'].set(true);
    component['messageInput'].setValue('Pytanie');
    await component['sendMessage']();
    expect(apiSpy.streamChat).not.toHaveBeenCalled();
  });
});
