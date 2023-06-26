from train.models import *
from train.serializers import *

d = TrainingDataType(name="CIFAR10_32x32x3")
d.save()
file = "/static/cifar10.tflite"
m = TFLiteModel(name="CIFAR10", file_path=file, n_layers=10, data_type=d)
m.save()
s = TFLiteModelSerializer(m)
assert s.data == {
    "id": 1,
    "name": "CIFAR10",
    "file_path": file,
    "n_layers": 10,
}
print("Successfully added CIFAR10 data type and model to the database.")
