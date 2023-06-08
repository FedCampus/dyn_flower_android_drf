# TFLite Converter

This is the module to convert each TensorFlow model to the 5 `.tflite` files
required to train on device.

This does not work on ARM Macs.

## Use

1. Download the required dependencies.

    ```sh
    python3 -m pip install -r requirements.txt
    ```

1. Run the script.

    ```sh
    python3 convert_to_tflite.py
    ```
