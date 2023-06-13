from train.models import *
from train.serializers import *

d = TrainingDataType(name="CIFAR10_32x32x3")
d.save()
m = TFLiteModel(name="CIFAR10_model", n_layers=10, data_type=d)
m.save()
files_names = ["bottleneck", "inference", "initialize", "optimizer", "train_head"]
for name in files_names:
    f = TFLiteFile(path=f"/static/CIFAR10_model/{name}.tflite", tflite_model=m)
    f.save()
s = TFLiteModelSerializer(m)
assert s.data == {
    "id": 1,
    "name": "CIFAR10_model",
    "n_layers": 10,
    "tflite_files": [
        "/static/CIFAR10_model/bottleneck.tflite",
        "/static/CIFAR10_model/inference.tflite",
        "/static/CIFAR10_model/initialize.tflite",
        "/static/CIFAR10_model/optimizer.tflite",
        "/static/CIFAR10_model/train_head.tflite",
    ],
}
print("Successfully added CIFAR10 data type and model to the database.")
