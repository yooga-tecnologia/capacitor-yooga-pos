import { registerPlugin } from '@capacitor/core';

import type { CapacitorYoogaPosPlugin } from './definitions';

const CapacitorYoogaPos = registerPlugin<CapacitorYoogaPosPlugin>('CapacitorYoogaPos', {
  web: () => import('./web').then((m) => new m.CapacitorYoogaPosWeb()),
});

export * from './definitions';
export { CapacitorYoogaPos };
