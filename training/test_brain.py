#!/usr/bin/env python3
from train_brain import features, fnv1a, normalize


def test_normalization() -> None:
    assert normalize("  ÁUDIOS, do João! ") == "audios do joao"


def test_features_are_deterministic() -> None:
    first = features("Ouça o áudio")
    second = features("ouca o audio")
    assert (first == second).all()
    assert abs(float((first * first).sum()) - 1.0) < 1e-5


def test_hash_is_stable() -> None:
    assert fnv1a("audio") == 0xE0613999


if __name__ == "__main__":
    test_normalization()
    test_features_are_deterministic()
    test_hash_is_stable()
    print("training tests: ok")
