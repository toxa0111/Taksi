#!/usr/bin/env python3
"""Zamonaviy ringtonlarni sintez qiladi (supersaw, FM, 808, sidechain, reverb, ping-pong delay) -> res/raw/rt_*.ogg
Ishlatish: python3 tools/make_ringtones.py <chiqish papkasi>"""
import sys, os, subprocess, numpy as np
from scipy.signal import butter, sosfilt, fftconvolve
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(7)
def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
def lp(x, fc, o=2): return sosfilt(butter(o, min(fc, SR * 0.45) / (SR / 2), 'low', output='sos'), x)
def hp(x, fc, o=2): return sosfilt(butter(o, fc / (SR / 2), 'high', output='sos'), x)
def bp(x, a, b): return sosfilt(butter(2, [a / (SR / 2), b / (SR / 2)], 'band', output='sos'), x)
def T(d): return np.arange(int(d * SR)) / SR
def adsr(n, a=0.005, r=0.08, hold=1.0):
    e = np.ones(n); na = max(1, int(a * SR)); nr = max(1, int(r * SR))
    e[:na] = np.linspace(0, 1, na); e[-nr:] *= np.linspace(1, 0, nr) if n > nr else 1
    return e

def saw(f, t, nh=28, ph=0.0):
    k = np.arange(1, nh + 1)
    k = k[k * f < 14000]
    return (np.sin(2 * np.pi * f * k[None, :] * t[:, None] + ph * k[None, :]) / k[None, :]).sum(1) * 0.6

def supersaw(f, d, fc=5000, voices=(-18, -9, 0, 9, 18), nh=18):
    t = T(d); x = np.zeros_like(t)
    for c in voices: x += saw(f * 2 ** (c / 1200), t, nh, rng.uniform(0, 6.28))
    return lp(x / len(voices), fc) * adsr(len(t), 0.012, 0.12)
def pluck(f, d, decay=7.0, bright=1.0):
    t = T(d + 0.3); k = np.arange(1, 17); k = k[k * f < 12000]
    amp = np.exp(-k[None, :] * decay * 0.18 * t[:, None] * bright) / k[None, :]
    x = (np.sin(2 * np.pi * f * k[None, :] * t[:, None]) * amp).sum(1)
    x += (np.sin(2 * np.pi * f * 1.003 * k[None, :] * t[:, None]) * amp).sum(1)
    return x * np.exp(-3.2 * t) * 0.5 * adsr(len(t), 0.003, 0.2)
def ep(f, d):
    t = T(d + 0.8)
    x = np.sin(2 * np.pi * f * t + 1.6 * np.exp(-4 * t) * np.sin(2 * np.pi * f * t))
    x += 0.12 * np.sin(2 * np.pi * f * 14 * t) * np.exp(-12 * t) + 0.3 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-3 * t)
    return x * np.exp(-1.6 * t) * 0.6 * adsr(len(t), 0.004, 0.3)
def bell(f, d):
    t = T(d + 1.0)
    x = np.sin(2 * np.pi * f * t + 2.4 * np.exp(-5 * t) * np.sin(2 * np.pi * f * 3.5 * t))
    x += 0.3 * np.sin(2 * np.pi * f * 2.01 * t) * np.exp(-3 * t)
    return x * np.exp(-2.6 * t) * 0.55 * adsr(len(t), 0.002, 0.3)
def bass(f, d, drive=1.8):
    t = T(d + 0.05)
    x = np.sin(2 * np.pi * f * t) + 0.35 * lp(saw(f, t, 10), 600)
    return np.tanh(x * drive) * 0.7 * adsr(len(t), 0.006, 0.06)
def s808(f, d):
    t = T(d + 0.1); fr = f * (1 + 0.6 * np.exp(-30 * t))
    x = np.sin(2 * np.pi * np.cumsum(fr) / SR)
    return np.tanh(x * 2.2) * np.exp(-1.7 * t) * 0.8 * adsr(len(t), 0.003, 0.1)
def kick():
    t = T(0.45); ph = 2 * np.pi * np.cumsum(46 + 130 * np.exp(-32 * t)) / SR
    return np.tanh(np.sin(ph) * 1.6) * np.exp(-9 * t) * 0.95
