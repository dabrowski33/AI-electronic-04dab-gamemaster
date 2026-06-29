import {
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  effect,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NgClass } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MarkdownModule } from 'ngx-markdown';
import {
  ChatMessage,
  DecisionCategory,
  DECISION_CATEGORY_LABELS,
  EQUIPMENT_CATEGORY_LABELS,
  EquipmentCategory,
  SubmitCaseResponse,
} from '../../core/models';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgClass,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MarkdownModule,
  ],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit {
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;

  protected messages = signal<ChatMessage[]>([]);
  protected streaming = signal(false);
  protected chatError = signal<string | null>(null);
  protected messageInput = new FormControl('', { nonNullable: true });
  protected caseResponse = signal<SubmitCaseResponse | null>(null);

  constructor(private route: ActivatedRoute, private api: ApiService) {
    effect(() => {
      // auto-scroll when messages change — reading signal triggers reactivity
      this.messages();
      setTimeout(() => this.scrollToBottom(), 0);
    });
  }

  ngOnInit(): void {
    const nav = history.state?.['response'] as SubmitCaseResponse | undefined;
    if (nav) {
      this.caseResponse.set(nav);
      this.messages.set([{ role: 'assistant', content: nav.firstMessage }]);
    }
  }

  protected getCategoryLabel(category: EquipmentCategory): string {
    return EQUIPMENT_CATEGORY_LABELS[category] ?? category;
  }

  protected getDecisionLabel(category: DecisionCategory): string {
    return DECISION_CATEGORY_LABELS[category] ?? category;
  }

  protected getDecisionClass(category: DecisionCategory): string {
    const map: Record<DecisionCategory, string> = {
      ELIGIBLE: 'eligible',
      NOT_ELIGIBLE: 'not-eligible',
      NEEDS_HUMAN_REVIEW: 'needs-human-review',
      MORE_INFO_REQUIRED: 'more-info-required',
    };
    return map[category] ?? '';
  }

  protected onEnterKey(event: Event): void {
    const keyboardEvent = event as KeyboardEvent;
    if (!keyboardEvent.shiftKey) {
      keyboardEvent.preventDefault();
      void this.sendMessage();
    }
  }

  protected async sendMessage(): Promise<void> {
    const text = this.messageInput.value.trim();
    if (!text || this.streaming()) return;

    const sessionId =
      this.caseResponse()?.sessionId ??
      (this.route.snapshot.params['sessionId'] as string);

    this.messageInput.setValue('');
    this.chatError.set(null);

    this.messages.update((msgs) => [...msgs, { role: 'user', content: text }]);
    const assistantIdx = this.messages().length;
    this.messages.update((msgs) => [
      ...msgs,
      { role: 'assistant', content: '', streaming: true },
    ]);
    this.streaming.set(true);

    try {
      for await (const token of this.api.streamChat(sessionId, text)) {
        this.messages.update((msgs) => {
          const updated = [...msgs];
          const current = updated[assistantIdx];
          if (current) {
            updated[assistantIdx] = { ...current, content: current.content + token };
          }
          return updated;
        });
      }
    } catch {
      this.chatError.set('Nie udało się uzyskać odpowiedzi asystenta. Spróbuj ponownie za chwilę.');
      // Restore the user's message so they can resend it.
      this.messageInput.setValue(text);
    } finally {
      this.messages.update((msgs) => {
        const updated = [...msgs];
        const current = updated[assistantIdx];
        if (current && !current.content) {
          // Drop the empty assistant placeholder if the stream produced nothing.
          updated.splice(assistantIdx, 1);
        } else if (current) {
          updated[assistantIdx] = { ...current, streaming: false };
        }
        return updated;
      });
      this.streaming.set(false);
    }
  }

  private scrollToBottom(): void {
    const el = this.messagesContainer?.nativeElement;
    if (el) el.scrollTop = el.scrollHeight;
  }
}
