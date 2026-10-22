# El Sebastián DJ (AudioDL)

App Android personal para extraer el audio de un video como MP3 con
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

## Instalar

1. Entra desde el celular a la pestaña **Releases** de este repositorio.
2. Descarga `app-debug.apk` del release más reciente.
3. Ábrelo y permite *instalar apps de fuentes desconocidas* cuando Android lo pida.

Cada push a `main` compila un APK nuevo con GitHub Actions y lo publica como release
`build-N`. Como cada compilación usa una clave de depuración distinta, para actualizar
puede ser necesario desinstalar la versión anterior.

La Biblioteca pide permiso para leer audio solo para mostrar archivos guardados por una
instalación anterior; si lo niegas, igual verás los que descargues de ahora en adelante.

Requisitos: Android 10 o superior (ARM de 32 o 64 bits).
