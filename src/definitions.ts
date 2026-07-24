export interface ShowPixOptions {
  /**
   * Conteúdo do QR Code (copia-e-cola PIX) a ser exibido no display.
   */
  value: string;
}

export interface PrintOptions {
  /**
   * HTML completo a ser renderizado em bitmap e impresso na térmica interna.
   */
  html: string;
  /**
   * Quantidade de linhas em branco a avançar antes do corte (default 4).
   */
  cutPaperLength?: number;
  /**
   * Zoom de texto aplicado no WebView que renderiza o HTML (em %, default 100).
   */
  webViewZoom?: number;
  /**
   * Largura do bitmap gerado em pixels (default 384, padrão da térmica 58mm).
   */
  bitmapWidth?: number;
  /**
   * Atraso (ms) antes de medir o conteúdo do WebView (default 0).
   */
  measureDelay?: number;
  /**
   * Atraso (ms) antes de capturar o screenshot do WebView (default 0).
   */
  screenshotDelay?: number;
  /**
   * Se true, falha imediatamente em qualquer erro ao gerar o bitmap.
   */
  strictMode?: boolean;
  /**
   * Timeout total (ms) para conversão HTML -> bitmap (default 30000).
   */
  timeout?: number;
  /**
   * Zoom de texto adicional aplicado no Html2Bitmap.Builder (em %, default 100).
   */
  builderTextZoom?: number;
}

export interface PrintTextOptions {
  /**
   * Texto a ser impresso.
   */
  text: string;
  /**
   * Linhas em branco antes do corte (default 4).
   */
  cutPaperLength?: number;
}

export interface PrintPdfOptions {
  /**
   * Bytes do PDF em base64 (sem prefixo data:). Cada pagina e rasterizada em
   * bitmap e impressa na termica interna. Usado para imprimir o DANFE da NFCe,
   * que so existe em PDF (gerado pelo servico fiscal).
   */
  base64: string;
  /**
   * Quantidade de linhas em branco a avancar antes do corte (default 4).
   */
  cutPaperLength?: number;
  /**
   * Largura do bitmap gerado em pixels (default 384, padrao da termica 58mm;
   * use 576 para 80mm).
   */
  bitmapWidth?: number;
}

import type { PluginListenerHandle } from '@capacitor/core';

export interface DiscoveredBluetoothDevice {
  /**
   * Nome anunciado pelo device (ou o MAC quando o nome ainda não chegou —
   * alguns clones só expõem o nome numa segunda passada do discovery).
   */
  name: string;
  /**
   * Endereço MAC.
   */
  address: string;
  /**
   * True quando a classe Bluetooth do device é IMAGING (impressoras "de
   * verdade"). Clones baratos às vezes reportam classe genérica — use como
   * dica de ordenação, não como filtro.
   */
  isPrinter: boolean;
  /**
   * True se já está pareado com este aparelho.
   */
  bonded: boolean;
}

export interface BluetoothDeviceInfo {
  /**
   * Nome do device pareado (ou o MAC, se o nome não estiver disponível).
   */
  name: string;
  /**
   * Endereço MAC do device (usado como `address` nos métodos de impressão BT).
   */
  address: string;
}

export interface ListBluetoothDevicesResult {
  devices: BluetoothDeviceInfo[];
}

export interface PrintBluetoothOptions extends PrintOptions {
  /**
   * MAC da impressora Bluetooth já pareada no Android (ver listBluetoothDevices).
   */
  address: string;
  /**
   * Linhas em branco a avançar no final (default 4). Térmicas portáteis como a
   * MTP-II não têm guilhotina, então o avanço substitui o corte.
   */
  feedLines?: number;
  /**
   * Heat time do comando ESC 7 em unidades de 10us. Default 0 = NÃO envia o
   * comando: ESC 7 é extensão fora do padrão Epson e quebra o parser de
   * firmwares como o da Trix POS80 (o raster sai impresso como texto). Em
   * impressoras que suportam (ex.: MTP-II), valores maiores deixam a
   * impressão mais escura e um pouco mais lenta (140 foi o valor calibrado).
   */
  heatTime?: number;
  /**
   * Limiar de luminância (0-255) para converter o bitmap em preto e branco
   * (default 200). Maior = mais pixels do anti-aliasing viram preto (letras
   * mais cheias); menor = traço mais fino.
   */
  luminanceThreshold?: number;
}

