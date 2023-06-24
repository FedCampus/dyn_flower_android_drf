import numpy as np
import pandas as pd
import tensorflow as tf

import example_model


def get_training_data():
    data = pd.read_csv(
        "example_data.csv", header=0, names=["step", "calorie", "distance"]
    ).dropna()

    x_train, y_train = data.iloc[:, 0:2].astype("float32"), data["distance"].to_frame(
        "distance"
    ).astype("float32")
    assert x_train.shape == (11, 2)
    assert y_train.shape == (11, 1)

    return x_train, y_train


def train_model(x_train, y_train):
    NUM_EPOCHS = 30
    BATCH_SIZE = 1
    losses = np.zeros([NUM_EPOCHS])
    model = example_model.ExampleModel(0.00000000003)

    train_ds = tf.data.Dataset.from_tensor_slices((x_train, y_train))
    train_ds = train_ds.batch(BATCH_SIZE)
    for i in range(NUM_EPOCHS):
        result = {}
        for x, y in train_ds:
            assert model.train is not None
            result = model.train(x, y)

        losses[i] = result["loss"]
        if (i + 1) % 10 == 0:
            print(f"Finished {i+1} epochs")
            print(f"  loss: {losses[i]:.3f}")

    h = model.model.predict(x_train)
    assert h.shape == (11, 1)

    return model


def save_model(model, saved_model_dir):
    tf.saved_model.save(
        model,
        saved_model_dir,
        signatures={
            "train": model.train.get_concrete_function(),
            "infer": model.infer.get_concrete_function(),
            "save": model.save.get_concrete_function(),
            "restore": model.restore.get_concrete_function(),
        },
    )


def convert_saved_model(saved_model_dir):
    # Convert the model
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,  # enable TensorFlow Lite ops.
        tf.lite.OpsSet.SELECT_TF_OPS,  # enable TensorFlow ops.
    ]

    converter.experimental_enable_resource_variables = True
    tflite_model = converter.convert()

    return tflite_model


def save_tflite_model(tflite_model, tflite_file):
    with open(tflite_file, "wb") as model_file:
        return model_file.write(tflite_model)


def test_tflite_file(tflite_file, x_train):
    interpreter = tf.lite.Interpreter(model_path=tflite_file)
    interpreter.allocate_tensors()

    infer = interpreter.get_signature_runner("infer")
    h1 = infer(x=x_train).get("logits")
    assert h1 is not None
    print(h1[0])

    train = interpreter.get_signature_runner("train")
    result = train(
        x=np.array([[1837, 72.332]], dtype="float32"),
        y=np.array([[1311]], dtype="float32"),
    )
    print(f"Training loss: {result['loss']}.")

    infer = interpreter.get_signature_runner("infer")
    h1 = infer(x=x_train).get("logits")
    assert h1 is not None
    print(h1[0])


SAVED_MODEL_DIR = "saved_model"
TFLITE_FILE = "toy_regression.tflite"


def main():
    x_train, y_train = get_training_data()
    model = train_model(x_train, y_train)
    save_model(model, SAVED_MODEL_DIR)
    tflite_model = convert_saved_model(SAVED_MODEL_DIR)
    save_tflite_model(tflite_model, TFLITE_FILE)
    test_tflite_file(TFLITE_FILE, x_train)


main() if __name__ == "__main__" else None
