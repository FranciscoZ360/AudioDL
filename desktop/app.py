"""El Sebastián DJ para PC (Windows): descargar MP3, biblioteca con reproductor y editor."""

from __future__ import annotations

import os
import subprocess
import sys
import zlib
from pathlib import Path
from typing import Callable, Optional

from PySide6.QtCore import (
    QObject, QPoint, QRect, QRectF, QRunnable, QSize, Qt, QThreadPool, QTimer, QUrl, Signal,
)
from PySide6.QtGui import (
    QColor, QDesktopServices, QFont, QFontMetrics, QGuiApplication, QIcon,
    QLinearGradient, QPainter, QPainterPath, QPen, QPixmap,
)
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtWidgets import (
    QApplication, QButtonGroup, QCheckBox, QFrame, QHBoxLayout, QInputDialog, QLabel,
    QLineEdit, QListWidget, QListWidgetItem, QMainWindow, QMenu, QMessageBox, QProgressBar,
    QPushButton, QScrollArea, QSizePolicy, QSlider, QStackedWidget, QVBoxLayout, QWidget,
)

import core
from core import Track

# ---------------------------------------------------------------------------
# Colores (los mismos de la app Android)
# ---------------------------------------------------------------------------

BG = "#09080F"
SURFACE = "#15131F"
SURFACE_2 = "#1F1C2D"
STROKE = "#2C293E"
TEXT = "#F6F5FB"
TEXT_SOFT = "#C9C6D8"
MUTED = "#9E9AB4"
EYEBROW = "#9D8CFF"
GRAD_START = "#7B5CFF"
GRAD_END = "#E45BC6"
GREEN = "#3DDC97"
AMBER = "#FFB547"
RED = "#FF5C7A"
WAVE_OFF = "#3A3652"

TILES = [
    ("#7B5CFF", "#E45BC6"), ("#3E7BFA", "#7B5CFF"), ("#FF7A59", "#E45BC6"),
    ("#1FC8A9", "#3E7BFA"), ("#FFB547", "#FF5C7A"),
]
GRAD_CSS = f"qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {GRAD_START}, stop:1 {GRAD_END})"

STYLE = f"""
* {{ font-family: "Segoe UI", "Segoe UI Variable", sans-serif; color: {TEXT}; }}
QMainWindow, #root {{ background: {BG}; }}
QToolTip {{ background: {SURFACE_2}; color: {TEXT}; border: 1px solid {STROKE}; padding: 6px; }}
#sidebar {{ background: #0D0C15; border-right: 1px solid {STROKE}; }}
QPushButton#nav {{
    text-align: left; padding: 12px 18px; border-radius: 14px; border: none;
    background: transparent; color: {TEXT_SOFT}; font-size: 15px; font-weight: 600;
}}
QPushButton#nav:hover {{ background: #17151F; }}
QPushButton#nav:checked {{ background: {SURFACE_2}; color: {TEXT}; }}
QFrame#card {{ background: rgba(21, 19, 31, 235); border: 1px solid {STROKE}; border-radius: 22px; }}
QFrame#playerbar {{ background: #0D0C15; border-top: 1px solid {STROKE}; }}
QLineEdit {{
    background: {SURFACE_2}; border: 1px solid {SURFACE_2}; border-radius: 16px;
    padding: 13px 16px; font-size: 15px; selection-background-color: {GRAD_START};
}}
QLineEdit:focus {{ border: 1px solid {GRAD_START}; }}
QLineEdit:disabled {{ color: {MUTED}; }}
QPushButton {{ outline: none; }}
QPushButton[kind="primary"] {{
    background: {GRAD_CSS}; border: none; border-radius: 28px;
    padding: 16px 30px; font-size: 16px; font-weight: 700;
}}
QPushButton[kind="primary"]:hover {{ background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #8A6EFF, stop:1 #EC6FD0); }}
QPushButton[kind="primary"]:disabled {{ background: {STROKE}; color: {MUTED}; }}
QPushButton[kind="secondary"] {{
    background: {SURFACE_2}; border: 1px solid {STROKE}; border-radius: 21px;
    padding: 10px 18px; font-size: 14px; font-weight: 600;
}}
QPushButton[kind="secondary"]:hover {{ background: #28243A; }}
QPushButton[kind="secondary"]:disabled {{ color: #6B6785; }}
QPushButton[kind="ghost"] {{
    background: transparent; border: none; color: {EYEBROW};
    padding: 8px 14px; font-size: 14px; font-weight: 600;
}}
QPushButton[kind="ghost"]:hover {{ color: {TEXT}; }}
QPushButton[kind="ghost"]:disabled {{ color: #6B6785; }}
QPushButton[kind="round"] {{
    background: {SURFACE_2}; border: 1px solid {STROKE}; border-radius: 20px;
    min-width: 40px; max-width: 40px; min-height: 40px; max-height: 40px;
    font-size: 13px; font-weight: 700; padding: 0;
}}
QPushButton[kind="round"]:hover {{ background: #28243A; }}
QPushButton[kind="round"]:disabled {{ color: #6B6785; }}
QPushButton[kind="play"] {{
    background: {GRAD_CSS}; border: none; border-radius: 24px;
    min-width: 48px; max-width: 48px; min-height: 48px; max-height: 48px;
    font-size: 16px; font-weight: 700; padding: 0;
}}
QPushButton[kind="play"]:disabled {{ background: {STROKE}; color: {MUTED}; }}
QPushButton[kind="chip"] {{
    background: {SURFACE_2}; border: 1px solid {STROKE}; border-radius: 15px;
    padding: 6px 12px; font-size: 13px; font-weight: 600; color: {TEXT_SOFT};
}}
QPushButton[kind="chip"]:hover {{ background: #28243A; color: {TEXT}; }}
QPushButton[kind="chip"]:disabled {{ color: #6B6785; }}
QProgressBar {{ background: {SURFACE_2}; border: none; border-radius: 4px; max-height: 8px; min-height: 8px; }}
QProgressBar::chunk {{ border-radius: 4px; background: {GRAD_CSS}; }}
QSlider::groove:horizontal {{ height: 4px; background: {STROKE}; border-radius: 2px; }}
QSlider::sub-page:horizontal {{ background: {GRAD_CSS}; border-radius: 2px; }}
QSlider::handle:horizontal {{ background: white; width: 14px; height: 14px; margin: -5px 0; border-radius: 7px; }}
QSlider::handle:horizontal:disabled {{ background: {STROKE}; }}
QListWidget {{ background: transparent; border: none; outline: none; }}
QListWidget::item {{ border-radius: 16px; margin: 1px 0; }}
QListWidget::item:hover {{ background: #13111C; }}
QListWidget::item:selected {{ background: {SURFACE_2}; }}
QScrollArea {{ background: transparent; border: none; }}
QScrollBar:vertical {{ background: transparent; width: 10px; margin: 4px 2px; }}
QScrollBar::handle:vertical {{ background: {STROKE}; border-radius: 3px; min-height: 30px; }}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0; }}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{ background: none; }}
QMenu {{ background: {SURFACE}; border: 1px solid {STROKE}; border-radius: 10px; padding: 6px; }}
QMenu::item {{ padding: 8px 22px; border-radius: 6px; }}
QMenu::item:selected {{ background: #28243A; }}
QMessageBox, QInputDialog {{ background: {SURFACE}; }}
QMessageBox QPushButton, QInputDialog QPushButton {{
    background: {SURFACE_2}; border: 1px solid {STROKE}; border-radius: 14px;
    padding: 8px 18px; min-width: 70px; font-weight: 600;
}}
"""


# ---------------------------------------------------------------------------
# Tareas en segundo plano
# ---------------------------------------------------------------------------

class _Signals(QObject):
    done = Signal(object)
    failed = Signal(str)
    progress = Signal(object, object)


