## How to run on minikube:

1. Install minikube, kubectl, docker
2. Install the ingress addon for minikube with `minikube addons enable ingress`
3. Build the docker images with the `build-images.sh` script: `./build-images.sh`
4. Deploy with the `deploy.sh` script: `./deploy.sh minikube start`
5. When your are done, clean the cluster: `./deploy.sh minikube purge`
