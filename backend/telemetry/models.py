from django.db import models
from train.models import TFLiteModel


class TrainingSession(models.Model):
    tflite_model = models.ForeignKey(
        TFLiteModel, on_delete=models.CASCADE, related_name="training_sessions"
    )
    start_time = models.DateTimeField(auto_now_add=True)
    end_time = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"Training session {self.id} for {self.tflite_model} {self.start_time} - {self.end_time}>"  # type: ignore
