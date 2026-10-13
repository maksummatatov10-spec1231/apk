#!/usr/bin/env python3
"""Озвучка одной фразы Джанкила: тот же конвейер, что у Спамтона (eSpeak NG + сглаживание).

Голос Джанкила ниже и медленнее, чем у Спамтона: pitch 58, темп 150 слов/мин. Помех нет.
Запуск (нужен venv с espeakng-loader; скрипт использует gen_voice.py из spamton-calc):
    /home/user/.venv-render/bin/python build-src/gen_junkil_voice.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(REPO, "spamton-calc", "scripts"))

import gen_voice as gv  # noqa: E402

PHRASE_ID = "bak"
SHOWN = "ТЫ ЧТО ДЕЛАЕШЬ В МОЁМ БАКЕ?!"
SPEECH = "Ты что делаешь в моём баке? Ха! Не трогай мой мусор!"

gv.PITCH = 58
gv.RATE = 150


def main():
    speaker = gv.Speaker()
    x = speaker.say(SPEECH)
    if not x:
        raise SystemExit("пустой синтез")
    x = gv.normalize(x)
    x = gv.smooth(x)
    x = gv.normalize(x, 0.8)
    out = os.path.join(HERE, "voice", "junkil_" + PHRASE_ID + ".wav")
    gv.write_wav(out, x)
    print("wrote", out, round(len(x) / gv.SR, 2), "s")
    print("subtitle:", SHOWN)


if __name__ == "__main__":
    main()
