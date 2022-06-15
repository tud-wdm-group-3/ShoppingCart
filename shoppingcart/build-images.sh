#!/bin/bash
# Builds all the images and pushes them to DockerHub.
# Make sure you are logged in with the docker cli (also change the handle accordingly)

DOCKERHUB_HANDLE='ngavalas'
DOCKERHUB_PUSH='false'
MINIKUBE='true'


build_push() {
  img_name="$1"
  tag="${2:-latest}"

  docker build -t "${img_name}:${tag}" -f "Dockerfile-${img_name}" .

  [[ $MINIKUBE == 'true' ]] && minikube image load "${img_name}:${tag}"

  if [[ $DOCKERHUB_PUSH == 'true' ]]; then
    docker tag "${img_name}:${tag}" "${DOCKERHUB_HANDLE}/${img_name}:${tag}" \
    && docker push "${DOCKERHUB_HANDLE}/${img_name}:${tag}"
  fi
}


build_push order
build_push payment
build_push stock
build_push orderwrapper
build_push paymentwrapper
build_push stockwrapper
