import { WebPlugin } from '@capacitor/core';

import type {
  CapacitorYoogaPosPlugin,
  ListBluetoothDevicesResult,
  PrintBluetoothOptions,
  PrintBluetoothTextOptions,
  PrintOptions,
  PrintPdfOptions,
  PrintTextOptions,
  ShowPixOptions,
} from './definitions';

/**
 * Stub web — o hardware (display traseiro e impressora térmica) só existe nos
 * terminais Elgin / iMin. No browser apenas logamos a chamada para facilitar
 * o desenvolvimento da camada Angular.
 */
export class CapacitorYoogaPosWeb extends WebPlugin implements CapacitorYoogaPosPlugin {
  async showLogoOnDisplay(): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] showLogoOnDisplay()');
  }

  async showPix(options: ShowPixOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] showPix', options);
  }

  async print(options: PrintOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] print', options);
  }

  async printText(options: PrintTextOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] printText', options);
  }

  async printPdf(options: PrintPdfOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] printPdf', options);
  }

  async listBluetoothDevices(): Promise<ListBluetoothDevicesResult> {
    console.log('[CapacitorYoogaPos web stub] listBluetoothDevices()');
    return { devices: [] };
  }

  async printBluetooth(options: PrintBluetoothOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] printBluetooth', options);
  }

  async printBluetoothText(options: PrintBluetoothTextOptions): Promise<void> {
    console.log('[CapacitorYoogaPos web stub] printBluetoothText', options);
  }
}
