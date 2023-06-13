from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.status import HTTP_400_BAD_REQUEST, HTTP_404_NOT_FOUND
from rest_framework.views import Request
from train.models import TFLiteModel, TrainingDataType
from train.scheduler import server
from train.serializers import TFLiteModelSerializer


def model_for_data_type(data_type):
    if not type(data_type) == str:
        return
    try:
        data_type = TrainingDataType.objects.get(name=data_type)
        print(data_type)
        return TFLiteModel.objects.filter(data_type=data_type).last()
    except RuntimeError:
        return


# https://www.django-rest-framework.org/api-guide/views/#api_view
@api_view(["POST"])
# https://stackoverflow.com/questions/31335736/cannot-apply-djangomodelpermissions-on-a-view-that-does-not-have-queryset-pro
@permission_classes((permissions.AllowAny,))
def advertise_model(request):
    data_type = request.data.get("data_type")
    model = model_for_data_type(data_type)
    if model is None:
        model = TFLiteModel.objects.last()
    # TODO: Use request content to decide model.
    serializer = TFLiteModelSerializer(model)
    print(serializer.data)
    return Response(serializer.data)


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def request_server(request: Request):
    id = request.data.get("id")  # type: ignore
    if id is None:
        return Response("Model ID not specified", HTTP_400_BAD_REQUEST)
    try:
        model = TFLiteModel.objects.get(pk=id)
    except TFLiteModel.DoesNotExist:
        return Response("Model not found", HTTP_404_NOT_FOUND)
    data = server(model)
    return Response(data.__dict__)
