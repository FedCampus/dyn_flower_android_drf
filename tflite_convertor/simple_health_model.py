import tensorflow as tf
from tfltransfer import bases, heads, optimizers
from tfltransfer.tflite_transfer_converter import TFLiteTransferConverter


def define_base_model():
    """Define the base model.

    To be compatible with TFLite Model Personalization, we need to define a
    base model and a head model.

    Here we are using an identity layer for base model, which just passes the
    input as it is to the head model.
    """
    base = tf.keras.Sequential(
        [tf.keras.Input(shape=(2,)), tf.keras.layers.Lambda(lambda x: x)]
    )

    base.compile(loss="categorical_crossentropy", optimizer="sgd")
    base.save("identity_model", save_format="tf")


def define_head_model():
    """Define the head model.

    This is the model architecture that we will train using Flower.
    """
    head = tf.keras.Sequential(
        [
            tf.keras.Input(shape=(2,)),
            tf.keras.layers.Dense(units=50, activation="relu"),
            tf.keras.layers.Dense(units=20, activation="relu"),
            tf.keras.layers.Dense(units=3, activation="softmax"),
        ]
    )

    head.compile(loss="categorical_crossentropy", optimizer="sgd")
    return head


def convert_to_tflite(head):
    base_path = bases.saved_model_base.SavedModelBase("identity_model")
    converter = TFLiteTransferConverter(
        3,
        base_path,
        heads.KerasModelHead(head),
        optimizers.SGD(1e-3),
        train_batch_size=1,
    )

    converter.convert_and_save("tflite_model")


def main():
    define_base_model()
    head = define_head_model()
    convert_to_tflite(head)


if __name__ == "__main__":
    main()
