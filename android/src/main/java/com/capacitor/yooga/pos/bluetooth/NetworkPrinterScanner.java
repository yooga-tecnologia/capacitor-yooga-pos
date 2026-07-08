package com.capacitor.yooga.pos.bluetooth;

import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Varredura da(s) subnet(s) locais na porta 9100 (raw printing) atrás de
 * impressoras de rede. Sweep /24 com timeouts curtos e alto paralelismo:
 * ~254 hosts em poucos segundos. Porta 9100 aberta não garante que é uma
 * térmica (uma laser de escritório também escuta ali) — a UI trata isso com
 * o teste de impressão logo após selecionar.
 */
public class NetworkPrinterScanner {

  private static final String TAG = "YoogaNetScan";
  private static final int PORT = TcpEscPosService.DEFAULT_PORT;
  private static final int CONNECT_TIMEOUT_MS = 400;
  private static final int PARALLELISM = 64;

  public interface ScanListener {
    void onPrinterFound(String ip);
    void onScanFinished();
  }

  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile ExecutorService executor;

  public synchronized void scan(ScanListener listener) throws IOException {
    stop();
    List<String> prefixes = localSubnetPrefixes();
    if (prefixes.isEmpty()) {
      throw new IOException("Nenhuma rede local encontrada (o aparelho está no Wi-Fi?)");
    }
    running.set(true);
    executor = Executors.newFixedThreadPool(PARALLELISM);
    ExecutorService pool = executor;

    int totalHosts = prefixes.size() * 254;
    AtomicInteger pending = new AtomicInteger(totalHosts);
    Log.d(TAG, "varrendo " + prefixes + " (" + totalHosts + " hosts) na porta " + PORT);

    for (String prefix : prefixes) {
      for (int host = 1; host <= 254; host++) {
        String ip = prefix + host;
        pool.execute(() -> {
          if (running.get() && isPortOpen(ip)) {
            listener.onPrinterFound(ip);
          }
          if (pending.decrementAndGet() == 0 && running.compareAndSet(true, false)) {
            listener.onScanFinished();
          }
        });
      }
    }
    pool.shutdown();
  }

  public synchronized void stop() {
    running.set(false);
    if (executor != null) {
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      executor = null;
    }
  }

  private boolean isPortOpen(String ip) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(ip, PORT), CONNECT_TIMEOUT_MS);
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  /**
   * Prefixos /24 ("192.168.0.") das interfaces IPv4 privadas ativas do
   * aparelho — normalmente só o Wi-Fi. Subnets maiores que /24 são varridas
   * apenas no /24 do próprio IP (custo x benefício: impressora e tablet quase
   * sempre dividem o mesmo /24).
   */
  private List<String> localSubnetPrefixes() {
    List<String> prefixes = new ArrayList<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
        for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
          if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
          String ip = address.getHostAddress();
          if (ip == null) continue;
          String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
          if (!prefixes.contains(prefix)) {
            prefixes.add(prefix);
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "erro enumerando interfaces: " + e.getMessage(), e);
    }
    return prefixes;
  }
}
