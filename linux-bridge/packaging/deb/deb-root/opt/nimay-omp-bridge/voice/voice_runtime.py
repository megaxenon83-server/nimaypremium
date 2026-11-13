"""Nimay OMP voice runtime: TTS (piper / chatterbox / vibevoice) og STT (faster-whisper)."""

import argparse
import json
import os
import struct
import sys
import wave


def load_config():
    voice_dir = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(voice_dir, "voice-config.json")
    with open(path, encoding="utf-8-sig") as handle:
        return json.load(handle)


def ok(**fields):
    payload = {"ok": True}
    payload.update(fields)
    reply_print(payload)
    sys.exit(0)


def fail(message):
    payload = {"ok": False, "error": message}
    reply_print(payload)
    sys.exit(1)


_REPLY_FILE = None

def reply_print(payload):
    """Svarlinjen til stdout (og i serve-modus ogsaa til .resp-filen fordi
    noen python-launchere rerelayser pipen forst naar prosessen avslutter).
    Filen skrives eksplisitt utf-8 (Windows-default cp1252 gaar i blokk
    paa ae/oe/aa, som var aarsaken til de "raare tegnene" i samtalen)."""
    line = json.dumps(payload, ensure_ascii=False)
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    print(line, flush=True)
    if _REPLY_FILE:
        try:
            with open(_REPLY_FILE, "w", encoding="utf-8") as handle:
                handle.write(line)
        except OSError:
            pass


def read_wav_as_float32(path):
    import numpy as np

    with wave.open(path, "rb") as handle:
        channels = handle.getnchannels()
        width = handle.getsampwidth()
        rate = handle.getframerate()
        frames = handle.readframes(handle.getnframes())
    if width != 2:
        fail("Forventet 16-bit PCM WAV, fikk %d byte per sample." % width)
    data = np.frombuffer(frames, dtype=np.int16).astype("float32") / 32768.0
    if channels > 1:
        data = data.reshape(-1, channels).mean(axis=1)
    if rate != 16000:
        # Linear resampling is good enough for speech recognition input.
        target = int(len(data) * 16000 / rate)
        if target > 0:
            positions = np.linspace(0, len(data) - 1, target)
            data = np.interp(positions, np.arange(len(data)), data).astype("float32")
    return data


