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
  // Limiar alto de propósito: captura os pixels cinza do anti-aliasing das
  // fontes, senão as letras saem falhadas/clarinhas na térmica.
  private static final int DEFAULT_LUMINANCE_THRESHOLD = 200;
  // Heat time do ESC 7 em unidades de 10us. Default 0 = NÃO envia o comando:
  // ESC 7 é extensão de controladora chinesa (fora do padrão Epson) e
  // dessincroniza o parser de firmwares como o da Trix POS80, que passa a
  // imprimir o raster GS v 0 como texto. Impressoras que aceitam (ex.: MTP-II)
  // podem ligar via config para sair mais escuro (140 foi o valor calibrado).
  private static final int DEFAULT_HEAT_TIME = 0;
  // Térmica portátil Bluetooth (MTP-II e afins) não tem guilhotina — o avanço
  // de papel é o "corte". Enviar GS V numa impressora sem cortador é, na melhor
  // das hipóteses, ignorado; na pior, dessincroniza o parser (ver ESC 7 acima).
  // Quem parear uma Bluetooth COM cortador liga via config.
  private static final boolean DEFAULT_CUT_PAPER = false;

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
  public void printBitmap(String address, Bitmap bitmap, int feedLines, int heatTime, int luminanceThreshold)
    throws IOException {
    printBitmap(address, bitmap, feedLines, heatTime, luminanceThreshold, DEFAULT_CUT_PAPER);
  }

  /** Overload com controle explícito do corte (ver {@link #DEFAULT_CUT_PAPER}). */
  @SuppressLint("MissingPermission")
  public void printBitmap(
    String address,
    Bitmap bitmap,
    int feedLines,
    int heatTime,
    int luminanceThreshold,
    boolean cutPaper
  ) throws IOException {
    printBitmaps(
      address,
      java.util.Collections.singletonList(bitmap),
      feedLines,
      heatTime,
      luminanceThreshold,
      cutPaper
    );
  }

  /** Overload com os defaults de densidade/limiar. */
  @SuppressLint("MissingPermission")
  public void printBitmap(String address, Bitmap bitmap, int feedLines) throws IOException {
    printBitmap(address, bitmap, feedLines, DEFAULT_HEAT_TIME, DEFAULT_LUMINANCE_THRESHOLD);
  }

  /**
   * Imprime uma sequência de bitmaps (ex.: páginas de um PDF rasterizado) numa
   * única conexão — conectar por página deixaria a impressora dormir no meio.
   * O avanço de {@code feedLines} sai uma vez só, no final do último bitmap.
   */
  @SuppressLint("MissingPermission")
  public void printBitmaps(
    String address,
    java.util.List<Bitmap> bitmaps,
    int feedLines,
    int heatTime,
    int luminanceThreshold
  ) throws IOException {
    printBitmaps(address, bitmaps, feedLines, heatTime, luminanceThreshold, DEFAULT_CUT_PAPER);
  }

  /** Overload com controle explícito do corte (ver {@link #DEFAULT_CUT_PAPER}). */
  @SuppressLint("MissingPermission")
  public void printBitmaps(
    String address,
    java.util.List<Bitmap> bitmaps,
    int feedLines,
    int heatTime,
    int luminanceThreshold,
    boolean cutPaper
  ) throws IOException {
    if (bitmaps == null || bitmaps.isEmpty()) {
      throw new IOException("Nenhum bitmap para imprimir");
    }
    for (Bitmap bitmap : bitmaps) {
      if (bitmap == null) {
        throw new IOException("Bitmap nulo — falha ao renderizar o conteúdo");
      }
    }
    BluetoothSocket socket = connect(address);
    try {
      OutputStream out = socket.getOutputStream();
      EscPosEncoder.writeJob(
        out,
        bitmaps,
        feedLines,
        heatTime,
        luminanceThreshold,
        cutPaper,
        CHUNK_SIZE,
        CHUNK_DELAY_MS
      );
      int totalHeight = 0;
      for (Bitmap bitmap : bitmaps) {
        totalHeight += bitmap.getHeight();
      }
      drainDelay(totalHeight);
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
