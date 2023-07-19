#!/usr/bin/env python3
"""CLI App for FedKit backend operations."""
import requests


def main():
    url = "http://localhost:8000/train/upload"  # Replace with your Django server URL
    files = {"file": open("test.txt", "rb")}
    data = {
        "name": "test_model",
        "layers_sizes": [100, 200, 300],
        "data_type": "test_type",
    }
    response = requests.post(url, data=data, files=files)
    print(response)
    print(response.text)


if __name__ == "__main__":
    main()
