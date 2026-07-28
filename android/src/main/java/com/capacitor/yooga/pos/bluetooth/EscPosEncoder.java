package com.capacitor.yooga.pos.bluetooth;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Codificação ESC/POS compartilhada pelos transportes (Bluetooth SPP e TCP
 * 9100): reset, controle térmico, raster GS v 0 e avanço final. O transporte
 * só decide o tamanho do chunk e a pausa entre eles — térmicas BT baratas têm
 * buffer minúsculo (512B/20ms); impressoras de rede aguentam chunk grande sem
 * pausa.
 */
final class EscPosEncoder {

  private static final int ROWS_PER_BLOCK = 128;

  private EscPosEncoder() {}

  static void writeJob(
    OutputStream out,
    List<Bitmap> bitmaps,
    int feedLines,
    int heatTime,
    int luminanceThreshold,
    int chunkSize,
    int chunkDelayMs
  ) throws IOException {
    out.write(new byte[] { 0x1B, 0x40 }); // ESC @ (reset)
    if (heatTime > 0) {
      // ESC 7 n1 n2 n3 (controle térmico das controladoras chinesas):
      // n1 = pontos aquecidos simultâneos ((n1+1)*8), n2 = heat time (10us),
      // n3 = intervalo entre aquecimentos (10us). n1=11 (96 pontos) equilibra
      // escuridão x consumo. ATENÇÃO: comando fora do padrão Epson — firmwares
      // que não o conhecem (ex.: Trix POS80) dessincronizam o parser e
      // imprimem o raster seguinte como texto. Por isso é opt-in
      // (heatTime 0 = não envia), só ligar em impressoras validadas.
      out.write(new byte[] { 0x1B, 0x37, 11, (byte) Math.min(255, heatTime), 4 });
    }
    for (Bitmap bitmap : bitmaps) {
      writeRaster(out, bitmap, luminanceThreshold, chunkSize, chunkDelayMs);
    }
    out.write(new byte[] { 0x1B, 0x64, (byte) Math.max(0, feedLines) }); // ESC d n (avanço)
    out.flush();
  }

  /**
   * Converte o bitmap em blocos GS v 0 (raster bit image): 1 bit por pixel,
   * bit ligado = ponto queimado. Pixels transparentes contam como branco.
   */
  private static void writeRaster(
    OutputStream out,
    Bitmap bitmap,
    int luminanceThreshold,
    int chunkSize,
    int chunkDelayMs
  ) throws IOException {
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
          if (Color.alpha(c) > 128 && luminance < luminanceThreshold) {
            block[offset + (x >> 3)] |= (byte) (0x80 >> (x & 7));
          }
        }
        offset += bytesPerRow;
      }
      writeChunked(out, block, chunkSize, chunkDelayMs);
    }
  }

  private static void writeChunked(OutputStream out, byte[] data, int chunkSize, int chunkDelayMs)
    throws IOException {
    for (int off = 0; off < data.length; off += chunkSize) {
      int len = Math.min(chunkSize, data.length - off);
      out.write(data, off, len);
      out.flush();
      if (chunkDelayMs > 0) {
        try {
          Thread.sleep(chunkDelayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Impressão interrompida", e);
        }
      }
    }
  }
}
