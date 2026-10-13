"""Тонкая обёртка над libRHVoice через ctypes: текст -> список int16 сэмплов.

Нужна собранная библиотека RHVoice (см. scripts/build_rhvoice.sh). Пути берутся из
переменных окружения RHVOICE_LIB (libRHVoice.so) и RHVOICE_DATA (каталог с voices/ и languages/).
"""
import ctypes as C
import os

LIB = os.environ.get("RHVOICE_LIB", "/tmp/rhv/RHVoice-master/build/src/lib/libRHVoice.so")
DATA = os.environ.get("RHVOICE_DATA", "/tmp/rhv/share/RHVoice")

SET_SR = C.CFUNCTYPE(C.c_int, C.c_int, C.c_void_p)
PLAY = C.CFUNCTYPE(C.c_int, C.POINTER(C.c_short), C.c_uint, C.c_void_p)
NONE_CB = C.CFUNCTYPE(C.c_int, C.c_char_p, C.c_void_p)  # заглушка для process_mark/play_audio
POS_CB = C.CFUNCTYPE(C.c_int, C.c_uint, C.c_uint, C.c_void_p)
DONE = C.CFUNCTYPE(None, C.c_void_p)


class Callbacks(C.Structure):
    _fields_ = [("set_sample_rate", SET_SR), ("play_speech", PLAY),
                ("process_mark", NONE_CB), ("word_starts", POS_CB), ("word_ends", POS_CB),
                ("sentence_starts", POS_CB), ("sentence_ends", POS_CB),
                ("play_audio", NONE_CB), ("done", DONE)]


class InitParams(C.Structure):
    _fields_ = [("data_path", C.c_char_p), ("config_path", C.c_char_p),
                ("resource_paths", C.POINTER(C.c_char_p)),
                ("callbacks", Callbacks), ("options", C.c_uint)]


class SynthParams(C.Structure):
    _fields_ = [("voice_profile", C.c_char_p),
                ("absolute_rate", C.c_double), ("absolute_pitch", C.c_double), ("absolute_volume", C.c_double),
                ("relative_rate", C.c_double), ("relative_pitch", C.c_double), ("relative_volume", C.c_double),
                ("punctuation_mode", C.c_int), ("punctuation_list", C.c_char_p),
                ("capitals_mode", C.c_int), ("flags", C.c_int)]


_lib = C.CDLL(LIB)
_lib.RHVoice_new_tts_engine.restype = C.c_void_p
_lib.RHVoice_new_tts_engine.argtypes = [C.POINTER(InitParams)]
_lib.RHVoice_new_message.restype = C.c_void_p
_lib.RHVoice_new_message.argtypes = [C.c_void_p, C.c_char_p, C.c_uint, C.c_int, C.POINTER(SynthParams), C.c_void_p]
_lib.RHVoice_speak.restype = C.c_int
_lib.RHVoice_speak.argtypes = [C.c_void_p]
_lib.RHVoice_delete_message.argtypes = [C.c_void_p]
_lib.RHVoice_delete_tts_engine.argtypes = [C.c_void_p]


class Engine:
    def __init__(self):
        self.samples = []
        self.sr = 0
        self._cbs = Callbacks(
            SET_SR(self._set_sr), PLAY(self._play), NONE_CB(0), POS_CB(0), POS_CB(0), POS_CB(0), POS_CB(0),
            NONE_CB(0), DONE(0))
        p = InitParams(DATA.encode(), None, None, self._cbs, 0)
        self.h = _lib.RHVoice_new_tts_engine(C.byref(p))
        if not self.h:
            raise RuntimeError("RHVoice_new_tts_engine failed")

    def _set_sr(self, sr, _ud):
        self.sr = sr
        return 1

    def _play(self, ptr, count, _ud):
        self.samples.extend(ptr[:count])
        return 1

    def say(self, text, voice="Aleksandr", rate=0.0, pitch=0.0, volume=0.0):
        self.samples = []
        sp = SynthParams(voice.encode(), rate, pitch, volume, 0, 0, 0, 0, None, 0, 0)
        msg = _lib.RHVoice_new_message(self.h, text.encode("utf-8"), len(text.encode("utf-8")), 0,
                                       C.byref(sp), None)
        if not msg:
            raise RuntimeError("RHVoice_new_message failed")
        ok = _lib.RHVoice_speak(msg)
        _lib.RHVoice_delete_message(msg)
        return ok, self.sr, list(self.samples)
