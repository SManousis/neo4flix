import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AppStateService } from './core/app-state.service';
import { AuthStore } from './core/auth.store';

@Component({
  selector: 'app-root',
  imports: [MatButtonModule, MatMenuModule, MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly appState = inject(AppStateService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly applicationName = this.appState.applicationName.asReadonly();
  protected readonly authState = this.appState.authState;

  protected logout(): void {
    this.authStore.logout().subscribe({
      next: () => void this.router.navigateByUrl('/auth/login'),
      error: () => void this.router.navigateByUrl('/auth/login'),
    });
  }
}
