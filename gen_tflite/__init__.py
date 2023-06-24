import tensorflow as tf


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
