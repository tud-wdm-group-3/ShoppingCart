## How to deploy on a cluster

1. Point your `kubectl` to your cluster
2. Update your helm repository (shortcut: `./deploy.sh k8s update-repo`)
3. Deploy with the `deploy.sh` script as such: `./deploy.sh k8s start`
4. The endpoint is the one provided by the ingress of your cloud provider
5. When done: `./deploy.sh k8s purge` to bring down everything (this also deletes all PVCs in the default namespace of the cluster)

## How to run on minikube

This is a scaled-down version of the deployment without replication, for testing locally.

1. Install minikube, kubectl, docker, helm
2. Start minikube, preferably with `minikube start --memory 4096 --cpus 4`.
3. Install the ingress addon for minikube with `minikube addons enable ingress`
4. Update your helm repository (shortcut: `./deploy.sh k8s update-repo`)
5. Build the docker images with the `build-images.sh` script: `./build-images.sh`
6. Deploy with the `deploy.sh` script: `./deploy.sh minikube start`
7. Use `minikube dashboard` for debugging
8. When you 're done, bring down the deployments and clean all volumes: `./deploy.sh minikube purge`
9. Shut down minikube: `minikube stop`

#### Notes

1. We never really used docker-compose so the `docker-compose.yml` is outdated
2. The Dockerfiles are at the parent directory because of the parent-child maven dependencies in the `pom.xml`s
3. *Scaling*: the deployments are configured to use a sharding factor of 2. Our implementation allows for this factor to be increased at will, but requires manual editing of the manifests - specifically adding another group of microservices' StatefulSets (so for example to use 3 shards, add order-2, payment-2, stock-2 to the already existing order-{0,1}, payment-{0,1}, stock-{0,1}).
