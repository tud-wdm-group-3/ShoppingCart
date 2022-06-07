#!/bin/bash

usage() {
  echo "usage: $0 <start|purge|update-repo>"
  exit 1
}

start() {
  helm install -f helm/zookeeper.yml zookeeper bitnami/zookeeper
  helm install -f helm/kafka.yml kafka bitnami/kafka

  helm install -f helm/postgres.yml order-0-db bitnami/postgresql-ha
  helm install -f helm/postgres.yml order-1-db bitnami/postgresql-ha
  helm install -f helm/postgres.yml payment-0-db bitnami/postgresql-ha
  helm install -f helm/postgres.yml payment-1-db bitnami/postgresql-ha
  helm install -f helm/postgres.yml stock-0-db bitnami/postgresql-ha
  helm install -f helm/postgres.yml stock-1-db bitnami/postgresql-ha

  kubectl apply -f k8s/ingress.yml

  kubectl apply -f k8s/order.yml
  kubectl apply -f k8s/payment.yml
  kubectl apply -f k8s/stock.yml
}

purge() {
  helm uninstall zookeeper
  helm uninstall kafka

  helm uninstall order-0-db
  helm uninstall order-1-db
  helm uninstall payment-0-db
  helm uninstall payment-1-db
  helm uninstall stock-0-db
  helm uninstall stock-1-db

  kubectl delete -f k8s/ingress.yml

  kubectl delete -f k8s/order.yml
  kubectl delete -f k8s/payment.yml
  kubectl delete -f k8s/stock.yml

  kubectl delete pvc --all
  kubectl delete pv --all
}

update_repo() {
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update
}

if [[ "$#" != 1 ]]; then
  usage
fi

case "$1" in
  "start")
    start
    ;;
  "purge")
    purge
    ;;
  "update-repo")
    update_repo
    ;;
  *) usage ;;
esac