def snare():
    t = T(0.32); n = bp(rng.uniform(-1, 1, len(t)), 1200, 7000) * np.exp(-16 * t)
    return (np.sin(2 * np.pi * 185 * t) * np.exp(-22 * t) * 0.6 + n * 0.9) * 0.8
def clap():
    t = T(0.35); n = bp(rng.uniform(-1, 1, len(t)), 1000, 6000)
    e = sum(np.exp(-60 * np.clip(t - o, 0, None)) * (t >= o) for o in (0, 0.011, 0.022)) * 0.5 + np.exp(-14 * t) * 0.6
    return n * e * 0.8
def hat(o=False):
    t = T(0.28 if o else 0.07); return hp(rng.uniform(-1, 1, len(t)), 7500) * np.exp(-(14 if o else 70) * t) * 0.35
def shaker():
    t = T(0.09); return bp(rng.uniform(-1, 1, len(t)), 4000, 11000) * np.exp(-40 * t) * 0.3
def conga(f=300):
    t = T(0.25); return np.sin(2 * np.pi * (f + 60 * np.exp(-40 * t)) * t) * np.exp(-18 * t) * 0.6

class Track:
    def __init__(s, bpm, swing=0.0, bars=4):
        s.bpm = bpm; s.step = 60 / bpm / 4; s.sw = swing; s.bars = bars
        s.N = int(s.step * 16 * bars * SR)
        s.m = np.zeros((2, s.N)); s.d = np.zeros((2, s.N)); s.kicks = []
    def pos(s, st): return int((st + (s.sw * (st % 2) if s.sw else 0)) * s.step * SR)
    def put(s, bus, x, st, gain=1.0, pan=0.0):
        a = s.pos(st); ang = (pan + 1) * np.pi / 4
        g = np.array([np.cos(ang), np.sin(ang)]) * 1.4142 * 0.7071 * gain
        b = s.m if bus == 'm' else s.d
        for c in (0, 1):
            idx = (a + np.arange(len(x))) % s.N
            np.add.at(b[c], idx, x * g[c])
    def render(s, duck=0.55, rev=0.28, delay=0.0, delay_steps=3):
        n = s.N
        env = np.ones(n)
        for st in s.kicks:
            a = s.pos(st); tt = np.arange(n) / SR
            idx = (a + np.arange(int(0.28 * SR))) % n
            dd = 1 - duck * np.exp(-np.arange(len(idx)) / SR / 0.09) * (1 - np.exp(-np.arange(len(idx)) / SR / 0.004))
            env[idx] = np.minimum(env[idx], dd)
        m = s.m * env[None, :]
        if delay > 0:
            dl = int(delay_steps * s.step * SR); out = m.copy()
            for rep in range(1, 5):
                sh = np.roll(m, dl * rep, axis=1); out[0] += sh[1] * delay ** rep; out[1] += sh[0] * delay ** rep
            m = out
        ir = (rng.standard_normal(int(1.5 * SR)) * np.exp(-np.arange(int(1.5 * SR)) / SR / 0.42))
        ir = lp(ir, 5500); ir /= np.abs(ir).max()
        wet = np.zeros_like(m)
        for c in (0, 1):
            tile = np.tile(m[c], 3)
            wet[c] = fftconvolve(tile, np.roll(ir, c * 197))[n:2 * n]
        wet *= rev * 0.07
        mix = m + wet + s.d
        mix = hp(mix, 28, 1)
        pk = np.abs(mix).max(); mix = np.tanh(mix / pk * 1.35) / np.tanh(1.35) * 0.93
        return mix

def prog_bar(t, bar, chord, fn, vel=1.0, **kw):
    for note in chord: t.put('m', fn(hz(note), kw.get('len', 3.8) * t.step * 4 / 4 * 1.0 * 1.0) if False else fn(hz(note), 16 * t.step), bar * 16, vel / len(chord) * 1.4)

def drums(t, bar, k=(), s=(), c=(), h=(), oh=(), sh=(), cg=()):
    for x in k: t.put('d', kick(), bar * 16 + x, 1.0); t.kicks.append(bar * 16 + x)
    for x in s: t.put('d', snare(), bar * 16 + x, 0.85)
    for x in c: t.put('d', clap(), bar * 16 + x, 0.8)
    for x in h: t.put('d', hat(), bar * 16 + x, 0.7, pan=0.25)
    for x in oh: t.put('d', hat(True), bar * 16 + x, 0.7, pan=-0.2)
    for x in sh: t.put('d', shaker(), bar * 16 + x, 0.8, pan=-0.3)
    for x in cg: t.put('d', conga(280 if x % 2 else 360), bar * 16 + x, 0.8, pan=0.35)

