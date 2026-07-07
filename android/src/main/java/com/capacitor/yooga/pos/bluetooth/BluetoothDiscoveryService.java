package com.capacitor.yooga.pos.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;

/**
 * Discovery e pareamento de devices Bluetooth Classic para o fluxo de UI
 * "buscar impressora". Discovery clássico (startDiscovery + ACTION_FOUND)
 * em vez de CompanionDeviceManager para a UI da busca viver dentro do app
 * (lista ao vivo, empty-state amigável, retry).
 */
public class BluetoothDiscoveryService {

  private static final String TAG = "YoogaBtDiscovery";

  public interface DiscoveryListener {
    void onDeviceFound(String name, String address, boolean isPrinter, boolean bonded);
    void onDiscoveryFinished();
  }

  public interface PairingListener {
    void onPaired();
    void onPairingFailed(String reason);
  }

  private final Context context;
  private BroadcastReceiver discoveryReceiver;
  private BroadcastReceiver pairingReceiver;

  public BluetoothDiscoveryService(Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Inicia o discovery (~12s, encerrado pelo sistema com ACTION_DISCOVERY_FINISHED).
   * Devices sem nome são reportados mesmo assim (alguns clones só expõem o nome
   * depois de um segundo ACTION_FOUND); o listener decide o que exibir.
   */
  @SuppressLint("MissingPermission")
  public synchronized void startDiscovery(DiscoveryListener listener) throws IOException {
    BluetoothAdapter adapter = requireAdapter();
    stopDiscovery(); // limpa um discovery anterior pendurado

    discoveryReceiver = new BroadcastReceiver() {
      @Override
      @SuppressLint("MissingPermission")
      public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
          BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
          if (device == null) return;
          String name = device.getName();
          boolean isPrinter = isImagingClass(device);
          boolean bonded = device.getBondState() == BluetoothDevice.BOND_BONDED;
          listener.onDeviceFound(
            name != null ? name : device.getAddress(),
            device.getAddress(),
            isPrinter,
            bonded
          );
        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
          unregisterDiscoveryReceiver();
          listener.onDiscoveryFinished();
        }
      }
    };

    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    context.registerReceiver(discoveryReceiver, filter);

    if (!adapter.startDiscovery()) {
      unregisterDiscoveryReceiver();
      throw new IOException("Não foi possível iniciar a busca Bluetooth");
    }
    Log.d(TAG, "discovery iniciado");
  }

  @SuppressLint("MissingPermission")
  public synchronized void stopDiscovery() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && adapter.isDiscovering()) {
      try {
        adapter.cancelDiscovery();
      } catch (SecurityException e) {
        Log.w(TAG, "cancelDiscovery negado: " + e.getMessage());
      }
    }
    unregisterDiscoveryReceiver();
  }

  /**
   * Pareia via createBond: o sistema mostra o diálogo de pareamento (PIN) na
   * hora. Resolve quando o bond completar; falha se o usuário recusar/errar o
   * PIN. Se já estiver pareado, resolve imediato.
   */
  @SuppressLint("MissingPermission")
  public synchronized void pair(String address, PairingListener listener) throws IOException {
    BluetoothAdapter adapter = requireAdapter();
    stopDiscovery(); // discovery ativo atrapalha o bond
    BluetoothDevice device = adapter.getRemoteDevice(address);

    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
      listener.onPaired();
      return;
    }

    unregisterPairingReceiver();
    pairingReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context ctx, Intent intent) {
        BluetoothDevice changed = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (changed == null || !address.equals(changed.getAddress())) return;
        int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        int previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);
        Log.d(TAG, "bond state: " + previous + " -> " + state);
        if (state == BluetoothDevice.BOND_BONDED) {
          unregisterPairingReceiver();
          listener.onPaired();
        } else if (state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING) {
          unregisterPairingReceiver();
          listener.onPairingFailed("Pareamento recusado ou PIN incorreto");
        }
      }
    };
    context.registerReceiver(pairingReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

    if (!device.createBond()) {
      unregisterPairingReceiver();
      throw new IOException("Não foi possível iniciar o pareamento");
    }
  }

  public synchronized void cleanup() {
    stopDiscovery();
    unregisterPairingReceiver();
  }

  @SuppressLint("MissingPermission")
  private boolean isImagingClass(BluetoothDevice device) {
    BluetoothClass btClass = device.getBluetoothClass();
    // IMAGING (0x600) cobre impressoras "de verdade"; clones baratos às vezes
    // reportam classe genérica, então isso é dica de ordenação, não filtro.
    return btClass != null && btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.IMAGING;
  }

  private BluetoothAdapter requireAdapter() throws IOException {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      throw new IOException("Este aparelho não tem Bluetooth");
    }
    if (!adapter.isEnabled()) {
      throw new IOException("Bluetooth desligado");
    }
    return adapter;
  }

  private void unregisterDiscoveryReceiver() {
    if (discoveryReceiver != null) {
      try {
        context.unregisterReceiver(discoveryReceiver);
      } catch (IllegalArgumentException ignored) {}
      discoveryReceiver = null;
    }
  }

  private void unregisterPairingReceiver() {
    if (pairingReceiver != null) {
      try {
        context.unregisterReceiver(pairingReceiver);
      } catch (IllegalArgumentException ignored) {}
      pairingReceiver = null;
    }
  }
}