export interface PrintPdfBluetoothOptions {
  /**
   * MAC da impressora Bluetooth já pareada no Android.
   */
  address: string;
  /**
   * Bytes do PDF em base64 (sem prefixo data:). Cada página é rasterizada e
   * enviada como raster ESC/POS numa única conexão. Usado para o DANFE da
   * NFC-e (gerar com lp=40&quebraQr=true no serviço fiscal para 58mm).
   */
  base64: string;
  /**
   * Largura do bitmap em pixels (default 384 = 58mm; 576 = 80mm).
   */
  bitmapWidth?: number;
  /**
   * Linhas em branco a avançar no final do documento (default 4).
   */
  feedLines?: number;
  /**
   * Heat time do ESC 7 em unidades de 10us (default 0 = não envia; ver
   * PrintBluetoothOptions.heatTime).
   */
  heatTime?: number;
  /**
   * Limiar de luminância 0-255 do raster (default 200).
   */
  luminanceThreshold?: number;
}

export interface DiscoveredNetworkPrinter {
  /**
   * IP da impressora encontrada com a porta 9100 aberta na rede local.
   * Atenção: 9100 aberta não garante térmica (lasers de escritório também
   * escutam) — o teste de impressão após selecionar tira a prova.
   */
  ip: string;
  /**
   * Porta raw de impressão (9100).
   */
  port: number;
}

export interface PrintTcpOptions extends PrintOptions {
  /**
   * IP da impressora de rede.
   */
  ip: string;
  /**
   * Porta raw (default 9100).
   */
  port?: number;
  /**
   * Linhas em branco a avançar no final (default 4).
   */
  feedLines?: number;
  /**
   * Heat time do ESC 7 em unidades de 10us (default 0 = não envia; ver
   * PrintBluetoothOptions.heatTime).
   */
  heatTime?: number;
  /**
   * Limiar de luminância 0-255 do raster (default 200).
   */
  luminanceThreshold?: number;
}

export interface PrintPdfTcpOptions {
  /**
   * IP da impressora de rede.
   */
  ip: string;
  /**
   * Porta raw (default 9100).
   */
  port?: number;
  /**
   * Bytes do PDF em base64 (sem prefixo data:).
   */
  base64: string;
  /**
   * Largura do bitmap em pixels (default 384 = 58mm; 576 = 80mm).
   */
  bitmapWidth?: number;
  /**
   * Linhas em branco a avançar no final (default 4).
   */
  feedLines?: number;
  /**
   * Heat time do ESC 7 (default 0 = não envia; ver
   * PrintBluetoothOptions.heatTime).
   */
  heatTime?: number;
  /**
   * Limiar de luminância 0-255 (default 200).
   */
  luminanceThreshold?: number;
}

export interface PrintBluetoothTextOptions {
  /**
   * MAC da impressora Bluetooth já pareada no Android.
   */
  address: string;
  /**
   * Texto ASCII de teste (acentuação depende do codepage do firmware; não usar
   * para recibos reais — prefira printBluetooth, que envia bitmap).
   */
  text?: string;
  /**
   * Linhas em branco a avançar no final (default 4).
   */
  feedLines?: number;
}

export interface CapacitorYoogaPosPlugin {
  /**
   * Mostra a logo padrão da Yooga no display traseiro do terminal
   * (Elgin PIX4 / TPro / iMin D1 / iMin M10).
   */
  showLogoOnDisplay(): Promise<void>;

  /**
   * Mostra um QR Code PIX no display traseiro do terminal.
   */
  showPix(options: ShowPixOptions): Promise<void>;

