package com.capacitor.yooga.pos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import com.capacitor.yooga.pos.bluetooth.BluetoothEscPosService;
import com.capacitor.yooga.pos.elgin.Services.Pix4.Pix4Service;
import com.elgin.e1.Impressora.Termica;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.izettle.html2bitmap.Html2Bitmap;
import com.izettle.html2bitmap.Html2BitmapConfigurator;
import com.izettle.html2bitmap.content.WebViewContent;

import java.io.File;
import java.io.FileOutputStream;


@CapacitorPlugin(
  name = "CapacitorYoogaPos",
  permissions = @Permission(alias = "bluetooth", strings = { "android.permission.BLUETOOTH_CONNECT" })
)
public class CapacitorYoogaPosPlugin extends Plugin {

  private static final String TAG = "CapacitorYoogaPos";
  private boolean printerReady = false;
  private final BluetoothEscPosService bluetoothService = new BluetoothEscPosService();

  @Override
  public void load() {
    super.load();
    // Setup inicial exigido pela doc da Elgin para M10/PosGo.
    // A conexão é mantida aberta (sem FechaConexaoImpressora) para que o
    // service binding assíncrono do minipdvm8 tenha tempo de completar
    // antes da primeira impressão. A doc da Elgin diz: "Caso deseje iniciar
    // uma conexão automática com a impressora, não adicione FechaConexaoImpressora".
    // Se falhar aqui (device sem POS, impressora ocupada no boot), os métodos
    // de impressão tentam reconectar sob demanda via ensurePrinterConnection().
    ensurePrinterConnection();
  }

