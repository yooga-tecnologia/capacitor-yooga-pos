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
  permissions = {
    @Permission(alias = "bluetooth", strings = { "android.permission.BLUETOOTH_CONNECT" }),
    @Permission(alias = "bluetoothScan", strings = { "android.permission.BLUETOOTH_SCAN" }),
    @Permission(alias = "location", strings = { "android.permission.ACCESS_FINE_LOCATION" })
  }
)
public class CapacitorYoogaPosPlugin extends Plugin {

  private static final String TAG = "CapacitorYoogaPos";
  private boolean printerReady = false;
  private final BluetoothEscPosService bluetoothService = new BluetoothEscPosService();
  private final com.capacitor.yooga.pos.bluetooth.TcpEscPosService tcpService =
    new com.capacitor.yooga.pos.bluetooth.TcpEscPosService();
  private final com.capacitor.yooga.pos.bluetooth.NetworkPrinterScanner networkScanner =
    new com.capacitor.yooga.pos.bluetooth.NetworkPrinterScanner();
  private com.capacitor.yooga.pos.bluetooth.BluetoothDiscoveryService discoveryService;

  private com.capacitor.yooga.pos.bluetooth.BluetoothDiscoveryService discoveryService() {
    if (discoveryService == null) {
      discoveryService = new com.capacitor.yooga.pos.bluetooth.BluetoothDiscoveryService(getContext());
    }
    return discoveryService;
  }

  @Override
  protected void handleOnDestroy() {
    if (discoveryService != null) {
      discoveryService.cleanup();
    }
    networkScanner.stop();
    super.handleOnDestroy();
  }

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
    // Densidade: heatTime do ESC 7 (10us/un, 0 desliga o comando) e limiar de
    // luminância do raster (0-255; maior = mais pixels viram preto).
    Integer heatTime = call.getInt("heatTime", 140);
    Integer luminanceThreshold = call.getInt("luminanceThreshold", 200);
    try {
      Bitmap bitmap = renderHtmlToBitmap(call);
      Log.d(TAG, "printBluetooth bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "NULL"));
      bluetoothService.printBitmap(address, bitmap, feedLines, heatTime, luminanceThreshold);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printBluetooth erro: " + e.getMessage(), e);
      call.reject("Erro na impressão Bluetooth: " + e.getMessage());
    }
  }

  /**
   * Rasteriza cada página de um PDF (base64) e imprime na térmica Bluetooth
   * numa única conexão. Mesmo pipeline do printPdf interno (fundo branco +
   * apara do rodapé), trocando só o destino. Usado para o DANFE da NFC-e.
   */
  @PluginMethod
  public void printPdfBluetooth(PluginCall call) {
    if (needsBluetoothPermission(call)) return;
    String address = call.getString("address");
    if (address == null || address.isEmpty()) {
      call.reject("address (MAC do device pareado) é obrigatório");
      return;
    }
    String base64 = call.getString("base64");
    if (base64 == null || base64.isEmpty()) {
      call.reject("base64 do PDF ausente");
      return;
    }
    Integer bitmapWidth = call.getInt("bitmapWidth", 384);
    Integer feedLines = call.getInt("feedLines", 4);
    Integer heatTime = call.getInt("heatTime", 140);
    Integer luminanceThreshold = call.getInt("luminanceThreshold", 200);

    java.util.List<Bitmap> pages = new java.util.ArrayList<>();
    try {
      pages = renderPdfToBitmaps(base64, bitmapWidth);
      bluetoothService.printBitmaps(address, pages, feedLines, heatTime, luminanceThreshold);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printPdfBluetooth erro: " + e.getMessage(), e);
      call.reject("Erro na impressão Bluetooth do PDF: " + e.getMessage());
    } finally {
      for (Bitmap page : pages) {
        page.recycle();
      }
    }
  }

