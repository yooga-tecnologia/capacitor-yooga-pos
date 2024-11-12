package com.capacitor.yooga.pos;

import android.graphics.Bitmap;
import android.webkit.WebView;

import com.capacitor.yooga.pos.elgin.Services.Pix4.Pix4Service;
import com.capacitor.yooga.pos.elgin.Services.PrinterService;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.izettle.html2bitmap.Html2Bitmap;
import com.izettle.html2bitmap.Html2BitmapConfigurator;
import com.izettle.html2bitmap.content.WebViewContent;

import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "CapacitorYoogaPos")
public class CapacitorYoogaPosPlugin extends Plugin {

    private CapacitorYoogaPos implementation = new CapacitorYoogaPos();

  @PluginMethod
  public void showLogoOnDisplay(PluginCall call) {
    Pix4Service p = new Pix4Service(getActivity());

    p.apresentaImagemYooga();
  }

  @PluginMethod
  public void showPix(PluginCall call) {
    String value = call.getString("value");
    Pix4Service p = new Pix4Service(getActivity());
    p.apresentaPix(value);
  }

  @PluginMethod
  public void print(PluginCall call) {
    String html = call.getString("html");
    Integer cutPaperLength = call.getInt("cutPaperLength");
    Integer webViewZoom = call.getInt("webViewZoom");
    Integer bitmapWidth = call.getInt("bitmapWidth");
    Integer measureDelay = call.getInt("measureDelay");
    Integer screenshotDelay = call.getInt("screenshotDelay");
    Boolean strictMode = call.getBoolean("strictMode");
    Integer timeout = call.getInt("timeout");
    Integer builderTextZoom = call.getInt("builderTextZoom");

    PrinterService printerService = new PrinterService(getActivity());
    printerService.printerInternalImpStart();
    Map<String, Object> map = new HashMap<>();

    map.put("quant", cutPaperLength);

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

    printerService.imprimeImagem(bitmap);

    printerService.cutPaper(map);
    printerService.printerStop();
  }

    @PluginMethod
    public void echo(PluginCall call) {
      String value = call.getString("value");
      Integer cutPaperLength = call.getInt("cutPaperLength");
      Integer webViewZoom = call.getInt("webViewZoom");


        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }
}
