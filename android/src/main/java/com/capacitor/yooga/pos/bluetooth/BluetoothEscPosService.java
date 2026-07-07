package com.capacitor.yooga.pos.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Transporte ESC/POS para impressoras térmicas Bluetooth genéricas (SPP/RFCOMM),
 * como a MTP-II 58mm. Sem SDK proprietário: abre o socket, manda os bytes e fecha.
 * Conexão por job (conecta -> imprime -> desconecta): esses firmwares dormem e
 * derrubam sockets ociosos, então manter conexão aberta é menos confiável.
 */
public class BluetoothEscPosService {

  private static final String TAG = "YoogaBtEscPos";
  private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  // Chunks pequenos com flush + pausa: o buffer dessas impressoras é minúsculo
  // e estourar ele faz linhas sumirem no meio do recibo.
  private static final int CHUNK_SIZE = 512;
  private static final int CHUNK_DELAY_MS = 20;
  // Blocos de raster (GS v 0) limitados em altura pelo mesmo motivo.
  private static final int ROWS_PER_BLOCK = 128;
  private static final int LUMINANCE_THRESHOLD = 160;

  public static class BondedDevice {
    public final String name;
    public final String address;

    BondedDevice(String name, String address) {
      this.name = name;
      this.address = address;
    }
  }

  /**
   * Lista os devices já pareados no Android (bond). O pareamento em si é feito
   * nas configurações de Bluetooth do sistema; aqui não há scan/discovery.
   */
  @SuppressLint("MissingPermission")
  public List<BondedDevice> listBondedDevices() throws IOException {
    BluetoothAdapter adapter = requireAdapter();
    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
    List<BondedDevice> devices = new ArrayList<>();
    if (bonded != null) {
      for (BluetoothDevice device : bonded) {
        String name = device.getName();
        devices.add(new BondedDevice(name != null ? name : device.getAddress(), device.getAddress()));
      }
    }
    return devices;
  }

  /**
   * Imprime um bitmap já renderizado (mesma saída do Html2Bitmap usado pela
   * térmica interna) como raster ESC/POS (GS v 0) e avança {@code feedLines}
   * linhas no final. A MTP-II não tem guilhotina, então o "corte" é só o avanço.
   */
  @SuppressLint("MissingPermission")
  public void printBitmap(String address, Bitmap bitmap, int feedLines) throws IOException {
    if (bitmap == null) {
      throw new IOException("Bitmap nulo — falha ao renderizar o conteúdo");
    }
    BluetoothSocket socket = connect(address);
    try {
      OutputStream out = socket.getOutputStream();
      out.write(new byte[] { 0x1B, 0x40 }); // ESC @ (reset)
      writeRaster(out, bitmap);
      out.write(new byte[] { 0x1B, 0x64, (byte) Math.max(0, feedLines) }); // ESC d n (avanço)
      out.flush();
      drainDelay(bitmap.getHeight());
    } finally {
      closeQuietly(socket);
    }
  }

  /**
   * Teste rápido de comunicação: texto puro ASCII, sem bitmap. Não usar para
   * recibos reais (acentuação depende de codepage, que varia por firmware).
   */
  @SuppressLint("MissingPermission")
  public void printText(String address, String text, int feedLines) throws IOException {
    BluetoothSocket socket = connect(address);
    try {
      OutputStream out = socket.getOutputStream();
      out.write(new byte[] { 0x1B, 0x40 });
      out.write(text.getBytes("US-ASCII"));
      out.write(new byte[] { 0x0A, 0x1B, 0x64, (byte) Math.max(0, feedLines) });
      out.flush();
      drainDelay(0);
    } finally {
      closeQuietly(socket);
    }
  }

  @SuppressLint("MissingPermission")
  private BluetoothSocket connect(String address) throws IOException {
    BluetoothAdapter adapter = requireAdapter();
    BluetoothDevice device = adapter.getRemoteDevice(address);
    // Discovery ativo degrada muito a conexão RFCOMM; cancelar é recomendação
    // oficial da doc do BluetoothSocket. Best-effort: no Android 12+ o
    // cancelDiscovery exige BLUETOOTH_SCAN, que não pedimos (não fazemos scan) —
    // se negar, seguimos direto pra conexão.
    try {
      adapter.cancelDiscovery();
    } catch (SecurityException e) {
      Log.w(TAG, "cancelDiscovery sem permissão BLUETOOTH_SCAN, ignorando: " + e.getMessage());
    }

    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
    try {
      socket.connect();
      return socket;
    } catch (IOException first) {
      closeQuietly(socket);
      Log.w(TAG, "Conexão SPP padrão falhou (" + first.getMessage() + "), tentando canal 1 direto");
      // Fallback clássico para clones com SDP quebrado: RFCOMM direto no canal 1.
      try {
        BluetoothSocket fallback =
          (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
        fallback.connect();
        return fallback;
      } catch (Exception second) {
        throw new IOException(
          "Não conectou na impressora " + address + ": " + first.getMessage(),
          first
        );
      }
    }
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

  /**
   * Converte o bitmap em blocos GS v 0 (raster bit image): 1 bit por pixel,
   * bit ligado = ponto queimado. Pixels transparentes contam como branco
   * (mesma razão do fundo branco forçado no printPdf da térmica interna).
   */
  private void writeRaster(OutputStream out, Bitmap bitmap) throws IOException {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int bytesPerRow = (width + 7) / 8;
    int[] pixels = new int[width];

    for (int startRow = 0; startRow < height; startRow += ROWS_PER_BLOCK) {
      int rows = Math.min(ROWS_PER_BLOCK, height - startRow);
      byte[] block = new byte[8 + bytesPerRow * rows];
      block[0] = 0x1D;
      block[1] = 0x76;
      block[2] = 0x30;
      block[3] = 0x00; // modo normal (sem escala)
      block[4] = (byte) (bytesPerRow & 0xFF);
      block[5] = (byte) ((bytesPerRow >> 8) & 0xFF);
      block[6] = (byte) (rows & 0xFF);
      block[7] = (byte) ((rows >> 8) & 0xFF);

      int offset = 8;
      for (int y = 0; y < rows; y++) {
        bitmap.getPixels(pixels, 0, width, 0, startRow + y, width, 1);
        for (int x = 0; x < width; x++) {
          int c = pixels[x];
          int luminance = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
          if (Color.alpha(c) > 128 && luminance < LUMINANCE_THRESHOLD) {
            block[offset + (x >> 3)] |= (byte) (0x80 >> (x & 7));
          }
        }
        offset += bytesPerRow;
      }
      writeChunked(out, block);
    }
    Log.d(TAG, "raster enviado: " + width + "x" + height + " (" + bytesPerRow + " bytes/linha)");
  }

  private void writeChunked(OutputStream out, byte[] data) throws IOException {
    for (int off = 0; off < data.length; off += CHUNK_SIZE) {
      int len = Math.min(CHUNK_SIZE, data.length - off);
      out.write(data, off, len);
      out.flush();
      try {
        Thread.sleep(CHUNK_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Impressão interrompida", e);
      }
    }
  }

  /**
   * Espera proporcional à altura impressa antes de fechar o socket: fechar
   * cedo demais descarta o que ainda está no buffer da impressora e corta o
   * rodapé do recibo.
   */
  private void drainDelay(int printedHeight) {
    long ms = Math.min(3000, 300 + printedHeight / 2);
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void closeQuietly(BluetoothSocket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ignored) {}
    }
  }
}
