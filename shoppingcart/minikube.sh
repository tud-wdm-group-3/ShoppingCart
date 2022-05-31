#!/bin/bash

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm install -f helm/zookeeper.yml myzookeeper bitnami/zookeeper
helm install -f helm/kafka.yml mykafka bitnami/kafka

kubectl apply -f k8s/ingress.yml
kubectl apply -f k8s/order.yml
kubectl apply -f k8s/payment.yml
kubectl apply -f k8s/stock.yml
