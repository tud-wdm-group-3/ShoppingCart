#!/bin/bash

#helm repo add bitnami https://charts.bitnami.com/bitnami
#helm repo update

helm install -f helm/zookeeper.yml myzookeeper bitnami/zookeeper
helm install -f helm/kafka.yml mykafka bitnami/kafka
helm install -f helm/postgres.yml order-db bitnami/postgresql-ha
helm install -f helm/postgres.yml payment-db bitnami/postgresql-ha
helm install -f helm/postgres.yml stock-db bitnami/postgresql-ha

kubectl apply -f k8s/ingress.yml
kubectl apply -f k8s/order.yml
kubectl apply -f k8s/payment.yml
kubectl apply -f k8s/stock.yml
