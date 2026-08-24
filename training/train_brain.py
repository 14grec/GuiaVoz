#!/usr/bin/env python3
"""Treina e exporta o cérebro neural compacto do GuiaVoz.

O arquivo binário usa somente tipos primitivos big-endian para que a inferência
Android possa ser implementada em Java puro, sem dependências de runtime.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import struct
import unicodedata
from pathlib import Path

import numpy as np
from sklearn.metrics import accuracy_score, classification_report
from sklearn.neural_network import MLPClassifier

MAGIC = b"GVB2"
INPUT_DIM = 768
NGRAM_MIN = 2
NGRAM_MAX = 5

SYNONYM_GROUPS = [
    ["abra", "abre", "abrir", "inicie", "iniciar", "entre no", "va para"],
    ["toque", "toca", "tocar", "reproduza", "reproduzir", "ouca", "ouvir", "escute", "escutar", "execute"],
    ["audio", "audios", "mensagem de voz", "mensagens de voz", "recado de voz", "recados de voz"],
    ["whatsapp", "whats", "zap"],
    ["ligue", "ligar", "telefone", "telefonar", "chame", "chamar", "faca uma ligacao"],
    ["leia", "ler", "fale", "diga", "mostre", "informe"],
    ["mensagem", "mensagens", "conversa", "conversas", "o que chegou", "o que recebi"],
    ["clique", "clicar", "toque", "tocar", "aperte", "pressione", "selecione", "escolha"],
    ["role para baixo", "desca a tela", "deslize para baixo", "avance a pagina"],
    ["role para cima", "suba a tela", "deslize para cima", "volte a pagina"],
    ["volte", "retorne", "va para a tela anterior", "volte um passo"],
    ["continue", "prossiga", "retome", "volte de onde parou"],
    ["pause", "pare", "interrompa", "segure"],
    ["proximo", "seguinte", "outro"],
    ["aplicativo", "app", "programa"],
    ["mande", "mandar", "envie", "enviar", "escreva", "avise"],
    ["responda", "responder", "responde", "retorne"],
]
FILLERS = ["por favor", "agora", "para mim", "voce pode", "eu quero que", "preciso que"]
STOP_WORDS = {"o", "a", "os", "as", "um", "uma", "me", "meu", "minha", "do", "da", "de", "no", "na"}


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text or "")
    without_marks = "".join(char for char in decomposed if unicodedata.category(char) != "Mn")
    cleaned = "".join(char.lower() if char.isalnum() or char in "+ " else " " for char in without_marks)
    return " ".join(cleaned.split())


def fnv1a(value: str) -> int:
    result = 0x811C9DC5
    for byte in value.encode("utf-8"):
        result ^= byte
        result = (result * 0x01000193) & 0xFFFFFFFF
    return result


def features(text: str) -> np.ndarray:
    normalized = f"^{normalize(text)}$"
    vector = np.zeros(INPUT_DIM, dtype=np.float32)
    for size in range(NGRAM_MIN, NGRAM_MAX + 1):
        for index in range(max(0, len(normalized) - size + 1)):
            token = normalized[index:index + size]
            vector[fnv1a(token) % INPUT_DIM] += 1.0
    norm = float(np.linalg.norm(vector))
    if norm > 0:
        vector /= norm
    return vector


def augment(text: str, seed: int, limit: int = 48) -> list[str]:
    """Gera variações linguísticas somente para o conjunto de treino."""
    base = normalize(text)
    candidates = {base}
    for _ in range(2):
        snapshot = sorted(candidates)
        for candidate in snapshot:
            padded = f" {candidate} "
            for group in SYNONYM_GROUPS:
                found = next((item for item in group if f" {item} " in padded), None)
                if found is None:
                    continue
                for replacement in group:
                    candidates.add(padded.replace(f" {found} ", f" {replacement} ").strip())
            if len(candidates) >= limit:
                break
        if len(candidates) >= limit:
            break
    rng = random.Random(seed ^ fnv1a(base))
    core = list(candidates)
    rng.shuffle(core)
    candidates.update(f"{filler} {candidate}" for filler, candidate in zip(FILLERS, core[:len(FILLERS)]))
    candidates.update(f"{candidate} por favor" for candidate in core[:4])
    compact = " ".join(word for word in base.split() if word not in STOP_WORDS)
    if compact:
        candidates.add(compact)
    result = sorted(candidates)
    rng.shuffle(result)
    return result[:limit]


def load_dataset(path: Path) -> tuple[list[str], list[str]]:
    texts: list[str] = []
    labels: list[str] = []
    with path.open("r", encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source):
            text = row["text"].strip()
            intent = row["intent"].strip()
            if text and intent:
                texts.append(text)
                labels.append(intent)
    if len(set(labels)) < 2:
        raise ValueError("O corpus precisa ter pelo menos duas intenções.")
    return texts, labels


def write_int(output, value: int) -> None:
    output.write(struct.pack(">i", value))


SEMANTICS = {
    "HELP": ("HELP", "NONE", "SYSTEM"),
    "TIME": ("GET", "TIME", "SYSTEM"),
    "OPEN_APP": ("OPEN", "APP", "ANY"),
    "LIST_APPS": ("LIST", "APP", "ANY"),
    "DIAL": ("CALL", "CONTACT", "PHONE"),
    "SMS": ("SEND", "MESSAGE", "SMS"),
    "MAP": ("OPEN", "MAP", "SYSTEM"),
    "ACCESSIBILITY_SETTINGS": ("OPEN", "ACCESSIBILITY", "SYSTEM"),
    "WHATSAPP_MESSAGES": ("READ", "MESSAGE", "WHATSAPP"),
    "WHATSAPP_LISTEN_MESSAGES": ("LISTEN", "MESSAGE", "WHATSAPP"),
    "WHATSAPP_CALL": ("CALL", "CONTACT", "WHATSAPP"),
    "WHATSAPP_SEND_MESSAGE": ("SEND", "MESSAGE", "WHATSAPP"),
    "WHATSAPP_REPLY_MESSAGE": ("REPLY", "MESSAGE", "WHATSAPP"),
    "PLAY_WHATSAPP_AUDIO": ("PLAY", "AUDIO", "WHATSAPP"),
    "PLAY_NEXT_AUDIO": ("NEXT", "AUDIO", "WHATSAPP"),
    "REPEAT_AUDIO": ("REPEAT", "AUDIO", "WHATSAPP"),
    "CONTINUE_SESSION": ("CONTINUE", "SESSION", "WHATSAPP"),
    "MEDIA_PLAY": ("PLAY", "MEDIA", "ANY"),
    "MEDIA_PAUSE": ("PAUSE", "MEDIA", "ANY"),
    "MEDIA_NEXT": ("NEXT", "MEDIA", "ANY"),
    "READ_SCREEN": ("READ", "SCREEN", "CURRENT"),
    "TAP_ELEMENT": ("TAP", "ELEMENT", "CURRENT"),
    "TYPE_TEXT": ("TYPE", "TEXT", "CURRENT"),
    "SCROLL_DOWN": ("SCROLL_DOWN", "SCREEN", "CURRENT"),
    "SCROLL_UP": ("SCROLL_UP", "SCREEN", "CURRENT"),
    "BACK": ("BACK", "SCREEN", "CURRENT"),
}


def train_head(x_train: np.ndarray, labels: list[str], seed: int) -> MLPClassifier:
    grouped: dict[str, list[int]] = {}
    for index, label in enumerate(labels):
        grouped.setdefault(label, []).append(index)
    maximum = max(len(indexes) for indexes in grouped.values())
    balanced_indexes: list[int] = []
    rng = random.Random(seed)
    for indexes in grouped.values():
        balanced_indexes.extend(indexes)
        balanced_indexes.extend(rng.choice(indexes) for _ in range(maximum - len(indexes)))
    rng.shuffle(balanced_indexes)
    balanced_x = x_train[balanced_indexes]
    balanced_labels = [labels[index] for index in balanced_indexes]
    classifier = MLPClassifier(
        hidden_layer_sizes=(128,),
        activation="relu",
        solver="adam",
        alpha=0.0008,
        batch_size=64,
        learning_rate_init=0.003,
        max_iter=500,
        early_stopping=False,
        random_state=seed,
    )
    classifier.fit(balanced_x, balanced_labels)
    return classifier


def write_text(output, value: str) -> None:
    encoded = value.encode("utf-8")
    write_int(output, len(encoded))
    output.write(encoded)


def export_model(path: Path, classifiers: dict[str, MLPClassifier]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as output:
        output.write(MAGIC)
        write_int(output, INPUT_DIM)
        write_int(output, NGRAM_MIN)
        write_int(output, NGRAM_MAX)
        write_int(output, len(classifiers))
        for name, classifier in classifiers.items():
            write_text(output, name)
            write_int(output, len(classifier.classes_))
            for label in classifier.classes_:
                write_text(output, str(label))
            write_int(output, len(classifier.coefs_))
            for weights, biases in zip(classifier.coefs_, classifier.intercepts_):
                matrix = np.asarray(weights, dtype=">f4")
                bias = np.asarray(biases, dtype=">f4")
                write_int(output, matrix.shape[0])
                write_int(output, matrix.shape[1])
                output.write(matrix.tobytes(order="C"))
                write_int(output, bias.shape[0])
                output.write(bias.tobytes(order="C"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path(__file__).with_name("intents.csv"))
    parser.add_argument("--evaluation", type=Path, default=Path(__file__).with_name("intents_eval.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parents[1] / "app/src/main/assets/guiavoz_brain.bin")
    parser.add_argument("--report", type=Path, default=Path(__file__).with_name("training_report.json"))
    parser.add_argument("--seed", type=int, default=42)
    arguments = parser.parse_args()

    texts, labels = load_dataset(arguments.data)
    test_texts, y_test = load_dataset(arguments.evaluation)
    augmented_texts: list[str] = []
    y_train: list[str] = []
    for index, (text, label) in enumerate(zip(texts, labels)):
        variants = augment(text, arguments.seed + index)
        augmented_texts.extend(variants)
        y_train.extend([label] * len(variants))
    x_train = np.stack([features(text) for text in augmented_texts])
    x_test = np.stack([features(text) for text in test_texts])
    unknown = sorted(set(y_train + y_test) - set(SEMANTICS))
    if unknown:
        raise ValueError(f"Intenções sem semântica hierárquica: {unknown}")
    head_labels = {
        "action": [SEMANTICS[label][0] for label in y_train],
        "object": [SEMANTICS[label][1] for label in y_train],
        "channel": [SEMANTICS[label][2] for label in y_train],
        "intent": y_train,
    }
    classifiers = {
        name: train_head(x_train, labels, arguments.seed + index)
        for index, (name, labels) in enumerate(head_labels.items())
    }
    predictions = {name: classifier.predict(x_test) for name, classifier in classifiers.items()}
    probabilities = {name: classifier.predict_proba(x_test) for name, classifier in classifiers.items()}
    confidence = probabilities["intent"].max(axis=1)
    predicted = predictions["intent"]
    expected_heads = {
        "action": [SEMANTICS[label][0] for label in y_test],
        "object": [SEMANTICS[label][1] for label in y_test],
        "channel": [SEMANTICS[label][2] for label in y_test],
        "intent": y_test,
    }
    head_accuracy = {
        name: float(accuracy_score(expected_heads[name], predictions[name]))
        for name in classifiers
    }
    structured_correct = [
        all(predictions[name][index] == expected_heads[name][index]
            for name in ("action", "object", "channel"))
        for index in range(len(y_test))
    ]
    tuple_to_intent = {value: intent for intent, value in SEMANTICS.items()}
    decoded: list[str] = []
    for index, intent in enumerate(predicted):
        semantic_tuple = tuple(predictions[name][index]
                               for name in ("action", "object", "channel"))
        semantic_intent = tuple_to_intent.get(semantic_tuple)
        # Decodificador restrito: uma saída terminal confiante repara combinações
        # impossíveis entre as cabeças; as cabeças continuam medindo a semântica.
        if semantic_intent is None or (semantic_intent != intent and confidence[index] >= 0.70):
            decoded.append(str(intent))
        else:
            decoded.append(semantic_intent)
    critical = {"WHATSAPP_SEND_MESSAGE", "WHATSAPP_REPLY_MESSAGE", "WHATSAPP_MESSAGES",
                "WHATSAPP_LISTEN_MESSAGES", "WHATSAPP_CALL"}
    critical_indexes = [index for index, label in enumerate(y_test) if label in critical]
    critical_accuracy = sum(decoded[index] == y_test[index] for index in critical_indexes) \
        / len(critical_indexes)
    report = {
        "examples": len(texts),
        "intents": len(set(labels)),
        "train_examples": len(labels),
        "augmented_train_examples": len(y_train),
        "test_examples": len(y_test),
        "accuracy": float(accuracy_score(y_test, decoded)),
        "raw_structured_accuracy": float(sum(structured_correct) / len(structured_correct)),
        "intent_accuracy": float(accuracy_score(y_test, predicted)),
        "critical_whatsapp_accuracy": float(critical_accuracy),
        "head_accuracy": head_accuracy,
        "mean_confidence": float(np.mean(confidence)),
        "min_confidence": float(np.min(confidence)),
        "classification": classification_report(y_test, decoded, output_dict=True, zero_division=0),
        "errors": [
            {
                "text": text,
                "expected": expected,
                "predicted": actual,
                "confidence": float(score),
            }
            for text, expected, actual, score in zip(test_texts, y_test, decoded, confidence)
            if expected != actual
        ],
        "model": {
            "kind": "hierarchical-multi-head-character-ngram-mlp",
            "input_dim": INPUT_DIM,
            "heads": {name: len(classifier.classes_) for name, classifier in classifiers.items()},
            "hidden_layers_per_head": [128],
            "ngram_range": [NGRAM_MIN, NGRAM_MAX],
            "parameters": int(sum(
                weights.size + bias.size
                for classifier in classifiers.values()
                for weights, bias in zip(classifier.coefs_, classifier.intercepts_)
            )),
        },
    }
    export_model(arguments.output, classifiers)
    arguments.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "decoded_accuracy": round(report["accuracy"], 4),
        "raw_structured_accuracy": round(report["raw_structured_accuracy"], 4),
        "intent_accuracy": round(report["intent_accuracy"], 4),
        "mean_confidence": round(report["mean_confidence"], 4),
        "model_bytes": arguments.output.stat().st_size,
        "examples": len(texts),
        "intents": len(set(labels)),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
