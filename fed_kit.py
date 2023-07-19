#!/usr/bin/env python3
"""CLI App for FedKit backend operations."""
import requests

DEFAULT_URL = "http://localhost:8000/"


def upload(base: str, file: str, name: str, layers_sizes: list[int], data_type: str):
    url = base + "/train/upload"
    files = {"file": open(file, "rb")}
    data = {
        "name": name,
        "layers_sizes": layers_sizes,
        "data_type": data_type,
    }
    return requests.post(url, data=data, files=files)


def main():
    response = upload(
        DEFAULT_URL, "test.txt", "test_model", [100, 200, 300], "test_type"
    )
    print(response)
    print(response.text)


if __name__ == "__main__":
    main()
