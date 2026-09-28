import { inject, Injectable, signal } from '@angular/core';
import { AuthStore } from './auth.store';

@Injectable({ providedIn: 'root' })
export class AppStateService {
  readonly applicationName = signal('Neo4flix');
  readonly authState = inject(AuthStore).state;
}
