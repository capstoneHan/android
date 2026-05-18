"""
Train a small TensorFlow Lite personalization ranker.

The model predicts how well one recommended product fits the user's local
preference profile. It is meant to rerank backend recommendation candidates
on device, not to store user preferences inside the .tflite file.

Output:
    app/src/main/assets/personalization_ranker.tflite

Later Android input order must match FEATURE_NAMES exactly.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import tensorflow as tf


ROOT_DIR = Path(__file__).resolve().parents[1]
OUTPUT_PATH = ROOT_DIR / "app" / "src" / "main" / "assets" / "personalization_ranker.tflite"
RANDOM_SEED = 42

FEATURE_NAMES = [
    "server_match_score",
    "price_score",
    "category_affinity",
    "fit_affinity",
    "tag_weight_sum",
    "tag_weight_average",
    "positive_tag_ratio",
    "negative_tag_ratio",
    "tag_combo_score",
]

TAG_VOCAB = [
    "clean_line_friendly",
    "relaxed_fit_friendly",
    "warm_soft_palette",
    "cool_clear_palette",
    "structured_top_candidate",
    "leg_lengthening_recommended",
    "waist_defined",
    "straight_body_line",
    "balanced_silhouette",
    "comfort_fit_friendly",
    "regular_fit_friendly",
    "sports_style",
]

POSITIVE_COMBOS = {
    frozenset(["clean_line_friendly", "leg_lengthening_recommended"]): 0.18,
    frozenset(["relaxed_fit_friendly", "comfort_fit_friendly"]): 0.14,
    frozenset(["cool_clear_palette", "structured_top_candidate"]): 0.12,
    frozenset(["waist_defined", "clean_line_friendly"]): 0.12,
}

NEGATIVE_COMBOS = {
    frozenset(["warm_soft_palette", "sports_style"]): -0.16,
    frozenset(["structured_top_candidate", "relaxed_fit_friendly"]): -0.10,
    frozenset(["straight_body_line", "comfort_fit_friendly"]): -0.08,
}


def sigmoid(value: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-value))


def combo_score(tags: list[str]) -> float:
    tag_set = set(tags)
    score = 0.0
    for combo, weight in POSITIVE_COMBOS.items():
        if combo.issubset(tag_set):
            score += weight
    for combo, weight in NEGATIVE_COMBOS.items():
        if combo.issubset(tag_set):
            score += weight
    return float(np.clip(score, -0.3, 0.3))


def make_synthetic_dataset(sample_count: int = 6000) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(RANDOM_SEED)
    rows: list[list[float]] = []
    labels: list[list[float]] = []

    for _ in range(sample_count):
        tag_count = int(rng.integers(1, 5))
        product_tags = rng.choice(TAG_VOCAB, size=tag_count, replace=False).tolist()

        tag_weights = {
            tag: float(rng.uniform(-1.0, 1.0))
            for tag in TAG_VOCAB
        }
        selected_weights = np.array([tag_weights[tag] for tag in product_tags], dtype=np.float32)

        server_match_score = float(rng.uniform(0.35, 1.0))
        price_score = float(rng.uniform(0.0, 1.0))
        category_affinity = float(rng.uniform(-1.0, 1.0))
        fit_affinity = float(rng.uniform(-1.0, 1.0))
        tag_weight_sum = float(np.clip(selected_weights.sum() / 4.0, -1.0, 1.0))
        tag_weight_average = float(selected_weights.mean())
        positive_tag_ratio = float((selected_weights > 0.15).sum() / tag_count)
        negative_tag_ratio = float((selected_weights < -0.15).sum() / tag_count)
        tag_combo = combo_score(product_tags)

        # This teacher formula creates labels that are more expressive than a
        # plain tag-weight sum: combinations, price, category, and fit can bend
        # the final preference up or down.
        raw_score = (
            1.25 * server_match_score
            + 1.10 * tag_weight_average
            + 0.55 * tag_weight_sum
            + 0.35 * category_affinity
            + 0.30 * fit_affinity
            + 0.75 * tag_combo
            - 0.28 * price_score
            + 0.20 * positive_tag_ratio
            - 0.32 * negative_tag_ratio
            - 0.55
        )
        personalized_score = float(sigmoid(np.array(raw_score)))

        # Add a little noise so the model does not simply memorize a perfectly
        # clean synthetic rule.
        personalized_score = float(
            np.clip(personalized_score + rng.normal(0.0, 0.025), 0.0, 1.0)
        )

        rows.append(
            [
                server_match_score,
                price_score,
                category_affinity,
                fit_affinity,
                tag_weight_sum,
                tag_weight_average,
                positive_tag_ratio,
                negative_tag_ratio,
                tag_combo,
            ]
        )
        labels.append([personalized_score])

    return np.array(rows, dtype=np.float32), np.array(labels, dtype=np.float32)


def build_model() -> tf.keras.Model:
    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(len(FEATURE_NAMES),), name="ranker_features"),
            tf.keras.layers.Dense(24, activation="relu"),
            tf.keras.layers.Dense(12, activation="relu"),
            tf.keras.layers.Dense(1, activation="sigmoid", name="personalized_score"),
        ]
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="mse",
        metrics=["mae"],
    )
    return model


def export_tflite(model: tf.keras.Model, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    output_path.write_bytes(tflite_model)


def main() -> None:
    np.random.seed(RANDOM_SEED)
    tf.random.set_seed(RANDOM_SEED)

    x, y = make_synthetic_dataset()
    model = build_model()

    model.fit(
        x,
        y,
        epochs=80,
        batch_size=32,
        validation_split=0.2,
        verbose=2,
    )

    export_tflite(model, OUTPUT_PATH)

    print("Saved:", OUTPUT_PATH)
    print("Input feature order:")
    for index, name in enumerate(FEATURE_NAMES):
        print(f"  {index}: {name}")

    # TODO: Replace make_synthetic_dataset() with real feedback exported from
    # Room when enough rating/dwell data exists.
    # TODO: Keep TAG_VOCAB and FEATURE_NAMES synchronized with the Android
    # TFLite input builder, otherwise inference scores will be meaningless.


if __name__ == "__main__":
    main()
