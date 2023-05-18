# Android Client

## Set up

For the first time you build, uncomment the last 33 lines in `client/app/build.gradle` to download the models (unnecessary) and the data (necessary).

## Run the demo

Set up and start the backend server according `Set up` and `Development` sections in `../backend/README.md`.

Install the app on *physical* Android devices and launch it.

In the user interface, fill in:

- Device number: a unique number among 1 ~ 10.
- Server IP: an IPv4 address of the computer your backend server is running on. You can probably find it in your system network settings.
- Server port: 8000, if you follow the `Development` section mentioned above.

Push the second button and connect to the backend server. This should take little time.

Push the first button and load the dataset. This may take a minute.

Push the last button and start the training.