class _Job(QRunnable):
    def __init__(self, fn: Callable, args: tuple, with_progress: bool):
        super().__init__()
        self.fn, self.args, self.with_progress = fn, args, with_progress
        self.s = _Signals()
        self.setAutoDelete(False)

    def run(self):
        try:
            if self.with_progress:
                result = self.fn(self.s.progress.emit, *self.args)
            else:
                result = self.fn(*self.args)
        except Exception as e:  # noqa: BLE001 - se muestra al usuario
            self.s.failed.emit(str(e) or e.__class__.__name__)
        else:
            self.s.done.emit(result)


_jobs: set = set()


def run_job(fn: Callable, *args, done=None, failed=None, progress=None) -> None:
    job = _Job(fn, args, progress is not None)
    _jobs.add(job)
    if progress:
        job.s.progress.connect(progress)
    if done:
        job.s.done.connect(done)
    if failed:
        job.s.failed.connect(failed)
    job.s.done.connect(lambda *_: _jobs.discard(job))
    job.s.failed.connect(lambda *_: _jobs.discard(job))
    QThreadPool.globalInstance().start(job)


# ---------------------------------------------------------------------------
# Piezas de interfaz
# ---------------------------------------------------------------------------

def text(s: str, size: int = 14, color: str = TEXT, bold: bool = False, wrap: bool = True) -> QLabel:
    lab = QLabel(s)
    lab.setWordWrap(wrap)
    weight = 700 if bold else 400
    lab.setStyleSheet(f"color: {color}; font-size: {size}px; font-weight: {weight}; background: transparent;")
    return lab


def headline(s: str, size: int = 34) -> QLabel:
    lab = QLabel(s)
    lab.setWordWrap(True)
    lab.setStyleSheet(f"color: {TEXT}; font-size: {size}px; font-weight: 800; background: transparent;")
    return lab


def eyebrow(s: str) -> QLabel:
    lab = QLabel(s.upper())
    lab.setStyleSheet(f"color: {EYEBROW}; font-size: 12px; font-weight: 800; letter-spacing: 2px; background: transparent;")
    return lab


def label(s: str) -> QLabel:
    lab = QLabel(s.upper())
    lab.setStyleSheet(f"color: {MUTED}; font-size: 11px; font-weight: 700; letter-spacing: 1.5px; background: transparent;")
    return lab


def button(s: str, kind: str = "secondary", tip: str = "") -> QPushButton:
    b = QPushButton(s)
    b.setProperty("kind", kind)
    b.setCursor(Qt.CursorShape.PointingHandCursor)
    b.setFocusPolicy(Qt.FocusPolicy.NoFocus)  # así Espacio no vuelve a "clicar" el último botón
    if tip:
        b.setToolTip(tip)
    return b


def card(spacing: int = 12) -> tuple[QFrame, QVBoxLayout]:
    f = QFrame()
    f.setObjectName("card")
    lay = QVBoxLayout(f)
    lay.setContentsMargins(22, 20, 22, 20)
    lay.setSpacing(spacing)
    return f, lay


def hbox(*widgets, spacing: int = 10, stretch_last: bool = False) -> QHBoxLayout:
    lay = QHBoxLayout()
    lay.setSpacing(spacing)
    lay.setContentsMargins(0, 0, 0, 0)
    for w in widgets:
        if w is None:
            lay.addStretch(1)
        elif isinstance(w, QWidget):
            lay.addWidget(w)
        else:
            lay.addLayout(w)
    return lay


class StatusChip(QLabel):
    def __init__(self):
        super().__init__()
        self.setStyleSheet(
            f"background: rgba(21,19,31,220); border: 1px solid {STROKE}; border-radius: 15px;"
            f"padding: 6px 14px; font-size: 13px; font-weight: 600; color: {TEXT_SOFT};"
        )
        self.setSizePolicy(QSizePolicy.Policy.Maximum, QSizePolicy.Policy.Fixed)

    def set(self, msg: str, color: str):
        self.setText(f"<span style='color:{color}'>●</span>&nbsp;&nbsp;{msg}")


class ElidedLabel(QWidget):
    """Texto de una línea que se corta con «…» si no cabe."""

    def __init__(self, s: str = "", size: int = 14, color: str = TEXT, bold: bool = False):
        super().__init__()
        self._text = s
        self._color = QColor(color)
        f = QFont(self.font())
        f.setPixelSize(size)
        f.setBold(bold)
        self.setFont(f)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.setFixedHeight(QFontMetrics(f).height() + 2)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)

    def setText(self, s: str):
        self._text = s
        self.setToolTip(s)
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setPen(self._color)
        p.setFont(self.font())
        el = self.fontMetrics().elidedText(self._text, Qt.TextElideMode.ElideRight, self.width())
        p.drawText(self.rect(), int(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft), el)


class ArtTile(QWidget):
    """Cuadro con degradado e iniciales, como la carátula de la app Android."""

    def __init__(self, size: int = 48, radius: int = 14):
        super().__init__()
        self.setFixedSize(size, size)
        self.radius = radius
        self.title = ""
        self.error = False

    def set_title(self, title: str):
        self.title, self.error = title, False
        self.update()

    def set_error(self):
        self.error = True
        self.update()

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        r = QRectF(self.rect())
        path = QPainterPath()
        path.addRoundedRect(r, self.radius, self.radius)
        if self.error:
            p.fillPath(path, QColor(255, 92, 122, 51))
            p.setPen(QColor(RED))
            f = QFont(self.font()); f.setPixelSize(int(self.height() * 0.42)); f.setBold(True)
            p.setFont(f)
            p.drawText(r, int(Qt.AlignmentFlag.AlignCenter), "✕")
            return
        c1, c2 = TILES[zlib.crc32(self.title.encode("utf-8")) % len(TILES)]
        g = QLinearGradient(r.topLeft(), r.bottomRight())
        g.setColorAt(0, QColor(c1)); g.setColorAt(1, QColor(c2))
        p.fillPath(path, g)
        words = [w for w in self.title.replace("_", " ").split() if w[:1].isalnum()]
        initials = "".join(w[0] for w in words[:2]).upper() or "♪"
        f = QFont(self.font()); f.setPixelSize(int(self.height() * 0.36)); f.setBold(True)
        p.setFont(f)
        p.setPen(QColor("white"))
        p.drawText(r, int(Qt.AlignmentFlag.AlignCenter), initials)


class Toggle(QCheckBox):
    def __init__(self):
        super().__init__()
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFixedSize(50, 28)

    def sizeHint(self):
        return QSize(50, 28)

    def hitButton(self, pos: QPoint) -> bool:
        return self.rect().contains(pos)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        track = QPainterPath(); track.addRoundedRect(r, r.height() / 2, r.height() / 2)
        if self.isChecked():
            g = QLinearGradient(r.topLeft(), r.topRight())
            g.setColorAt(0, QColor(GRAD_START)); g.setColorAt(1, QColor(GRAD_END))
            p.fillPath(track, g)
        else:
            p.fillPath(track, QColor(STROKE))
        d = r.height() - 6
        x = r.right() - d - 3 if self.isChecked() else r.left() + 3
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor("white") if self.isChecked() else QColor(TEXT_SOFT))
        p.drawEllipse(QRectF(x, r.top() + 3, d, d))


def scroll_page(content: QWidget) -> QScrollArea:
    sc = QScrollArea()
    sc.setWidgetResizable(True)
    sc.setFrameShape(QFrame.Shape.NoFrame)
    sc.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
    sc.viewport().setAutoFillBackground(False)
    content.setAutoFillBackground(False)
    sc.setWidget(content)
    return sc


def centered_column(max_width: int = 680) -> tuple[QWidget, QVBoxLayout]:
    """Columna centrada de ancho máximo, para que no se estire en pantallas anchas."""
    outer = QWidget()
    h = QHBoxLayout(outer)
    h.setContentsMargins(32, 0, 32, 32)
    inner = QWidget()
    inner.setMaximumWidth(max_width)
    v = QVBoxLayout(inner)
    v.setContentsMargins(0, 0, 0, 0)
    v.setSpacing(16)
    h.addStretch(1)
    h.addWidget(inner, 100)
    h.addStretch(1)
    return outer, v


