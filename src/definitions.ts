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
}
