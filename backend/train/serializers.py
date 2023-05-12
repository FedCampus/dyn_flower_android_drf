from rest_framework import serializers
from train.models import TFLiteModel


class TFLiteModelSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    n_layers = serializers.IntegerField()
    tflite_files = serializers.StringRelatedField(many=True)

    class Meta:
        model = TFLiteModel
        fields = ["id", "name", "n_layers", "tflite_files"]