def open_in_explorer(path: Path, select: bool = False):
    if core.IS_WINDOWS:
        if select and path.exists():
            subprocess.Popen(["explorer", "/select,", str(path)])
        else:
            os.startfile(str(path if path.is_dir() else path.parent))  # type: ignore[attr-defined]
    else:
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(path if path.is_dir() else path.parent)))


# ---------------------------------------------------------------------------
# Página: Descargar
# ---------------------------------------------------------------------------

class HeroBackground(QWidget):
    """Imagen de fondo arriba con un degradado oscuro para que el texto se lea."""

    def __init__(self):
        super().__init__()
        self.img = QPixmap(str(core.resource("assets", "bg_home.jpg")))
        self._cache: Optional[QPixmap] = None

    def resizeEvent(self, e):
        self._cache = None
        super().resizeEvent(e)

    def paintEvent(self, _):
        p = QPainter(self)
        p.fillRect(self.rect(), QColor(BG))
        if self.img.isNull():
            return
        h = int(min(self.height() * 0.8, self.width() * 0.75))
        if self._cache is None or self._cache.width() != self.width():
            scaled = self.img.scaled(QSize(self.width(), h), Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                     Qt.TransformationMode.SmoothTransformation)
            sx = (scaled.width() - self.width()) // 2
            sy = (scaled.height() - h) // 3
            self._cache = scaled.copy(QRect(sx, sy, self.width(), h))
        p.drawPixmap(0, 0, self._cache)
        g = QLinearGradient(0, 0, 0, h)
        for stop, alpha in ((0, 0x40), (0.25, 0x30), (0.45, 0x70), (0.65, 0xC7), (0.85, 0xF2), (1, 0xFF)):
            c = QColor(BG); c.setAlpha(alpha)
            g.setColorAt(stop, c)
        p.fillRect(QRect(0, 0, self.width(), h), g)


class DownloadPage(HeroBackground):
    def __init__(self, main: "MainWindow"):
        super().__init__()
        self.main = main
        self.busy = False
        self.cancel = False
        self.last_track: Optional[Track] = None
        self.pending_release: Optional[core.Release] = None

        col_w, col = centered_column()
        col.addSpacing(110)
        col.addWidget(headline("Tu música,\nen MP3.", 40))
        self.chip = StatusChip()
        col.addLayout(hbox(self.chip, None))
        col.addSpacing(8)

        # Enlace
        link_card, lc = card()
        lc.addWidget(label("Enlace del video"))
        self.url = QLineEdit()
        self.url.setPlaceholderText("https://youtu.be/…  (también puedes arrastrarlo aquí)")
        self.url.returnPressed.connect(self.start_download)
        lc.addWidget(self.url)
        self.paste_btn = button("📋  Pegar")
        self.paste_btn.clicked.connect(self.paste)
        self.clear_btn = button("✕  Limpiar")
        self.clear_btn.clicked.connect(self.clear)
        lc.addLayout(hbox(self.paste_btn, self.clear_btn, None))
        col.addWidget(link_card)

        # Progreso
        self.prog_card, pc = card(10)
        self.percent = text("", 24, bold=True, wrap=False)
        pc.addLayout(hbox(label("Progreso"), None, self.percent))
        self.bar = QProgressBar()
        self.bar.setTextVisible(False)
        self.bar.setRange(0, 100)
        pc.addWidget(self.bar)
        self.prog_text = text("", 13, MUTED)
        pc.addWidget(self.prog_text)
        self.prog_card.hide()
        col.addWidget(self.prog_card)

        # Resultado
        self.res_card, rc = card(14)
        self.res_tile = ArtTile(56, 16)
        self.res_title = text("", 16, bold=True)
        self.res_sub = text("", 13, MUTED)
        self.res_sub.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        texts = QVBoxLayout(); texts.setSpacing(4)
        texts.addWidget(self.res_title); texts.addWidget(self.res_sub)
        top = hbox(self.res_tile, texts, spacing=14)
        top.setAlignment(self.res_tile, Qt.AlignmentFlag.AlignTop)
        rc.addLayout(top)
        self.res_actions = QWidget()
        play = button("▶  Reproducir"); play.clicked.connect(lambda: self.last_track and main.play(self.last_track))
        trim = button("✂  Recortar"); trim.clicked.connect(lambda: self.last_track and main.open_editor(self.last_track))
        folder = button("📁  Ver en carpeta")
        folder.clicked.connect(lambda: self.last_track and open_in_explorer(self.last_track.path, True))
        ra = hbox(play, trim, folder, None); self.res_actions.setLayout(ra)
        rc.addWidget(self.res_actions)
        self.res_card.hide()
        col.addWidget(self.res_card)

        # Acciones
        col.addSpacing(6)
        self.dl_btn = button("⬇   Descargar MP3", "primary")
        self.dl_btn.setMinimumHeight(58)
        self.dl_btn.clicked.connect(self.start_download)
        col.addWidget(self.dl_btn)

        self.yt_btn = button("⟳  Actualizar yt-dlp", "ghost", "Si las descargas empiezan a fallar, actualiza el motor")
        self.yt_btn.clicked.connect(self.update_ytdlp)
        self.app_btn = button("⬇  Actualizar la app", "secondary")
        self.app_btn.clicked.connect(self.update_app)
        col.addLayout(hbox(None, self.yt_btn, self.app_btn, None))
        ver = "desarrollo" if not core.BUILD_NUMBER else f"build {core.BUILD_NUMBER}"
        self.ver_label = text(f"Versión instalada: {ver}", 12, MUTED)
        self.ver_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        col.addWidget(self.ver_label)
        note = text("Úsalo solo con contenido propio o de licencia libre.", 12, MUTED)
        note.setAlignment(Qt.AlignmentFlag.AlignCenter)
        col.addWidget(note)
        col.addStretch(1)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.addWidget(scroll_page(col_w))
        self.set_idle()
        QTimer.singleShot(2500, self.quiet_update_check)

    # -- estado ------------------------------------------------------------

    def set_idle(self):
        self.chip.set("Listo para descargar", GREEN)

    def set_url(self, url: str):
        self.url.setText(url)
        self.prog_card.hide()
        self.res_card.hide()
        self.chip.set("Enlace recibido, listo para descargar", GREEN)

    def paste(self):
        url = core.find_url(QGuiApplication.clipboard().text())
        if url:
            self.set_url(url)
        else:
            self.chip.set("El portapapeles no tiene un enlace", RED)

    def clear(self):
        self.url.clear()
        self.prog_card.hide()
        self.res_card.hide()
        self.set_idle()

    def set_busy(self, value: bool, label_text: str = "Trabajando…"):
        self.busy = value
        for w in (self.yt_btn, self.app_btn, self.paste_btn, self.clear_btn, self.url, self.dl_btn):
            w.setEnabled(not value)
        self.dl_btn.setText(f"   {label_text}" if value else "⬇   Descargar MP3")

    def show_progress(self, pct, msg):
        self.prog_card.show()
        if pct is None or pct < 0:
            self.bar.setRange(0, 0)
            self.percent.setText("")
        else:
            self.bar.setRange(0, 100)
            self.bar.setValue(int(pct))
            self.percent.setText(f"{int(pct)}%")
        self.prog_text.setText(msg)

    def show_success(self, t: Track):
        self.last_track = t
        self.prog_card.hide()
        self.res_tile.set_title(t.title)
        self.res_title.setText(t.title)
        parts = ["Guardado en Música › AudioDL"]
        if t.duration_ms > 0:
            parts.append(core.fmt_time(t.duration_ms))
        if t.size_bytes > 0:
            parts.append(core.fmt_size(t.size_bytes))
        self.res_sub.setText(" · ".join(parts))
        self.res_actions.show()
        self.res_card.show()
        self.chip.set("Descarga completa", GREEN)

    def show_error(self, title: str, detail: str):
        self.prog_card.hide()
        self.res_tile.set_error()
        self.res_title.setText(title)
        self.res_sub.setText(detail)
        self.res_actions.hide()
        self.res_card.show()

    # -- descarga ----------------------------------------------------------

    def start_download(self):
        if self.busy:
            return
        url = core.find_url(self.url.text().strip())
        if not url:
            self.chip.set("Pega un enlace válido (http/https)", RED)
            return
        self.cancel = False
        self.set_busy(True, "Descargando…")
        self.res_card.hide()
        self.chip.set("Descargando…", AMBER)
        self.show_progress(None, "Preparando descarga…")

        def work(emit, u):
            return core.download(u, emit, lambda: self.cancel)

        run_job(work, url, progress=self.show_progress, done=self._dl_done, failed=self._dl_failed)

    def _dl_done(self, t: Track):
        self.set_busy(False)
        self.show_success(t)
        self.main.library.refresh()

    def _dl_failed(self, msg: str):
        self.set_busy(False)
        self.chip.set("Algo salió mal", RED)
        self.show_error("No se pudo descargar", msg)

    def update_ytdlp(self):
        if self.busy:
            return
        self.set_busy(True, "Actualizando yt-dlp…")
        self.chip.set("Actualizando yt-dlp…", AMBER)

        def ok(msg):
            self.set_busy(False)
            self.chip.set(msg, GREEN)

        def bad(msg):
            self.set_busy(False)
            self.chip.set("No se pudo actualizar yt-dlp", RED)
            self.show_error("Error al actualizar", msg)

        run_job(core.update_ytdlp, done=ok, failed=bad)

    # -- actualizar la app -------------------------------------------------

    def quiet_update_check(self):
        if not core.BUILD_NUMBER:
            return

        def ok(rel):
            if rel and rel.build > core.BUILD_NUMBER:
                self.app_btn.setText(f"⬇  Actualizar a build {rel.build}")
                self.ver_label.setText(
                    f"Versión instalada: build {core.BUILD_NUMBER} · hay una nueva: build {rel.build}")

        run_job(core.fetch_latest, done=ok, failed=lambda _m: None)

    def update_app(self):
        if self.busy:
            return
        self.set_busy(True, "Buscando actualización…")
        self.chip.set("Buscando la versión más reciente…", AMBER)
        run_job(core.fetch_latest, done=self._got_release, failed=self._update_failed)

    def _got_release(self, rel: Optional[core.Release]):
        self.set_busy(False)
        if rel is None:
            self.chip.set("El último release no trae instalador de PC", RED)
            return
        if core.BUILD_NUMBER and rel.build <= core.BUILD_NUMBER:
            self.chip.set(f"Ya tienes la última versión (build {core.BUILD_NUMBER})", GREEN)
            return
        if not getattr(sys, "frozen", False) or not core.IS_WINDOWS:
            self.chip.set(f"Hay una versión nueva (build {rel.build}) en GitHub Releases", AMBER)
            return
        size = f" ({core.fmt_size(rel.size)})" if rel.size else ""
        ans = QMessageBox.question(
            self, "Actualizar la app",
            f"Hay una versión nueva: build {rel.build}{size}.\n\n"
            "Se descargará el instalador y la app se cerrará para actualizarse. "
            "Se vuelve a abrir sola al terminar.\n\n¿Actualizar ahora?",
        )
        if ans != QMessageBox.StandardButton.Yes:
            self.set_idle()
            return
        self.set_busy(True, "Descargando actualización…")
        self.chip.set(f"Descargando build {rel.build}…", AMBER)
        self.show_progress(None, "Descargando el instalador…")

        def work(emit, r):
            return core.download_release(r, lambda pct: emit(pct, "Descargando el instalador…"))

        run_job(work, rel, progress=self.show_progress, done=self._install, failed=self._update_failed)

    def _install(self, setup: Path):
        self.show_progress(100, "Abriendo el instalador…")
        try:
            core.launch_installer(setup)
        except Exception as e:  # noqa: BLE001
            self._update_failed(str(e))
            return
        QTimer.singleShot(400, QApplication.quit)

    def _update_failed(self, msg: str):
        self.set_busy(False)
        self.chip.set("No se pudo actualizar la app", RED)
        self.show_error("Error al actualizar la app", msg)


