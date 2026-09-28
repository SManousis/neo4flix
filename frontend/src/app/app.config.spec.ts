import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { appConfig } from './app.config';
import { AuthStore } from './core/auth.store';

describe('appConfig', () => {
  it('bootstraps authentication from the refresh cookie before startup completes', async () => {
    const bootstrap = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, { provide: AuthStore, useValue: { bootstrap } }],
    });

    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(bootstrap).toHaveBeenCalledOnce();
  });
});
