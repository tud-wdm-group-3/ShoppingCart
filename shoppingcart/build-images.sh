#!/bin/bash

docker build -t order:latest -f Dockerfile-order .
docker build -t payment:latest -f Dockerfile-payment .
docker build -t stock:latest -f Dockerfile-stock .

minikube image load order:latest
minikube image load payment:latest
minikube image load stock:latest
