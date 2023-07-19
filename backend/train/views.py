import logging
from typing import OrderedDict

from django.core.files.uploadedfile import UploadedFile
from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.request import MultiValueDict
from rest_framework.response import Response
from rest_framework.status import HTTP_400_BAD_REQUEST, HTTP_404_NOT_FOUND
from rest_framework.views import Request
from train.models import TFLiteModel, TrainingDataType
from train.scheduler import server
from train.serializers import *

from backend.settings import STATICFILES_DIRS

logger = logging.getLogger(__name__)


def model_for_data_type(data_type):
    if not type(data_type) == str:
        logger.error(f"Looking up model for non-string data_type `{data_type}`.")
        return
    try:
        data_type = TrainingDataType.objects.get(name=data_type)
        return TFLiteModel.objects.filter(data_type=data_type).last()
    except Exception as err:
        logger.error(f"{err} while looking up model for data_type `{data_type}`.")
        return


# https://www.django-rest-framework.org/api-guide/views/#api_view
@api_view(["POST"])
# https://stackoverflow.com/questions/31335736/cannot-apply-djangomodelpermissions-on-a-view-that-does-not-have-queryset-pro
@permission_classes((permissions.AllowAny,))
def advertise_model(request):
    data_type = request.data.get("data_type")
    model = model_for_data_type(data_type)
    if model is None:
        return Response("No model corresponding to data_type", HTTP_404_NOT_FOUND)
    serializer = TFLiteModelSerializer(model)
    return Response(serializer.data)


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def request_server(request: Request):
    serializer = PostServerDataSerializer(data=request.data)  # type: ignore
    if not serializer.is_valid():
        logger.error(serializer.errors)
        return Response(serializer.errors, HTTP_400_BAD_REQUEST)
    data: OrderedDict = serializer.validated_data  # type: ignore
    try:
        model = TFLiteModel.objects.get(pk=data["id"])
    except TFLiteModel.DoesNotExist:
        logger.error(f"Model with id {data['id']} not found.")
        return Response("Model not found", HTTP_404_NOT_FOUND)
    response = server(model, data["start_fresh"])
    return Response(response.__dict__)


def file_in_request(request: Request):
    files = request.FILES
    if isinstance(files, MultiValueDict):
        file = files.get("file")
        if isinstance(file, UploadedFile):
            return file


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def upload_file(request: Request):
    # TODO: Other parameters.
    file = file_in_request(request)
    if file is None:
        return Response("No file in request.", HTTP_400_BAD_REQUEST)
    name = file.name
    # TODO: Validate unique file name.
    with open(STATICFILES_DIRS[0] / name, "wb") as fd:
        fd.write(file.file.read())
    # TODO: Save TFLiteModel object.
    return Response("ok")