# ---------------------------------------------------------------------------
# Página: Biblioteca
# ---------------------------------------------------------------------------

class TrackRow(QWidget):
    def __init__(self, page: "LibraryPage", track: Track):
        super().__init__()
        self.track = track
        lay = QHBoxLayout(self)
        lay.setContentsMargins(12, 8, 12, 8)
        lay.setSpacing(14)
        tile = ArtTile(46, 13); tile.set_title(track.title)
        lay.addWidget(tile)
        texts = QVBoxLayout(); texts.setSpacing(2)
        texts.addWidget(ElidedLabel(track.title, 15, TEXT, True))
        parts = []
        if track.duration_ms:
            parts.append(core.fmt_time(track.duration_ms))
        parts.append(core.fmt_size(track.size_bytes))
        texts.addWidget(ElidedLabel(" · ".join(parts), 12, MUTED))
        lay.addLayout(texts, 1)
        play = button("▶", "round", "Reproducir"); play.clicked.connect(lambda: page.main.play(track))
        cut = button("✂", "round", "Recortar"); cut.clicked.connect(lambda: page.main.open_editor(track))
        more = button("⋯", "round", "Más opciones"); more.clicked.connect(lambda: page.menu_for(track, more))
        for b in (play, cut, more):
            lay.addWidget(b)


class LibraryPage(QWidget):
    def __init__(self, main: "MainWindow"):
        super().__init__()
        self.main = main
        self.tracks: list[Track] = []
        self.loading = False
        self.again = False

        lay = QVBoxLayout(self)
        lay.setContentsMargins(36, 30, 36, 16)
        lay.setSpacing(14)
        lay.addWidget(eyebrow("Biblioteca"))
        self.count = text("", 13, MUTED, wrap=False)
        lay.addLayout(hbox(headline("Tus audios.", 34), None, self.count))

        self.search = QLineEdit()
        self.search.setPlaceholderText("Buscar en tus audios…")
        self.search.textChanged.connect(self.apply_filter)
        reload_btn = button("⟳  Actualizar", "secondary"); reload_btn.clicked.connect(self.refresh)
        folder_btn = button("📁  Abrir carpeta", "secondary")
        folder_btn.clicked.connect(lambda: open_in_explorer(core.music_dir()))
        bar = hbox(self.search, reload_btn, folder_btn)
        bar.setStretch(0, 1)
        lay.addLayout(bar)

        self.list = QListWidget()
        self.list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.list.setVerticalScrollMode(QListWidget.ScrollMode.ScrollPerPixel)
        self.list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.list.customContextMenuRequested.connect(self._context_menu)
        self.list.itemDoubleClicked.connect(lambda it: self.main.play(it.data(Qt.ItemDataRole.UserRole)))
        lay.addWidget(self.list, 1)

        self.empty = text("Todavía no hay audios.\nDescarga uno desde la sección Descargar "
                          "o copia tus MP3 a la carpeta Música › AudioDL.", 14, MUTED)
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.empty.hide()
        lay.addWidget(self.empty, 1)

    def refresh(self):
        if self.loading:
            self.again = True
            return
        self.loading = True
        self.again = False
        self.count.setText("Cargando…")

        def ok(tracks):
            self.loading = False
            self.tracks = tracks
            self.populate()
            if self.again:
                self.refresh()

        def bad(msg):
            self.loading = False
            self.count.setText("Error al leer la carpeta")
            QMessageBox.warning(self, "Biblioteca", msg)

        run_job(core.list_tracks, done=ok, failed=bad)

    def populate(self):
        self.list.clear()
        for t in self.tracks:
            it = QListWidgetItem()
            it.setData(Qt.ItemDataRole.UserRole, t)
            it.setSizeHint(QSize(0, 66))
            self.list.addItem(it)
            self.list.setItemWidget(it, TrackRow(self, t))
        n = len(self.tracks)
        self.count.setText("1 audio" if n == 1 else f"{n} audios")
        self.list.setVisible(n > 0)
        self.empty.setVisible(n == 0)
        self.apply_filter()

    def apply_filter(self):
        q = self.search.text().strip().lower()
        for i in range(self.list.count()):
            it = self.list.item(i)
            t: Track = it.data(Qt.ItemDataRole.UserRole)
            it.setHidden(bool(q) and q not in t.title.lower())

    def neighbor(self, track: Track, step: int) -> Optional[Track]:
        paths = [t.path for t in self.tracks]
        if track.path not in paths:
            return None
        i = paths.index(track.path) + step
        return self.tracks[i] if 0 <= i < len(self.tracks) else None

    def _context_menu(self, pos):
        it = self.list.itemAt(pos)
        if it:
            self.menu_for(it.data(Qt.ItemDataRole.UserRole), None, self.list.viewport().mapToGlobal(pos))

    def menu_for(self, track: Track, anchor: Optional[QWidget], at: Optional[QPoint] = None):
        m = QMenu(self)
        m.addAction("▶  Reproducir", lambda: self.main.play(track))
        m.addAction("✂  Recortar", lambda: self.main.open_editor(track))
        m.addSeparator()
        m.addAction("✎  Renombrar…", lambda: self.rename(track))
        m.addAction("📁  Mostrar en la carpeta", lambda: open_in_explorer(track.path, True))
        m.addSeparator()
        m.addAction("🗑  Eliminar…", lambda: self.delete(track))
        if at is None and anchor is not None:
            at = anchor.mapToGlobal(QPoint(0, anchor.height()))
        m.exec(at or self.cursor().pos())

    def rename(self, track: Track):
        name, ok = QInputDialog.getText(self, "Renombrar", "Nuevo nombre:", QLineEdit.EchoMode.Normal, track.title)
        if not ok or not name.strip():
            return
        self.main.release_file(track.path)
        try:
            core.rename_track(track, name)
        except Exception as e:  # noqa: BLE001
            QMessageBox.warning(self, "Renombrar", f"No se pudo renombrar:\n{e}")
        self.refresh()

    def delete(self, track: Track):
        ans = QMessageBox.question(self, "Eliminar", f"¿Eliminar «{track.title}»?\nEl archivo se borra del PC.")
        if ans != QMessageBox.StandardButton.Yes:
            return
        self.main.release_file(track.path)
        try:
            core.delete_track(track)
        except Exception as e:  # noqa: BLE001
            QMessageBox.warning(self, "Eliminar", f"No se pudo eliminar:\n{e}")
        self.refresh()


