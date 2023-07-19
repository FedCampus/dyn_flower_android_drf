#!/usr/bin/env python3
"""CLI App for FedKit backend operations."""
import requests


def main():
    url = "http://localhost:8000/train/upload"  # Replace with your Django server URL
    files = {"file": open("test.txt", "rb")}
    response = requests.post(url, files=files)
    print(response)
    print(response.reason)


if __name__ == "__main__":
    main()
