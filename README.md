# El Sebastián DJ (AudioDL)

App personal para Android y Windows para extraer el audio de un video como MP3 con
[yt-dlp](https://github.com/yt-dlp/yt-dlp) (vía `youtubedl-android`), escucharlo con su
propio reproductor y recortarlo. Todo se guarda en **Música › AudioDL**.

> **Uso personal.** Esta app es solo para descargar contenido propio o con licencia
> libre (Creative Commons, dominio público, etc.). Descargar contenido protegido sin
> permiso puede infringir los derechos de autor y los términos de servicio de YouTube.

## Funciones

- **Descargar**: pega un enlace (o usa *Compartir › AudioDL* desde YouTube) y pulsa
  *Descargar MP3*.
- **Biblioteca**: reproductor con barra de avance, saltos de 10 s y lista de todos los audios.
- **Editor**: forma de onda con tiradores para elegir el tramo, ajuste fino de ±0,1 s / ±1 s,
  vista previa del recorte y fundido suave opcional. El recorte se guarda como un MP3 nuevo;
  el original no se modifica.
- **Actualizar yt-dlp**: si las descargas empiezan a fallar, actualiza el motor desde la app.
- **Actualizar la app**: busca el release más reciente en GitHub, descarga el APK y abre el
  instalador. La primera vez Android pide activar *Permitir desde esta fuente*.

## Instalar

1. Entra desde el celular a la pestaña **Releases** de este repositorio.
2. Descarga `app-debug.apk` del release más reciente.
3. Ábrelo y permite *instalar apps de fuentes desconocidas* cuando Android lo pida.

Cada push a `main` compila un APK nuevo con GitHub Actions y lo publica como release
`build-N`. Desde la versión 2.1 todas las compilaciones se firman con la misma clave
(`app/audiodl.jks`), así que las actualizaciones se instalan encima sin desinstalar: basta
con tocar **Actualizar la app**. Si vienes de una versión anterior a la 2.1, desinstálala
una última vez antes de instalar esta.

La Biblioteca pide permiso para leer audio solo para mostrar archivos guardados por una
instalación anterior; si lo niegas, igual verás los que descargues de ahora en adelante.

Requisitos: Android 10 o superior (ARM de 32 o 64 bits).

## Versión para PC (Windows)

La misma app para Windows 10/11 de 64 bits, con descarga de MP3, biblioteca con
reproductor (barra de avance, ±10 s, anterior/siguiente, volumen) y editor de recortes con
forma de onda. Los audios se guardan en **Música › AudioDL** de tu PC.

1. Entra a la pestaña **Releases** y descarga `ElSebastianDJ-Setup.exe` del release más reciente.
2. Ábrelo. Si Windows muestra *Windows protegió su PC*, toca *Más información › Ejecutar de
   todas formas* (pasa porque el instalador no está firmado).
3. Se instala solo para tu usuario, sin pedir permisos de administrador.

Para actualizar, pulsa **Actualizar la app**: descarga el instalador nuevo, cierra la app,
la actualiza y la vuelve a abrir. Atajos: *Espacio* reproduce o pausa; puedes arrastrar un
enlace sobre la ventana.

El código está en `desktop/` (Python + PySide6). GitHub Actions lo compila con PyInstaller,
le agrega yt-dlp, FFmpeg y Deno, prueba el `.exe` sin pantalla y arma el instalador con
Inno Setup. El APK y el instalador se publican juntos en cada release `build-N`.
