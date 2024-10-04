import { WebPlugin } from '@capacitor/core';

import type { CapacitorYoogaPosPlugin } from './definitions';

export class CapacitorYoogaPosWeb extends WebPlugin implements CapacitorYoogaPosPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