# ---------------------------------------------------------------------------
# Barra del reproductor
# ---------------------------------------------------------------------------

class PlayerBar(QFrame):
    def __init__(self, main: "MainWindow"):
        super().__init__()
        self.main = main
        self.setObjectName("playerbar")
        self.track: Optional[Track] = None
        self.dragging = False

        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.audio.setVolume(0.85)
        self.player.setAudioOutput(self.audio)
        self.player.positionChanged.connect(self._on_position)
        self.player.durationChanged.connect(self._on_duration)
        self.player.playbackStateChanged.connect(self._on_state)
        self.player.mediaStatusChanged.connect(self._on_status)
        self.player.errorOccurred.connect(lambda _e, msg: self.sub.setText(f"Error: {msg}"))

        lay = QHBoxLayout(self)
        lay.setContentsMargins(20, 10, 24, 10)
        lay.setSpacing(14)
        self.tile = ArtTile(46, 13)
        lay.addWidget(self.tile)
        info = QVBoxLayout(); info.setSpacing(2)
        self.title = ElidedLabel("Nada sonando", 14, TEXT, True)
        self.sub = ElidedLabel("Elige un audio de tu biblioteca", 12, MUTED)
        info.addWidget(self.title); info.addWidget(self.sub)
        info_w = QWidget(); info_w.setLayout(info); info_w.setFixedWidth(230)
        lay.addWidget(info_w)

        self.prev_btn = button("⏮", "round", "Anterior")
        self.back_btn = button("−10", "round", "Retroceder 10 s")
        self.play_btn = button("▶", "play", "Reproducir / pausar (Espacio)")
        self.fwd_btn = button("+10", "round", "Adelantar 10 s")
        self.next_btn = button("⏭", "round", "Siguiente")
        self.prev_btn.clicked.connect(lambda: self.skip_track(-1))
        self.back_btn.clicked.connect(lambda: self.jump(-10_000))
        self.play_btn.clicked.connect(self.toggle)
        self.fwd_btn.clicked.connect(lambda: self.jump(10_000))
        self.next_btn.clicked.connect(lambda: self.skip_track(1))
        for b in (self.prev_btn, self.back_btn, self.play_btn, self.fwd_btn, self.next_btn):
            lay.addWidget(b)

        self.pos_label = text("0:00", 12, MUTED, wrap=False)
        self.pos_label.setFixedWidth(46)
        self.pos_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        self.slider = QSlider(Qt.Orientation.Horizontal)
        self.slider.setCursor(Qt.CursorShape.PointingHandCursor)
        self.slider.sliderPressed.connect(self._press)
        self.slider.sliderReleased.connect(self._release)
        self.slider.sliderMoved.connect(lambda v: self.pos_label.setText(core.fmt_time(v)))
        self.dur_label = text("0:00", 12, MUTED, wrap=False)
        self.dur_label.setFixedWidth(46)
        lay.addWidget(self.pos_label)
        lay.addWidget(self.slider, 1)
        lay.addWidget(self.dur_label)

        lay.addWidget(text("🔊", 14, MUTED, wrap=False))
        self.vol = QSlider(Qt.Orientation.Horizontal)
        self.vol.setRange(0, 100)
        self.vol.setValue(85)
        self.vol.setFixedWidth(90)
        self.vol.valueChanged.connect(lambda v: self.audio.setVolume(v / 100))
        lay.addWidget(self.vol)
        self._set_enabled(False)

    def _set_enabled(self, on: bool):
        for w in (self.prev_btn, self.back_btn, self.play_btn, self.fwd_btn, self.next_btn, self.slider):
            w.setEnabled(on)

    def play(self, t: Track):
        self.track = t
        self.tile.set_title(t.title)
        self.title.setText(t.title)
        self.sub.setText("Música › AudioDL")
        self._set_enabled(True)
        self.player.setSource(QUrl.fromLocalFile(str(t.path)))
        self.player.play()

    def toggle(self):
        if not self.track:
            return
        if self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
        else:
            self.main.editor.stop_preview()
            self.player.play()

    def pause(self):
        self.player.pause()

    def jump(self, ms: int):
        dur = self.player.duration()
        self.player.setPosition(max(0, min(dur if dur > 0 else 0, self.player.position() + ms)))

    def skip_track(self, step: int):
        if not self.track:
            return
        if step < 0 and self.player.position() > 3000:
            self.player.setPosition(0)
            return
        nxt = self.main.library.neighbor(self.track, step)
        if nxt:
            self.play(nxt)

    def release(self, path: Path):
        if self.track and self.track.path == path:
            self.player.stop()
            self.player.setSource(QUrl())
            self.track = None
            self.tile.set_title("")
            self.title.setText("Nada sonando")
            self.sub.setText("Elige un audio de tu biblioteca")
            self.slider.setValue(0)
            self.pos_label.setText("0:00")
            self.dur_label.setText("0:00")
            self._set_enabled(False)

    def _press(self):
        self.dragging = True

    def _release(self):
        self.dragging = False
        self.player.setPosition(self.slider.value())

    def _on_position(self, ms: int):
        if not self.dragging:
            self.slider.setValue(ms)
            self.pos_label.setText(core.fmt_time(ms))

    def _on_duration(self, ms: int):
        self.slider.setRange(0, max(0, ms))
        self.dur_label.setText(core.fmt_time(ms))

    def _on_state(self, state):
        playing = state == QMediaPlayer.PlaybackState.PlayingState
        self.play_btn.setText("❚❚" if playing else "▶")

    def _on_status(self, status):
        if status == QMediaPlayer.MediaStatus.EndOfMedia and self.track:
            nxt = self.main.library.neighbor(self.track, 1)
            if nxt:
                self.play(nxt)


