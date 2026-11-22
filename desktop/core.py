"""
Lógica sin interfaz de El Sebastián DJ para PC: descargas con yt-dlp, recortes con
FFmpeg, la carpeta Música › AudioDL y la actualización desde GitHub Releases.

Todo lo que corre aquí bloquea: la interfaz lo llama desde un hilo aparte.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

APP_NAME = "El Sebastián DJ"
REPO = "FranciscoZ360/AudioDL"
SETUP_SUFFIX = "-Setup.exe"
IS_WINDOWS = sys.platform == "win32"

try:  # Lo genera GitHub Actions al compilar.
    from build_info import BUILD_NUMBER  # type: ignore
except Exception:  # pragma: no cover - solo al correr desde el código fuente
    BUILD_NUMBER = 0


# ---------------------------------------------------------------------------
# Rutas
# ---------------------------------------------------------------------------

def resource(*parts: str) -> Path:
    """Archivo incluido en la app (en la compilación de PyInstaller vive en _MEIPASS)."""
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return base.joinpath(*parts)


def data_dir() -> Path:
    root = os.environ.get("LOCALAPPDATA") if IS_WINDOWS else None
    base = Path(root) if root else Path.home() / ".local" / "share"
    d = base / "ElSebastianDJ"
    d.mkdir(parents=True, exist_ok=True)
    return d


def music_dir() -> Path:
    """Música › AudioDL, respetando la carpeta Música real de Windows (OneDrive, etc.)."""
    base: Optional[Path] = None
    try:
        from PySide6.QtCore import QStandardPaths

        loc = QStandardPaths.writableLocation(QStandardPaths.StandardLocation.MusicLocation)
        if loc:
            base = Path(loc)
    except Exception:
        pass
    if base is None:
        base = Path.home() / "Music"
    d = base / "AudioDL"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _exe(name: str) -> str:
    return f"{name}.exe" if IS_WINDOWS else name


def _bundled_bin() -> Path:
    return resource("bin")


def _user_bin() -> Path:
    d = data_dir() / "bin"
    d.mkdir(parents=True, exist_ok=True)
    return d


def ytdlp_path() -> str:
    """yt-dlp se copia a la carpeta del usuario para poder actualizarlo con -U."""
    user = _user_bin() / _exe("yt-dlp")
    bundled = _bundled_bin() / _exe("yt-dlp")
    if bundled.exists():
        if not user.exists() or bundled.stat().st_mtime > user.stat().st_mtime:
            shutil.copy2(bundled, user)
        return str(user)
    if user.exists():
        return str(user)
    found = shutil.which("yt-dlp")
    if found:
        return found
    raise RuntimeError("No se encontró yt-dlp.")


def ffmpeg_path() -> str:
    bundled = _bundled_bin() / _exe("ffmpeg")
    if bundled.exists():
        return str(bundled)
    found = shutil.which("ffmpeg")
    if found:
        return found
    raise RuntimeError("No se encontró FFmpeg.")


def _env() -> dict:
    env = dict(os.environ)
    # yt-dlp busca Deno (para YouTube) y FFmpeg en el PATH.
    extra = [str(_user_bin()), str(_bundled_bin())]
    env["PATH"] = os.pathsep.join(extra + [env.get("PATH", "")])
    env["PYTHONIOENCODING"] = "utf-8"
    env["PYTHONUTF8"] = "1"
    return env


def _popen(args: list[str], **kw) -> subprocess.Popen:
    if IS_WINDOWS:
        kw.setdefault("creationflags", subprocess.CREATE_NO_WINDOW)
    return subprocess.Popen(args, env=_env(), **kw)


def _run(args: list[str], timeout: Optional[float] = None) -> subprocess.CompletedProcess:
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = subprocess.CREATE_NO_WINDOW
    return subprocess.run(
        args, env=_env(), capture_output=True, timeout=timeout,
        stdin=subprocess.DEVNULL, **kw,
    )


def _text(b: bytes) -> str:
    return b.decode("utf-8", errors="replace")


# ---------------------------------------------------------------------------
# Biblioteca
# ---------------------------------------------------------------------------

@dataclass
class Track:
    path: Path
    title: str
    duration_ms: int
    size_bytes: int


_DUR_RE = re.compile(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)")
_duration_cache: dict[str, list] = {}
_cache_loaded = False
_cache_lock = threading.RLock()


def _cache_file() -> Path:
    return data_dir() / "duraciones.json"


def _load_cache() -> None:
    global _cache_loaded, _duration_cache
    if _cache_loaded:
        return
    _cache_loaded = True
    try:
        _duration_cache = json.loads(_cache_file().read_text("utf-8"))
    except Exception:
        _duration_cache = {}


def _save_cache() -> None:
    try:
        with _cache_lock:
            payload = json.dumps(_duration_cache)
        _cache_file().write_text(payload, "utf-8")
    except Exception:
        pass


def duration_of(path: Path) -> int:
    """Duración en ms leyendo la cabecera con FFmpeg (0 si no se puede)."""
    try:
        r = _run([ffmpeg_path(), "-hide_banner", "-nostdin", "-i", str(path)], timeout=30)
    except Exception:
        return 0
    m = _DUR_RE.search(_text(r.stderr))
    if not m:
        return 0
    h, mnt, s = m.groups()
    return int((int(h) * 3600 + int(mnt) * 60 + float(s)) * 1000)


def _cached_duration(path: Path, st: os.stat_result) -> int:
    key = str(path)
    with _cache_lock:
        _load_cache()
        entry = _duration_cache.get(key)
    if entry and entry[0] == st.st_size and abs(entry[1] - st.st_mtime) < 1:
        return int(entry[2])
    ms = duration_of(path)
    with _cache_lock:
        _duration_cache[key] = [st.st_size, st.st_mtime, ms]
    return ms


def list_tracks() -> list[Track]:
    _load_cache()
    folder = music_dir()
    files = []
    for p in folder.iterdir():
        if p.is_file() and p.suffix.lower() == ".mp3":
            try:
                files.append((p, p.stat()))
            except OSError:
                pass
    files.sort(key=lambda x: x[1].st_mtime, reverse=True)
    tracks = [Track(p, p.stem, _cached_duration(p, st), st.st_size) for p, st in files]
    _save_cache()
    return tracks


def track_for(path: Path) -> Track:
    st = path.stat()
    _load_cache()
    t = Track(path, path.stem, _cached_duration(path, st), st.st_size)
    _save_cache()
    return t


def unique_path(folder: Path, stem: str, ext: str = ".mp3") -> Path:
    stem = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", stem).strip(" .") or "audio"
    candidate = folder / f"{stem}{ext}"
    n = 2
    while candidate.exists():
        candidate = folder / f"{stem} ({n}){ext}"
        n += 1
    return candidate


def delete_track(track: Track) -> None:
    track.path.unlink(missing_ok=True)
    with _cache_lock:
        _duration_cache.pop(str(track.path), None)
    _save_cache()


def rename_track(track: Track, new_title: str) -> Track:
    new_title = new_title.strip()
    if not new_title or new_title == track.title:
        return track
    dest = unique_path(track.path.parent, new_title)
    track.path.rename(dest)
    return track_for(dest)


# ---------------------------------------------------------------------------
# yt-dlp
# ---------------------------------------------------------------------------

URL_RE = re.compile(r"https?://\S+")
_PCT_RE = re.compile(r"\[download\]\s+([\d.]+)%")


def find_url(text: str) -> Optional[str]:
    m = URL_RE.search(text or "")
    return m.group(0) if m else None


def download(
    url: str,
    on_progress: Callable[[Optional[int], str], None],
    is_cancelled: Callable[[], bool] = lambda: False,
) -> Track:
    """Descarga el audio como MP3 y lo deja en Música › AudioDL."""
    tmp = Path(tempfile.mkdtemp(prefix="sebdj_"))
    try:
        args = [
            ytdlp_path(),
            "-x", "--audio-format", "mp3", "--audio-quality", "0",
            "--no-playlist", "--newline", "--no-mtime", "--no-colors",
            "--encoding", "utf-8",
            "--ffmpeg-location", str(Path(ffmpeg_path()).parent),
            "-o", str(tmp / "%(title)s.%(ext)s"),
            url,
        ]
        on_progress(None, "Preparando descarga…")
        proc = _popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                      stdin=subprocess.DEVNULL)
        tail: list[str] = []
        errors: list[str] = []
        assert proc.stdout is not None
        for raw in proc.stdout:
            if is_cancelled():
                proc.kill()
                raise RuntimeError("Descarga cancelada.")
            line = _text(raw).strip()
            if not line:
                continue
            tail = (tail + [line])[-8:]
            if line.startswith("ERROR"):
                errors.append(line.removeprefix("ERROR:").strip())
            m = _PCT_RE.search(line)
            if m:
                on_progress(min(100, int(float(m.group(1)))), "Descargando audio…")
            elif "[ExtractAudio]" in line or "ffmpeg" in line.lower():
                on_progress(None, "Convirtiendo a MP3…")
        code = proc.wait()

        mp3s = sorted(tmp.glob("*.mp3"), key=lambda p: p.stat().st_mtime)
        if code != 0 or not mp3s:
            detail = "\n".join(errors) or "\n".join(tail[-3:]) or f"yt-dlp terminó con código {code}"
            raise RuntimeError(detail)

        on_progress(None, "Guardando en Música › AudioDL…")
        src = mp3s[-1]
        dest = unique_path(music_dir(), src.stem)
        shutil.move(str(src), str(dest))
        os.utime(dest, None)
        return track_for(dest)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def update_ytdlp() -> str:
    r = _run([ytdlp_path(), "-U"], timeout=300)
    out = (_text(r.stdout) + "\n" + _text(r.stderr)).strip()
    low = out.lower()
    if "up to date" in low:
        return "yt-dlp ya está al día"
    if "updated yt-dlp" in low:
        return "yt-dlp actualizado"
    if r.returncode != 0:
        raise RuntimeError(out.splitlines()[-1] if out else "No se pudo actualizar yt-dlp")
    return "Actualización terminada"


def ytdlp_version() -> str:
    try:
        r = _run([ytdlp_path(), "--version"], timeout=30)
        return _text(r.stdout).strip() or "?"
    except Exception:
        return "?"


# ---------------------------------------------------------------------------
# FFmpeg: forma de onda y recorte
# ---------------------------------------------------------------------------

_COMMON = ["-hide_banner", "-nostdin", "-loglevel", "error", "-y"]


def _ffmpeg(args: list[str]) -> None:
    r = _run([ffmpeg_path()] + _COMMON + args, timeout=600)
    if r.returncode != 0:
        out = (_text(r.stdout) + _text(r.stderr)).strip()
        raise RuntimeError(f"FFmpeg falló ({r.returncode}): {out[-300:]}")


def peaks(path: Path, buckets: int) -> list[float]:
    """Envolvente de volumen normalizada (0..1) para dibujar la forma de onda."""
    r = _run(
        [ffmpeg_path()] + _COMMON
        + ["-i", str(path), "-vn", "-ac", "1", "-ar", "800", "-f", "s16le", "-"],
        timeout=300,
    )
    data = r.stdout
    n_samples = len(data) // 2
    if n_samples == 0:
        raise RuntimeError("No se pudo analizar el audio.")
    import array

    samples = array.array("h")
    samples.frombytes(data[: n_samples * 2])
    if sys.byteorder == "big":
        samples.byteswap()

    env = [max(abs(v) for v in samples[i:i + 16]) for i in range(0, len(samples), 16)]
    n = min(buckets, len(env))
    out = []
    for i in range(n):
        a = i * len(env) // n
        b = max(a + 1, (i + 1) * len(env) // n)
        out.append(max(env[a:b]))
    top = max(out) or 1
    return [(v / top) ** 0.5 for v in out]


def _secs(ms: int) -> str:
    return f"{ms / 1000:.3f}"


def trim(src: Path, dest: Path, start_ms: int, end_ms: int, fade: bool) -> None:
    """Recorta [start_ms, end_ms]. Sin fundido copia el audio sin recodificar."""
    dur = end_ms - start_ms
    base = ["-ss", _secs(start_ms), "-i", str(src), "-t", _secs(dur),
            "-map", "0:a:0", "-map_metadata", "-1"]
    if not fade:
        try:
            _ffmpeg(base + ["-c:a", "copy", str(dest)])
            if dest.exists() and dest.stat().st_size > 0:
                return
        except Exception:
            dest.unlink(missing_ok=True)
    enc: list[str] = []
    if fade:
        f = min(1000, dur // 4)
        enc += ["-af", f"afade=t=in:st=0:d={_secs(f)},afade=t=out:st={_secs(dur - f)}:d={_secs(f)}"]
    enc += ["-c:a", "libmp3lame", "-q:a", "2"]
    _ffmpeg(base + enc + [str(dest)])


def save_trim(track: Track, start_ms: int, end_ms: int, fade: bool) -> Track:
    dest = unique_path(track.path.parent, f"{track.title} (recorte)")
    tmp = dest.with_name(dest.stem + ".part.mp3")
    try:
        trim(track.path, tmp, start_ms, end_ms, fade)
        tmp.replace(dest)
    finally:
        tmp.unlink(missing_ok=True)
    return track_for(dest)


def make_preview(track: Track, start_ms: int, end_ms: int, fade: bool) -> Path:
    """Archivo temporal con el tramo elegido (con fundido) para escucharlo."""
    out = data_dir() / "preview.mp3"
    out.unlink(missing_ok=True)
    trim(track.path, out, start_ms, end_ms, fade)
    return out


# ---------------------------------------------------------------------------
# Actualizar la app desde GitHub Releases
# ---------------------------------------------------------------------------

@dataclass
class Release:
    build: int
    url: str
    size: int


def fetch_latest() -> Optional[Release]:
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "SebastianDJ-PC"},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    tag = str(data.get("tag_name", ""))
    try:
        build = int(tag.removeprefix("build-"))
    except ValueError:
        return None
    for a in data.get("assets", []):
        if str(a.get("name", "")).endswith(SETUP_SUFFIX):
            return Release(build, a["browser_download_url"], int(a.get("size", 0)))
    return None


def download_release(rel: Release, on_progress: Callable[[int], None]) -> Path:
    out_dir = data_dir() / "update"
    shutil.rmtree(out_dir, ignore_errors=True)
    out_dir.mkdir(parents=True)
    out = out_dir / f"ElSebastianDJ-build-{rel.build}{SETUP_SUFFIX}"
    req = urllib.request.Request(rel.url, headers={"User-Agent": "SebastianDJ-PC"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(out, "wb") as f:
        total = int(resp.headers.get("Content-Length") or rel.size or 0)
        done, last = 0, -2
        while True:
            chunk = resp.read(256 * 1024)
            if not chunk:
                break
            f.write(chunk)
            done += len(chunk)
            pct = int(done * 100 / total) if total else -1
            if pct != last:
                last = pct
                on_progress(pct)
    return out


def launch_installer(setup: Path) -> None:
    """Abre el instalador en modo silencioso; al terminar vuelve a abrir la app."""
    args = [str(setup), "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS"]
    kw = {}
    if IS_WINDOWS:
        kw["creationflags"] = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
    subprocess.Popen(args, close_fds=True, **kw)


# ---------------------------------------------------------------------------
# Formato
# ---------------------------------------------------------------------------

def fmt_time(ms: int) -> str:
    s = max(0, ms) // 1000
    h, rem = divmod(s, 3600)
    m, s = divmod(rem, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def fmt_time_fine(ms: int) -> str:
    ms = max(0, ms)
    m, rem = divmod(ms, 60000)
    return f"{m}:{rem // 1000:02d}.{(rem % 1000) // 100}"


def fmt_size(b: int) -> str:
    if b >= 1024 * 1024:
        return f"{b / 1024 / 1024:.1f} MB".replace(".", ",")
    return f"{max(1, b // 1024)} KB"