# ---------- treklar ----------
def neon():      # synthwave 108
    t = Track(108); P = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]; R = [33, 29, 36, 31]
    for b in range(4):
        for n in P[b]: t.put('m', supersaw(hz(n + 12), 16 * t.step, 2600), b * 16, 0.5, pan=0.2)
        for st in range(0, 16, 2): t.put('m', bass(hz(R[b] + (12 if st % 8 == 6 else 0)), 1.6 * t.step), b * 16 + st, 0.9)
        arp = [P[b][i % 3] + 12 * (i // 3 % 2) + 12 for i in range(16)]
        for st in range(16): t.put('m', pluck(hz(arp[st]), 1.5 * t.step, 6), b * 16 + st, 0.5, pan=-0.3 + 0.6 * (st % 2))
        drums(t, b, k=(0, 4, 8, 12), c=(4, 12), h=range(2, 16, 4), oh=(2, 6, 10, 14))
    return t.render(0.5, 0.35, 0.35)
def aurora():    # melodic house 122
    t = Track(122); P = [[62, 65, 69, 72], [58, 62, 65, 69], [60, 64, 67, 71], [57, 60, 64, 67]]; R = [38, 34, 36, 33]
    for b in range(4):
        for n in P[b]: t.put('m', supersaw(hz(n), 15 * t.step, 3500, (-12, 0, 12)), b * 16, 0.45)
        for st in (0, 3, 6, 10, 12): t.put('m', bass(hz(R[b] + (12 if st in (6, 12) else 0)), 2 * t.step), b * 16 + st, 0.85)
        for i, st in enumerate((0, 3, 6, 9, 12, 14)): t.put('m', bell(hz(P[b][i % 4] + 24), 2 * t.step), b * 16 + st, 0.5, pan=0.4 * (-1) ** i)
        drums(t, b, k=(0, 4, 8, 12), c=(4, 12), h=(2, 6, 10, 14), oh=(2, 6, 10, 14))
    return t.render(0.6, 0.45, 0.4)
def puls():      # afro-house 118
    t = Track(118, swing=0.12); P = [[55, 58, 62], [55, 58, 62], [53, 57, 60], [50, 55, 58]]; R = [31, 31, 29, 26]
    for b in range(4):
        for st in (0, 3, 6, 8, 11, 14):
            for n in P[b]: t.put('m', ep(hz(n + 12), 1.2 * t.step), b * 16 + st, 0.4, pan=0.3)
        for st in (0, 3, 6, 8, 10, 14): t.put('m', bass(hz(R[b] + (12 if st in (6, 14) else 0)), 1.8 * t.step), b * 16 + st, 0.85)
        for st in (2, 5, 7, 10, 13, 15): t.put('m', pluck(hz(P[b][st % 3] + 24), 1.2 * t.step, 8), b * 16 + st, 0.45, pan=-0.35)
        drums(t, b, k=(0, 4, 8, 12), c=(4, 12), sh=range(0, 16, 2), cg=(3, 6, 9, 11, 14), oh=(2, 6, 10, 14))
    return t.render(0.45, 0.3, 0.3)
def drift():     # future bass 140 halftime
    t = Track(140); P = [[63, 67, 70, 74], [61, 65, 68, 72], [58, 62, 65, 69], [60, 63, 67, 70]]; R = [27, 25, 22, 24]
    for b in range(4):
        for st in (0, 6, 8, 14):
            for n in P[b]: t.put('m', supersaw(hz(n), 3.2 * t.step, 6500), b * 16 + st, 0.4, pan=0.0)
        t.put('m', s808(hz(R[b] + 12), 14 * t.step), b * 16, 0.9)
        for i, st in enumerate(range(0, 16, 2)): t.put('m', pluck(hz(P[b][i % 4] + 24), 1.6 * t.step, 5), b * 16 + st, 0.4, pan=-0.4 + 0.8 * (i % 2))
        drums(t, b, k=(0, 10), s=(8,), c=(8,), h=range(0, 16, 2), oh=(14,))
    return t.render(0.72, 0.4, 0.3)
def trap():      # trap 140
    t = Track(140); R = [[34, 34, 41, 36], [32, 32, 39, 34]]; M = [[70, 73, 77, 82], [68, 72, 75, 80]]
    for b in range(4):
        for st, n in zip((0, 6, 10, 14), R[b // 2]): t.put('m', s808(hz(n), 5 * t.step), b * 16 + st, 0.95)
        for i, st in enumerate(range(0, 16, 2)): t.put('m', bell(hz(M[b // 2][i % 4] + 12 * (i % 2)), 2 * t.step), b * 16 + st, 0.4, pan=0.4 * (-1) ** i)
        hh = list(range(0, 16, 2)) + ([13, 14, 15] if b % 2 else [])
        drums(t, b, k=(0, 7, 10), s=(8,), c=(8,), h=hh, oh=(6, 14))
    return t.render(0.35, 0.3, 0.35)
def velvet():    # lo-fi R&B 86
    t = Track(86, swing=0.2); P = [[60, 64, 67, 71], [57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 65]]; R = [36, 33, 29, 31]
    for b in range(4):
        for st in (0, 6, 10):
            for n in P[b]: t.put('m', ep(hz(n), 2.5 * t.step), b * 16 + st, 0.4, pan=0.15)
        for st in (0, 6, 8, 11): t.put('m', bass(hz(R[b]), 2 * t.step, 1.2), b * 16 + st, 0.9)
        for st, n in zip((2, 7, 12, 14), (79, 76, 74, 72)): t.put('m', ep(hz(n + (b % 2) * 2), 1.5 * t.step), b * 16 + st, 0.4, pan=-0.3)
        drums(t, b, k=(0, 7, 10), s=(4, 12), h=range(0, 16, 2), sh=(3, 11))
    return t.render(0.4, 0.5, 0.3)
def lift():      # pop dance 126
    t = Track(126); P = [[60, 64, 67], [67, 71, 74], [57, 60, 64], [65, 69, 72]]; R = [36, 31, 33, 29]
    for b in range(4):
        for st in (0, 3, 6, 8, 11, 14):
            for n in P[b]: t.put('m', supersaw(hz(n + 12), 1.8 * t.step, 5500), b * 16 + st, 0.38)
        for st in range(0, 16, 2): t.put('m', bass(hz(R[b] + (12 if st % 4 == 2 else 0)), 1.7 * t.step), b * 16 + st, 0.8)
        for i, st in enumerate(range(0, 16)): t.put('m', pluck(hz(P[b][i % 3] + 24 + 12 * (i % 5 == 4)), 1.2 * t.step, 8), b * 16 + st, 0.32, pan=0.5 * (-1) ** i)
        drums(t, b, k=(0, 4, 8, 12), c=(4, 12), h=range(2, 16, 4), oh=(2, 6, 10, 14), sh=range(1, 16, 2))
    return t.render(0.55, 0.3, 0.3)
def kristall():  # ambient pluck + bell, yumshoq
    t = Track(100); P = [[72, 76, 79, 83], [69, 72, 76, 79], [65, 69, 72, 76], [67, 71, 74, 79]]
    for b in range(4):
        for n in P[b]: t.put('m', supersaw(hz(n - 12), 15 * t.step, 1800, (-8, 0, 8)), b * 16, 0.35)
        for i in range(8): t.put('m', bell(hz(P[b][(i * 3) % 4] + 12), 3 * t.step), b * 16 + i * 2, 0.5, pan=0.5 * (-1) ** i)
        t.put('m', bass(hz(P[b][0] - 36), 8 * t.step, 1.0), b * 16, 0.8)
        drums(t, b, k=(0, 8), h=(4, 12), sh=(2, 6, 10, 14))
    return t.render(0.35, 0.65, 0.45, 4)

TRACKS = [('neon', neon), ('aurora', aurora), ('puls', puls), ('drift', drift), ('trap', trap), ('velvet', velvet), ('lift', lift), ('kristall', kristall)]

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    for name, fn in TRACKS:
        m = fn(); st = np.stack([m[0], m[1]], 1)
        wav = f'/tmp/rt_{name}.wav'; wavfile.write(wav, SR, (st * 32000).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', f'{out}/rt_{name}.ogg'], check=True)
        print(name, round(len(m[0]) / SR, 2), 's', 'peak', round(float(np.abs(m).max()), 2), 'rms', round(float(np.sqrt((m ** 2).mean())), 3))
