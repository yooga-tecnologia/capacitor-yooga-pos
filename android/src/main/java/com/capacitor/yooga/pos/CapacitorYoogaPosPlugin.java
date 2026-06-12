package com.capacitor.yooga.pos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import com.capacitor.yooga.pos.elgin.Services.Pix4.Pix4Service;
import com.elgin.e1.Impressora.Termica;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.izettle.html2bitmap.Html2Bitmap;
import com.izettle.html2bitmap.Html2BitmapConfigurator;
import com.izettle.html2bitmap.content.WebViewContent;

import java.io.File;
import java.io.FileOutputStream;


@CapacitorPlugin(name = "CapacitorYoogaPos")
public class CapacitorYoogaPosPlugin extends Plugin {

  private static final String TAG = "CapacitorYoogaPos";
  private boolean printerReady = false;

  @Override
  public void load() {
    super.load();
    // Setup inicial exigido pela doc da Elgin para M10/PosGo.
    // A conexão é mantida aberta (sem FechaConexaoImpressora) para que o
    // service binding assíncrono do minipdvm8 tenha tempo de completar
    // antes da primeira impressão. A doc da Elgin diz: "Caso deseje iniciar
    // uma conexão automática com a impressora, não adicione FechaConexaoImpressora".
    Termica.setContext(getActivity());
    Termica.setActivity(getActivity());
    int setupResult = Termica.AbreConexaoImpressora(5, "", "", 0);
    Log.d(TAG, "Setup AbreConexaoImpressora(5): " + setupResult);
    if (setupResult != 0) {
      setupResult = Termica.AbreConexaoImpressora(6, "M8", "", 0);
      Log.d(TAG, "Setup fallback AbreConexaoImpressora(6): " + setupResult);
    }
    printerReady = (setupResult == 0);
    Log.d(TAG, "printerReady: " + printerReady);
  }

  @PluginMethod
  public void showLogoOnDisplay(PluginCall call) {
    Pix4Service p = new Pix4Service(getActivity());
    p.apresentaImagemYooga();
    call.resolve();
  }

  @PluginMethod
  public void showPix(PluginCall call) {
    String value = call.getString("value");
    Pix4Service p = new Pix4Service(getActivity());
    p.apresentaPix(value);
    call.resolve();
  }

