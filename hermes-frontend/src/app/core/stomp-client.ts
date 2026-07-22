import { Client } from '@stomp/stompjs';
import Keycloak from 'keycloak-js';

/**
 * Creates a STOMP client authenticated with the current Keycloak token,
 * refreshing it on every (re)connect attempt.
 */
export function createAuthenticatedStompClient(keycloak: Keycloak, onConnect: () => void): Client {
  const client = new Client({
    brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
    reconnectDelay: 5000,
    beforeConnect: async () => {
      await keycloak.updateToken(30);
      client.connectHeaders = { Authorization: `Bearer ${keycloak.token}` };
    },
    onConnect,
  });
  return client;
}