# ---------------------------------------------------------------------------
# Página: Editor
# ---------------------------------------------------------------------------

MIN_SEL = 500


class TrimView(QWidget):
    rangeChanged = Signal(bool)   # finished
    seekRequested = Signal(int)

    HANDLE_HIT = 14

    def __init__(self):
        super().__init__()
        self.setMinimumHeight(170)
        self.setMouseTracking(True)
        self.peaks: list[float] = []
        self.duration = 0
        self.start = 0
        self.end = 0
        self.playhead = -1
        self.message = "Analizando audio…"
        self._drag: Optional[str] = None

    def set_data(self, peaks: list[float], duration: int):
        self.peaks, self.duration = peaks, duration
        self.start, self.end = 0, duration
        self.message = ""
        self.update()

    def set_message(self, msg: str):
        self.message = msg
        self.update()

    def _x(self, ms: int) -> float:
        r = self._area()
        return r.left() + (ms / self.duration) * r.width() if self.duration else r.left()

    def _ms(self, x: float) -> int:
        r = self._area()
        if not self.duration or r.width() <= 0:
            return 0
        return int(max(0.0, min(1.0, (x - r.left()) / r.width())) * self.duration)

    def _area(self) -> QRectF:
        return QRectF(self.rect()).adjusted(12, 18, -12, -18)

    def paintEvent(self, _):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        bg = QPainterPath(); bg.addRoundedRect(QRectF(self.rect()), 18, 18)
        p.fillPath(bg, QColor(SURFACE_2))
        if self.message or not self.peaks or not self.duration:
            p.setPen(QColor(MUTED))
            p.drawText(self.rect(), int(Qt.AlignmentFlag.AlignCenter), self.message or "Sin datos")
            return
        r = self._area()
        n = len(self.peaks)
        step = r.width() / n
        bar_w = max(1.0, step * 0.62)
        mid = r.center().y()
        xs, xe = self._x(self.start), self._x(self.end)
        g = QLinearGradient(r.left(), 0, r.right(), 0)
        g.setColorAt(0, QColor(GRAD_START)); g.setColorAt(1, QColor(GRAD_END))
        off = QColor(WAVE_OFF)
        p.setPen(Qt.PenStyle.NoPen)
        for i, v in enumerate(self.peaks):
            x = r.left() + i * step
            h = max(2.0, v * r.height())
            inside = xs <= x + bar_w / 2 <= xe
            p.setBrush(g if inside else off)
            p.drawRoundedRect(QRectF(x, mid - h / 2, bar_w, h), bar_w / 2, bar_w / 2)

        # Zona seleccionada
        sel = QColor(GRAD_START); sel.setAlpha(28)
        p.fillRect(QRectF(xs, r.top() - 8, xe - xs, r.height() + 16), sel)

        # Tiradores
        for x in (xs, xe):
            p.setPen(QPen(QColor("white"), 2))
            p.drawLine(int(x), int(r.top() - 10), int(x), int(r.bottom() + 10))
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor("white"))
            p.drawRoundedRect(QRectF(x - 5, mid - 16, 10, 32), 5, 5)

        if self.playhead >= 0:
            x = self._x(self.playhead)
            p.setPen(QPen(QColor(AMBER), 2))
            p.drawLine(int(x), int(r.top() - 12), int(x), int(r.bottom() + 12))

    def _handle_at(self, x: float) -> Optional[str]:
        ds, de = abs(x - self._x(self.start)), abs(x - self._x(self.end))
        if min(ds, de) > self.HANDLE_HIT:
            return None
        return "start" if ds <= de else "end"

    def mousePressEvent(self, e):
        if not self.duration or self.message:
            return
        x = e.position().x()
        self._drag = self._handle_at(x)
        if self._drag is None:
            self.seekRequested.emit(self._ms(x))

    def mouseMoveEvent(self, e):
        x = e.position().x()
        if self._drag is None:
            hover = self.duration and not self.message and self._handle_at(x)
            self.setCursor(Qt.CursorShape.SizeHorCursor if hover else Qt.CursorShape.PointingHandCursor)
            return
        ms = self._ms(x)
        if self._drag == "start":
            self.start = max(0, min(ms, self.end - MIN_SEL))
        else:
            self.end = min(self.duration, max(ms, self.start + MIN_SEL))
        self.update()
        self.rangeChanged.emit(False)

    def mouseReleaseEvent(self, _):
        if self._drag:
            self._drag = None
            self.rangeChanged.emit(True)