  /**
   * Abre a conexão com a térmica interna se ainda não estiver aberta.
   * Tipo 5 = SmartPOS / impressoras acopladas Android (M10, PosGo);
   * tipo 6 = MiniPDV M8 (legado), usado como fallback. Nunca lança exceção:
   * em devices sem o hardware/serviço Elgin apenas retorna false, preservando
   * o comportamento das versões antigas do plugin (que tentavam conectar a
   * cada impressão e falhavam de forma recuperável).
   */
  private synchronized boolean ensurePrinterConnection() {
    if (printerReady) return true;
    try {
      Termica.setContext(getActivity());
      Termica.setActivity(getActivity());
      int setupResult = Termica.AbreConexaoImpressora(5, "", "", 0);
      Log.d(TAG, "AbreConexaoImpressora(5): " + setupResult);
      if (setupResult != 0) {
        setupResult = Termica.AbreConexaoImpressora(6, "M8", "", 0);
        Log.d(TAG, "Fallback AbreConexaoImpressora(6): " + setupResult);
      }
      printerReady = (setupResult == 0);
    } catch (Throwable t) {
      Log.e(TAG, "ensurePrinterConnection erro: " + t.getMessage(), t);
      printerReady = false;
    }
    Log.d(TAG, "printerReady: " + printerReady);
    return printerReady;
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
    ensurePrinterConnection();

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
    ensurePrinterConnection();

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
    ensurePrinterConnection();

    if (base64 == null || base64.isEmpty()) {
      call.reject("base64 do PDF ausente");
      return;
    }

    File tempFile = null;
    ParcelFileDescriptor pfd = null;
    PdfRenderer renderer = null;
    // Controla o corte no finally: so cortamos se ao menos uma pagina foi
    // impressa, garantindo o destaque do papel mesmo se uma pagina seguinte
    // lancar excecao no meio da impressao.
    boolean anyPagePrinted = false;
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

        // O DANFE da NFCe vem com um vao em branco gigante no rodape (a pagina
        // do PDF e bem mais alta que o conteudo). Aparamos esse branco para
        // nao desperdicar bobina nem deixar um espaco vazio antes do corte.
        Bitmap toPrint = trimBottomWhitespace(bitmap);

        int printResult = Termica.ImprimeBitmap(toPrint);
        anyPagePrinted = true;
        Log.d(TAG, "ImprimeBitmap pagina " + i + ": " + printResult
          + " | alturaOriginal=" + bitmap.getHeight()
          + " alturaImpressa=" + toPrint.getHeight());

        if (toPrint != bitmap) {
          toPrint.recycle();
        }
        bitmap.recycle();
      }

      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printPdf erro: " + e.getMessage(), e);
      call.reject("Erro ao rasterizar/imprimir PDF: " + e.getMessage());
    } finally {
      // Corte no finally: garante o destaque do papel mesmo apos impressao
      // parcial (ex.: excecao numa pagina do meio). So corta se algo foi
      // impresso, para nao avancar/cortar bobina em branco quando falhou antes
      // de imprimir qualquer pagina.
      if (anyPagePrinted) {
        try {
          int cutResult = Termica.Corte(cutPaperLength);
          Log.d(TAG, "Corte: " + cutResult);
        } catch (Exception ex) {
          Log.e(TAG, "Corte falhou: " + ex.getMessage(), ex);
        }
      }
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
   * Recorta o espaco em branco no rodape de um bitmap rasterizado. Varre as
   * linhas de baixo para cima ate achar a primeira com pixel "escuro" (fora do
   * limiar de branco) e devolve um bitmap cortado nessa altura + uma pequena
   * folga. Se a pagina for toda branca, devolve o bitmap original sem alterar.
   */
  private Bitmap trimBottomWhitespace(Bitmap src) {
    if (src == null) {
      return null;
    }
    final int width = src.getWidth();
    final int height = src.getHeight();
    if (width <= 0 || height <= 0) {
      return src;
    }

    // Limiar: pixels com qualquer canal abaixo disso contam como conteudo.
    final int threshold = 245;
    final int[] row = new int[width];
    int lastContentRow = -1;

    for (int y = height - 1; y >= 0; y--) {
      src.getPixels(row, 0, width, 0, y, width, 1);
      boolean hasContent = false;
      for (int x = 0; x < width; x++) {
        final int c = row[x];
        final int r = (c >> 16) & 0xFF;
        final int g = (c >> 8) & 0xFF;
        final int b = c & 0xFF;
        if (r < threshold || g < threshold || b < threshold) {
          hasContent = true;
          break;
        }
      }
      if (hasContent) {
        lastContentRow = y;
        break;
      }
    }

    // Tudo branco -> nao mexe (evita bitmap de altura 0).
    if (lastContentRow < 0) {
      return src;
    }

    // Pequena folga proporcional a largura para nao "colar" no corte.
    final int margin = Math.round(width * 0.03f);
    final int newHeight = Math.min(height, lastContentRow + 1 + margin);
    if (newHeight >= height) {
      return src;
    }

    return Bitmap.createBitmap(src, 0, 0, width, newHeight);
  }

  /**
   * Lista os devices Bluetooth já pareados no Android. O pareamento é feito nas
   * configurações do sistema; o app só escolhe entre os pareados.
   */
  @PluginMethod
  public void listBluetoothDevices(PluginCall call) {
    if (needsBluetoothPermission(call)) return;
    try {
      JSArray devices = new JSArray();
      for (BluetoothEscPosService.BondedDevice device : bluetoothService.listBondedDevices()) {
        JSObject item = new JSObject();
        item.put("name", device.name);
        item.put("address", device.address);
        devices.put(item);
      }
      JSObject result = new JSObject();
      result.put("devices", devices);
      call.resolve(result);
    } catch (Exception e) {
      Log.e(TAG, "listBluetoothDevices erro: " + e.getMessage(), e);
      call.reject(e.getMessage());
    }
  }

  /**
   * Renderiza o HTML em bitmap (mesmo pipeline do print interno) e envia como
   * raster ESC/POS para uma térmica Bluetooth genérica (ex.: MTP-II 58mm).
   */
  @PluginMethod
  public void printBluetooth(PluginCall call) {
    if (needsBluetoothPermission(call)) return;
    String address = call.getString("address");
    if (address == null || address.isEmpty()) {
      call.reject("address (MAC do device pareado) é obrigatório");
      return;
    }
    Integer feedLines = call.getInt("feedLines", 4);
    try {
      Bitmap bitmap = renderHtmlToBitmap(call);
      Log.d(TAG, "printBluetooth bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "NULL"));
      bluetoothService.printBitmap(address, bitmap, feedLines);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printBluetooth erro: " + e.getMessage(), e);
      call.reject("Erro na impressão Bluetooth: " + e.getMessage());
    }
  }

  /**
   * Teste rápido de comunicação com a térmica Bluetooth (texto ASCII, sem bitmap).
   */
  @PluginMethod
  public void printBluetoothText(PluginCall call) {
    if (needsBluetoothPermission(call)) return;
    String address = call.getString("address");
    if (address == null || address.isEmpty()) {
      call.reject("address (MAC do device pareado) é obrigatório");
      return;
    }
    String text = call.getString("text", "TESTE YOOGA BT\n");
    Integer feedLines = call.getInt("feedLines", 4);
    try {
      bluetoothService.printText(address, text, feedLines);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printBluetoothText erro: " + e.getMessage(), e);
      call.reject("Erro na impressão Bluetooth: " + e.getMessage());
    }
  }

  /**
   * BLUETOOTH_CONNECT só é permissão de runtime a partir do Android 12 (API 31);
   * antes disso o BLUETOOTH do manifest basta. Retorna true se a call ficou
   * pendente aguardando o prompt de permissão (o método deve retornar sem fazer nada).
   */
  private boolean needsBluetoothPermission(PluginCall call) {
    if (android.os.Build.VERSION.SDK_INT < 31) return false;
    if (getPermissionState("bluetooth") == PermissionState.GRANTED) return false;
    requestPermissionForAlias("bluetooth", call, "bluetoothPermissionCallback");
    return true;
  }

  @PermissionCallback
  private void bluetoothPermissionCallback(PluginCall call) {
    if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
      call.reject("Permissão Bluetooth negada");
      return;
    }
    switch (call.getMethodName()) {
      case "listBluetoothDevices":
        listBluetoothDevices(call);
        break;
      case "printBluetooth":
        printBluetooth(call);
        break;
      case "printBluetoothText":
        printBluetoothText(call);
        break;
      default:
        call.reject("Método desconhecido após permissão: " + call.getMethodName());
    }
  }

  /**
   * Mesmo pipeline HTML -> bitmap do print() da térmica interna, lendo as
   * mesmas opções da call (bitmapWidth 384 = 58mm, 576 = 80mm).
   */
  private Bitmap renderHtmlToBitmap(PluginCall call) {
    String html = call.getString("html");
    Integer webViewZoom = call.getInt("webViewZoom", 100);
    Integer bitmapWidth = call.getInt("bitmapWidth", 384);
    Integer measureDelay = call.getInt("measureDelay", 0);
    Integer screenshotDelay = call.getInt("screenshotDelay", 0);
    Boolean strictMode = call.getBoolean("strictMode", false);
    Integer timeout = call.getInt("timeout", 30000);
    Integer builderTextZoom = call.getInt("builderTextZoom", 100);

    Html2BitmapConfigurator configurator = new Html2BitmapConfigurator() {
      @Override
      public void configureWebView(WebView webview) {
        webview.getSettings().setTextZoom(webViewZoom);
      }
    };

    return new Html2Bitmap.Builder()
      .setContext(getContext())
      .setContent(WebViewContent.html(html))
      .setBitmapWidth(bitmapWidth)
      .setMeasureDelay(measureDelay)
      .setScreenshotDelay(screenshotDelay)
      .setStrictMode(strictMode)
      .setTimeout(timeout)
      .setTextZoom(builderTextZoom)
      .setConfigurator(configurator)
      .build()
      .getBitmap();
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