  /**
   * Rasteriza as páginas de um PDF (base64) em bitmaps prontos para a
   * térmica: fundo branco (PdfRenderer mantém transparência, que sai como
   * mancha preta) e rodapé em branco aparado. Compartilhado pelos transportes
   * Bluetooth e TCP.
   */
  private java.util.List<Bitmap> renderPdfToBitmaps(String base64, int bitmapWidth) throws Exception {
    java.util.List<Bitmap> pages = new java.util.ArrayList<>();
    File tempFile = null;
    ParcelFileDescriptor pfd = null;
    PdfRenderer renderer = null;
    try {
      byte[] pdfBytes = Base64.decode(base64, Base64.DEFAULT);
      tempFile = File.createTempFile("danfe-raster", ".pdf", getContext().getCacheDir());
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        fos.write(pdfBytes);
      }
      pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
      renderer = new PdfRenderer(pfd);

      int pageCount = renderer.getPageCount();
      Log.d(TAG, "renderPdfToBitmaps paginas: " + pageCount);
      for (int i = 0; i < pageCount; i++) {
        PdfRenderer.Page page = renderer.openPage(i);
        int targetWidth = bitmapWidth;
        int targetHeight = Math.max(
          1,
          Math.round(page.getHeight() * ((float) targetWidth / page.getWidth()))
        );
        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        page.close();

        Bitmap toPrint = trimBottomWhitespace(bitmap);
        if (toPrint != bitmap) {
          bitmap.recycle();
        }
        pages.add(toPrint);
      }
      return pages;
    } catch (Exception e) {
      for (Bitmap page : pages) {
        page.recycle();
      }
      throw e;
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
   * Renderiza o HTML em bitmap e envia como raster ESC/POS para uma térmica
   * de rede (TCP 9100). Sem permissão de runtime: rede local é INTERNET.
   */
  @PluginMethod
  public void printTcp(PluginCall call) {
    String ip = call.getString("ip");
    if (ip == null || ip.isEmpty()) {
      call.reject("ip da impressora de rede é obrigatório");
      return;
    }
    Integer port = call.getInt("port", com.capacitor.yooga.pos.bluetooth.TcpEscPosService.DEFAULT_PORT);
    Integer feedLines = call.getInt("feedLines", 4);
    Integer heatTime = call.getInt("heatTime", 140);
    Integer luminanceThreshold = call.getInt("luminanceThreshold", 200);
    try {
      Bitmap bitmap = renderHtmlToBitmap(call);
      Log.d(TAG, "printTcp bitmap: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "NULL"));
      tcpService.printBitmaps(
        ip,
        port,
        java.util.Collections.singletonList(bitmap),
        feedLines,
        heatTime,
        luminanceThreshold
      );
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printTcp erro: " + e.getMessage(), e);
      call.reject("Erro na impressão de rede: " + e.getMessage());
    }
  }

  /**
   * Rasteriza um PDF (base64) e imprime na térmica de rede numa única
   * conexão. Contraparte TCP do printPdfBluetooth (DANFE da NFC-e).
   */
  @PluginMethod
  public void printPdfTcp(PluginCall call) {
    String ip = call.getString("ip");
    if (ip == null || ip.isEmpty()) {
      call.reject("ip da impressora de rede é obrigatório");
      return;
    }
    String base64 = call.getString("base64");
    if (base64 == null || base64.isEmpty()) {
      call.reject("base64 do PDF ausente");
      return;
    }
    Integer port = call.getInt("port", com.capacitor.yooga.pos.bluetooth.TcpEscPosService.DEFAULT_PORT);
    Integer bitmapWidth = call.getInt("bitmapWidth", 384);
    Integer feedLines = call.getInt("feedLines", 4);
    Integer heatTime = call.getInt("heatTime", 140);
    Integer luminanceThreshold = call.getInt("luminanceThreshold", 200);

    java.util.List<Bitmap> pages = new java.util.ArrayList<>();
    try {
      pages = renderPdfToBitmaps(base64, bitmapWidth);
      tcpService.printBitmaps(ip, port, pages, feedLines, heatTime, luminanceThreshold);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "printPdfTcp erro: " + e.getMessage(), e);
      call.reject("Erro na impressão de rede do PDF: " + e.getMessage());
    } finally {
      for (Bitmap page : pages) {
        page.recycle();
      }
    }
  }

  /**
   * Varre a(s) subnet(s) locais na porta 9100 atrás de impressoras de rede.
   * Achados chegam via evento `networkPrinterFound`; o fim dispara
   * `networkScanFinished`. Mesma ergonomia do discovery Bluetooth.
   */
  @PluginMethod
  public void scanNetworkPrinters(PluginCall call) {
    try {
      networkScanner.scan(new com.capacitor.yooga.pos.bluetooth.NetworkPrinterScanner.ScanListener() {
        @Override
        public void onPrinterFound(String ip) {
          JSObject device = new JSObject();
          device.put("ip", ip);
          device.put("port", com.capacitor.yooga.pos.bluetooth.TcpEscPosService.DEFAULT_PORT);
          notifyListeners("networkPrinterFound", device);
        }

        @Override
        public void onScanFinished() {
          notifyListeners("networkScanFinished", new JSObject());
        }
      });
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "scanNetworkPrinters erro: " + e.getMessage(), e);
      call.reject(e.getMessage());
    }
  }