def write_wav_pcm16(samples, sample_rate, out_path):
    """Skriver float-approksen [0..1] som 16-bit PCM WAV - uten float32-format."""
    import numpy as np

    if samples.dim() > 1:
        mono = samples.cpu().numpy().mean(axis=0)
    else:
        mono = samples.cpu().numpy()
    pcm = np.clip(mono, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype(np.int16)
    with wave.open(out_path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(pcm.tobytes())


def synthesize_piper(engine, text, out_path):
    import json as _json

    from piper import PiperVoice, SynthesisConfig

    voice = PiperVoice.load(engine["model"], config_path=engine.get("config"))
    # NVCC er en 10-stemmers norsk motor (5 kvinner: KNN/KSV/KON/KMN/KNV,
    # 5 menn: MMN/MSV/MON/MNV). voiceId i voice-config velger hvem som taler.
    # Piper 1.8 vil ha heltals indeks, broen sender stemmekoden.
    requested = engine.get("speakerId", engine.get("defaultSpeakerId"))
    speaker_id = None
    if requested is not None:
        if isinstance(requested, int):
            speaker_id = requested
        else:
            try:
                speaker_id = int(requested)
            except (TypeError, ValueError):
                try:
                    with open(engine["config"], encoding="utf-8-sig") as handle:
                        config_json = _json.load(handle)
                    speaker_id = config_json.get("speaker_id_map", {}).get(str(requested))
                except Exception:
                    speaker_id = None
    syn_config = SynthesisConfig(speaker_id=speaker_id) if speaker_id is not None else None
    with wave.open(out_path, "wb") as handle:
        voice.synthesize_wav(text, handle, syn_config=syn_config)


def synthesize_chatterbox(text, out_path):
    import torch
    import torchaudio

    from chatterbox.mtl_tts import ChatterboxMultilingualTTS

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = ChatterboxMultilingualTTS.from_pretrained(device=device)
    wav = model.generate(text, language_id="no")
    torchaudio.save(out_path, wav.cpu(), model.sr, encoding="PCM_S", bits_per_sample=16)


_VIBE_CACHE = {}


def _resolve_vibe_dir(engine, model_dir):
    """Some install runs stamped the wrong parent dir (models\\piper\\<id>)
    into voice-status/config. The real models live directly under models\\,
    so repair automatically instead of failing every speech call."""
    if os.path.isdir(model_dir):
        return model_dir
    voice_dir = os.path.dirname(os.path.abspath(__file__))
    for candidate in (
        os.path.join(voice_dir, "models", os.path.basename(model_dir.rstrip("\\/"))),
        os.path.join(voice_dir, "models", str(engine.get("id", ""))),
    ):
        try:
            if os.path.isdir(candidate):
                return candidate
        except Exception:
            pass
    return model_dir


def synthesize_vibevoice(engine, text, out_path):
    """Ekte long-form inferens med vibevoice-community, modell-load paa GPU
    og stemmepreset fra engine['voice']."""
    import torch
    import torchaudio
    import sys
    import types

    # Transformers 5.2 registrerer VibeVoice-acoustic/semantic-tokenizer
    # natively, og community-pakken kolliderer i dobbelt registry. Losning:
    # stub transformer's EGEN vibevoice-moduler foer import, slik at bare
    # community-pakkens klasser registreres.
    for _mod in (
        "transformers.models.vibevoice_acoustic_tokenizer",
        "transformers.models.vibevoice_acoustic_tokenizer."
        "configuration_vibevoice_acoustic_tokenizer",
        "transformers.models.vibevoice_acoustic_tokenizer."
        "feature_extraction_vibevoice_acoustic_tokenizer",
        "transformers.models.vibevoice_acoustic_tokenizer."
        "modeling_vibevoice_acoustic_tokenizer",
        "transformers.models.vibevoice_semantic_tokenizer",
        "transformers.models.vibevoice_semantic_tokenizer."
        "configuration_vibevoice_semantic_tokenizer",
        "transformers.models.vibevoice_semantic_tokenizer."
        "modeling_vibevoice_semantic_tokenizer",
    ):
        if _mod not in sys.modules:
            stub = types.ModuleType(_mod)
            stub.__path__ = []
            sys.modules[_mod] = stub

    # transformers 5.x splittet/omdoepet qwen2-fast (tokenizers-unifisering).
    # Community-koden importerer den gamle stien: shim-modul der
    # Qwen2TokenizerFast = ny Qwen2Tokenizer (naa tokenizers-basert).
    try:
        import importlib as _importlib
        _qwen2mod = _importlib.import_module(
            "transformers.models.qwen2.tokenization_qwen2")
        shim = types.ModuleType(
            "transformers.models.qwen2.tokenization_qwen2_fast")
        shim.__path__ = list(getattr(_qwen2mod, "__path__", []))
        fast_class = getattr(_qwen2mod, "Qwen2TokenizerFast", None)
        if fast_class is None:
            fast_class = getattr(_qwen2mod, "Qwen2Tokenizer", None)
        if fast_class is None:
            fast_class = getattr(_qwen2mod, "Tokenizer", None)
        setattr(shim, "Qwen2TokenizerFast", fast_class)
        setattr(shim, "Qwen2Tokenizer",
                getattr(_qwen2mod, "Qwen2Tokenizer", fast_class))
        sys.modules["transformers.models.qwen2.tokenization_qwen2_fast"] = shim
    except Exception:
        pass

    from vibevoice.modular.modeling_vibevoice_inference import (
        VibeVoiceForConditionalGenerationInference,
    )
    from vibevoice.processor.vibevoice_processor import VibeVoiceProcessor

    model_dir = engine.get("dir") or engine.get("repo")
    model_dir = _resolve_vibe_dir(engine, model_dir)
    if not os.path.isdir(model_dir):
        fail("Modellmappen mangler: %s" % model_dir)

    voice = engine.get("voice") or ""
    is_streaming = False
    cfg_path = os.path.join(model_dir, "config.json")
    try:
        with open(cfg_path, encoding="utf-8") as handle:
            is_streaming = json.load(handle).get("model_type") == "vibevoice_streaming"
    except Exception:
        is_streaming = str(engine.get("id", "")).endswith("0.5b")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.bfloat16 if device == "cuda" else torch.float32
    device_map = "cuda" if device == "cuda" else "cpu"

    if is_streaming:
        # Realtime 0.5B: pre-beregne stemme-embeddings (.pt fra streaming_model/)
        preset = voice.strip() or "en-Emma_woman"
        pt_path = os.path.join(model_dir, "voices", "streaming_model",
                               preset + ".pt")
        if not os.path.isfile(pt_path):
            # fallback: aapnings/Emma
            voices_root = os.path.join(model_dir, "voices", "streaming_model")
            candidates = []
            if os.path.isdir(voices_root):
                candidates = [f for f in os.listdir(voices_root)
                              if f.lower().endswith(".pt")]
            if "_" in preset:
                far = preset.split("_", 1)[1]
                for cand in candidates:
                    if cand.endswith("_" + far + ".pt"):
                        pt_path = os.path.join(voices_root, cand)
                        break
            if not os.path.isfile(pt_path):
                pt_path = os.path.join(voices_root, "en-Emma_woman.pt")
        if not os.path.isfile(pt_path):
            fail("Ingen .pt-stemme funnet (forventet voices/streaming_model).")

        import copy as _copy
        from vibevoice.modular.modeling_vibevoice_streaming_inference import (
            VibeVoiceStreamingForConditionalGenerationInference,
        )
        from vibevoice.processor.vibevoice_streaming_processor import (
            VibeVoiceStreamingProcessor,
        )

        key = (engine.get("id") or model_dir, device_map)
        entry = _VIBE_CACHE.get(key)
        if entry is None:
            model = VibeVoiceStreamingForConditionalGenerationInference.from_pretrained(
                model_dir, torch_dtype=dtype, device_map=device_map,
                attn_implementation="sdpa")
            model.eval()
            model.set_ddpm_inference_steps(num_steps=5)
            processor = VibeVoiceStreamingProcessor.from_pretrained(model_dir)
            entry = {"model": model, "processor": processor}
            _VIBE_CACHE[key] = entry

        model = entry["model"]
        processor = entry["processor"]

        all_prefilled = torch.load(pt_path, map_location=device, weights_only=False)
        inputs = processor.process_input_with_cached_prompt(
            text="Speaker 1: " + text,
            cached_prompt=all_prefilled,
            padding=True,
            return_tensors="pt",
            return_attention_mask=True,
        )
        for k, v in inputs.items():
            if torch.is_tensor(v):
                inputs[k] = v.to(device)

        outputs = model.generate(
            **inputs,
            max_new_tokens=None,
            cfg_scale=1.5,
            tokenizer=processor.tokenizer,
            generation_config={"do_sample": False},
            verbose=False,
            all_prefilled_outputs=_copy.deepcopy(all_prefilled),
        )
        if not outputs.speech_outputs or outputs.speech_outputs[0] is None:
            fail("VibeVoice genererte ingen lyd.")
        write_wav_pcm16(outputs.speech_outputs[0], 24000, out_path)
        return

    ## Fullversjon (1.5B/7B): waver-preset fra repo demovoices
    voice_preset = voice.strip() or "en-Alice_woman"
    voice_wav = os.path.join(model_dir, "voices", voice_preset + ".wav")
    if not os.path.isfile(voice_wav):
        voice_wav = os.path.join(model_dir, voice_preset + ".wav")
    if not os.path.isfile(voice_wav):
        fail("Stemme-filen for '%s' finnes ikke i %s." %
             (voice_preset, model_dir))

    key = (engine.get("id") or model_dir, device_map)
    entry = _VIBE_CACHE.get(key)
    if entry is None:
        model = VibeVoiceForConditionalGenerationInference.from_pretrained(
            model_dir, torch_dtype=dtype, device_map=device_map)
        model.eval()
        # Faerre diffusion-steg: 10 steg tok ca 150 s per kort setning pa RTX
        # 4080; 4 steg lander rundt 60 s med forsvarlig kvalitet til samtale.
        model.set_ddpm_inference_steps(num_steps=4)
        processor = VibeVoiceProcessor.from_pretrained(model_dir)
        entry = {"model": model, "processor": processor}
        _VIBE_CACHE[key] = entry

    model = entry["model"]
    processor = entry["processor"]

    script = "Speaker 1: " + text.replace(chr(8217), "'")
    inputs = processor(
        text=[script],
        voice_samples=[[voice_wav]],
        padding=True,
        return_tensors="pt",
        return_attention_mask=True,
    )
    target = device if device != "cpu" else "cpu"
    for k, v in inputs.items():
        if torch.is_tensor(v):
            inputs[k] = v.to(target)

    outputs = model.generate(
        **inputs,
        cfg_scale=1.3,
        tokenizer=processor.tokenizer,
        generation_config={"do_sample": False},
        verbose=False,
        is_prefill=True,
    )
    if not outputs.speech_outputs or outputs.speech_outputs[0] is None:
        fail("VibeVoice genererte ingen lyd.")
    wav_tensor = outputs.speech_outputs[0]
    write_wav_pcm16(wav_tensor.squeeze(0), 24000, out_path)


def cmd_tts(args):
    config = load_config()
    engine = config.get("tts")
    if not engine:
        fail("Ingen talesyntese er installert. Kjør Nimay OMP-installasjonen på nytt.")
    text = (args.text or "").strip()
    if not text:
        fail("Mangler tekst.")
    if len(text) > 2000:
        fail("Teksten er for lang (maks 2000 tegn).")
    out_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    kind = engine.get("type", "piper")
    try:
        if kind == "piper":
            synthesize_piper(engine, text, out_path)
        elif kind == "chatterbox":
            synthesize_chatterbox(text, out_path)
        elif kind == "vibevoice":
            synthesize_vibevoice(engine, text, out_path)
        else:
            fail("Ukjent TTS-type: %s" % kind)
    except SystemExit:
        raise
    except Exception as error:  # noqa: BLE001
        fail("Talesyntese feilet: %s" % error)

    if not os.path.exists(out_path) or os.path.getsize(out_path) < 100:
        fail("Talesyntese produserte ingen lyd.")
    with wave.open(out_path, "rb") as handle:
        duration = handle.getnframes() / float(handle.getframerate() or 1)
    ok(engine=engine.get("id", kind), duration=round(duration, 2),
       bytes=os.path.getsize(out_path))


_STT_CACHE = {}

def _ensure_openvino_cuda_paths():
    """ctranslate2 trenger cublas64_12.dll (osv.) fra torch/lib naar den proever
    CUDA; ellers krasjet talesvaret med "Library cublas64_12.dll is not found".
    Torch's wheels inneholder DLL-direktorer; gjor dem synlige for ctranslate2."""
    import glob as _glob  # noqa: F401
    venv_root = os.path.dirname(os.path.dirname(sys.executable))
    torch_lib = os.path.join(venv_root, "Lib", "site-packages", "torch", "lib")
    if os.path.isdir(torch_lib):
        try:
            os.add_dll_directory(torch_lib)
        except Exception:
            pass
        path_now = os.environ.get("PATH", "")
        if torch_lib.lower() not in path_now.lower():
            os.environ["PATH"] = torch_lib + os.pathsep + path_now
    return torch_lib


def transcribe_stt(engine, wav_path):
    """Talegjenkjenning med whisper-modell cache per (id, compute)."""
    model_dir = engine.get("dir") or engine.get("repo")
    if engine.get("dir") and not os.path.isdir(engine["dir"]):
        fail("Modellmappen mangler: %s" % engine["dir"])

    audio = read_wav_as_float32(wav_path)
    if len(audio) < 1600:
        fail("Lydklippet er for kort.")

    try:
        from faster_whisper import WhisperModel
    except Exception as error:  # noqa: BLE001
        fail("faster-whisper er ikke installert: %s" % error)

    device = engine.get("device", "cpu")
    compute = engine.get("compute", "int8")
    _ensure_openvino_cuda_paths()

    def make_model(dev, comp):
        return WhisperModel(model_dir, device=dev, compute_type=comp)

    key = (engine.get("id"), compute, device)
    entry = _STT_CACHE.get(key)
    if entry is None:
        try:
            entry = make_model(device, compute)
        except Exception as load_error:  # noqa: BLE001
            # GPU/CUDA-DLL-ulike feil (f.eks. manglende cublas/cudnn) skal ikke
            # stoppe samtalen: faell tilbake til CPU/int8 uten aa gi opp.
            message = str(load_error)
            gpu_hint = any(word in message.lower()
                           for word in ("cublas", "cudnn", "cuda", "dll"))
            if device != "cpu" and gpu_hint:
                entry = make_model("cpu", "int8")
                key = (engine.get("id"), "int8", "cpu")
            else:
                raise
        _STT_CACHE[key] = entry

    segments, info = entry.transcribe(audio, language="no", beam_size=1, vad_filter=False)
    text = " ".join(segment.text.strip() for segment in segments).strip()
    return text, info


def cmd_stt(args):
    config = load_config()
    engine = config.get("stt")
    if not engine:
        fail("Ingen talegjenkjenning er installert. Kjør Nimay OMP-installasjonen på nytt.")
    wav_path = os.path.abspath(args.wav)
    if not os.path.exists(wav_path):
        fail("Lydfilen finnes ikke: %s" % wav_path)

    try:
        text, info = transcribe_stt(engine, wav_path)
    except SystemExit:
        raise
    except Exception as error:  # noqa: BLE001
        fail("Talegjenkjenning feilet: %s" % error)

    ok(text=text, engine=engine.get("id"), language=getattr(info, "language", "no"),
       duration=round(getattr(info, "duration", 0.0), 2))


def cmd_serve(args):
    """Resident worker for broen: leser JSON-linjer paa stdin, svarer med
    JSON-linjer paa stdout. Modeller holdes lastet i prosessen (_VIBE_CACHE
    og _STT_CACHE) saa samtale-motoren slipper aa laste paa nytt per kall."""
    import types  # noqa: F401

    global _REPLY_FILE
    commands = {"ping", "status", "tts", "stt", "quit"}
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        _REPLY_FILE = None
        try:
            req = json.loads(line)
        except (json.JSONDecodeError, ValueError) as error:
            print(json.dumps({"ok": False, "error": "Ugyldig JSON: %s" % error},
                             ensure_ascii=False), flush=True)
            continue
        command = req.get("command", "")
        if command not in commands:
            print(json.dumps({"ok": False, "error": "Ukjent kommando: %s" % command},
                             ensure_ascii=False), flush=True)
            continue
        if command == "ping":
            _REPLY_FILE = req.get("resp")
            print(json.dumps({"ok": True, "engine": "worker"}, ensure_ascii=False), flush=True)
            continue
        if command == "quit":
            print(json.dumps({"ok": True, "bye": True}, ensure_ascii=False), flush=True)
            break

        _REPLY_FILE = (req.get("resp") or
                       (((req.get("out") or req.get("wav")) or "") + ".resp" or None))
        args_ns = types.SimpleNamespace(
            text=req.get("text", ""),
            out=req.get("out", ""),
            wav=req.get("wav", ""),
        )
        try:
            if command == "tts":
                cmd_tts(args_ns)
            elif command == "stt":
                cmd_stt(args_ns)
            else:
                cmd_status(args_ns)
        except SystemExit:
            pass  # ok()/fail() skriver allerede JSON-linjen (og .resp-filen)
        except Exception as error:  # noqa: BLE001
            print(json.dumps({"ok": False, "error": str(error)},
                             ensure_ascii=False), flush=True)
        if _REPLY_FILE and not os.path.isfile(_REPLY_FILE):
            try:
                with open(_REPLY_FILE, "w", encoding="utf-8") as handle:
                    handle.write(json.dumps({"ok": False, "error": "Ukjent feil etter %s" % command},
                                            ensure_ascii=False))
            except OSError:
                pass


def cmd_status(args):
    config = load_config()
    out = {
        "ok": True,
        "tts": config.get("tts"),
        "stt": config.get("stt"),
        "python": sys.version.split()[0],
    }
    print(json.dumps(out, ensure_ascii=False))
    sys.exit(0)


def main():
    parser = argparse.ArgumentParser(description="Nimay OMP voice runtime")
    sub = parser.add_subparsers(dest="command")
    tts = sub.add_parser("tts")
    tts.add_argument("--text", required=True)
    tts.add_argument("--out", required=True)
    stt = sub.add_parser("stt")
    stt.add_argument("--wav", required=True)
    status = sub.add_parser("status")
    sub.add_parser("serve")
    args = parser.parse_args()
    if args.command == "tts":
        cmd_tts(args)
    elif args.command == "stt":
        cmd_stt(args)
    elif args.command == "status":
        cmd_status(args)
    elif args.command == "serve":
        cmd_serve(args)
    else:
        parser.print_help()
        sys.exit(2)


if __name__ == "__main__":
    main()
