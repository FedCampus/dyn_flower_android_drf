import tensorflow as tf


class ExampleModel(tf.Module):
    def __init__(self, lr=0.000000001):
        self.model = tf.keras.Sequential(
            [
                tf.keras.layers.Dense(
                    units=1,
                    input_shape=[
                        2,
                    ],
                    name="regression",
                )
            ]
        )

        opt = tf.keras.optimizers.SGD(learning_rate=lr)

        self.model.compile(optimizer=opt, loss=tf.keras.losses.MeanSquaredError())

    # The `train` function takes a batch of input images and labels.
    @tf.function(
        input_signature=[
            tf.TensorSpec([None, 2], tf.float32),
            tf.TensorSpec([None, 1], tf.float32),
        ]
    )
    def train(self, x, y):
        with tf.GradientTape() as tape:
            prediction = self.model(x)
            loss = self.model.loss(y, prediction)
        gradients = tape.gradient(loss, self.model.trainable_variables)
        self.model.optimizer.apply_gradients(
            zip(gradients, self.model.trainable_variables)
        )
        result = {"loss": loss}
        return result

    @tf.function(
        input_signature=[
            tf.TensorSpec([None, 2], tf.float32),
        ]
    )
    def infer(self, x):
        logits = self.model(x)
        return {"logits": logits}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def save(self, checkpoint_path):
        tensor_names = [weight.name for weight in self.model.weights]
        tensors_to_save = [weight.read_value() for weight in self.model.weights]
        tf.raw_ops.Save(
            filename=checkpoint_path,
            tensor_names=tensor_names,
            data=tensors_to_save,
            name="save",
        )
        return {"checkpoint_path": checkpoint_path}

    @tf.function(input_signature=[tf.TensorSpec(shape=[], dtype=tf.string)])
    def restore(self, checkpoint_path):
        restored_tensors = {}
        for var in self.model.weights:
            restored = tf.raw_ops.Restore(
                file_pattern=checkpoint_path,
                tensor_name=var.name,
                dt=var.dtype,
                name="restore",
            )
            var.assign(restored)
            restored_tensors[var.name] = restored
        return restored_tensors
