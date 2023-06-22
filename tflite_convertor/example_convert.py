import os

import numpy as np
import pandas as pd
import tensorflow as tf

from .example_model import ExampleModel

data = pd.read_csv("example_data.csv", header=0, names=["step", "calorie", "distance"])

data = data.dropna()

x_train, y_train = data.iloc[:, 0:2].astype("float32"), data["distance"].to_frame(
    "distance"
).astype("float32")
assert x_train.shape == (11, 2)
assert y_train.shape == (11, 1)

NUM_EPOCHS = 30
BATCH_SIZE = 1
epochs = np.arange(1, NUM_EPOCHS + 1, 1)
losses = np.zeros([NUM_EPOCHS])
m = ExampleModel(0.00000000003)

train_ds = tf.data.Dataset.from_tensor_slices((x_train, y_train))
train_ds = train_ds.batch(BATCH_SIZE)
for i in range(NUM_EPOCHS):
    for x, y in train_ds:
        result = m.train(x, y)

    losses[i] = result["loss"]
    if (i + 1) % 10 == 0:
        print(f"Finished {i+1} epochs")
        print(f"  loss: {losses[i]:.3f}")

h = m.model.predict(x_train)
assert h.shape == (11, 1)

SAVED_MODEL_DIR = "saved_model"

tf.saved_model.save(
    m,
    SAVED_MODEL_DIR,
    signatures={
        "train": m.train.get_concrete_function(),
        "infer": m.infer.get_concrete_function(),
        "save": m.save.get_concrete_function(),
        "restore": m.restore.get_concrete_function(),
    },
)

# Convert the model
converter = tf.lite.TFLiteConverter.from_saved_model(SAVED_MODEL_DIR)
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,  # enable TensorFlow Lite ops.
    tf.lite.OpsSet.SELECT_TF_OPS,  # enable TensorFlow ops.
]

converter.experimental_enable_resource_variables = True
tflite_model = converter.convert()

model_file_path = os.path.join("toy_regression.tflite")
with open(model_file_path, "wb") as model_file:
    model_file.write(tflite_model)

interpreter = tf.lite.Interpreter(model_path="toy_regression.tflite")
interpreter.allocate_tensors()

infer = interpreter.get_signature_runner("infer")

h1 = infer(x=x_train).get("logits")
print(h1[0])

train = interpreter.get_signature_runner("train")

result = train(
    x=np.array([[1837, 72.332]], dtype="float32"), y=np.array([[1311]], dtype="float32")
)

infer = interpreter.get_signature_runner("infer")

h1 = infer(x=x_train).get("logits")
print(h1[0])
