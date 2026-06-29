import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/intake/intake.component').then(m => m.IntakeComponent) },
  { path: 'chat/:sessionId', loadComponent: () => import('./features/chat/chat.component').then(m => m.ChatComponent) },
  { path: '**', redirectTo: '' },
];