class EditorPage(QWidget):
    def __init__(self, main: "MainWindow"):
        super().__init__()
        self.main = main
        self.track: Optional[Track] = None
        self.duration = 0
        self.busy = False
        self.offset = 0          # posición del archivo de vista previa dentro del original
        self.preview_fade = False
        self.pending_pos: Optional[int] = None
        self.load_token = 0

        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.player.setAudioOutput(self.audio)
        self.player.mediaStatusChanged.connect(self._on_status)
        self.player.playbackStateChanged.connect(self._on_state)
        self.tick = QTimer(self)
        self.tick.setInterval(40)
        self.tick.timeout.connect(self._on_tick)

        col_w, col = centered_column(860)
        col.setSpacing(16)
        col.addSpacing(28)
        back = button("←", "round", "Volver")
        back.clicked.connect(self.main.close_editor)
        col.addLayout(hbox(back, eyebrow("Editor"), None, spacing=16))
        self.title = headline("", 30)
        col.addWidget(self.title)
        self.chip = StatusChip()
        col.addLayout(hbox(self.chip, None))

        wave_card, wc = card(14)
        wc.addWidget(label("Selección · arrastra los tiradores, haz clic para mover la reproducción"))
        self.trim = TrimView()
        self.trim.rangeChanged.connect(self._range_changed)
        self.trim.seekRequested.connect(self._seek)
        wc.addWidget(self.trim)
        self.start_t = text("0:00.0", 18, bold=True, wrap=False)
        self.len_t = text("0:00.0", 18, bold=True, wrap=False)
        self.end_t = text("0:00.0", 18, bold=True, wrap=False)
        times = QHBoxLayout()
        for name, lab, align in (("Inicio", self.start_t, Qt.AlignmentFlag.AlignLeft),
                                 ("Duración", self.len_t, Qt.AlignmentFlag.AlignHCenter),
                                 ("Fin", self.end_t, Qt.AlignmentFlag.AlignRight)):
            v = QVBoxLayout(); v.setSpacing(2)
            cap = label(name)
            cap.setAlignment(align); lab.setAlignment(align)
            v.addWidget(cap); v.addWidget(lab)
            times.addLayout(v, 1)
        wc.addLayout(times)
        col.addWidget(wave_card)

        fine_card, fc = card(12)
        fc.addWidget(label("Ajuste fino"))
        self.nudges: list[QPushButton] = []
        for name, is_start in (("Inicio", True), ("Fin", False)):
            row = QHBoxLayout(); row.setSpacing(8)
            cap = text(name, 15, TEXT_SOFT, True, wrap=False); cap.setFixedWidth(70)
            row.addWidget(cap)
            for lab, delta in (("−1 s", -1000), ("−0,1 s", -100), ("+0,1 s", 100), ("+1 s", 1000)):
                b = button(lab, "chip")
                b.clicked.connect(lambda _=False, s=is_start, d=delta: self.nudge(s, d))
                self.nudges.append(b)
                row.addWidget(b)
            set_here = button("⌖ Aquí", "chip", "Usar la posición de reproducción actual")
            set_here.clicked.connect(lambda _=False, s=is_start: self.set_at_playhead(s))
            self.nudges.append(set_here)
            row.addWidget(set_here)
            row.addStretch(1)
            fc.addLayout(row)
        col.addWidget(fine_card)

        opt_card, oc = card(4)
        opt_texts = QVBoxLayout(); opt_texts.setSpacing(2)
        opt_texts.addWidget(text("Fundido suave", 16, bold=True))
        opt_texts.addWidget(text("Entrada y salida graduales del volumen", 13, MUTED))
        self.fade = Toggle()
        self.fade.toggled.connect(lambda _: self._invalidate_preview())
        oc.addLayout(hbox(opt_texts, None, self.fade))
        col.addWidget(opt_card)

        self.listen_btn = button("▶   Escuchar", "secondary")
        self.listen_btn.setMinimumHeight(54)
        self.listen_btn.clicked.connect(self.toggle_preview)
        self.save_btn = button("✓   Guardar recorte", "primary")
        self.save_btn.setMinimumHeight(54)
        self.save_btn.clicked.connect(self.save)
        acts = hbox(self.listen_btn, self.save_btn, spacing=12)
        acts.setStretch(0, 1); acts.setStretch(1, 1)
        col.addLayout(acts)
        self.result = text("", 13, MUTED)
        self.result.hide()
        col.addWidget(self.result)
        note = text("El recorte se guarda como un MP3 nuevo; el original no se modifica.", 12, MUTED)
        col.addWidget(note)
        col.addStretch(1)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.addWidget(scroll_page(col_w))

    # -- carga -------------------------------------------------------------

    def load(self, track: Track):
        self.stop_preview()
        self._release_player()
        self.track = track
        self.duration = track.duration_ms
        self.title.setText(track.title)
        self.trim.peaks = []
        self.trim.playhead = -1
        self.trim.set_message("Analizando audio…")
        self.result.hide()
        self.chip.set("Analizando la forma de onda…", AMBER)
        self._set_controls(False)
        self.load_token += 1
        token = self.load_token

        def work(path: Path, dur: int):
            if dur <= 0:
                dur = core.duration_of(path)
            return core.peaks(path, 700), dur

        def ok(res):
            if token != self.load_token:
                return
            peaks, dur = res
            if dur <= 0:
                bad("No se pudo leer la duración del audio.")
                return
            self.duration = dur
            self.trim.set_data(peaks, dur)
            self._update_times()
            self._set_controls(True)
            self.chip.set("Elige el tramo que quieres conservar", GREEN)

        def bad(msg):
            if token != self.load_token:
                return
            self.trim.set_message("No se pudo analizar el audio")
            self.chip.set(msg, RED)

        run_job(work, track.path, track.duration_ms, done=ok, failed=bad)

    def _set_controls(self, on: bool):
        for w in self.nudges + [self.listen_btn, self.save_btn, self.fade]:
            w.setEnabled(on)

    def _update_times(self):
        self.start_t.setText(core.fmt_time_fine(self.trim.start))
        self.end_t.setText(core.fmt_time_fine(self.trim.end))
        self.len_t.setText(core.fmt_time_fine(self.trim.end - self.trim.start))

    # -- edición -----------------------------------------------------------

    def nudge(self, is_start: bool, delta: int):
        if not self.duration:
            return
        if is_start:
            self.trim.start = max(0, min(self.trim.start + delta, self.trim.end - MIN_SEL))
        else:
            self.trim.end = min(self.duration, max(self.trim.end + delta, self.trim.start + MIN_SEL))
        self.trim.update()
        self._range_changed(True)

    def set_at_playhead(self, is_start: bool):
        ph = self.trim.playhead
        if ph < 0:
            self.chip.set("Primero haz clic en la onda o escucha para fijar una posición", AMBER)
            return
        if is_start:
            self.trim.start = max(0, min(ph, self.trim.end - MIN_SEL))
        else:
            self.trim.end = min(self.duration, max(ph, self.trim.start + MIN_SEL))
        self.trim.update()
        self._range_changed(True)

    def _range_changed(self, finished: bool):
        self._update_times()
        self.result.hide()
        if not finished:
            return
        if self.is_playing():
            if self.preview_fade:
                self.stop_preview()
            else:
                self.player.setPosition(self.trim.start)

    def _seek(self, ms: int):
        self.trim.playhead = ms
        self.trim.update()
        if self.is_playing() and not self.preview_fade:
            self.player.setPosition(ms)

    def _invalidate_preview(self):
        if self.is_playing():
            self.stop_preview()

    # -- vista previa ------------------------------------------------------

    def is_playing(self) -> bool:
        return self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState

    def toggle_preview(self):
        if not self.track or self.busy:
            return
        if self.is_playing():
            self.stop_preview()
            return
        self.main.player.pause()
        start, end = self.trim.start, self.trim.end
        if self.fade.isChecked():
            self.busy = True
            self.listen_btn.setText("   Preparando…")
            self._release_player()

            def ok(path: Path):
                self.busy = False
                self.preview_fade = True
                self.offset = start
                self._play_from(QUrl.fromLocalFile(str(path)), 0, force=True)

            def bad(msg):
                self.busy = False
                self.listen_btn.setText("▶   Escuchar")
                self.chip.set(f"No se pudo preparar la vista previa: {msg}", RED)

            run_job(core.make_preview, self.track, start, end, True, done=ok, failed=bad)
        else:
            self.preview_fade = False
            self.offset = 0
            ph = self.trim.playhead
            pos = ph if start <= ph < end - 200 else start
            self._play_from(QUrl.fromLocalFile(str(self.track.path)), pos)

    def _play_from(self, url: QUrl, pos: int, force: bool = False):
        ready = (QMediaPlayer.MediaStatus.LoadedMedia, QMediaPlayer.MediaStatus.BufferedMedia,
                 QMediaPlayer.MediaStatus.EndOfMedia, QMediaPlayer.MediaStatus.BufferingMedia)
        if not force and self.player.source() == url and self.player.mediaStatus() in ready:
            self.player.setPosition(pos)
            self.player.play()
        else:
            self.pending_pos = pos
            self.player.setSource(url)
        self.tick.start()

    def _on_status(self, status):
        if status == QMediaPlayer.MediaStatus.LoadedMedia and self.pending_pos is not None:
            pos, self.pending_pos = self.pending_pos, None
            self.player.setPosition(pos)
            self.player.play()
        elif status == QMediaPlayer.MediaStatus.EndOfMedia:
            self.stop_preview()
        elif status == QMediaPlayer.MediaStatus.InvalidMedia:
            self.pending_pos = None
            self.stop_preview()
            self.chip.set("No se pudo reproducir el audio", RED)

    def _on_state(self, state):
        playing = state == QMediaPlayer.PlaybackState.PlayingState
        if not self.busy:
            self.listen_btn.setText("❚❚   Detener" if playing else "▶   Escuchar")

    def _on_tick(self):
        if not self.is_playing():
            return
        pos = self.offset + self.player.position()
        self.trim.playhead = pos
        self.trim.update()
        if not self.preview_fade and pos >= self.trim.end:
            self.stop_preview()
            self.trim.playhead = self.trim.end
            self.trim.update()

    def stop_preview(self):
        self.tick.stop()
        self.pending_pos = None
        if self.player.playbackState() != QMediaPlayer.PlaybackState.StoppedState:
            self.player.pause()
        if not self.busy:
            self.listen_btn.setText("▶   Escuchar")

    def _release_player(self):
        self.player.stop()
        self.player.setSource(QUrl())

    def release_file(self, path: Path):
        if self.track and self.track.path == path:
            self.stop_preview()
            self._release_player()

    # -- guardar -----------------------------------------------------------

    def save(self):
        if not self.track or self.busy:
            return
        self.stop_preview()
        self.busy = True
        self._set_controls(False)
        self.save_btn.setText("   Guardando…")
        self.chip.set("Guardando el recorte…", AMBER)

        def ok(t: Track):
            self.busy = False
            self._set_controls(True)
            self.save_btn.setText("✓   Guardar recorte")
            self.chip.set("Recorte guardado", GREEN)
            self.result.setText(f"Guardado como «{t.path.name}» en Música › AudioDL · "
                                f"{core.fmt_time(t.duration_ms)} · {core.fmt_size(t.size_bytes)}")
            self.result.show()
            self.main.library.refresh()

        def bad(msg):
            self.busy = False
            self._set_controls(True)
            self.save_btn.setText("✓   Guardar recorte")
            self.chip.set("No se pudo guardar el recorte", RED)
            self.result.setText(msg)
            self.result.show()

        run_job(core.save_trim, self.track, self.trim.start, self.trim.end, self.fade.isChecked(),
                done=ok, failed=bad)

    def leave(self):
        self.stop_preview()
        self._release_player()
        self.load_token += 1