  @PluginMethod
  public void print(PluginCall call) {
    String html = call.getString("html");
    Integer cutPaperLength = call.getInt("cutPaperLength", 4);
    Integer webViewZoom = call.getInt("webViewZoom", 100);
    Integer bitmapWidth = call.getInt("bitmapWidth", 384);
    Integer measureDelay = call.getInt("measureDelay", 0);
    Integer screenshotDelay = call.getInt("screenshotDelay", 0);
    Boolean strictMode = call.getBoolean("strictMode", false);
    Integer timeout = call.getInt("timeout", 30000);
    Integer builderTextZoom = call.getInt("builderTextZoom", 100);

    Log.d(TAG, "print() chamado, printerReady=" + printerReady);

    Html2BitmapConfigurator html2BitmapConfigurator = new Html2BitmapConfigurator() {
      @Override
      public void configureWebView(WebView webview) {
        webview.getSettings().setTextZoom(webViewZoom);
      }
    };

    Html2Bitmap build = new Html2Bitmap.Builder()
      .setContext(getContext())
      .setContent(WebViewContent.html(html))
      .setBitmapWidth(bitmapWidth)
      .setMeasureDelay(measureDelay)
      .setScreenshotDelay(screenshotDelay)
      .setStrictMode(strictMode)
      .setTimeout(timeout)
      .setTextZoom(builderTextZoom)
      .setConfigurator(html2BitmapConfigurator)
      .build();

    Bitmap bitmap = build.getBitmap();
    Log.d(TAG, "bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "NULL"));

    int printResult = Termica.ImprimeBitmap(bitmap);
    Log.d(TAG, "ImprimeBitmap: " + printResult);

    int cutResult = Termica.Corte(cutPaperLength);
    Log.d(TAG, "Corte: " + cutResult);

    call.resolve();
  }

  @PluginMethod
  public void printText(PluginCall call) {
    String text = call.getString("text", "");
    Integer cutPaperLength = call.getInt("cutPaperLength", 4);

    Log.d(TAG, "printText() chamado, printerReady=" + printerReady);

    int printResult = Termica.ImpressaoTexto(text, 0, 0, 20);
    Log.d(TAG, "ImpressaoTexto: " + printResult);

    int cutResult = Termica.Corte(cutPaperLength);
    Log.d(TAG, "Corte: " + cutResult);

    call.resolve();
  }

  /**
   * Rasteriza cada pagina de um PDF (base64) em bitmap e imprime na termica
   * interna. Usado para o DANFE da NFCe, que so existe em PDF (gerado pelo
   * servico fiscal). Reaproveita o mesmo Termica.ImprimeBitmap do print(HTML).
   */
  @PluginMethod
  public void printPdf(PluginCall call) {
    String base64 = call.getString("base64");
    Integer cutPaperLength = call.getInt("cutPaperLength", 4);
    Integer bitmapWidth = call.getInt("bitmapWidth", 384);

    Log.d(TAG, "printPdf() chamado, printerReady=" + printerReady);

    if (base64 == null || base64.isEmpty()) {
      call.reject("base64 do PDF ausente");
      return;
    }

    File tempFile = null;
    ParcelFileDescriptor pfd = null;
    PdfRenderer renderer = null;
    try {
      byte[] pdfBytes = Base64.decode(base64, Base64.DEFAULT);
      tempFile = File.createTempFile("danfe", ".pdf", getContext().getCacheDir());
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        fos.write(pdfBytes);
      }

      pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
      renderer = new PdfRenderer(pfd);

      int pageCount = renderer.getPageCount();
      Log.d(TAG, "printPdf paginas: " + pageCount);

      for (int i = 0; i < pageCount; i++) {
        PdfRenderer.Page page = renderer.openPage(i);

        int targetWidth = bitmapWidth;
        int targetHeight = Math.max(
          1,
          Math.round(page.getHeight() * ((float) targetWidth / page.getWidth()))
        );

        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Fundo branco obrigatorio: o PdfRenderer mantem areas transparentes,
        // que na termica saem como mancha preta.
        canvas.drawColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        page.close();

        int printResult = Termica.ImprimeBitmap(bitmap);
        Log.d(TAG, "ImprimeBitmap pagina " + i + ": " + printResult);
        bitmap.recycle();
      }

      int cutResult = Termica.Corte(cutPaperLength);
      Log.d(TAG, "Corte: " + cutResult);

      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printPdf erro: " + e.getMessage(), e);
      call.reject("Erro ao rasterizar/imprimir PDF: " + e.getMessage());
    } finally {
      if (renderer != null) {
        try { renderer.close(); } catch (Exception ignored) {}
      }
      if (pfd != null) {
        try { pfd.close(); } catch (Exception ignored) {}
      }
      if (tempFile != null) {
        tempFile.delete();
      }
    }
  }

  /**
   * Teste diagnóstico: imprime direto pelo SDK iMin, sem passar pelo e1.
   * Usar para verificar se a impressora iMin responde.
   */
  @PluginMethod
  public void testIminDirect(PluginCall call) {
    try {
      Log.d(TAG, "testIminDirect() - device: " + android.os.Build.MODEL + " / " + android.os.Build.MANUFACTURER);

      com.imin.printerlib.IminPrintUtils imin = com.imin.printerlib.IminPrintUtils.getInstance(getContext());
      Log.d(TAG, "IminPrintUtils instance: " + (imin != null));
      Log.d(TAG, "initIminPrinter antes: " + imin.isInitIminPrinter());

      // D1 usa USB para impressora interna (SPI é para Swift/M2)
      imin.initPrinter(com.imin.printerlib.IminPrintUtils.PrintConnectType.USB);
      Log.d(TAG, "initPrinter(USB) ok, initIminPrinter depois: " + imin.isInitIminPrinter());

      // Pequeno delay para inicialização do SPI
      Thread.sleep(500);

      imin.printText("TESTE DIRETO IMIN\n");
      imin.printText("Model: " + android.os.Build.MODEL + "\n");
      imin.printText("Manufacturer: " + android.os.Build.MANUFACTURER + "\n");
      imin.printAndFeedPaper(100);
      Log.d(TAG, "printText + feedPaper executados");

      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "testIminDirect ERRO: " + e.getMessage(), e);
      call.reject("Erro iMin: " + e.getMessage());
    }
  }
}