  @PluginMethod
  public void stopNetworkScan(PluginCall call) {
    networkScanner.stop();
    call.resolve();
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
   * Busca devices Bluetooth por perto (discovery clássico, ~12s). Devices vão
   * chegando via evento `bluetoothDeviceFound`; o fim da busca dispara
   * `bluetoothDiscoveryFinished`. A UI monta a lista ao vivo.
   */
  @PluginMethod
  public void startBluetoothDiscovery(PluginCall call) {
    if (needsDiscoveryPermission(call)) return;
    try {
      discoveryService().startDiscovery(new com.capacitor.yooga.pos.bluetooth.BluetoothDiscoveryService.DiscoveryListener() {
        @Override
        public void onDeviceFound(String name, String address, boolean isPrinter, boolean bonded) {
          JSObject device = new JSObject();
          device.put("name", name);
          device.put("address", address);
          device.put("isPrinter", isPrinter);
          device.put("bonded", bonded);
          notifyListeners("bluetoothDeviceFound", device);
        }

        @Override
        public void onDiscoveryFinished() {
          notifyListeners("bluetoothDiscoveryFinished", new JSObject());
        }
      });
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "startBluetoothDiscovery erro: " + e.getMessage(), e);
      call.reject(e.getMessage());
    }
  }

  @PluginMethod
  public void stopBluetoothDiscovery(PluginCall call) {
    discoveryService().stopDiscovery();
    call.resolve();
  }

  /**
   * Pareia com o device (createBond): o Android mostra o diálogo de PIN do
   * sistema na hora. Resolve quando o bond completa; rejeita se o usuário
   * recusar ou o PIN falhar. Já pareado resolve imediato.
   */
  @PluginMethod
  public void pairBluetoothDevice(PluginCall call) {
    if (needsBluetoothPermission(call)) return;
    String address = call.getString("address");
    if (address == null || address.isEmpty()) {
      call.reject("address é obrigatório");
      return;
    }
    try {
      discoveryService().pair(address, new com.capacitor.yooga.pos.bluetooth.BluetoothDiscoveryService.PairingListener() {
        @Override
        public void onPaired() {
          call.resolve();
        }

        @Override
        public void onPairingFailed(String reason) {
          call.reject(reason);
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "pairBluetoothDevice erro: " + e.getMessage(), e);
      call.reject(e.getMessage());
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

  /**
   * Permissões do discovery mudaram no Android 12: antes (<= API 30) a busca
   * exige localização (ACCESS_FINE_LOCATION, runtime) — herança do BLE scan;
   * do 12 em diante são BLUETOOTH_SCAN + BLUETOOTH_CONNECT (o CONNECT é para
   * ler nome/bond dos devices achados).
   */
  private boolean needsDiscoveryPermission(PluginCall call) {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
      boolean scanOk = getPermissionState("bluetoothScan") == PermissionState.GRANTED;
      boolean connectOk = getPermissionState("bluetooth") == PermissionState.GRANTED;
      if (scanOk && connectOk) return false;
      requestPermissionForAliases(
        new String[] { "bluetoothScan", "bluetooth" },
        call,
        "bluetoothPermissionCallback"
      );
      return true;
    }
    if (getPermissionState("location") == PermissionState.GRANTED) return false;
    requestPermissionForAlias("location", call, "bluetoothPermissionCallback");
    return true;
  }

  @PermissionCallback
  private void bluetoothPermissionCallback(PluginCall call) {
    boolean granted;
    if ("startBluetoothDiscovery".equals(call.getMethodName())) {
      granted = android.os.Build.VERSION.SDK_INT >= 31
        ? getPermissionState("bluetoothScan") == PermissionState.GRANTED &&
          getPermissionState("bluetooth") == PermissionState.GRANTED
        : getPermissionState("location") == PermissionState.GRANTED;
    } else {
      granted = getPermissionState("bluetooth") == PermissionState.GRANTED;
    }
    if (!granted) {
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
      case "printPdfBluetooth":
        printPdfBluetooth(call);
        break;
      case "printBluetoothText":
        printBluetoothText(call);
        break;
      case "startBluetoothDiscovery":
        startBluetoothDiscovery(call);
        break;
      case "pairBluetoothDevice":
        pairBluetoothDevice(call);
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
