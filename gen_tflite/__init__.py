import tensorflow as tf

SAVED_MODEL_DIR = "saved_model"


def red(string: str) -> str:
    return f"\033[91m{string}\033[0m"


class BaseModel(tf.Module):
    model: tf.keras.Model

    @tf.function
    def train(self, x, y):
        return self.model.train_step((x, y))

    @tf.function
    def infer(self, x):
        return {"logits": self.model(x)}

    @tf.function(input_signature=[])
    def parameters(self):
        return [weight.read_value() for weight in self.model.weights]

    @tf.function
    def restore(self, **parameters):
        for index, weight in enumerate(self.model.weights):
            parameter = parameters[f"output_{index}"]
            weight.assign(parameter)
        assert self.parameters is not None
        return self.parameters()


def save_model(model, saved_model_dir):
    parameters = model.parameters.get_concrete_function()
    init_params = parameters()
    print(f"Initial parameters is {init_params}.")
    transformed_params = {
        f"output_{index}": param for index, param in enumerate(init_params)
    }
    print(f"Transformed parameters is {transformed_params}.")
    restore = model.restore.get_concrete_function(**transformed_params)
    restore_test = restore(**transformed_params)
    print(f"Restore test result: {restore_test}.")
    tf.saved_model.save(
        model,
        saved_model_dir,
        signatures={
            "train": model.train.get_concrete_function(),
            "infer": model.infer.get_concrete_function(),
            "parameters": parameters,
            "restore": restore,
        },
    )

    shape = f"{[param.shape.as_list() for param in init_params]}"
    print(f"Model parameter shape: {red(shape)}.")


def convert_saved_model(saved_model_dir):
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,  # enable TensorFlow Lite ops.
        tf.lite.OpsSet.SELECT_TF_OPS,  # enable TensorFlow ops.
    ]

    converter.experimental_enable_resource_variables = True
    tflite_model = converter.convert()

    return tflite_model


def parameters_from_raw_dict(raw_dict):
    parameters = []
    index = 0
    while True:
        parameter = raw_dict.get(f"output_{index}")
        if parameter is None:
            break
        parameters.append(parameter)
        index += 1
    return parameters


def save_tflite_model(tflite_model, tflite_file):
    with open(tflite_file, "wb") as model_file:
        return model_file.write(tflite_model)
