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
}
