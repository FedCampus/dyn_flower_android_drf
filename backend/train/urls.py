from django.urls import path
from train.views import advertise_model

urlpatterns = [path("get_advertised", advertise_model)]
