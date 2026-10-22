# AudioDL

App Android sencilla que descarga el audio de un video como MP3 usando
[yt-dlp](https://github.com/yt-dlp/yt-dlp) (vía `youtubedl-android`) y lo guarda en
**Música › AudioDL** del teléfono.

> **Uso personal.** Esta app es solo para descargar contenido propio o con licencia
> libre (Creative Commons, dominio público, etc.). Descargar contenido protegido sin
> permiso puede infringir los derechos de autor y los términos de servicio de YouTube.

## Instalar

1. Entra desde el celular a la pestaña **Releases** de este repositorio.
2. Descarga `app-debug.apk` del release más reciente.
3. Ábrelo y permite *instalar apps de fuentes desconocidas* cuando Android lo pida.

Cada push a `main` compila un APK nuevo con GitHub Actions y lo publica como
release `build-N`. Como el APK se firma con una clave de depuración distinta en
cada compilación, para actualizar puede ser necesario desinstalar la versión anterior.

## Usar

- Pega el enlace y pulsa **Descargar MP3**, o desde la app de YouTube usa
  **Compartir › AudioDL**.
- Si las descargas empiezan a fallar, pulsa **Actualizar yt-dlp**.

Requisitos: Android 10 o superior (ARM de 32 o 64 bits).
