import tensorflow as tf

from .. import *


@tflite_model_class
class FedMCRNNModel(BaseTFLiteModel):
    X_SHAPE = [6, 7]
    Y_SHAPE = [1]

    def __init__(self):
        self.model = tf.keras.models.Sequential(
            [
                tf.keras.layers.Conv1D(input_shape=(6, 7), filters=7, kernel_size=2),
                tf.keras.layers.BatchNormalization(),
                tf.keras.layers.Conv1D(filters=7, kernel_size=2),
                tf.keras.layers.BatchNormalization(),
                tf.keras.layers.Conv1D(filters=7, kernel_size=1),
                tf.keras.layers.BatchNormalization(),
                tf.keras.layers.Dense(units=4, activation="relu"),
                tf.keras.layers.LSTM(units=7, return_sequences=True),
                tf.keras.layers.Dropout(0.2),
                tf.keras.layers.LSTM(units=7, return_sequences=True),
                tf.keras.layers.Dropout(0.2),
                tf.keras.layers.LSTM(units=7, return_sequences=True),
                tf.keras.layers.Dropout(0.2),
                tf.keras.layers.Conv1D(filters=7, kernel_size=4),
                tf.keras.layers.Dense(units=1, activation="relu"),
            ]
        )
        self.model.compile(loss=tf.keras.losses.MeanSquaredError())
