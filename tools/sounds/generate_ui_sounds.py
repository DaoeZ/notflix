"""Genera los sonidos originales de interfaz de Notflix (M44).

Solo biblioteca estándar: wave, math y struct. Mono, 44,1 kHz, 16 bits.
- confirm: dos tonos ascendentes (660 Hz y 990 Hz), 90 ms en total.
- open: barrido descendente de 880 Hz a 520 Hz, 140 ms.
Pico a -12 dBFS; envolvente con ataque de 5 ms y caída exponencial.
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44_100
PEAK = 10 ** (-12 / 20)  # -12 dBFS
ATTACK_SECONDS = 0.005
OUT_DIR = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"


def envelope(position: int, total: int) -> float:
    attack = int(SAMPLE_RATE * ATTACK_SECONDS)
    if position < attack:
        return position / attack
    decay_span = max(total - attack, 1)
    return math.exp(-5.0 * (position - attack) / decay_span)


def render(duration_seconds: float, frequency_at) -> list[int]:
    total = int(SAMPLE_RATE * duration_seconds)
    samples = []
    phase = 0.0
    for index in range(total):
        seconds = index / SAMPLE_RATE
        frequency = frequency_at(seconds, duration_seconds)
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        value = math.sin(phase) * envelope(index, total) * PEAK
        samples.append(int(max(-1.0, min(1.0, value)) * 32767))
    return samples


def write_wav(path: Path, samples: list[int]) -> None:
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(b"".join(struct.pack("<h", sample) for sample in samples))


def generate_confirm() -> list[int]:
    half = 0.045
    first = render(half, lambda _seconds, _total: 660.0)
    second = render(half, lambda _seconds, _total: 990.0)
    return first + second


def generate_open() -> list[int]:
    start_hz, end_hz = 880.0, 520.0

    def sweep(seconds: float, total: float) -> float:
        progress = seconds / total
        return start_hz + (end_hz - start_hz) * progress

    return render(0.140, sweep)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    confirm = OUT_DIR / "notflix_ui_confirm.wav"
    open_screen = OUT_DIR / "notflix_ui_open.wav"
    write_wav(confirm, generate_confirm())
    write_wav(open_screen, generate_open())
    for path in (confirm, open_screen):
        print(f"{path.name}: {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