# ---------------------------------------------------------------------------
# Ventana principal
# ---------------------------------------------------------------------------

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(core.APP_NAME)
        self.setMinimumSize(980, 680)
        self.resize(1180, 800)
        self.setAcceptDrops(True)

        root = QWidget(); root.setObjectName("root")
        self.setCentralWidget(root)
        outer = QVBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)
        body = QHBoxLayout()
        body.setContentsMargins(0, 0, 0, 0)
        body.setSpacing(0)
        outer.addLayout(body, 1)

        # Barra lateral
        side = QFrame(); side.setObjectName("sidebar"); side.setFixedWidth(230)
        sl = QVBoxLayout(side)
        sl.setContentsMargins(16, 20, 16, 20)
        sl.setSpacing(6)
        logo = QLabel()
        pm = QPixmap(str(core.resource("assets", "logo.png")))
        if not pm.isNull():
            logo.setPixmap(pm.scaledToWidth(170, Qt.TransformationMode.SmoothTransformation))
        else:
            logo.setText(core.APP_NAME)
        logo.setStyleSheet("background: transparent;")
        sl.addWidget(logo)
        sl.addSpacing(18)
        self.nav_group = QButtonGroup(self)
        self.nav_dl = self._nav("⬇    Descargar")
        self.nav_lib = self._nav("♫    Biblioteca")
        sl.addWidget(self.nav_dl)
        sl.addWidget(self.nav_lib)
        sl.addStretch(1)
        sl.addWidget(text("Todo se guarda en\nMúsica › AudioDL", 12, MUTED))
        body.addWidget(side)

        self.stack = QStackedWidget()
        body.addWidget(self.stack, 1)

        self.player = PlayerBar(self)
        self.download = DownloadPage(self)
        self.library = LibraryPage(self)
        self.editor = EditorPage(self)
        for page in (self.download, self.library, self.editor):
            self.stack.addWidget(page)
        outer.addWidget(self.player)

        self.nav_dl.clicked.connect(lambda: self.show_page(self.download))
        self.nav_lib.clicked.connect(lambda: self.show_page(self.library))
        self.nav_dl.setChecked(True)
        self.prev_page: QWidget = self.download

        self.library.refresh()

    def _nav(self, s: str) -> QPushButton:
        b = QPushButton(s)
        b.setObjectName("nav")
        b.setCheckable(True)
        b.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        b.setCursor(Qt.CursorShape.PointingHandCursor)
        self.nav_group.addButton(b)
        return b

    def keyPressEvent(self, e):
        # Solo llega aquí si ningún campo de texto usó la tecla.
        if e.key() != Qt.Key.Key_Space:
            super().keyPressEvent(e)
            return
        if self.stack.currentWidget() is self.editor:
            self.editor.toggle_preview()
        else:
            self.player.toggle()

    def show_page(self, page: QWidget):
        if self.stack.currentWidget() is self.editor and page is not self.editor:
            self.editor.leave()
        self.stack.setCurrentWidget(page)
        self.nav_dl.setChecked(page is self.download)
        self.nav_lib.setChecked(page is self.library)
        if page is self.editor:
            self.nav_group.setExclusive(False)
            self.nav_dl.setChecked(False)
            self.nav_lib.setChecked(False)
            self.nav_group.setExclusive(True)

    def play(self, t: Track):
        self.editor.stop_preview()
        self.player.play(t)

    def open_editor(self, t: Track):
        cur = self.stack.currentWidget()
        if cur is not self.editor:
            self.prev_page = cur
        self.player.pause()
        self.editor.load(t)
        self.show_page(self.editor)

    def close_editor(self):
        self.show_page(self.prev_page)

    def release_file(self, path: Path):
        self.player.release(path)
        self.editor.release_file(path)

    # Arrastrar un enlace sobre la ventana
    def dragEnterEvent(self, e):
        md = e.mimeData()
        if md.hasUrls() or md.hasText():
            e.acceptProposedAction()

    def dropEvent(self, e):
        md = e.mimeData()
        raw = md.text() if md.hasText() else " ".join(u.toString() for u in md.urls())
        url = core.find_url(raw)
        if url and not self.download.busy:
            self.show_page(self.download)
            self.download.set_url(url)

    def closeEvent(self, e):
        self.download.cancel = True
        self.player.player.stop()
        self.editor.leave()
        super().closeEvent(e)


def selftest(log_path: str) -> int:
    """Prueba la app ya compilada en GitHub Actions (sin pantalla) y deja un registro."""
    import traceback

    log: list[str] = []
    errors: list[str] = []
    sys.excepthook = lambda t, v, tb: errors.append("".join(traceback.format_exception(t, v, tb)))

    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    app.setStyleSheet(STYLE)
    w = MainWindow()
    w.show()

    test = core.unique_path(core.music_dir(), "Prueba automática")
    state: dict = {}

    def step1():
        try:
            log.append(f"yt-dlp {core.ytdlp_version()}")
            log.append(f"ffmpeg {core.ffmpeg_path()}")
            core._ffmpeg(["-f", "lavfi", "-i", "sine=frequency=440:duration=8",
                          "-c:a", "libmp3lame", "-q:a", "4", str(test)])
            state["track"] = core.track_for(test)
            log.append(f"duración {state['track'].duration_ms} ms")
            w.library.refresh()
            w.show_page(w.library)
            w.play(state["track"])
        except Exception:
            errors.append(traceback.format_exc())

    def step2():
        log.append(f"reproductor disponible: {w.player.player.isAvailable()}")
        log.append(f"estado del medio: {w.player.player.mediaStatus()}")
        if not w.player.player.isAvailable():
            errors.append("QtMultimedia no tiene backend (falta el plugin FFmpeg)")
        log.append(f"audios en la biblioteca: {len(w.library.tracks)}")
        if "track" in state:
            w.open_editor(state["track"])

    def step3():
        log.append(f"picos de la onda: {len(w.editor.trim.peaks)}")
        if not w.editor.trim.peaks:
            errors.append("El editor no cargó la forma de onda")
        w.editor.nudge(True, 1000)
        w.editor.nudge(False, -1000)
        w.editor.toggle_preview()

    def step4():
        log.append(f"vista previa: {w.editor.player.mediaStatus()}")
        w.editor.fade.setChecked(True)
        w.editor.save()

    def step5():
        saved = list(core.music_dir().glob("Prueba automática*(recorte)*.mp3"))
        log.append(f"recortes guardados: {[p.name for p in saved]}")
        if not saved:
            errors.append("No se guardó el recorte")
        w.show_page(w.download)
        w.release_file(test)
        for p in saved + [test]:
            p.unlink(missing_ok=True)
        app.exit(0)

    for delay, fn in ((500, step1), (3000, step2), (7000, step3), (9500, step4), (14000, step5)):
        QTimer.singleShot(delay, fn)
    QTimer.singleShot(40000, lambda: (errors.append("Tiempo agotado"), app.exit(1)))
    app.exec()

    ok = not errors
    Path(log_path).write_text("\n".join(log + ["", "ERRORES:"] + errors + ["", "OK" if ok else "FALLÓ"]),
                              encoding="utf-8")
    return 0 if ok else 1


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "--selftest":
        sys.exit(selftest(sys.argv[2]))
    if core.IS_WINDOWS:
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("FranciscoZ360.ElSebastianDJ")
        except Exception:
            pass
    app = QApplication(sys.argv)
    app.setApplicationName(core.APP_NAME)
    app.setStyle("Fusion")
    app.setStyleSheet(STYLE)
    icon = core.resource("assets", "icon.ico")
    if icon.exists():
        app.setWindowIcon(QIcon(str(icon)))
    w = MainWindow()
    w.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
