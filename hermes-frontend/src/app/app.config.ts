import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
} from 'keycloak-angular';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideKeycloak({
      config: {
        url: 'http://localhost:8081',
        realm: 'hermes',
        clientId: 'hermes-frontend',
      },
      initOptions: {
        onLoad: 'check-sso',
        pkceMethod: 'S256',
      },
    }),
    provideHttpClient(withFetch(), withInterceptors([includeBearerTokenInterceptor])),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: [
        {
          urlPattern: /^\/api(\/.*)?$/,
          bearerPrefix: 'Bearer',
        },
      ],
    },
    provideAnimationsAsync(),
    provideCharts(withDefaultRegisterables()),
  ]
};
