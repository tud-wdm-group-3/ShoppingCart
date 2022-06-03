#!/bin/bash

helm uninstall myzookeeper
helm uninstall mykafka
helm uninstall order-db
helm uninstall payment-db
helm uninstall stock-db

kubectl delete -f k8s/ingress.yml
kubectl delete -f k8s/order.yml
kubectl delete -f k8s/payment.yml
kubectl delete -f k8s/stock.yml

kubectl delete pvc --all
kubectl delete pv --all
