from django.urls import path
from train.views import advertise_model, request_server

urlpatterns = [path("get_advertised", advertise_model), path("server", request_server)]
