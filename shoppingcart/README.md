## How to run on minikube:

1. Install minikube, kubectl, docker, helm
2. Start minikube, preferably with `minikube start --memory 4096 --cpus 4`.
3. Install the ingress addon for minikube with `minikube addons enable ingress`
4. Build the docker images with the `build-images.sh` script: `./build-images.sh`
5. Deploy with the `deploy.sh` script: `./deploy.sh minikube start`
6. Use `minikube dashboard` for debugging
7. When you 're done, bring down the cluster and clean all volumes: `./deploy.sh minikube purge`
8. Shut down minikube: `minikube stop`
