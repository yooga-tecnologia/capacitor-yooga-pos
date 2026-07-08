package com.capacitor.yooga.pos.bluetooth;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Transporte ESC/POS para impressoras térmicas de rede (TCP porta 9100, o
 * "raw printing" que praticamente toda térmica Ethernet/Wi-Fi escuta).
 * Mesmo encoder do Bluetooth; aqui o buffer é folgado, então chunk grande e
 * sem pausa. Conexão por job, timeout curto — falha rápida quando a
 * impressora está desligada/fora da rede.
 */
public class TcpEscPosService {

  public static final int DEFAULT_PORT = 9100;

  private static final int CONNECT_TIMEOUT_MS = 4000;
  private static final int CHUNK_SIZE = 4096;
  private static final int DEFAULT_HEAT_TIME = 140;
  private static final int DEFAULT_LUMINANCE_THRESHOLD = 200;

  public void printBitmaps(
    String ip,
    int port,
    List<Bitmap> bitmaps,
    int feedLines,
    int heatTime,
    int luminanceThreshold
  ) throws IOException {
    if (bitmaps == null || bitmaps.isEmpty()) {
      throw new IOException("Nenhum bitmap para imprimir");
    }
    for (Bitmap bitmap : bitmaps) {
      if (bitmap == null) {
        throw new IOException("Bitmap nulo — falha ao renderizar o conteúdo");
      }
    }
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(ip, port > 0 ? port : DEFAULT_PORT), CONNECT_TIMEOUT_MS);
      OutputStream out = socket.getOutputStream();
      EscPosEncoder.writeJob(out, bitmaps, feedLines, heatTime, luminanceThreshold, CHUNK_SIZE, 0);
      // Meio segundo antes do close: TCP entrega, mas impressoras baratas
      // descartam o que ainda não drenou do buffer quando o peer some.
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } catch (IOException e) {
      throw new IOException("Não conectou na impressora " + ip + ":" + port + ": " + e.getMessage(), e);
    }
  }

  public void printBitmaps(String ip, int port, List<Bitmap> bitmaps, int feedLines) throws IOException {
    printBitmaps(ip, port, bitmaps, feedLines, DEFAULT_HEAT_TIME, DEFAULT_LUMINANCE_THRESHOLD);
  }
}
