from django.db import models


class TFLiteModel(models.Model):
    name = models.CharField(
        max_length=64,
        unique=True,
        null=False,
        editable=False,
    )
    n_layers = models.IntegerField(null=False, editable=False)


class TFLiteFile(models.Model):
    path = models.CharField(max_length=64, unique=True, null=False, editable=False)
    tflite_model = models.ForeignKey(
        TFLiteModel, on_delete=models.CASCADE, related_name="tflite_files"
    )

    def __str__(self) -> str:
        return f"{self.path}"
