# capacitor-yooga-pos

Capacitor plugin para integração com hardware POS Elgin (linha PIX4 / TPro /
M8 / M10) e iMin (D1 / M10 com display traseiro estilo TPro).

Funcionalidades atuais:

- `showLogoOnDisplay()` — exibe a logo Yooga no display traseiro.
- `showPix({ value })` — exibe um QR Code PIX no display traseiro.
- `print({ html, ... })` — renderiza HTML em bitmap e imprime na térmica
  interna do terminal (compatível com Elgin M8/M10).

No iOS e na web os métodos são no-op, pois o hardware não existe nessas
plataformas.

## Install

```bash
npm install capacitor-yooga-pos
npx cap sync
```

## Setup adicional no Android (obrigatório)

A SDK da Elgin é distribuída como AARs locais (`android/libs/*.aar`). O AGP 8
**não permite** que uma `android-library` empacote AARs locais (o build falha
com _"Direct local .aar file dependencies are not supported when building an
AAR"_), então este plugin declara esses AARs como `compileOnly` e o **app
consumidor** precisa empacotá-los.

Cole no `android/app/build.gradle` do seu app Capacitor:

```gradle
repositories {
    flatDir {
        dirs '../capacitor-cordova-android-plugins/src/main/libs',
             'libs',
             '../../node_modules/capacitor-yooga-pos/android/libs'
    }
}

dependencies {
    // AARs da Elgin vendorados em capacitor-yooga-pos.
    // AGP 8 não permite empacotar AARs dentro de uma android-library, então
    // o plugin declara como compileOnly e o app empacota aqui.
    implementation(name: 'e1-V02.23.03-release',         ext: 'aar')
    implementation(name: 'minipdvm8-v01.00.00-release',  ext: 'aar')
    implementation(name: 'display-v02.00.00-release',    ext: 'aar')
}
```

### Workaround Capacitor 6 + AGP 8 (`testClasses`)

O sync do Android Studio com Capacitor 6 + AGP 8 pede `:testClasses` em todos
os subprojetos, mas `:capacitor-cordova-android-plugins` não tem fontes de
teste. Adicione no `android/build.gradle` (root) do app:

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
    }
    afterEvaluate { project ->
        if (!project.tasks.findByName('testClasses')) {
            project.tasks.register('testClasses')
        }
    }
}
```

## Uso

```ts
import { CapacitorYoogaPos } from 'capacitor-yooga-pos';

await CapacitorYoogaPos.showLogoOnDisplay();

await CapacitorYoogaPos.showPix({
  value: '00020126...PIX-COPIA-E-COLA...6304ABCD',
});

await CapacitorYoogaPos.print({
  html: '<html><body style="font-family:monospace">Cupom...</body></html>',
});
```

## API

<docgen-index>

* [`showLogoOnDisplay()`](#showlogoondisplay)
* [`showPix(...)`](#showpix)
* [`print(...)`](#print)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### showLogoOnDisplay()

```typescript
showLogoOnDisplay() => Promise<void>
```

Mostra a logo padrão da Yooga no display traseiro do terminal
(Elgin PIX4 / TPro / iMin D1 / iMin M10).

--------------------

### showPix(...)

```typescript
showPix(options: ShowPixOptions) => Promise<void>
```

Mostra um QR Code PIX no display traseiro do terminal.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#showpixoptions">ShowPixOptions</a></code> |

--------------------

### print(...)

```typescript
print(options: PrintOptions) => Promise<void>
```

Renderiza o HTML em bitmap e imprime na impressora térmica interna do
terminal Elgin (M8/M10).

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#printoptions">PrintOptions</a></code> |

--------------------

### Interfaces

#### ShowPixOptions

| Prop        | Type                | Description                                                          |
| ----------- | ------------------- | -------------------------------------------------------------------- |
| **`value`** | <code>string</code> | Conteúdo do QR Code (copia-e-cola PIX) a ser exibido no display.     |

#### PrintOptions

| Prop                   | Type                 | Description                                                                                  |
| ---------------------- | -------------------- | -------------------------------------------------------------------------------------------- |
| **`html`**             | <code>string</code>  | HTML completo a ser renderizado em bitmap e impresso na térmica interna.                     |
| **`cutPaperLength`**   | <code>number</code>  | Quantidade de linhas em branco a avançar antes do corte (default 4).                         |
| **`webViewZoom`**      | <code>number</code>  | Zoom de texto aplicado no WebView que renderiza o HTML (em %, default 100).                  |
| **`bitmapWidth`**      | <code>number</code>  | Largura do bitmap gerado em pixels (default 384, padrão da térmica 58mm).                    |
| **`measureDelay`**     | <code>number</code>  | Atraso (ms) antes de medir o conteúdo do WebView (default 0).                                |
| **`screenshotDelay`**  | <code>number</code>  | Atraso (ms) antes de capturar o screenshot do WebView (default 0).                           |
| **`strictMode`**       | <code>boolean</code> | Se true, falha imediatamente em qualquer erro ao gerar o bitmap.                             |
| **`timeout`**          | <code>number</code>  | Timeout total (ms) para conversão HTML -> bitmap (default 30000).                            |
| **`builderTextZoom`**  | <code>number</code>  | Zoom de texto adicional aplicado no Html2Bitmap.Builder (em %, default 100).                 |

</docgen-api>
