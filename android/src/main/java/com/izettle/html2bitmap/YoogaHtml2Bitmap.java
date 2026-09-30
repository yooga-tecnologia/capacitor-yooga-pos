package com.izettle.html2bitmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;

import com.izettle.html2bitmap.content.WebViewContent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Substitui o Html2Bitmap.getBitmap() da lib (1.9), que vaza recursos a cada
 * chamada: cria um Executors.newFixedThreadPool(1) que nunca recebe shutdown
 * (uma thread presa por impressão) e nunca chama WebView.destroy(). Em dia de
 * movimento isso acumula centenas de threads e WebViews no processo do app.
 * Além disso a lib interpreta o timeout em SEGUNDOS, então os 30000 que o app
 * manda (ms) viravam ~8h: um render travado segurava a thread de plugins do
 * Capacitor indefinidamente.
 *
 * Fica neste pacote de propósito: reaproveita o Html2BitmapWebView da lib
 * (package-private), então o render é idêntico — só o ciclo de vida muda.
 * Deve ser chamado fora da main thread, como o getBitmap() original.
 */
public final class YoogaHtml2Bitmap {

  private static final String TAG = "YoogaHtml2Bitmap";

  // No timeout a thread da lib pode estar no meio do WebView.draw(); espera
  // ela terminar antes de destruir o WebView.
  private static final long DESTROY_DELAY_AFTER_TIMEOUT_MS = 10_000;

  private YoogaHtml2Bitmap() {}

  /** Retorna o bitmap renderizado, ou null em caso de erro ou timeout. */
  public static Bitmap render(
    Context context,
    WebViewContent content,
    int bitmapWidth,
    int measureDelay,
    int screenshotDelay,
    boolean strictMode,
    long timeoutMs,
    Integer textZoom,
    final Html2BitmapConfigurator configurator
  ) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Bitmap> result = new AtomicReference<>();
    final AtomicBoolean abandoned = new AtomicBoolean(false);
    final AtomicReference<WebView> webViewRef = new AtomicReference<>();

    final BitmapCallback callback = new BitmapCallback() {
      @Override
      public void finished(Bitmap bitmap) {
        if (abandoned.get()) {
          if (bitmap != null) bitmap.recycle();
          return;
        }
        result.set(bitmap);
        latch.countDown();
      }

      @Override
      public void error(Throwable error) {
        Log.e(TAG, "erro ao renderizar HTML", error);
        latch.countDown();
      }
    };

    // A lib só expõe o WebView pelo configurator; guardamos a referência para
    // poder destruí-lo no final.
    final Html2BitmapConfigurator capturingConfigurator = new Html2BitmapConfigurator() {
      @Override
      public void configureWebView(WebView webView) {
        webViewRef.set(webView);
        if (configurator != null) {
          configurator.configureWebView(webView);
        }
      }
    };

    final Html2BitmapWebView html2BitmapWebView = new Html2BitmapWebView(
      context,
      content,
      bitmapWidth,
      measureDelay,
      screenshotDelay,
      strictMode,
      textZoom,
      capturingConfigurator
    );

    final Handler mainHandler = new Handler(context.getMainLooper());
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          html2BitmapWebView.load(callback);
        } catch (Throwable t) {
          callback.error(t);
        }
      }
    });

    boolean completed = false;
    try {
      completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      if (!completed) {
        Log.e(TAG, "timeout de " + timeoutMs + "ms ao renderizar HTML");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Log.e(TAG, "render interrompido", e);
    }

    if (!completed) {
      abandoned.set(true);
    }

    final Runnable stop = new Runnable() {
      @Override
      public void run() {
        try {
          html2BitmapWebView.cleanup();
        } catch (Throwable t) {
          Log.w(TAG, "cleanup falhou", t);
        }
      }
    };
    final Runnable destroy = new Runnable() {
      @Override
      public void run() {
        WebView webView = webViewRef.getAndSet(null);
        if (webView == null) return;
        try {
          webView.destroy();
        } catch (Throwable t) {
          Log.w(TAG, "destroy do WebView falhou", t);
        }
      }
    };

    mainHandler.post(stop);
    if (completed) {
      mainHandler.post(destroy);
    } else {
      mainHandler.postDelayed(destroy, DESTROY_DELAY_AFTER_TIMEOUT_MS);
    }

    // Se o callback chegou entre o timeout e o abandoned.set(true), o bitmap
    // ficou em result e é devolvido normalmente.
    return result.get();
  }
}