  /**
   * Renderiza o HTML em bitmap e imprime na impressora térmica interna do
   * terminal Elgin (M8/M10).
   */
  print(options: PrintOptions): Promise<void>;

  /**
   * Imprime texto simples na impressora térmica interna (sem bitmap).
   * Útil para testes rápidos de comunicação com a impressora.
   */
  printText(options: PrintTextOptions): Promise<void>;

  /**
   * Rasteriza cada pagina de um PDF (base64) em bitmap e imprime na termica
   * interna. Pensado para o DANFE da NFCe, que so existe em PDF.
   */
  printPdf(options: PrintPdfOptions): Promise<void>;

  /**
   * Lista os devices Bluetooth já pareados no Android (o pareamento é feito nas
   * configurações do sistema). No Android 12+ dispara o prompt de permissão
   * BLUETOOTH_CONNECT na primeira chamada.
   */
  listBluetoothDevices(): Promise<ListBluetoothDevicesResult>;

  /**
   * Renderiza o HTML em bitmap (mesmo pipeline do print interno) e envia como
   * raster ESC/POS para uma térmica Bluetooth genérica via SPP/RFCOMM
   * (ex.: MTP-II 58mm — bitmapWidth 384, o default).
   */
  printBluetooth(options: PrintBluetoothOptions): Promise<void>;

  /**
   * Rasteriza cada página de um PDF (base64) e envia como raster ESC/POS para
   * a térmica Bluetooth numa única conexão. Pensado para o DANFE da NFC-e.
   */
  printPdfBluetooth(options: PrintPdfBluetoothOptions): Promise<void>;

  /**
   * Teste rápido de comunicação com a térmica Bluetooth (texto ASCII puro).
   */
  printBluetoothText(options: PrintBluetoothTextOptions): Promise<void>;

  /**
   * Inicia o discovery clássico (~12s, encerrado pelo sistema). Devices
   * chegam via evento `bluetoothDeviceFound`; o término dispara
   * `bluetoothDiscoveryFinished`. No Android 12+ pede BLUETOOTH_SCAN+CONNECT;
   * no 11- pede localização (exigência do SO para discovery).
   */
  startBluetoothDiscovery(): Promise<void>;

  /**
   * Cancela um discovery em andamento (ex.: usuário fechou o modal de busca).
   */
  stopBluetoothDiscovery(): Promise<void>;

  /**
   * Pareia com o device: o Android mostra o diálogo de PIN do sistema.
   * Resolve quando o bond completa; rejeita em recusa/PIN errado. Já pareado
   * resolve imediato.
   */
  pairBluetoothDevice(options: { address: string }): Promise<void>;

  /**
   * Renderiza o HTML em bitmap e envia como raster ESC/POS para uma térmica
   * de rede via TCP (porta raw 9100). Contraparte Ethernet/Wi-Fi do
   * printBluetooth — mesmo motor, outro transporte.
   */
  printTcp(options: PrintTcpOptions): Promise<void>;

  /**
   * Rasteriza um PDF (base64) e imprime na térmica de rede numa única
   * conexão. Contraparte TCP do printPdfBluetooth (DANFE da NFC-e).
   */
  printPdfTcp(options: PrintPdfTcpOptions): Promise<void>;

  /**
   * Varre a(s) subnet(s) locais na porta 9100 atrás de impressoras de rede
   * (sweep /24 em poucos segundos). Achados chegam via evento
   * `networkPrinterFound`; o fim dispara `networkScanFinished`.
   */
  scanNetworkPrinters(): Promise<void>;

  /**
   * Cancela uma varredura de rede em andamento.
   */
  stopNetworkScan(): Promise<void>;

  addListener(
    eventName: 'bluetoothDeviceFound',
    listenerFunc: (device: DiscoveredBluetoothDevice) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'bluetoothDiscoveryFinished',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'networkPrinterFound',
    listenerFunc: (printer: DiscoveredNetworkPrinter) => void,
  ): Promise<PluginListenerHandle>;

  addListener(
    eventName: 'networkScanFinished',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
