#!/bin/bash
# Controller for the deployment


CLUSTER='minikube'

usage() {
  echo "usage: $0 <k8s|minikube> <start|purge|update-repo>"
  exit 1
}

start() {
  helm install -f "${CLUSTER}/helm/kafka.yml"     kafka     bitnami/kafka

  [[ "${CLUSTER}" == 'k8s' ]] && postgres='bitnami/postgresql-ha' || postgres='bitnami/postgresql'
  helm install -f "${CLUSTER}/helm/postgres.yml" order-db-0   "${postgres}"
  helm install -f "${CLUSTER}/helm/postgres.yml" order-db-1   "${postgres}"
  helm install -f "${CLUSTER}/helm/postgres.yml" payment-db-0 "${postgres}"
  helm install -f "${CLUSTER}/helm/postgres.yml" payment-db-1 "${postgres}"
  helm install -f "${CLUSTER}/helm/postgres.yml" stock-db-0   "${postgres}"
  helm install -f "${CLUSTER}/helm/postgres.yml" stock-db-1   "${postgres}"

  kubectl apply -f "${CLUSTER}/manifests/ingress.yml"

  kubectl apply -f "${CLUSTER}/manifests/orderwrapper.yml"
  kubectl apply -f "${CLUSTER}/manifests/paymentwrapper.yml"
  kubectl apply -f "${CLUSTER}/manifests/stockwrapper.yml"

  kubectl apply -f "${CLUSTER}/manifests/order.yml"
  kubectl apply -f "${CLUSTER}/manifests/payment.yml"
  kubectl apply -f "${CLUSTER}/manifests/stock.yml"
}

stop() {
  helm uninstall kafka

  helm uninstall order-db-0
  helm uninstall order-db-1
  helm uninstall payment-db-0
  helm uninstall payment-db-1
  helm uninstall stock-db-0
  helm uninstall stock-db-1

  kubectl delete -f "${CLUSTER}/manifests/ingress.yml"

  kubectl delete -f "${CLUSTER}/manifests/orderwrapper.yml"
  kubectl delete -f "${CLUSTER}/manifests/paymentwrapper.yml"
  kubectl delete -f "${CLUSTER}/manifests/stockwrapper.yml"

  kubectl delete -f "${CLUSTER}/manifests/order.yml"
  kubectl delete -f "${CLUSTER}/manifests/payment.yml"
  kubectl delete -f "${CLUSTER}/manifests/stock.yml"
}

purge() {
  stop
  kubectl delete pvc --all
  kubectl delete pv --all
}

update_repo() {
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update
}

if [[ "$#" != 2 ]]; then
  usage
fi

case "$1" in
  'k8s') CLUSTER='k8s' ;;
  'minikube') CLUSTER='minikube' ;;
  *) usage ;;
esac

case "$2" in
  'start') start ;;
  'stop') stop ;;
  'purge') purge ;;
  'update-repo') update_repo ;;
  *) usage ;;
esac
