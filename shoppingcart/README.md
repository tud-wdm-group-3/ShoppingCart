## How to deploy on a cluster

1. Point your `kubectl` to your cluster
2. Deploy with the `deploy.sh` script: `./deploy.sh k8s start`
3. When done: `./deploy.sh k8s purge` to bring down everything

## How to run on minikube:

1. Install minikube, kubectl, docker, helm
2. Start minikube, preferably with `minikube start --memory 4096 --cpus 4`.
3. Install the ingress addon for minikube with `minikube addons enable ingress`
4. Build the docker images with the `build-images.sh` script: `./build-images.sh`
5. Deploy with the `deploy.sh` script: `./deploy.sh minikube start`
6. Use `minikube dashboard` for debugging
7. When you 're done, bring down the cluster and clean all volumes: `./deploy.sh minikube purge`
8. Shut down minikube: `minikube stop`

#### Acknowledgements

1. We never really used docker-compose so the `docker-compose.yml` is outdated
2. The Dockerfiles are at the parent directory because of the parent-child maven dependencies in the `pom.xml`s
