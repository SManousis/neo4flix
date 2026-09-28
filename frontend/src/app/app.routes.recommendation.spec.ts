import { authGuard } from './core/auth.guards';
import { routes } from './app.routes';
import { RecommendationsComponent } from './features/recommendations/recommendations.component';

describe('recommendation routes', () => {
  it('registers the authenticated lazy recommendations page', async () => {
    const route = routes.find((candidate) => candidate.path === 'recommendations');

    expect(route?.canActivate).toContain(authGuard);
    expect(await route?.loadComponent?.()).toBe(RecommendationsComponent);
  });
});
